# Pipeline de áudio do Xendroid+

Do áudio do jogo até o alto-falante, o que cada etapa faz, quem é dono de cada buffer, que
relógio manda e o que a auditoria de outubro de 2026 achou e corrigiu.

## O caminho das amostras

1. **XMA → PCM.** O `XmaDecoder` (thread própria, `use_dedicated_xma_thread`; contexto
   `xma_decoder = "new"`) decodifica os pacotes XMA que o jogo entrega, em buffers PCM na
   memória do guest. Roda quando o jogo "chuta" um contexto; não toca o relógio de áudio.
2. **Mixagem.** O próprio jogo (o XAudio do guest, código do jogo) mistura as vozes em blocos de
   **256 amostras × 6 canais**: 5.1 em float *big endian*, um canal depois do outro (FL, FR, C,
   LFE, BL, BR), a 48 kHz. Um bloco = 5,333 ms.
3. **Entrega.** A thread `Audio Worker` (`AudioSystem::WorkerThreadMain`) chama o callback de áudio
   do jogo, que entrega o bloco com `XAudioSubmitRenderDriverFrame` → `AudioSystem::SubmitFrame` →
   `driver->SubmitFrame`: o bloco é copiado para um buffer do pool do driver e entra na fila
   (`frames_queued_`, sob `frames_mutex_`). Alocação só aqui, no produtor, e só até o pool
   cobrir a fila; nunca no callback do dispositivo.
4. **Saída** (`xe_audio_block_renderer.h`, no callback do dispositivo):
   - dobra 5.1 → estéreo, com a matriz padrão do XAudio2: frente 1, centro e traseiros −3 dB,
     LFE −6 dB, e a troca de *endianness*;
   - ganho (volume do driver × volume mestre), limitado a ±1 e contado (`clipped`);
   - ocultação de bloco que não chegou a tempo (abaixo);
   - reamostragem linear na taxa do controle de taxa (só AAudio);
   - um crédito do semáforo do cliente por bloco consumido (ou um por falha, para o jogo
     continuar andando).
5. **Player de música (XMP).** Tem driver próprio: a música, convertida para estéreo
   intercalado *host endian* (seja mono ou multicanal), em blocos de 768 quadros, na taxa da
   música.

Backends: **AAudio** (padrão, `apu = "aaudio"`) e **OpenSL ES** (`apu = "opensles"`).

## Buffers e donos

| Buffer | Tamanho | Dono / threads |
|---|---|---|
| Créditos por cliente (semáforo) | `apu_max_queued_frames` = 8 blocos (42,7 ms) | Audio Worker consome; callback do dispositivo devolve |
| Fila do driver | 4 a 5 blocos; até 7 depois de travamentos (`apu_adaptive_queue`); pool reaproveitado | produtor: Audio Worker; consumidor: callback (mutex curto) |
| Bloco em reprodução (`AudioBlockRenderer`) | 1 bloco estéreo | só o callback |
| Buffer do dispositivo, AAudio | `apu_aaudio_buffer_bursts` = 4 bursts (adaptativo opcional) | AAudio |
| Buffers de saída, OpenSL ES | 2 por driver, na fila do OpenSL | thread do player |

## Relógios e sincronização

- **Quem manda é o relógio do dispositivo de áudio.** Cada bloco que ele consome devolve um
  crédito; o Audio Worker só chama o jogo com crédito, no ritmo de 5,333 ms do relógio
  `steady_clock` (dividido por `guest_time_scalar`), e repõe até em dobro quando a fila cai
  abaixo da metade dos créditos (`apu_pump_topup`), ou da profundidade maior que a fila
  adaptativa pediu depois de um travamento (abaixo). O tempo de áudio do jogo anda no ritmo do
  dispositivo.
- **O vídeo anda no relógio do guest** (vblank de 60 Hz sobre o `steady_clock`). A diferença
  entre os dois cristais (partes por milhão) não acumula: a fila absorve e os créditos regulam.
- **Jogo abaixo do tempo real** (CPU): a fila esvazia. Abaixo de 3 blocos, o AAudio reamostra
  até 0,90× (`apu_aaudio_dynamic_rate`: tom até ~1,8 semitom abaixo, com rampa de 0,003 por
  callback) em vez de falhar; sem bloco nenhum, oculta a falha.
- **Pausa/retomada**: o Worker para antes dos drivers (para a fila não esvaziar à toa); na
  retomada o próximo callback recomeça a taxa em 1.
- **Troca de saída** (fone, Bluetooth, USB): o AAudio recebe o erro de desconexão, e uma thread
  própria reabre o stream no novo dispositivo padrão, com a taxa e o burst negociados de novo
  (o log mostra os valores concedidos). O OpenSL ES segue o roteamento do sistema.

## Achados e correções

Confirmados no código; os números são de medição.

1. **`RegisterClient` com todos os clientes ocupados** seguia com o índice −1 (o `assert` some
   no build release, com `NDEBUG`): lia o semáforo de antes do array e escrevia o cliente antes
   de `clients_`, corrupção de memória. Agora recusa o cliente, com log. Se o driver não abre,
   os créditos já liberados ficavam no semáforo, e a liberação do próximo cliente do slot
   falhava por passar do máximo; agora são retirados, como no `UnregisterClient`.
2. **`UnregisterClient` e `Restore` com índice ruim.** O índice vem do handle do jogo (ou do
   save state) e só um `assert` o checava: fora do intervalo, escrita fora de `clients_`; um
   cliente desregistrado duas vezes destruía um driver nulo. Agora os dois recusam com log,
   como o `SubmitFrame` já fazia, e um `Restore` cujo driver não abre deixa o slot livre e sem
   créditos, em vez de em uso sem driver.
3. **Player de música: o semáforo era destruído antes do driver** (`DeleteDriver` e a falha
   do `SetupDriver`). Os callbacks do AAudio e do OpenSL ES rodam em threads próprias e
   devolvem créditos nele até o driver parar: no intervalo, `Release` num semáforo já
   destruído (uso após liberação). Agora o driver para antes. E `Pause`, `Continue` e `Stop`,
   que chegam por outra thread, usam o driver sob a mesma trava com que o `DeleteDriver` o
   libera.
4. **Player de música: o número de canais da música era confiado.** O driver era criado com os
   canais da música, e os blocos dele têm o tamanho de estéreo: numa música mono, o driver lia
   o dobro do que havia no bloco (leitura fora do heap); com 6 canais, o 5.1 intercalado da
   música era dobrado como o 5.1 *big endian* do jogo, ruído. Agora a música vira estéreo na
   conversão (mono nos dois lados; mais canais, frente esquerda e direita com o centro a −3 dB)
   e os dois drivers recusam qualquer contagem que não seja 2 ou 6.
5. **OpenSL ES: um único buffer de saída `static`**, de todos os drivers: o do jogo e o do
   player de música, cada um na sua thread de callback, escreviam e enfileiravam a mesma
   memória. Agora cada driver tem os seus, dois na fila (um tocando, um esperando).
6. **OpenSL ES: a pausa parava o player** (`SL_PLAYSTATE_STOPPED`) em vez de pausá-lo. A
   suspeita era que a parada esvaziasse a fila e a retomada não voltasse; **medido no POCO F7,
   não é o caso**: a trilha fica parada (`S`) e volta a tocar na retomada. Agora a pausa é uma
   pausa (`SL_PLAYSTATE_PAUSED`): a trilha fica `P` e retoma de onde estava. Ajuste de
   semântica, sem falha comprovada por trás.
7. **OpenSL ES: falha sem crédito.** Com a fila do driver vazia, o callback tocava silêncio e
   não devolvia crédito. O Worker gasta um crédito em cada chamada do jogo, também nas que não
   entregam bloco (o caso que o *topup* já detecta), e esses créditos não voltavam mais: sem
   nenhum, o Worker não chama mais o jogo. Agora cada buffer tocado devolve um crédito, com
   bloco ou com falha (o AAudio devolve um por bloco, e um no callback que só teve falha); com o
   semáforo cheio a liberação falha, sem efeito.
8. **OpenSL ES: o player de música era criado como driver do jogo** (`//FIXME`): a música,
   estéreo intercalado, era tocada como 5.1 *big endian*, ruído em volume máximo. Agora o driver
   do player usa o formato e a taxa da música, como já fazia o AAudio.
9. **OpenSL ES: volume por driver em milibéis errados** (`volume × 100`, de 0 a +1 dB): não
   atenuava nada. Agora em software, como no AAudio; e o pico da dobra 5.1 passou a ser
   limitado também aqui (a contagem fica na estatística do AAudio, que o OpenSL ES não tem).
10. **Ocultação de falha com degraus.** O AAudio repetia o último bloco como estava, decaindo:
   degrau onde o fim do bloco reencontrava o início, a cada repetição, e outro na entrada da
   rampa do bloco seguinte, que começa do zero; o OpenSL ES tocava silêncio, com degrau nas
   duas bordas. Agora, nos dois, a falha continua o último bloco espelhado no tempo, a partir
   da última amostra (sem degrau), e o leva a zero dentro do bloco (cosseno); as falhas
   seguintes são silêncio, e o bloco seguinte sobe em rampa a partir do silêncio. **Medido**
   com uma senoide de 440 Hz e falhas de 1 e 3 blocos, em callbacks de 192 quadros: maior
   salto entre amostras **0,3275 → 0,0295**, ou seja, **11,4× → 1,0×** o maior passo da
   própria senoide.
11. **NaN do jogo passava pela limitação de ±1** (as duas comparações dão falso) e chegava ao
   dispositivo e à ocultação seguinte. Agora vira silêncio e é contado como amostra limitada.
12. **AAudio: `Resume` zerava o estado do callback em outra thread.** Agora sinaliza, e o
   próximo callback recomeça a taxa.
13. **Diagnóstico:** com `apu_aaudio_log_stats`, a linha de cada segundo traz também a duração
   dos callbacks (média e máxima, em µs), além de falhas, profundidade da fila, taxa, xruns e
   clipping.

## Áudio sob carga

A queixa: o áudio falha nas quedas de FPS, nas batidas e nas cenas pesadas. Medido no Forza
Horizon, dirigindo reto com o acelerador preso (o carro bate no que estiver à frente), com
`apu_aaudio_log_stats`.

- **As falhas vêm de travamentos do Audio Worker, não do jogo.** Em 22 min medidos, o jogo
  entregou um só bloco zerado no meio do som. As falhas vêm logo depois de o Worker chegar
  atrasado ou de o callback do jogo demorar 20 ms ou mais, e esses travamentos vêm em
  sequência, nas cenas pesadas. Com a fila de sempre, 4 a 5 blocos (21 a 27 ms), houve 17
  falhas em 162 s.
- **O Worker espera por um núcleo.** O callback do jogo gasta ~150 µs de CPU, mas nas quedas
  de FPS leva até 38 ms. O resto é espera, quase toda com a thread pronta e sem núcleo: 146 a
  254 ms por segundo nas quedas de FPS, e até 431 ms por segundo e 45 ms num só callback com
  carga extra.
  - **O escalonador:** no Snapdragon 8s Gen 4, o WALT da Qualcomm trata as threads mais
    pesadas do emulador (Guest CPU, GPU Commands) como "pipeline" (MVP). Elas passam na frente
    de qualquer `nice`: o Worker já roda com −12, e −20 não mudou nada. Os ajustes do WALT só
    valem com root.
  - **Fora do callback:** uma vez, o Worker ficou 62 ms parado, provavelmente na trava global
    de seções críticas.
- **Fila fixa mais funda não resolve:** com 6 blocos fixos, um callback de 38,6 ms ainda deu 6
  falhas, e a latência fica maior o tempo todo.
- **A correção: fila adaptativa** (`apu_adaptive_queue`, ligada por padrão).
  - **Quando cresce:** depois de um travamento que gastou metade do que a fila cobre (o Worker
    atrasado ou o callback lento), ou ao achar a fila com no máximo 1 bloco. Só contam as
    chamadas em que o jogo entregou um bloco: a reposição só vem depois delas, e um jogo sem
    nada a entregar esvazia a fila sem travamento nenhum.
  - **Quanto:** o *topup* passa a encher 2 blocos a mais, no máximo uma vez a cada 500 ms, até
    `apu_max_queued_frames` − 1 (7 blocos, 37 ms, com os 8 créditos padrão).
  - **Quando volta:** depois de 20 s sem travamento, desce 1 bloco. A latência extra só vem
    depois de travamentos.
  - **O que não conta:** o início de um cliente e a volta de uma pausa. Cada cliente tem seu
    próprio estado, que recomeça nesses dois momentos. Até chegar à profundidade de sempre, a
    fila ainda está enchendo, e a pausa não conta como atraso.
  - **Profundidade de sempre:** `apu_queue_target` a muda, de 4 a `apu_max_queued_frames` − 1
    (0 = metade dos créditos, no mínimo 4). Abaixo de 4, a fila ficaria onde o AAudio já
    baixa o tom.

  Medido com dois laços de CPU a mais rodando no aparelho, para tornar as quedas de FPS mais
  fortes. Na build final, o mesmo percurso teve a primeira metade com a fila adaptativa e a
  segunda sem (433 s). Um segundo conta como com falha quando a linha `AAudio:` dele ou a
  seguinte tem falha:

  | Pior travamento do segundo | Sem a fila adaptativa | Com |
  |---|---|---|
  | menos de 20 ms | 5 de 195 segundos com falha | 0 de 202 |
  | 20 a 40 ms | 12 de 14 | 1 de 13, logo antes de uma sequência acima de 40 ms |
  | 40 a 50 ms | 1 de 1 | 4 de 4 |
  | 50 ms ou mais | (nenhum) | 4 de 4 |

  Com a fila adaptativa, as falhas vieram só de duas sequências de travamentos acima de 40 ms
  (até 100 ms). Uma build anterior à revisão, na ordem inversa (sem, depois com), deu 3 de 6
  segundos de 20 a 40 ms com falha sem e 0 de 9 com. Uma primeira versão, que só crescia ao
  achar a fila quase vazia, deu 2 falhas sem e 0 com, sem carga extra.
- **O que fica: travamentos acima de ~40 ms**, mais longos que a fila mais funda (37 ms). No
  pior caso, o Worker passou 25 a 43% de cada segundo esperando núcleo, por 3 s seguidos, e
  entregou 168 a 177 blocos por segundo, contra 187,5 do tempo real; nenhuma fila dentro dos 8
  créditos cobre isso.
  - **Mais créditos** (`apu_max_queued_frames`, que o app deixa mudar) dão mais espaço à fila
    adaptativa. Sozinhos, também aprofundam a fila de sempre, que é metade dos créditos; com
    `apu_queue_target` = 4, ela fica em 4 blocos. Não medido.
  - **Não testado:** afinidade de CPU do Worker longe das threads de pipeline, e menos idas
    dele à trava global.
- **Diagnóstico novo** (`apu_aaudio_log_stats`), a cada segundo:
  - `AudioWorker: N pumps (...)`: chamadas ao jogo, as sem bloco, os *top-ups* e os prazos
    sem crédito; blocos entregues; duração do callback (média e máxima); o maior atraso; e
    blocos zerados do jogo (e quantos logo depois de som);
  - `AudioWorker: guest callback CPU time ...`: CPU do callback (média e no mais lento) e o
    núcleo de cada chamada;
  - `AudioWorker: waiting for a core ...`: espera por núcleo no callback mais lento, a maior
    num callback e o total do segundo (de `/proc/thread-self/schedstat`);
  - na linha `AAudio:`, a profundidade mínima da fila (`min`).

  A cada crescimento sai `AudioWorker: a N us stall with M blocks queued, refilling to K
  blocks`. Com o jogo rodando, `debug.xendroid.apu_adaptive_queue` e
  `debug.xendroid.apu_queue_target` mudam as duas opções.

## Hipóteses e o que fica

- **Taxa dinâmica até 0,90×.** É reamostragem adaptativa com mudança de tom audível perto do
  limite. Só entra com a fila quase vazia (jogo abaixo do tempo real). Em todos os segundos
  medidos (Gears of War 3, abertura) ela ficou em 1,000; no Forza Horizon sob carga também, mas o
  log só mostra a taxa no instante de cada linha, não o mínimo do segundo. Falta isso antes de
  mexer no limite.
- **Mutex no callback.** Seções críticas de poucas instruções (contar a fila, retirar e devolver
  um ponteiro, liberar o crédito). Medido em 609 segundos: média de 7 a 32 µs por callback; a
  máxima de cada segundo tem mediana de ~200 µs, fica abaixo de 0,65 ms em 95% deles e chegou
  uma vez a 2,0 ms (saída no submix remoto), contra 5,3 ms de período. Sem sinal de espera que
  justifique uma fila sem trava.
- **Fila mais funda depois de uma pausa (AAudio, já existia).** Antes da pausa a fila média era
  de 5,0 a 5,6 blocos; depois, de 7,0 a 8,7 (+8 a +16 ms de latência), nas duas builds. Ela não
  esvazia sozinha: a taxa só desce abaixo de 1, nunca sobe. Hipótese: o Worker volta a produzir
  enquanto o stream ainda está reiniciando. Fica para outro passo.
- **Clipping da dobra 5.1.** O pico pode chegar a ~2,9× um canal; é limitado e contado. A cura
  é menos ganho, não um limitador que mude a mixagem. Nenhuma amostra limitada nas medições.
- **Player de música (XMP)**, a conversão de canais e o buffer compartilhado do OpenSL ES:
  corrigidos pelo código, não exercitados no aparelho (nenhum jogo testado toca música pelo
  XMP).
- **Fila do OpenSL ES no teto.** O OpenSL ES não informa a profundidade da fila
  (`GetQueuedFrameCount`), então o *topup* do Worker sempre age e a fila fica perto dos 8
  blocos de crédito (~43 ms). Limitado e anterior a esta auditoria.
- **XMA** (decodificador "new") não foi auditado a fundo.

## Validação

- **Host** (`tools/test-native-logic.sh`, com o NDK): `audio_block_renderer_test` cobre
  - a passagem exata a taxa 1;
  - a dobra 5.1 e a ordem dos bytes;
  - o estéreo do player;
  - o ganho limitado e contado;
  - as falhas sem degrau em callbacks de 96, 192, 256 e 1000 quadros, e no estéreo do player;
  - a reamostragem contínua a 0,90, 0,97 e 1,0;
  - o NaN silenciado e um bloco ou falha por callback do tamanho de um bloco (o ritmo do
    OpenSL ES).

  O host é x86_64, onde a dobra 5.1 roda em SSE; o mesmo teste, compilado para arm64, rodou no
  POCO F7, que usa a versão escalar.
- **Aparelho** (POCO F7, Snapdragon 8s Gen 4, Android 16; Gears of War 3, música da abertura;
  build anterior × build nova; `apu_aaudio_log_stats` ligado):
  - **Reprodução:** as duas builds, nos dois backends, tocam a 48 kHz sem *underrun* na trilha
    do AudioFlinger, sem falha, xrun ou clipping no AAudio, e com fila de 4 a 6 blocos.
  - **Pausa e retomada** (menu do jogo, "Pausar o jogo ao abrir o menu"): no AAudio e no OpenSL
    ES novos a trilha fica `P` com a posição parada e volta a andar a ~48 kHz na retomada; no
    OpenSL ES antigo fica `S` (parada) e também volta.
  - **Troca de saída** (o scrcpy captura a saída inteira, o que a desvia para o submix remoto):
    o AAudio recebe a desconexão (−899), reabre o stream no novo dispositivo (burst 960) e volta
    ao alto-falante (burst 192) quando a captura acaba, nas duas builds. Na ida, 6 a 9 blocos
    faltam e são ocultados.
  - **O áudio que sai do aparelho**, capturado a 16 bits: nível igual nas duas builds (RMS de
    −39 a −42 dBFS, picos de −21 a −28 dBFS), nenhuma amostra em fundo de escala, mesma energia
    acima de 16 kHz (−56 a −69 dB) e nenhum transiente alinhado aos blocos de 256 quadros. Na
    troca de saída, o OpenSL ES antigo cai de 258 a 0 numa amostra (2,5× o maior passo da
    música) e fica em silêncio digital por um bloco (5,3 ms), duas vezes; o novo entra em rampa
    (13, 50, 90, 132… em amostras seguidas), com o maior passo em 0,5× o da música. No AAudio
    novo a falha aparece como uma saída suave, ~33 ms de silêncio e uma entrada em rampa, com o
    maior passo em 1,0× o da música; no antigo a música estava baixa demais na troca para
    comparar (repetições decaindo, maior passo 0,4×).
- **Sob carga** (Forza Horizon, com e sem a fila adaptativa no mesmo percurso, trocada com o
  jogo rodando): os números estão em [Áudio sob carga](#áudio-sob-carga).
