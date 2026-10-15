/* Dados do simulador.
   - Ajustes, grupos, predefinições, ajustes fixados e flags do Turnip vêm do código do app,
     extraídos no build do site (XDR_SIM, em dados-<versão>.js); nada disso é escrito aqui.
   - Jogos: os onze medidos no README do projeto, com Title IDs e patches reais do catálogo.
     Formato, tamanho, sessões, saves e coleções são exemplos. Nenhum jogo real recebe uma
     avaliação de compatibilidade: a ficha começa “Sem avaliação”, como no app.
   - Falhas, sessões interrompidas e a comparação A/B de exemplo ficam num jogo fictício. */
'use strict';
const XDR = window.XDR_SIM;

/* ============ jogos de exemplo ============ */
const COLS = ['Corrida', 'Campanha', 'Zerar em 2026'];
const FMT = { ISO: 'ISO', ZAR: 'ZAR', GOD: 'GOD', XEX: 'Pasta XEX', STFS: 'XBLA' };
const FICTIONAL = 'FFFF0001';
const BASE_PATH = '/storage/emulated/0/XenDroid/Jogos/';
/* estado de exemplo de cada jogo; pal é só a cor do marcador de capa (o simulador não tem as
   capas nem os ícones reais dos jogos) */
const DEMO = [
  { id: '4D5309C9', fmt: 'ISO', size: '7,8 GB', pal: ['#260d06', '#c4501c', '#ffd27a'], last: 1, ago: 'há 2 h', playMin: 395, runs: 31, fav: 1, cols: ['Corrida'], tu: 4, dlc: [], saves: 2, cache: [5, 112] },
  { id: '4D530AA4', fmt: 'ISO', size: '7,1 GB', pal: ['#06202a', '#1c7d8c', '#ffe08a'], last: 3, ago: 'ontem', playMin: 182, runs: 12, fav: 1, cols: ['Corrida'], tu: 2, dlc: [], saves: 1, cache: [4, 96] },
  { id: '4D5308AB', fmt: 'ISO', size: '7,9 GB', pal: ['#191312', '#5b1a17', '#d8d1c4'], last: 2, ago: 'há 5 h', playMin: 254, runs: 9, fav: 0, cols: ['Campanha'], tu: 6, dlc: [], saves: 3, cache: [6, 133] },
  { id: '4D530919', fmt: 'ISO', size: '7,6 GB', pal: ['#081f2b', '#1d6f86', '#f0c96a'], last: 4, ago: 'há 2 dias', playMin: 141, runs: 6, fav: 1, cols: ['Campanha'], tu: 10, dlc: [], saves: 1, cache: [3, 74] },
  { id: '45410961', fmt: 'GOD', size: '6,2 GB', pal: ['#0c1418', '#3a5260', '#e9e2cf'], last: 6, ago: 'há 1 semana', playMin: 63, runs: 4, fav: 0, cols: ['Corrida'], tu: null, dlc: [], saves: 1, cache: [2, 41] },
  { id: '53450812', fmt: 'ISO', size: '6,8 GB', pal: ['#061a2d', '#1a5a9a', '#9fe3ff'], last: 5, ago: 'há 4 dias', playMin: 88, runs: 5, fav: 0, cols: [], tu: null, dlc: [], saves: 1, cache: [3, 58] },
  { id: '4D53085B', fmt: 'ISO', size: '7,2 GB', pal: ['#14070a', '#7d1020', '#f4e2c0'], last: 7, ago: 'há 2 semanas', playMin: 34, runs: 3, fav: 0, cols: ['Campanha'], tu: null, dlc: [], saves: 0, cache: [1, 22] },
  { id: '4541098E', fmt: 'ISO', size: '7,4 GB', pal: ['#0d1a0f', '#2f6b3a', '#e8d58a'], last: 8, ago: 'há 3 semanas', playMin: 21, runs: 2, fav: 0, cols: [], tu: null, dlc: [], saves: 0, cache: [1, 17] },
  { id: '545407F2', fmt: 'ZAR', size: '5,4 GB', pal: ['#05080e', '#1b2c4a', '#cfe1ff'], last: 9, ago: 'há 1 mês', playMin: 47, runs: 3, fav: 0, cols: ['Zerar em 2026'], tu: 8, dlc: [], saves: 1, cache: [2, 38] },
  { id: '5454082B', fmt: 'ISO', size: '7,1 GB', pal: ['#2a1005', '#8e3a14', '#f2b35a'], discs: 2, last: 10, ago: 'há 1 mês', playMin: 120, runs: 5, fav: 1, cols: ['Zerar em 2026'], tu: null, dlc: ['Undead Nightmare'], saves: 2, cache: [3, 64] },
  { id: '53510823', fmt: 'ISO', size: '6,9 GB', pal: ['#0b1216', '#3d5a66', '#e6eef2'], last: 11, ago: 'há 1 mês', playMin: 52, runs: 4, fav: 0, cols: ['Zerar em 2026'], tu: null, dlc: [], saves: 1, cache: [2, 45] },
  { id: FICTIONAL, name: 'Jogo de exemplo', fictional: true, fmt: 'XEX', size: '2,1 GB', pal: ['#04050a', '#1c0f3a', '#3ef0ff'], last: 12, ago: 'há 2 meses', playMin: 9, runs: 3, fav: 0, cols: [], tu: null, dlc: [], saves: 0, cache: [1, 9], noFrames: true, crash: 'SIGSEGV em libvulkan_turnip.so (exemplo fictício)' },
];
/* sessão de exemplo: o FPS mediano e o mais baixo seguem a medição do README; o resto é ilustrativo */
function demoPerf(d) {
  if (d.fictional) return null;
  const m = XDR.medidas[d.id];
  if (!m) return null;
  const cap = m.t <= 31 ? 30 : 60;
  return { t: m.t, sd: m.range ? Math.max(.6, (m.range[1] - m.range[0]) / 3) : .8, low: m.low != null ? m.low : m.t - 2, lowP: m.low != null ? m.lowShare : .03, sec: 1200 + d.playMin * 2, cap, first: 40 + (d.cache[1] % 30), pipes: [1200 + d.cache[1] * 9, +(3 + d.cache[1] / 25).toFixed(1)], audio: ['AAudio', 72000, 30], bat: [31, 40, 39], hz: 120, lim: cap, drv: 'Turnip A (exemplo)', example: true, readme: m };
}
const GAMES = DEMO.map(d => {
  const real = XDR.jogos[d.id] || {};
  const name = d.name || (XDR.medidas[d.id] && XDR.medidas[d.id].jogo) || real.nome || d.id;
  // patches reais do catálogo, um grupo por arquivo; cada entrada tem um id próprio para o interruptor
  const pfiles = (real.arquivos || []).map((f, i) => ({ label: f.rotulo, entries: f.entradas.map((e, j) => ({ id: i + ':' + j, n: e.n, d: e.d, on: !!e.on })) }));
  const patches = pfiles.flatMap(f => f.entries);
  return Object.assign({ discs: 1, compat: null }, d, {
    name,
    pfiles,
    patches,
    pOn: patches.filter(e => e.on).map(e => e.id),
    quirks: real.correcoes || 0,
    perf: demoPerf(d),
    path: d.fmt === 'XEX' ? BASE_PATH + name + '/default.xex' : d.fmt === 'GOD' || d.fmt === 'STFS' ? BASE_PATH + d.id : BASE_PATH + name + (d.fmt === 'ZAR' ? '.zar' : '.iso'),
  });
});
const GBY = Object.fromEntries(GAMES.map(g => [g.id, g]));
const CS = { playable: ['Jogável', 'Jogável'], ingame: ['No jogo, com problemas', 'Com problemas'], intro: ['Só abertura ou menus', 'Só menus'], boots: ['Abre, sem imagem', 'Sem imagem'], nothing: ['Não abre', 'Não abre'], none: ['Sem avaliação', 'Sem avaliação'] };
const stOf = g => g.compat ? g.compat.s : 'none';
const fmtLabel = g => FMT[g.fmt] || g.fmt;

/* ============ ajustes (da versão escolhida) ============ */
let VERSION = null, GROUPS = [], GNAME = {}, SET = [], DEF = {}, RES = null, PRESETS = [], TURNIP = [], CVARS = {}, NEW_COUNT = 0, NEWOPT_COUNT = 0;
/* opções do próprio app (só globais; guardadas pelo app, fora do TOML), com os textos do app */
const APPSET = [
  { k: '@app.mode', g: 'app', lvl: 1, ty: 'list', def: 'auto', ap: 'live', t: 'Modo da interface', o: [['auto', 'Automático'], ['b', 'Toque'], ['c', 'Controle']], d: 'Automático: layout de controle enquanto um controle está conectado; de toque sem controle.' },
  { k: '@app.level', g: 'app', lvl: 1, ty: 'list', def: '1', ap: 'live', t: 'Ajustes mostrados', o: [['1', 'Essencial'], ['2', 'Avançado'], ['3', 'Tudo']], d: '' },
  { k: '@app.confirm', g: 'app', lvl: 1, ty: 'list', def: 'a', ap: 'live', t: 'Botões nos menus', o: [['a', 'A confirma'], ['b', 'B confirma']], d: 'Para a biblioteca, o menu do jogo e o editor de layout; os botões do próprio jogo nunca mudam.' },
  { k: '@app.advice', g: 'app', lvl: 1, ty: 'list', def: 'always', ap: 'live', t: 'Sugestões ao fim da sessão', o: [['always', 'Sempre'], ['errors', 'Só depois de erros'], ['never', 'Nunca']], d: 'O que mudar, com base no log e nos números da sessão, quando há algo a fazer.' },
  { k: '@app.uisize', g: 'app', lvl: 1, ty: 'list', def: '100', ap: 'live', t: 'Tamanho da interface', o: [['85', '85%'], ['90', '90%'], ['100', '100%'], ['110', '110%'], ['120', '120%'], ['130', '130%']], d: 'Telas do app e menu em jogo. Os controles de toque, o HUD e o editor de toque mantêm o tamanho próprio.' },
  { k: '@app.textsize', g: 'app', lvl: 1, ty: 'list', def: '100', ap: 'live', t: 'Tamanho do texto', o: [['85', '85%'], ['100', '100%'], ['115', '115%'], ['130', '130%'], ['150', '150%']], d: 'Além do tamanho de fonte do aparelho.' },
  { k: '@app.lang', g: 'app', lvl: 1, ty: 'list', def: 'sys', ap: 'live', t: 'Idioma do app', o: [['sys', 'Idioma do aparelho'], ['en', 'English'], ['pt', 'Português (Brasil)']], d: 'O idioma do próprio app. O idioma que os jogos veem é o ajuste “Idioma do console”.' },
  { k: '@app.updates', g: 'app', lvl: 1, ty: 'list', def: 'stable', ap: 'live', t: 'Atualizações do app', o: [['stable', 'Estável'], ['preview', 'Prévia'], ['off', 'Desligado']], d: 'Estável oferece novas versões; cada uma é conferida com o SHA-256 publicado e só é instalada depois que você confirma. Prévia também oferece versões de prévia, que podem ser menos testadas.' },
];

/** Carrega os ajustes de uma versão (estavel ou desenvolvimento) e recria DEF. */
function loadChannel(id) {
  const c = XDR.canais[id] || XDR.canais[XDR.padrao];
  VERSION = c;
  GROUPS = c.grupos;
  GNAME = Object.fromEntries(GROUPS.map(g => [g[0], g[1]]));
  SET = c.ajustes.map(d => Object.assign({}, d, d.o ? { o: d.o.map(o => o.slice()) } : {}));
  RES = Object.assign({}, c.resolucao);
  PRESETS = c.predefinicoes;
  TURNIP = c.turnip;
  CVARS = c.cvars;
  DEF = Object.fromEntries(SET.concat([RES]).map(d => [d.k, d]));
  for (const a of APPSET) DEF[a.k] = a;
  const lv = l => SET.filter(d => d.lvl <= l).length;
  DEF['@app.level'].d = `Essencial: o que muda a imagem e o desempenho (${lv(1)}). Avançado: também compatibilidade, entrada e sistema (${lv(2)}). Tudo: todos os ajustes que o core lê, depuração inclusive (${lv(3)}).`;
  // drivers instalados (de exemplo) viram opções do ajuste do driver
  const drv = DEF['Vulkan.vulkan_lib_path'];
  if (drv) drv.o = [['', 'Driver do sistema']].concat(DRIVERS.installed.map(d => [d.id, d.name]));
  NEW_COUNT = SET.filter(d => d.n).length;
  NEWOPT_COUNT = SET.filter(d => d.nOpt).length;
}

/* ============ drivers (catálogo de exemplo; a fonte padrão é a do app) ============
   checked: o pacote tem o arquivo de conferência (“arquivos conferidos”); sem ele, “importação antiga”. */
const DRIVERS = {
  sources: [XDR.fonteDrivers],
  gpu: 'Adreno 825 (exemplo)', model: 825,
  installed: [
    { id: 'turnip-a', name: 'Turnip A (exemplo)', checked: true, size: '14,2 MB', date: '02/09/2026' },
    { id: 'turnip-b', name: 'Turnip B (exemplo)', checked: true, size: '15,0 MB', date: '20/09/2026' },
    { id: 'turnip-c', name: 'Turnip C (exemplo)', checked: false, size: '12,8 MB', date: '11/05/2026' },
  ],
  available: [
    { id: 'turnip-d', name: 'Turnip D (exemplo)', from: XDR.fonteDrivers, date: '2026-09-28', sha: true, suggested: true },
    { id: 'turnip-e', name: 'Turnip E (exemplo)', from: XDR.fonteDrivers, date: '2026-09-15', sha: true },
    { id: 'turnip-f', name: 'Turnip F (exemplo)', from: XDR.fonteDrivers, date: '2026-08-03', sha: false },
  ],
  previous: 'turnip-b', selectedAt: '28/09/2026 20:05',
  lastRun: { drv: 'Turnip A (exemplo)', game: GAMES[0].name, when: 'hoje, 13:10' },
};

/* ============ perfis e saves (fictícios) ============ */
const PROFILES = [
  { xuid: 'E03000A1B2C3D4E5', tag: 'XenPlayer', c: ['#2f8f4e', '#0f3d22'], lang: 'Português', region: 'Brasil', active: true, slot: 1, games: 7, files: 38, mb: 29 },
  { xuid: 'E03000F6A7B8C9D0', tag: 'Lucas', c: ['#2f6fd1', '#0a2246'], lang: 'Português', region: 'Brasil', active: false, slot: 2, games: 3, files: 9, mb: 12 },
  { xuid: 'E030001122334455', tag: 'Convidado', c: ['#b23a3f', '#3a0608'], lang: 'Inglês', region: 'Estados Unidos', active: false, slot: null, games: 1, files: 2, mb: 2 },
];
const PROFILE_TRASH = [{ xuid: 'E0300099AABBCCDD', tag: 'Antigo', c: ['#7a6633', '#2a2210'], removed: '12/09/2026', games: 2, files: 14, mb: 6 }];
const SAVES = {
  '4D5309C9': [{ xuid: 'E03000A1B2C3D4E5', files: 3, kb: 1310, when: 'hoje, 13:58' }, { xuid: 'E030001122334455', files: 1, kb: 260, when: '02/09/2026' }],
  '4D5308AB': [{ xuid: 'E03000A1B2C3D4E5', files: 4, kb: 2240, when: 'há 5 h' }, { xuid: 'E03000F6A7B8C9D0', files: 2, kb: 512, when: '21/09/2026' }],
  '5454082B': [{ xuid: 'E03000A1B2C3D4E5', files: 12, kb: 8960, when: 'ontem' }],
};
/* backups na pasta de sincronização: nome imutável xendroid-saves-<TITLE ID>-<sha256>.zip (BackupReplication.kt) */
const SAVE_BACKUPS = [
  { name: 'xendroid-saves-4D5309C9-7f3a9c1e….zip', when: '01/10/2026 22:10', size: '1,4 MB' },
  { name: 'xendroid-saves-4D5309C9-2b81d04a….zip', when: '24/09/2026 19:30', size: '1,3 MB' },
];

/* ============ conteúdo instalado e lixeira (cota real do app: 4 GiB) ============ */
const CONTENT_TRASH = [{ gid: '4D530919', name: 'Title Update 9', type: 'tu', size: '5,9 MB', removed: '14/08/2026' }, { gid: '5454082B', name: 'Pacote de missões (antigo)', type: 'dlc', size: '402 MB', removed: '02/09/2026' }];
const TRASH_LIMIT = '4 GiB';

/* ============ sessões guardadas e comparação A/B (exemplos) ============ */
const RUNS = [
  { gid: '4D5309C9', when: 'hoje, 13:10', dur: '48 min', end: 'ok', kb: 412, drv: 'Turnip A (exemplo)' },
  { gid: '4D5308AB', when: 'hoje, 08:22', dur: '33 min', end: 'ok', kb: 655, drv: 'Turnip A (exemplo)' },
  { gid: '4D530AA4', when: 'ontem, 21:44', dur: '16 min', end: 'ok', kb: 140, drv: 'Driver do sistema' },
  { gid: FICTIONAL, when: 'há 1 semana', dur: '8 min', end: 'interrupted', kb: 188, drv: 'Turnip A (exemplo)', why: 'Encerrada pelo Android (pouca memória), exemplo fictício' },
  { gid: FICTIONAL, when: 'há 2 semanas', dur: '9 s', end: 'fail', kb: 96, drv: 'Turnip B (exemplo)', why: 'Falha nativa (sinal 11) antes do primeiro quadro, exemplo fictício' },
];
const BENCH = {
  gid: FICTIONAL, dim: 'driver',
  runs: [
    { id: 1, when: '28/09 20:01', side: 'A', drv: 'Turnip A (exemplo)', lim: 30, hz: 120, p50: 24, p5: 18, p99: 52, sec: 180, temp: 31, marks: 2 },
    { id: 2, when: '28/09 20:09', side: 'B', drv: 'Turnip B (exemplo)', lim: 30, hz: 120, p50: 26, p5: 20, p99: 47, sec: 176, temp: 33, marks: 2 },
    { id: 3, when: '28/09 20:17', side: 'B', drv: 'Turnip B (exemplo)', lim: 30, hz: 120, p50: 26, p5: 21, p99: 46, sec: 181, temp: 34, marks: 2 },
    { id: 4, when: '28/09 20:25', side: 'A', drv: 'Turnip A (exemplo)', lim: 30, hz: 120, p50: 24, p5: 18, p99: 53, sec: 179, temp: 34, marks: 2 },
    { id: 5, when: '29/09 19:40', side: null, drv: 'Turnip B (exemplo)', lim: 60, hz: 120, p50: 27, p5: 20, p99: 45, sec: 95, temp: 30, marks: 0 },
  ],
};

/* ============ controles conectados e mapeamento ============ */
const PADS = [
  { id: 'p1', name: '8BitDo Ultimate 2C', vidpid: '2DC8:301A', src: ['gamepad', 'joystick', 'direcional'], conn: 'Bluetooth', motor: true, gyro: false, rumble: null },
  { id: 'p2', name: 'DualSense Wireless Controller', vidpid: '054C:0CE6', src: ['gamepad', 'joystick'], conn: 'USB', motor: true, gyro: true, rumble: 'low' },
];
const INPUT_OTHER = [{ name: 'Teclado Bluetooth', src: ['teclado'], conn: 'Bluetooth' }];
const PAD_EVENTS = [['DualSense Wireless Controller', 'reconectado'], ['DualSense Wireless Controller', 'desconectado'], ['DualSense Wireless Controller', 'conectado'], ['8BitDo Ultimate 2C', 'conectado']];
/* os 16 botões do mapeamento, na ordem do app (GameButtons.ALL): id, nome, tecla padrão */
const KM_NAMES = { LEFT: 'Direcional para a esquerda', UP: 'Direcional para cima', RIGHT: 'Direcional para a direita', DOWN: 'Direcional para baixo', A: 'A', B: 'B', X: 'X', Y: 'Y', BACK: 'Back', START: 'Start', LB: 'Botão superior esquerdo (LB)', RB: 'Botão superior direito (RB)', L3: 'Apertar o analógico esquerdo (L3)', R3: 'Apertar o analógico direito (R3)', LT: 'Gatilho esquerdo (LT)', RT: 'Gatilho direito (RT)' };
const KM_BTNS = XDR.botoes.map(b => [b.id, KM_NAMES[b.id] || b.label, b.tecla]);
const KM_DEF = Object.fromEntries(KM_BTNS.map(b => [b[0], b[2]]));
/* layouts de toque salvos (exemplos): o que mudam em relação ao atual, por orientação */
const LAYOUTS = [
  { id: 'big', name: 'Polegares grandes', when: '12/09/2026', land: [3, 4, 0, 0], port: [2, 4, 0, 0] },
  { id: 'race', name: 'Corrida: gatilhos embaixo', when: '20/09/2026', land: [4, 0, 0, 1], port: null },
  { id: 'tab', name: 'Tablet compacto', when: '01/10/2026', land: [6, 6, 0, 0], port: [5, 5, 1, 0] },
];

loadChannel(XDR.padrao);
