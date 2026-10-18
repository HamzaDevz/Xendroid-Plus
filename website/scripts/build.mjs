#!/usr/bin/env node
// Gera o site em website/dist: lê os dados do código (versão estável e de desenvolvimento),
// renderiza as páginas, copia e prepara os recursos e monta o índice da busca.
//
//   node scripts/build.mjs [--strict] [--offline] [--out <pasta>]
//
//   --strict   erros param o build (CI); sem ele, viram avisos
//   --offline  não consulta a API do GitHub; usa a última cópia em .cache/releases.json
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import subsetFont from 'subset-font';
import config from '../site.config.mjs';
import { loadData } from './data.mjs';
import { createMarkdown, frontMatter } from './lib/markdown.mjs';
import { rel, outFile } from './lib/paths.mjs';
import { html, raw, esc, icon } from './lib/html.mjs';
import { git, isAncestor, hasCommit } from './lib/git.mjs';
import { shell } from './site/layout.mjs';
import { landing } from './site/landing.mjs';
import { docPage, docsIndex } from './site/docs.mjs';
import { createBlocks } from './site/blocks.mjs';
import { simulatorPage, writeSimulator, SIM_SCREENS } from './site/simulator.mjs';
import { notFound } from './site/notfound.mjs';
import { PRINTS } from '../content/prints.mjs';
import { PRINT_ALT_EN } from '../content/prints.en.mjs';
import { settingAnchor, textOf } from './site/settings-view.mjs';
import { lang as langOf, LANG_IDS, LANGS } from './site/i18n.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const SITE = path.resolve(HERE, '..');
const ROOT = path.resolve(SITE, '..');
const argv = process.argv.slice(2);
const strict = argv.includes('--strict');
const offline = argv.includes('--offline');
const outArg = argv.indexOf('--out');
const OUT = path.resolve(SITE, outArg >= 0 ? argv[outArg + 1] : 'dist');
const CACHE = path.join(SITE, '.cache');

const problems = [];
const error = msg => problems.push({ level: 'error', msg });
const warn = msg => problems.push({ level: 'warn', msg });

const sha1 = buf => crypto.createHash('sha1').update(buf).digest('hex');
function write(rel, content) {
  const file = path.join(OUT, rel);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, content);
}
function copy(from, to) {
  const file = path.join(OUT, to);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.copyFileSync(from, file);
}
/** Largura e altura de um PNG (cabeçalho IHDR). */
function pngSize(file) {
  const b = fs.readFileSync(file);
  return { w: b.readUInt32BE(16), h: b.readUInt32BE(20) };
}

const t0 = Date.now();
// O workflow do Pages baixa só os caminhos de sparsePaths e roda quando um deles muda: as três
// listas precisam ser iguais, ou o CI deixa de ver um arquivo que o site lê.
{
  const wfFile = path.join(ROOT, '.github/workflows/pages.yml');
  if (!fs.existsSync(wfFile)) warn('.github/workflows/pages.yml não existe: nada publica o site');
  else {
    const wf = fs.readFileSync(wfFile, 'utf8');
    const diff = (what, got, want) => {
      const missing = want.filter(x => !got.includes(x)), extra = got.filter(x => !want.includes(x));
      if (missing.length || extra.length) error(`.github/workflows/pages.yml: ${what} difere de sparsePaths em site.config.mjs (falta: ${missing.join(', ') || 'nada'}; sobra: ${extra.join(', ') || 'nada'})`);
    };
    const sparse = /\n\s*sparse-checkout: \|\n((?:[ \t]+\S.*\n)+)/.exec(wf);
    diff('o checkout esparso', sparse ? sparse[1].split('\n').map(s => s.trim()).filter(Boolean) : [], config.sparsePaths);
    const filters = config.sparsePaths.map(p => p.replace(/^\//, '').replace(/\/$/, '/**'));
    for (const ev of ['push', 'pull_request']) {
      const m = new RegExp(`\\n  ${ev}:\\n(?:    .*\\n)*?    paths:\\n((?:      - .*\\n)+)`).exec(wf);
      diff(`a lista paths de ${ev}`, m ? m[1].split('\n').map(s => s.replace(/^\s*-\s*/, '').replace(/^'(.*)'$/, '$1').trim()).filter(Boolean) : [], filters);
    }
  }
}

console.log('lendo os dados do repositório…');
const data = await loadData({ root: ROOT, config, strict, cacheDir: CACHE, offline });
problems.push(...data.problems);
if (!data.channels.estavel) error('sem os dados da versão estável; o site não tem o que mostrar como versão publicada');

fs.rmSync(OUT, { recursive: true, force: true });
fs.mkdirSync(OUT, { recursive: true });

// ---------- versões ----------
const st = data.stable;
const v = {
  stable: st ? {
    build: st.build,
    label: st.version ? `v${st.version}` : st.build != null ? `Build ${st.build}` : st.name,
    tag: st.tag,
    date: st.publishedAt,
    commit: st.commit,
    short: (st.commit || st.tagSha || '').slice(0, 8),
    url: st.url,
    apk: st.apk,
  } : null,
  dev: { commit: data.head.sha, short: data.head.sha.slice(0, 8), date: data.head.date },
};
const est = data.channels.estavel || data.channels.desenvolvimento;
const devCh = data.channels.desenvolvimento;

// ---------- números da página inicial (sempre da versão estável) ----------
const S = est.settings.settings;
const names = est.games.quirkTitles.map(q => q.name).filter(Boolean);
const logKeep = S.find(s => s.key === 'Logging|log_sessions_keep');
const stats = {
  settings: S.length,
  essential: S.filter(s => s.level === 'ESSENTIAL').length,
  advanced: S.filter(s => s.level !== 'ALL').length,
  live: S.filter(s => s.live).length,
  quirkTitles: est.games.quirkTitles.length,
  quirkEntries: est.games.quirks.length,
  quirkExampleNames: ['Forza Horizon', 'Gears of War 3', 'Ninja Gaiden II'].filter(n => names.includes(n)).concat(names.filter(n => !['Forza Horizon', 'Gears of War 3', 'Ninja Gaiden II'].includes(n))).slice(0, 3),
  patchTitles: est.games.patches.length,
  patchFiles: est.games.patchFileCount,
  turnipFlags: est.settings.turnipFlags.length,
  logSessions: logKeep ? logKeep.default : null,
  cvars: est.cvarCount,
};

// ---------- prints do app ----------
const printOut = {};
{
  const jobs = [];
  fs.mkdirSync(path.join(CACHE, 'prints'), { recursive: true });
  for (const [id, p] of Object.entries(PRINTS)) {
    const src = path.join(ROOT, p.src);
    if (!fs.existsSync(src)) { error(`print "${id}": ${p.src} não existe`); continue; }
    const buf = fs.readFileSync(src);
    const h = sha1(buf).slice(0, 10);
    const { w, h: hh } = pngSize(src);
    printOut[id] = { ...p, hash: h, w, h: hh, cached: path.join(CACHE, 'prints', `${h}.webp`) };
    if (!fs.existsSync(printOut[id].cached)) jobs.push({ src, out: printOut[id].cached });
  }
  let webp = true;
  if (jobs.length) {
    try {
      execFileSync('python3', ['-I', path.join(HERE, 'prints.py')], { input: JSON.stringify(jobs), stdio: ['pipe', 'pipe', 'pipe'] });
    } catch (e) {
      webp = false;
      warn(`prints copiados em PNG: a conversão para WebP precisa de python3 com Pillow (${String(e.stderr || e.message).trim().split('\n').pop()})`);
    }
  }
  for (const [id, p] of Object.entries(printOut)) {
    if (webp && fs.existsSync(p.cached)) {
      p.file = `assets/prints/${id}.${p.hash}.webp`;
      copy(p.cached, p.file);
    } else {
      p.file = `assets/prints/${id}.${p.hash}.png`;
      copy(path.join(ROOT, p.src), p.file);
    }
  }
}

/** Figura com um print real: a moldura de aparelho e a legenda (os prints mostram o app em português). */
function shot(pagePath, id, { caption = null, eager = false, portrait = false, lang = 'pt' } = {}) {
  const p = printOut[id];
  if (!p) { error(`print desconhecido: ${id}`); return ''; }
  const alt = lang === 'en' ? PRINT_ALT_EN[id] : p.alt;
  if (!alt) error(`print "${id}" sem texto alternativo em ${lang === 'en' ? 'content/prints.en.mjs' : 'content/prints.mjs'}`);
  const vertical = portrait || p.h > p.w;
  return html`<figure class="shot${vertical ? ' portrait' : ''}">
  <div class="shot-frame"><img src="${rel(pagePath, p.file)}" width="${p.w}" height="${p.h}" alt="${alt}"${eager ? raw(' fetchpriority="high"') : raw(' loading="lazy" decoding="async"')}></div>
  ${caption ? html`<figcaption>${caption}${lang === 'en' ? html` <span class="u-muted">The app is shown in Portuguese.</span>` : ''}</figcaption>` : ''}
</figure>`;
}

// ---------- fontes de cada dado (página "Dados do site") ----------
const sourcesTable = ({ tx }) => [
  { what: tx('Settings (name, group, level, texts, options)', 'Ajustes (nome, grupo, nível, textos, opções)'), files: [data.sources.settings.schema, data.sources.settings.catalog, data.sources.settings.texts, data.sources.settings.xd], how: tx('read from the Kotlin code, with the texts of values/ (English) and values-pt-rBR (Portuguese, English where a translation is missing)', 'lidos do Kotlin, com os textos de values-pt-rBR (inglês quando falta tradução) e, nas páginas em inglês, de values/'), channel: tx('stable and development', 'estável e desenvolvimento') },
  { what: tx('Default of each setting', 'Padrão de cada ajuste'), files: [data.sources.settings.template, data.sources.settings.schema], how: tx('the value in the default_config.toml template; without it, the schema’s, as the app does', 'o valor do modelo default_config.toml; sem ele, o do esquema, como o app faz'), channel: tx('stable and development', 'estável e desenvolvimento') },
  { what: tx('Core cvars (type, section, default)', 'Cvars do núcleo (tipo, seção, padrão)'), files: ['emulator-core/src/main/cpp/xenia/src/xenia/'], how: tx('DEFINE_* in the C++ code, with the Android build’s preprocessor', 'DEFINE_* do C++, com o pré-processador do build Android'), channel: tx('stable and development', 'estável e desenvolvimento') },
  { what: tx('Automatic per-game fixes', 'Correções automáticas por jogo'), files: [data.sources.games.quirks], how: tx('the game_quirks.cc table', 'tabela do game_quirks.cc'), channel: tx('stable', 'estável') },
  { what: tx('Game patches', 'Patches de jogos'), files: [data.sources.games.patches], how: tx('*.patch.toml files copied into the APK by the build', 'arquivos *.patch.toml copiados para o APK no build'), channel: tx('stable', 'estável') },
  { what: tx('Game names by Title ID', 'Nomes de jogos por Title ID'), files: [data.sources.games.patches, data.sources.games.titles], how: tx('the patch file name; otherwise Xenia’s vendored database (name only)', 'nome do arquivo de patch; senão a base vendorizada do Xenia (só o nome)'), channel: tx('stable', 'estável') },
  { what: tx('Package, minimum Android, ABI and folders', 'Pacote, Android mínimo, ABI e pastas'), files: data.sources.app, how: tx('defaults in build.gradle, in the workflow and in the code', 'padrões no build.gradle, no workflow e no código'), channel: tx('stable and development', 'estável e desenvolvimento') },
  { what: tx('Performance measurements', 'Medições de desempenho'), files: [data.sources.games.readmeEn, data.sources.games.readmePt], how: tx('the "## Performance snapshot" table of README.md (English pages) and "## Desempenho" of README.pt-BR.md (Portuguese pages)', 'tabela "## Desempenho" do README.pt-BR.md (páginas em português) e "## Performance snapshot" do README.md (páginas em inglês)'), channel: tx('development (current README)', 'desenvolvimento (README atual)') },
  { what: tx('Published versions and APK', 'Versões publicadas e APK'), files: [], how: tx('GitHub releases API, when the site is built; English pages show each release’s update-summary:en block, Portuguese pages the update-summary:pt-BR one', 'API de releases do GitHub, no momento do build; as páginas em português mostram o bloco update-summary:pt-BR de cada release, as em inglês o update-summary:en'), channel: 'releases' },
  { what: tx('Screenshots', 'Prints das telas'), files: ['docs/ui-redesign/prints/app/'], how: tx('images from the screen tests (Roborazzi), converted to WebP', 'imagens dos testes de tela (Roborazzi), convertidas para WebP'), channel: tx('those in the repository', 'as do repositório') },
];

const site = {
  config,
  data,
  v,
  ch: { est, dev: devCh },
  stats,
  builtAt: new Date().toISOString(),
  assetVersion: '',
  shot,
  sourcesTable,
  rel,
  docs: { en: [], pt: [] },
  /** Tabela de medições do README no idioma da página. */
  perf: id => (id === 'en' ? devCh.games.perfEn : devCh.games.perf),
};

// ---------- documentação ----------
// content/docs/*.md em português (o slug é a chave que pareia as páginas) e content/docs/en/*.md
// em inglês, cada uma com "pt: <slug em português>" no front matter.
const DOCS_DIR = path.join(SITE, 'content', 'docs');
const sectionOrder = new Map(config.docSections.map((s, i) => [s.id, i]));
for (const lid of LANG_IDS) {
  const dir = lid === 'pt' ? DOCS_DIR : path.join(DOCS_DIR, lid);
  for (const file of fs.readdirSync(dir).filter(f => f.endsWith('.md')).sort()) {
    const rel = path.relative(SITE, path.join(dir, file));
    const src = fs.readFileSync(path.join(dir, file), 'utf8');
    const { data: fm, body } = frontMatter(src, rel);
    for (const k of ['title', 'description', 'section', 'order']) if (fm[k] == null) error(`${rel}: falta "${k}" no front matter`);
    if (lid !== 'pt' && !fm.pt) error(`${rel}: falta "pt" (o slug da página em português) no front matter`);
    if (fm.section && !sectionOrder.has(fm.section)) error(`${rel}: seção desconhecida "${fm.section}"`);
    const slug = file.replace(/\.md$/, '');
    const key = lid === 'pt' ? slug : fm.pt;
    site.docs[lid].push({ file: rel, lang: lid, slug, key, path: `${LANGS[lid].prefix}docs/${slug}/`, body, ...fm, fontes: fm.fontes || [], headings: [], ids: new Set() });
  }
  site.docs[lid].sort((a, b) => (sectionOrder.get(a.section) - sectionOrder.get(b.section)) || (a.order - b.order));
}
const docByKey = Object.fromEntries(LANG_IDS.map(lid => [lid, new Map(site.docs[lid].map(d => [d.key, d]))]));
for (const d of site.docs.en) if (!docByKey.pt.has(d.key)) error(`${d.file}: "pt: ${d.key}" não corresponde a nenhuma página em content/docs/`);
for (const d of site.docs.pt) if (!docByKey.en.has(d.key)) error(`${d.file}: a página não tem versão em inglês em content/docs/en/ (com "pt: ${d.key}")`);
for (const d of site.docs.en) {
  const p = docByKey.pt.get(d.key);
  if (p && (p.section !== d.section || p.order !== d.order)) error(`${d.file}: seção ou ordem diferente da página em português (${p.file})`);
}
/** Endereço da página de documentação `key` (o slug em português; '' é o índice) no idioma `lid`. */
site.docPath = (lid, key) => {
  if (!key) return `${LANGS[lid].prefix}docs/`;
  const d = docByKey[lid].get(key);
  if (!d) { error(`página de documentação "${key}" não existe em ${lid}`); return `${LANGS[lid].prefix}docs/`; }
  return d.path;
};
/** Os endereços de uma página nos dois idiomas: 'home', 'sim', '' (índice da documentação) ou a chave de uma página. */
site.alternatesOf = key => Object.fromEntries(LANG_IDS.map(lid => {
  const L = langOf(lid);
  if (key === 'home') return [lid, L.home];
  if (key === 'sim') return [lid, L.simPath];
  if (key === '') return [lid, L.docs];
  return [lid, docByKey[lid].has(key) ? docByKey[lid].get(key).path : null];
}).filter(([, p]) => p != null));

/** Valores para {{caminho}} no Markdown, formatados no idioma da página. */
function valuesFor(L) {
  const size = n => `${(n / 1024 / 1024).toLocaleString(L.locale, { maximumFractionDigits: 1 })} MB`;
  return {
    estavel: v.stable ? {
      build: v.stable.build, nome: v.stable.label.toLowerCase(), tag: v.stable.tag, commit: v.stable.short,
      data: L.dateLong(v.stable.date),
      apk: v.stable.apk ? { nome: v.stable.apk.name, tamanho: size(v.stable.apk.size), sha256: v.stable.apk.sha256 || L.tx('not published', 'não publicado'), url: v.stable.apk.url } : {},
      url: v.stable.url,
    } : {},
    dev: { commit: v.dev.short },
    app: {
      pacote: est.app.package, pacoteTeste: est.app.testPackage, abi: est.app.abi,
      androidMin: est.app.minAndroid, apiMin: est.app.minSdk, androidAlvo: est.app.targetAndroid, apiAlvo: est.app.targetSdk,
      pastaDados: est.app.dataRoot, configGlobal: est.app.globalConfig, configJogo: est.app.gameConfigPattern, pastaConfigJogos: est.app.gameConfigDir,
      repoAtualizacoes: est.app.updateRepo,
    },
    contagens: { ...stats, essencial: stats.essential, avancado: stats.advanced, ajustes: stats.settings, aoVivo: stats.live, jogosCorrecoes: stats.quirkTitles, correcoes: stats.quirkEntries, jogosPatches: stats.patchTitles, arquivosPatch: stats.patchFiles, flagsTurnip: stats.turnipFlags, sessoesLog: stats.logSessions },
    repo: config.repoUrl,
    discord: config.discord,
  };
}

/** Rótulo de um bloco "::: versao desde=<sha>" pela primeira release que contém o commit. */
function versionLabel(args, env, L) {
  const { tx } = L;
  const sha = args.desde || args.ate;
  if (!sha) throw new Error(`${env.file}: "::: versao" precisa de desde=<commit> ou ate=<commit>`);
  const full = git(ROOT, ['rev-parse', '--verify', '--quiet', `${sha}^{commit}`], { allowFail: true });
  if (!full) throw new Error(`${env.file}: commit ${sha} não encontrado`);
  const rels = data.releases.releases.filter(r => r.commit && r.build != null).slice().reverse();
  const first = rels.find(r => isAncestor(ROOT, full, r.commit) === true);
  if (args.desde) {
    if (first) return { label: tx(`Since build ${first.build}${v.stable && first.build === v.stable.build ? ' (the current stable)' : ''}`, `Desde a build ${first.build}${v.stable && first.build === v.stable.build ? ' (a estável atual)' : ''}`), kind: 'stable' };
    if (isAncestor(ROOT, full, v.dev.commit)) return { label: tx('Only in development (main), no release yet', 'Só no desenvolvimento (main), ainda sem release'), kind: 'dev' };
    throw new Error(`${env.file}: o commit ${sha} não está no main`);
  }
  if (first) {
    const before = rels[rels.indexOf(first) - 1] ? rels[rels.indexOf(first) - 1].build : '—';
    return { label: tx(`Up to build ${before}; changed in build ${first.build}`, `Até a build ${before}; mudou na build ${first.build}`), kind: 'stable' };
  }
  return { label: tx('Applies until the next release (main has already changed)', 'Vale até a próxima release (o main já mudou)'), kind: 'dev' };
}

function repoLink(p) {
  const [file, line] = p.split(':');
  const clean = file.replace(/#.*$/, '');
  if (!fs.existsSync(path.join(ROOT, clean))) throw new Error(`link repo:${p}: o arquivo não existe no repositório`);
  const isDir = fs.statSync(path.join(ROOT, clean)).isDirectory();
  if (line) {
    const ref = v.stable ? v.stable.commit : 'main';
    return `${config.repoUrl}/blob/${ref}/${clean}#L${line}`;
  }
  return `${config.repoUrl}/${isDir ? 'tree' : 'blob'}/main/${file}`;
}

const docLinks = [];
for (const lid of LANG_IDS) {
  const L = langOf(lid);
  const md = createMarkdown({
    lang: lid,
    data: valuesFor(L),
    resolveDoc(slug, anchor, env) {
      const d = site.docs[lid].find(x => x.slug === slug);
      if (slug !== '' && !d) throw new Error(`${env.file}: link para doc:${slug}, que não existe em ${lid === 'pt' ? 'content/docs/' : `content/docs/${lid}/`}`);
      if (anchor) docLinks.push({ from: env.file, lid, slug, anchor });
      return rel(env.pagePath, slug === '' ? L.docs : d.path) + (anchor ? `#${anchor}` : '');
    },
    repoUrl: p => repoLink(p),
    toolUrl(screen, env) {
      if (screen && !SIM_SCREENS.includes(screen)) throw new Error(`${env.file}: tela "${screen}" não existe no simulador`);
      return rel(env.pagePath, L.simPath) + (screen ? `#${screen}` : '');
    },
    version: (args, env) => versionLabel(args, env || {}, L),
    icon: (n, s) => String(icon(n, s)),
    blocks: createBlocks(site, L),
  });

  for (const doc of site.docs[lid]) {
    const env = { file: doc.file, pagePath: doc.path, title: doc.title };
    try {
      const r = md.render(doc.body, env);
      doc.html = r.html;
      doc.headings = r.headings;
      doc.sections = r.sections;
      for (const m of r.html.matchAll(/\sid="([^"]+)"/g)) doc.ids.add(m[1]);
      for (const a of env.anchorsUsed || []) if (!doc.ids.has(a)) error(`${env.file}: âncora #${a} não existe na página`);
    } catch (e) {
      error(e.message);
      doc.html = `<p>${esc(L.tx('Error generating this page', 'Erro ao gerar esta página'))}: ${esc(e.message)}</p>`;
      doc.sections = [];
    }
    // revisão: o commit com que a página foi conferida e o que mudou depois dele
    if (doc.conferido) {
      const full = git(ROOT, ['rev-parse', '--verify', '--quiet', `${doc.conferido}^{commit}`], { allowFail: true });
      if (!full) {
        warn(`${env.file}: commit de revisão ${doc.conferido} não está no clone`);
        doc.review = { short: String(doc.conferido).slice(0, 8), date: null, changed: null };
      } else {
        const date = git(ROOT, ['log', '-1', '--format=%cI', full]);
        const target = v.stable && v.stable.commit && hasCommit(ROOT, v.stable.commit) ? v.stable.commit : v.dev.commit;
        const changed = doc.fontes.length ? (git(ROOT, ['diff', '--name-only', full, target, '--', ...doc.fontes], { allowFail: true }) || '').split('\n').filter(Boolean) : [];
        for (const f of doc.fontes) if (!fs.existsSync(path.join(ROOT, f))) error(`${env.file}: fonte ${f} não existe`);
        doc.review = { short: full.slice(0, 8), date, changed };
        if (changed.length) warn(`${env.file}: ${changed.length} fonte(s) mudaram depois da revisão ${full.slice(0, 8)}`);
      }
    }
  }
}
for (const l of docLinks) {
  const d = site.docs[l.lid].find(x => x.slug === l.slug);
  if (d && !d.ids.has(l.anchor)) error(`${l.from}: link doc:${l.slug}#${l.anchor}, mas a âncora não existe`);
}
// as duas versões de uma página têm as mesmas seções com âncora explícita nos links entre páginas
for (const d of site.docs.en) {
  const p = docByKey.pt.get(d.key);
  if (p && p.headings.length !== d.headings.length) warn(`${d.file}: ${d.headings.length} títulos, e a página em português (${p.file}) tem ${p.headings.length}`);
}

// ---------- CSS e JS ----------
const css = ['tokens', 'base', 'layout', 'components', 'landing', 'docs'].map(n => fs.readFileSync(path.join(SITE, 'src/css', `${n}.css`), 'utf8')).join('\n');
write('assets/css/site.css', css);
for (const f of fs.readdirSync(path.join(SITE, 'src/js'))) copy(path.join(SITE, 'src/js', f), `assets/js/${f}`);
copy(path.join(SITE, 'node_modules/minisearch/dist/es/index.js'), 'assets/js/vendor/minisearch.js');
site.printFiles = Object.fromEntries(Object.entries(printOut).map(([id, p]) => [id, { file: p.file, w: p.w, h: p.h }]));
const simAssets = Object.fromEntries(LANG_IDS.map(lid => [lid, writeSimulator(site, langOf(lid), { SITE, ROOT, OUT, write, copy, error, warn })]));
site.assetVersion = sha1(css + fs.readFileSync(path.join(SITE, 'src/js/site.js'), 'utf8') + fs.readFileSync(path.join(SITE, 'src/js/search.js'), 'utf8') + LANG_IDS.map(lid => simAssets[lid].hash).join('')).slice(0, 10);

// ---------- fontes tipográficas do app, recortadas ----------
const FONTS = {
  'barlow-400': 'barlow_regular.ttf', 'barlow-500': 'barlow_medium.ttf', 'barlow-600': 'barlow_semibold.ttf', 'barlow-700': 'barlow_bold.ttf',
  'barlow-semi-condensed-600': 'barlow_semi_condensed_semibold.ttf', 'barlow-semi-condensed-700': 'barlow_semi_condensed_bold.ttf',
  'jetbrains-mono': 'jetbrains_mono.ttf',
};
const GLYPHS = (() => {
  let s = '';
  for (let c = 0x20; c < 0x7f; c++) s += String.fromCharCode(c);
  for (let c = 0xa0; c <= 0x17f; c++) s += String.fromCharCode(c);
  return s + '‐‑‒–—―‘’‚“”„†‡•…‰′″‹›€™←↑→↓↔⇄≈≠≤≥×÷±°√∞≡◀▶▲▼●○■□✓✕⧉⌘⏎';
})();
fs.mkdirSync(path.join(CACHE, 'fonts'), { recursive: true });
for (const [name, file] of Object.entries(FONTS)) {
  const src = path.join(ROOT, 'app/src/main/res/font', file);
  if (!fs.existsSync(src)) { error(`fonte ${file} não existe em app/src/main/res/font`); continue; }
  const buf = fs.readFileSync(src);
  const cached = path.join(CACHE, 'fonts', `${name}.${sha1(buf + GLYPHS).slice(0, 10)}.woff2`);
  if (!fs.existsSync(cached)) fs.writeFileSync(cached, await subsetFont(buf, GLYPHS, { targetFormat: 'woff2' }));
  copy(cached, `assets/fonts/${name}.woff2`);
}
// licença OFL das fontes, que acompanha os arquivos
for (const lic of ['Barlow-OFL.txt', 'JetBrainsMono-OFL.txt']) {
  const p = path.join(ROOT, 'app/src/main/assets/font-licenses', lic);
  if (fs.existsSync(p)) copy(p, `assets/fonts/${lic}`);
}

// ---------- marca ----------
for (const f of fs.readdirSync(path.join(SITE, 'src/assets/brand'))) copy(path.join(SITE, 'src/assets/brand', f), `assets/brand/${f}`);
copy(path.join(SITE, 'src/assets/brand/favicon.ico'), 'favicon.ico');
write('site.webmanifest', JSON.stringify({
  name: 'Xendroid+', short_name: 'Xendroid+', lang: 'en', start_url: './', scope: './', display: 'browser',
  background_color: '#0f1214', theme_color: '#0f1214',
  icons: [{ src: 'assets/brand/icon-192.png', sizes: '192x192', type: 'image/png' }, { src: 'assets/brand/icon-512.png', sizes: '512x512', type: 'image/png' }],
}, null, 2));

// ---------- páginas ----------
const pages = [];
function emit({ page, content }) {
  write(outFile(page.path), String(shell(site, page, content)));
  if (!page.noindex) pages.push(page);
}
for (const lid of LANG_IDS) {
  const L = langOf(lid);
  emit(landing(site, L));
  emit(docsIndex(site, L));
  for (const doc of site.docs[lid]) emit(docPage(site, doc, L));
  emit(simulatorPage(site, simAssets[lid], L));
}
const nf = notFound(site);
write('404.html', String(shell(site, nf.page, nf.content)));

// endereços antigos: até a versão bilíngue, o português ficava na raiz (docs/<slug>/ e simulador/);
// quem tem um desses links cai na mesma página em pt-br/, com a âncora (#…) preservada
{
  const taken = new Set(pages.map(p => p.path));
  const moved = [['simulador/', langOf('pt').simPath], ...site.docs.pt.map(d => [`docs/${d.key}/`, d.path])].filter(([from]) => !taken.has(from));
  for (const [from, to] of moved) {
    const target = rel(from, to);
    write(outFile(from), `<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<meta name="robots" content="noindex">
<title>Xendroid+</title>
<link rel="canonical" href="${esc(config.url + to)}">
<meta http-equiv="refresh" content="0; url=${esc(target)}">
<script>location.replace(${JSON.stringify(target)} + location.hash)</script>
</head>
<body><p>Esta página mudou para <a href="${esc(target)}">${esc(config.url + to)}</a>.</p></body>
</html>
`);
  }
}

// ---------- busca (um índice por idioma) ----------
for (const lid of LANG_IDS) {
  const L = langOf(lid);
  const searchDocs = [];
  for (const doc of site.docs[lid]) {
    for (const s of doc.sections || []) {
      if (!s.text && !s.title) continue;
      searchDocs.push({ id: `${doc.slug}#${s.id}`, t: s.title || doc.title, p: doc.title, u: `${doc.path}${s.id ? `#${s.id}` : ''}`, x: s.text.slice(0, 420) });
    }
  }
  const ref = site.docPath(lid, 'referencia-de-ajustes');
  const refTitle = docByKey[lid].get('referencia-de-ajustes').title;
  for (const s of S) {
    searchDocs.push({ id: `ajuste:${s.key}`, t: textOf(s.title, lid), p: refTitle, u: `${ref}#${settingAnchor(s, lid)}`, x: textOf(s.desc, lid).slice(0, 300), k: `${s.section || ''} ${s.name || ''} ${s.key} ${s.englishTitle || ''} ${lid === 'en' ? textOf(s.title, 'pt') : ''}` });
  }
  write(`assets/search-${lid}.json`, JSON.stringify({ v: site.assetVersion, lang: L.html, docs: searchDocs }));
}

// ---------- dados publicados ----------
function channelJson(ch, id) {
  return {
    canal: id,
    versao: id === 'estavel' ? v.stable : { commit: v.dev.commit, data: v.dev.date },
    app: ch.app,
    ajustes: ch.settings.settings,
    grupos: ch.settings.groups,
    predefinicoes: ch.settings.presets,
    flagsTurnip: ch.settings.turnipFlags,
    correcoesPorJogo: ch.games.quirkTitles,
    consistencia: ch.consistency,
  };
}
write('dados/estavel.json', JSON.stringify(channelJson(est, 'estavel'), null, 1));
write('dados/desenvolvimento.json', JSON.stringify(channelJson(devCh, 'desenvolvimento'), null, 1));
write('dados/relatorio.json', JSON.stringify({ geradoEm: site.builtAt, commit: v.dev.commit, estavel: v.stable, problemas: problems }, null, 1));

// ---------- arquivos do GitHub Pages ----------
write('.nojekyll', '');
write('robots.txt', `User-agent: *\nAllow: /\nSitemap: ${config.url}sitemap.xml\n`);
// cada endereço com as versões nos dois idiomas (hreflang), como no <head> das páginas
const alternates = p => Object.entries(p.alternates || {}).map(([lid, to]) => `<xhtml:link rel="alternate" hreflang="${LANGS[lid].html}" href="${esc(config.url + to)}"/>`).join('')
  + (p.alternates && p.alternates.en != null ? `<xhtml:link rel="alternate" hreflang="x-default" href="${esc(config.url + p.alternates.en)}"/>` : '');
write('sitemap.xml', `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">\n${pages.map(p => `  <url><loc>${esc(config.url + p.path)}</loc><lastmod>${site.builtAt.slice(0, 10)}</lastmod>${alternates(p)}</url>`).join('\n')}\n</urlset>\n`);

// ---------- relatório ----------
const errors = problems.filter(p => p.level === 'error');
const warnings = problems.filter(p => p.level !== 'error');
for (const p of warnings) console.warn(`aviso: ${p.msg}`);
for (const p of errors) console.error(`erro: ${p.msg}`);
console.log(`${pages.length} páginas em ${path.relative(process.cwd(), OUT) || OUT} · ${warnings.length} avisos · ${errors.length} erros · ${((Date.now() - t0) / 1000).toFixed(1)} s`);
if (errors.length && strict) process.exit(1);
