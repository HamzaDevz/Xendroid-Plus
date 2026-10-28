// Releases publicadas no GitHub: a estável (a mais recente que não é rascunho nem prévia),
// o APK de cada uma e o histórico. Lidas na hora do build; o token do Actions (GITHUB_TOKEN)
// é usado só aqui, no servidor do build, e nunca chega às páginas.
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

/** GET de JSON na API do GitHub; atrás de um proxy sem suporte no fetch, usa o curl. */
async function getJson(url, token) {
  const headers = { Accept: 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'xendroid-plus-site-build' };
  if (token) headers.Authorization = `Bearer ${token}`;
  const viaCurl = (process.env.HTTPS_PROXY || process.env.https_proxy) && !process.env.NODE_USE_ENV_PROXY;
  if (viaCurl) {
    const args = ['-sS', '--fail-with-body', '-D', '-', '--max-time', '30'];
    for (const [k, v] of Object.entries(headers)) args.push('-H', `${k}: ${v}`);
    args.push(url);
    const out = execFileSync('curl', args, { encoding: 'utf8', maxBuffer: 32 * 1024 * 1024 });
    // o proxy acrescenta o próprio bloco de cabeçalhos ("Connection established") antes do da API
    let rest = out;
    let head = '';
    while (/^HTTP\/\S+ \d{3}/.test(rest)) {
      const split = rest.indexOf('\r\n\r\n');
      if (split < 0) break;
      head = rest.slice(0, split);
      rest = rest.slice(split + 4);
    }
    const link = /^link:\s*(.+)$/im.exec(head)?.[1] || '';
    return { body: JSON.parse(rest), link };
  }
  const res = await fetch(url, { headers });
  if (!res.ok) throw new Error(`GitHub ${res.status} em ${url}: ${(await res.text()).slice(0, 200)}`);
  return { body: await res.json(), link: res.headers.get('link') || '' };
}

/** Tag do workflow de release: XenDroid-v<build>-<sha> (updater/ReleaseTags.kt) ou XenDroid-<sha>. */
export function parseTag(tag) {
  let m = /^XenDroid-v(\d+)-([0-9a-f]{7,40})$/i.exec(tag);
  if (m) return { build: Number(m[1]), sha: m[2].toLowerCase() };
  m = /^XenDroid-([0-9a-f]{7,40})$/i.exec(tag);
  if (m) return { build: null, sha: m[1].toLowerCase() };
  return { build: null, sha: null };
}

function simplify(r, repo) {
  const tag = parseTag(r.tag_name);
  const apk = (r.assets || []).find(a => /\.apk$/i.test(a.name));
  // Versão SemVer no nome das releases publicadas pelo CI ("Xendroid+ 1.2.0",
  // "Xendroid+ 1.3.0-preview.88"; tools/release_version.py); as antigas têm o commit.
  const version = /^Xendroid\+ (\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?)$/.exec((r.name || '').trim());
  return {
    tag: r.tag_name,
    name: r.name || r.tag_name,
    version: version ? version[1] : null,
    build: tag.build,
    commit: /^[0-9a-f]{40}$/i.test(r.target_commitish || '') ? r.target_commitish.toLowerCase() : null,
    tagSha: tag.sha,
    url: r.html_url,
    publishedAt: r.published_at,
    prerelease: !!r.prerelease,
    draft: !!r.draft,
    // notas geradas pelo GitHub: links do nome antigo do repositório levam ao atual
    body: (r.body || '').replace(/github\.com\/phforner0\/Xendroid-Fork-main/g, `github.com/${repo}`),
    apk: apk ? {
      name: apk.name,
      url: apk.browser_download_url,
      size: apk.size,
      sha256: apk.digest && apk.digest.startsWith('sha256:') ? apk.digest.slice(7) : null,
    } : null,
  };
}

/**
 * Lê as releases. Com `cacheFile`, guarda a última leitura boa e, se a API falhar fora do
 * modo estrito, usa a cópia guardada (marcada como tal). No modo estrito (CI), uma falha
 * interrompe o build.
 */
export async function readReleases({ repo, token, cacheFile, strict, offline = false }) {
  try {
    if (offline) throw new Error('modo sem rede (--offline)');
    const all = [];
    let url = `https://api.github.com/repos/${repo}/releases?per_page=100`;
    for (let page = 0; url && page < 20; page++) {
      const { body, link } = await getJson(url, token);
      all.push(...body);
      url = /<([^>]+)>;\s*rel="next"/.exec(link)?.[1] || null;
    }
    const releases = all.map(r => simplify(r, repo)).filter(r => !r.draft)
      .sort((a, b) => (b.publishedAt || '').localeCompare(a.publishedAt || ''));
    const data = { fetchedAt: new Date().toISOString(), source: 'api', releases };
    if (cacheFile) {
      fs.mkdirSync(path.dirname(cacheFile), { recursive: true });
      fs.writeFileSync(cacheFile, JSON.stringify(data, null, 2));
    }
    return data;
  } catch (e) {
    if (strict || !cacheFile || !fs.existsSync(cacheFile)) throw new Error(`não foi possível ler as releases do GitHub: ${e.message}`);
    const cached = JSON.parse(fs.readFileSync(cacheFile, 'utf8'));
    console.warn(`aviso: usando a cópia local das releases de ${cached.fetchedAt} (${e.message})`);
    return { ...cached, source: 'cache' };
  }
}

/** A release estável: a mais recente publicada que não é prévia e tem APK. */
export function stableRelease(releases) {
  return releases.find(r => !r.prerelease && r.apk) || null;
}
