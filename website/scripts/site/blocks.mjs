// Blocos gerados para a documentação: {{> nome arg=valor}} numa linha do Markdown.
// Cada bloco lê os dados extraídos do código; nenhum traz número ou lista escrito à mão.
// Os textos saem no idioma da página (`L.id`): os do app vêm do próprio app em cada idioma.
import { html, raw, esc, icon, langText } from '../lib/html.mjs';
import { slugify } from '../lib/paths.mjs';
import { levelLabel, settingAnchor, textOf, valueLabel, applyLabel } from './settings-view.mjs';
import { releaseSummary } from './releases-view.mjs';

function tableWrap(label, head, rows) {
  return html`<div class="table-wrap" tabindex="0" role="region" aria-label="${label}"><table><thead><tr>${head.map(h => html`<th scope="col">${h}</th>`)}</tr></thead><tbody>${rows}</tbody></table></div>`;
}

/** Âncora de um grupo de ajustes: grupo-imagem / group-image. */
export function groupAnchor(g, L) {
  return `${L.tx('group', 'grupo')}-${slugify(textOf(g.title, L.id))}`;
}

function settingBlock(L, s, ch) {
  const { tx, id } = L;
  const t = x => raw(langText(x, id));
  const texts = ch.settings.texts;
  const def = s.type === 'action' ? null : valueLabel(s, s.default, texts, id);
  const opts = s.type === 'list' ? s.options.map(o => textOf(o.label, id)) : null;
  const n = x => L.num(x);
  const range = s.type === 'int' && s.min != null
    ? tx(`${n(s.min)} to ${n(s.max)}${s.unit || ''}${s.step && s.step !== 1 ? `, in steps of ${s.step}` : ''}`,
      `de ${n(s.min)} a ${n(s.max)}${s.unit || ''}${s.step && s.step !== 1 ? `, de ${s.step} em ${s.step}` : ''}`)
    : null;
  const dep = s.dependsOn ? ch.settings.settings.find(x => x.key === s.dependsOn.key) : null;
  return html`<div class="setting" id="${settingAnchor(s, id)}">
  <h4>${t(s.title)} <span class="key">${s.section ? `[${s.section}] ${s.name}` : s.key}</span>${s.isNew ? html` <span class="tag info">${tx('new in the interface', 'novo na interface')}</span>` : ''}</h4>
  <p>${t(s.desc)}</p>
  <p class="facts-line">
    ${def != null ? html`<span>${tx('Default', 'Padrão')}: <b>${def}</b></span>` : ''}
    <span>${tx('Level', 'Nível')}: <b>${levelLabel(s.level, texts, id)}</b></span>
    <span>${tx('Applies', 'Vale')} ${applyLabel(s, id)}</span>
    ${range ? html`<span>${tx('Range', 'Faixa')}: ${range}</span>` : ''}
  </p>
  ${opts ? html`<p class="opts">${tx('Options', 'Opções')}: ${opts.join(' · ')}</p>` : ''}
  ${dep ? html`<p class="opts">${icon('info', 15)} ${tx(`Only applies with “${textOf(dep.title, id)}” set to ${valueLabel(dep, s.dependsOn.value, texts, id)}.`, `Só vale com “${textOf(dep.title, id)}” em ${valueLabel(dep, s.dependsOn.value, texts, id)}.`)}</p>` : ''}
  ${s.warning ? html`<p class="opts">${icon('warn', 15)} ${t(s.warning)}</p>` : ''}
</div>`;
}

export function createBlocks(site, L) {
  const { tx, id } = L;
  const t = x => raw(langText(x, id));
  const ch = () => site.ch.est;
  const stableName = () => (site.v.stable ? site.v.stable.label.toLowerCase() : tx('the stable version', 'versão estável'));
  const docPath = slug => site.docPath(L.id, slug);

  return {
    /** Os ajustes de um grupo (ou todos), na ordem da interface. */
    'ajustes'(args) {
      const c = ch();
      const groups = args.grupo ? [args.grupo] : c.settings.groups.map(g => g.id);
      const headings = [];
      const out = groups.map(gid => {
        const list = c.settings.settings.filter(s => s.group === gid).sort((a, b) => a.order - b.order);
        if (!list.length) throw new Error(`grupo de ajustes desconhecido ou vazio: ${gid}`);
        if (args.grupo) return list.map(s => settingBlock(L, s, c)).join('');
        const g = c.settings.groups.find(x => x.id === gid);
        const title = textOf(g.title, id);
        const anchor = groupAnchor(g, L);
        headings.push({ level: 2, id: anchor, text: title });
        const lv = l => list.filter(s => s.level === l).length;
        const lvName = l => levelLabel(l, c.settings.texts, id);
        return html`<h2 id="${anchor}">${title} <a class="anchor" href="#${anchor}" aria-label="${tx('Link to this section', 'Link para esta seção')}: ${title}">#</a></h2>
<p class="u-muted">${tx(`${list.length} settings`, `${list.length} ajustes`)} · ${lvName('ESSENTIAL')} ${lv('ESSENTIAL')}, ${lvName('ADVANCED')} ${lv('ADVANCED')}, ${lvName('ALL')} ${lv('ALL')}</p>
${raw(list.map(s => settingBlock(L, s, c)).join(''))}`;
      }).join('');
      return { html: out, headings };
    },

    /** Contagem por grupo e nível, numa tabela. */
    'ajustes-resumo'(args, env) {
      const c = ch();
      const ref = site.rel(env.pagePath, docPath('referencia-de-ajustes'));
      const lvName = l => levelLabel(l, c.settings.texts, id);
      const rows = c.settings.groups.map(g => {
        const list = c.settings.settings.filter(s => s.group === g.id);
        const n = l => list.filter(s => s.level === l).length;
        return html`<tr><th scope="row"><a href="${ref}#${groupAnchor(g, L)}">${t(g.title)}</a></th><td>${n('ESSENTIAL')}</td><td>${n('ADVANCED')}</td><td>${n('ALL')}</td><td>${list.length}</td></tr>`;
      });
      return { html: String(tableWrap(tx('Settings by group and level', 'Ajustes por grupo e nível'), [tx('Group', 'Grupo'), lvName('ESSENTIAL'), lvName('ADVANCED'), lvName('ALL'), 'Total'], rows)) };
    },

    /** Diferenças de ajustes entre a estável e o desenvolvimento. */
    'ajustes-diferencas'() {
      if (site.data.sameChannels || !site.ch.dev) return { html: String(html`<p>${tx(`The ${stableName()} and the current main have the same settings.`, `A ${stableName()} e o main atual têm os mesmos ajustes.`)}</p>`) };
      const a = new Map(site.ch.est.settings.settings.map(s => [s.key, s]));
      const b = new Map(site.ch.dev.settings.settings.map(s => [s.key, s]));
      const added = [...b.keys()].filter(k => !a.has(k));
      const removed = [...a.keys()].filter(k => !b.has(k));
      const changed = [...b.keys()].filter(k => a.has(k) && (String(a.get(k).default) !== String(b.get(k).default) || JSON.stringify(a.get(k).options) !== JSON.stringify(b.get(k).options)));
      if (!added.length && !removed.length && !changed.length) return { html: `<p>${esc(tx('The settings are the same in both versions.', 'Os ajustes são os mesmos nas duas versões.'))}</p>` };
      const li = (k, s) => html`<li><code>${k}</code> ${s ? html`(${t(s.title)})` : ''}</li>`;
      return { html: String(html`${added.length ? html`<p>${tx('New in development:', 'Novos no desenvolvimento:')}</p><ul>${added.map(k => li(k, b.get(k)))}</ul>` : ''}${removed.length ? html`<p>${tx('Removed in development:', 'Saíram no desenvolvimento:')}</p><ul>${removed.map(k => li(k, a.get(k)))}</ul>` : ''}${changed.length ? html`<p>${tx('With a different default or options:', 'Com padrão ou opções diferentes:')}</p><ul>${changed.map(k => li(k, b.get(k)))}</ul>` : ''}`) };
    },

    'predefinicoes'() {
      const c = ch();
      const byKey = new Map(c.settings.settings.map(s => [s.key, s]));
      const rows = c.settings.presets.map(p => html`<tr><th scope="row">${t(p.title)}</th><td>${t(p.description)}</td><td>${Object.entries(p.values).map(([k, v], i) => {
        const s = byKey.get(k);
        return html`${i ? '; ' : ''}${s ? textOf(s.title, id) : k}: ${s ? valueLabel(s, v, c.settings.texts, id) : v}`;
      })}</td></tr>`);
      return { html: String(tableWrap(tx('Presets', 'Predefinições'), [tx('Preset', 'Predefinição'), tx('What it is for', 'Para quê'), tx('What it changes', 'O que muda')], rows)) };
    },

    'turnip-flags'() {
      const rows = ch().settings.turnipFlags.map(f => html`<tr><th scope="row"><code>${f.name}</code></th><td>${t(f.help)}${f.exclusive ? html` <span class="tag plain">${tx('excludes', 'exclui')} ${f.name === 'sysmem' ? 'gmem' : 'sysmem'}</span>` : ''}</td></tr>`);
      return { html: String(tableWrap(tx('Turnip flags', 'Flags do Turnip'), ['Flag', tx('What it does', 'O que faz')], rows)) };
    },

    'correcoes'() {
      const g = ch().games;
      const rows = g.quirkTitles.map(q => html`<tr><td><code>${q.titleId}</code></td><th scope="row">${q.name || tx('no known name', 'sem nome conhecido')}</th><td>${q.entries.length}</td><td>${q.entries.slice(0, 4).map((e, i) => html`${i ? ', ' : ''}<code>${e.cvar}</code>`)}${q.entries.length > 4 ? tx(` and ${q.entries.length - 4} more`, ` e mais ${q.entries.length - 4}`) : ''}</td></tr>`);
      return { html: String(tableWrap(tx('Automatic per-game fixes', 'Correções automáticas por jogo'), ['Title ID', tx('Game', 'Jogo'), tx('Settings', 'Ajustes'), 'Cvars'], rows)) };
    },

    'desempenho-readme'() {
      const perf = site.perf(id);
      if (!perf) throw new Error(`${id === 'en' ? 'README.md sem a tabela "## Performance snapshot"' : 'README.pt-BR.md sem a tabela "## Desempenho"'}`);
      const rows = perf.rows.map(row => html`<tr>${perf.header.map((h, i) => (i === 0 ? html`<th scope="row">${row[h]}</th>` : html`<td>${row[h] || '—'}</td>`))}</tr>`);
      return { html: String(html`<p>${perf.context.replace(/\*\*/g, '')}</p>${tableWrap(tx('Measurements from the README', 'Medições do README'), perf.header, rows)}`) };
    },

    'requisitos'() {
      const a = ch().app;
      return { html: String(html`<dl class="spec">
<dt>Android</dt><dd>${tx(`${a.minAndroid} or newer (API ${a.minSdk}); built for Android ${a.targetAndroid} (API ${a.targetSdk})`, `${a.minAndroid} ou mais novo (API ${a.minSdk}); feito para o Android ${a.targetAndroid} (API ${a.targetSdk})`)}</dd>
<dt>ABI</dt><dd><span class="u-mono">${a.abi}</span>, ${tx('64-bit ARM only', 'só ARM de 64 bits')}</dd>
<dt>GPU</dt><dd>${tx('with Vulkan; without Vulkan the app does not run games', 'com Vulkan; sem Vulkan o app não roda jogos')}</dd>
<dt>${tx('Package', 'Pacote')}</dt><dd><span class="u-mono">${a.package}</span> ${tx('(the published APK)', '(o APK publicado)')}</dd>
</dl>`) };
    },

    'pastas'() {
      const a = ch().app;
      const rows = [
        [tx('App data folder', 'Pasta de dados do app'), a.dataRoot],
        [tx('Global emulator config', 'Config global do emulador'), a.globalConfig],
        [tx('Per-game config', 'Config de cada jogo'), a.gameConfigPattern],
      ].map(([k, v]) => html`<tr><th scope="row">${k}</th><td><code>${v}</code></td></tr>`);
      return { html: String(tableWrap(tx('App folders and files', 'Pastas e arquivos do app'), [tx('What', 'O quê'), tx('Path in internal storage', 'Caminho no armazenamento interno')], rows)) };
    },

    'releases'(args) {
      const list = site.data.releases.releases.filter(r => r.build != null || args.todas === 'sim');
      const shown = list.slice(0, Number(args.max || 20));
      const out = shown.map(r => {
        const sum = releaseSummary(r.body, id);
        const rid = `build-${r.build ?? r.tag.toLowerCase()}`;
        return html`<section class="release" aria-labelledby="${rid}">
<h3 id="${rid}">${r.version ? `v${r.version}` : r.build != null ? `Build ${r.build}` : r.name} <a class="anchor" href="#${rid}" aria-label="${tx('Link to this version', 'Link para esta versão')}">#</a></h3>
<p class="page-meta"><span>${L.date(r.publishedAt)}</span><span class="u-mono">${r.tagSha ? r.tagSha.slice(0, 8) : ''}</span>${r.apk ? html`<a href="${r.apk.url}">APK, ${L.bytes(r.apk.size)}</a>` : ''}<a href="${r.url}" rel="noopener">${tx('Full notes on GitHub', 'Notas completas no GitHub')}</a></p>
${sum.items.length ? html`<ul${sum.lang !== (id === 'en' ? 'en' : 'pt-BR') ? raw(` lang="${sum.lang}"`) : ''}>${sum.items.map(i => html`<li>${i}</li>`)}</ul>` : html`<p class="u-muted">${tx('No summary in the notes of this version.', 'Sem resumo nas notas desta versão.')}</p>`}
${id === 'pt' && sum.lang === 'en' ? html`<p class="u-muted" style="font-size:var(--step--1)">Resumo só em inglês nas notas desta versão.</p>` : ''}
</section>`;
      });
      return { html: out.join(''), headings: shown.map(r => ({ level: 3, id: `build-${r.build ?? r.tag.toLowerCase()}`, text: r.version ? `v${r.version}` : r.build != null ? `Build ${r.build}` : r.name })) };
    },

    'nao-lancado'() {
      const u = site.data.unreleased;
      if (u == null) return { html: `<p>${esc(tx('The main history could not be read in this build.', 'Não deu para ler a história do main neste build.'))}</p>` };
      if (!u.length) return { html: String(html`<p>${tx(`Nothing: main has no app changes after the ${stableName()}.`, `Nada: o main não tem mudanças no app depois da ${stableName()}.`)}</p>`) };
      return { html: String(html`<ul>${u.map(c => html`<li><a href="${site.config.repoUrl}/commit/${c.sha}" rel="noopener"><code>${c.sha.slice(0, 8)}</code></a> <span lang="${/[ãçõáéíóúâêô]/i.test(c.subject) ? 'pt-BR' : 'en'}">${c.subject}</span> <span class="u-muted">(${L.date(c.date)})</span></li>`)}</ul>`) };
    },

    'consistencia'() {
      const est = site.ch.est;
      const w = est.consistency.warnings;
      const cd = est.consistency.coreDefaults;
      const probs = site.data.problems.filter(p => p.level !== 'error');
      const src = s => (s && /^[\w./-]+:\d+$/.test(s) ? html`<a href="${site.config.repoUrl}/blob/${site.v.stable ? site.v.stable.commit : 'main'}/${s.replace(/:(\d+)$/, '#L$1')}" rel="noopener"><code>${s}</code></a>` : s ? html`<code>${s}</code>` : '');
      const ids = { warn: tx('code-warnings', 'avisos-do-codigo'), defaults: tx('app-vs-core-defaults', 'padroes-app-core') };
      const titles = { warn: tx('Warnings about the code', 'Avisos sobre o código'), defaults: tx('App defaults that differ from the core', 'Padrões do app diferentes dos do núcleo') };
      // os avisos do build são escritos em português (vêm dos extratores)
      const lang = id === 'en' ? raw(' lang="pt-BR"') : '';
      return { html: String(html`
<h3 id="${ids.warn}">${titles.warn} (${w.length + probs.length})</h3>
${w.length + probs.length ? html`<ul${lang}>${w.map(x => html`<li>${x.msg}${x.source ? html` · ${src(x.source)}` : ''}</li>`)}${probs.map(p => html`<li>${p.msg}</li>`)}</ul>` : html`<p>${tx('None.', 'Nenhum.')}</p>`}
<h3 id="${ids.defaults}">${titles.defaults} (${cd.length})</h3>
<p>${tx(raw('The app writes its own value on the first launch (the one in the <code>default_config.toml</code> template, or the schema’s when the template lacks the key); the core would only use its own default if the key were missing from the file.'), raw('O app grava o valor dele na primeira abertura (o do modelo <code>default_config.toml</code>, ou o do esquema quando o modelo não traz a chave); o núcleo só usaria o próprio padrão se a chave faltasse no arquivo.'))}</p>
${tableWrap(titles.defaults, [tx('Key', 'Chave'), 'App', tx('Core', 'Núcleo'), tx('Definition in the core', 'Definição no núcleo')], cd.map(x => html`<tr><td><code>${x.key}</code></td><td><code>${x.app === '' ? '""' : x.app}</code> <span class="u-muted">(${appFrom(x.appFrom, L)})</span></td><td><code>${x.core === '' ? '""' : x.core}</code></td><td>${src(x.cvarSource.replace(/^xenia\//, 'emulator-core/src/main/cpp/xenia/'))}</td></tr>`))}
`), headings: [{ level: 3, id: ids.warn, text: titles.warn }, { level: 3, id: ids.defaults, text: titles.defaults }] };
    },

    'fontes'() {
      const rows = site.sourcesTable(L).map(r => html`<tr><th scope="row">${r.what}</th><td>${r.files.map((f, i) => html`${i ? ', ' : ''}<a href="${site.config.repoUrl}/blob/main/${f}" rel="noopener"><code>${f}</code></a>`)}</td><td>${r.how}</td><td>${r.channel}</td></tr>`);
      return { html: String(tableWrap(tx('Sources of the site data', 'Fontes dos dados do site'), [tx('Data', 'Dado'), tx('Source file', 'Arquivo de origem'), tx('How it is read', 'Como é lido'), tx('Version', 'Versão')], rows)) };
    },

    'print'(args, env) {
      if (!args.id) throw new Error('{{> print}} precisa de id=');
      return { html: String(site.shot(env.pagePath, args.id, { caption: args.legenda ? raw(esc(args.legenda)) : null, portrait: args.retrato === 'sim', lang: id })) };
    },
  };
}

/** De onde vem o padrão do app (o extrator diz "modelo" ou "esquema"). */
function appFrom(from, L) {
  if (L.id === 'pt') return from;
  return { modelo: 'template', esquema: 'schema' }[from] || from;
}
