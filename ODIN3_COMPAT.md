# Compatibilité Odin 3 : Xendroid+ (Xbox 360)

Notre propre liste de compatibilité, tenue d'après nos tests sur l'AYN Odin 3 (Snapdragon 8 Elite, Adreno 830) avec notre build de la branche `odin3`. Chaque test met à jour la ligne du jeu : statut, mesure, date et commit testé.

Autres émulateurs : [Eden (Switch)](https://github.com/HamzaDevz/eden/blob/odin3/ODIN3_COMPAT.md) · [Azahar (3DS)](https://github.com/HamzaDevz/azahar/blob/odin3/ODIN3_COMPAT.md) · [Dolphin (GC/Wii)](https://github.com/HamzaDevz/dolphin/blob/odin3/ODIN3_COMPAT.md) · [Cemu (Wii U)](https://github.com/HamzaDevz/Cemu/blob/odin3/ODIN3_COMPAT.md) · [Vita3K (Vita)](https://github.com/HamzaDevz/Vita3K/blob/odin3/ODIN3_COMPAT.md) · [aPS3e (PS3)](https://github.com/HamzaDevz/aps3e/blob/odin3/ODIN3_COMPAT.md) · [Bachata S4 (PS4)](https://github.com/HamzaDevz/bachata-s4/blob/odin3/ODIN3_COMPAT.md) · [xemu (Xbox)](https://github.com/HamzaDevz/xemu/blob/odin3/ODIN3_COMPAT.md) · [Xendroid+ (Xbox 360)](https://github.com/HamzaDevz/Xendroid-Plus/blob/odin3/ODIN3_COMPAT.md)

## Échelle

| Statut | Sens |
|---|---|
| 🟢 Parfait | vu en partie, plein régime, aucun défaut vu |
| 🔵 Jouable | vu en partie, défauts mineurs |
| 🟡 Problèmes | vu en partie, défauts gênants (fps, graphismes, son) |
| 🟠 Menus/intro | démarre, pas encore vu en partie |
| 🔴 Ne marche pas | crash, écran noir ou blocage |
| ⚪ Non testé | présent sur la console, jamais lancé |

## Comment on teste

Paquet `xendroid.compose.odin3` (release ThinLTO restée débogable), construit sur le PC RTX 5080 par `tools/odin3/build.sh`, pilote Vulkan Qualcomm du système. Banc adb dans `tools/odin3/` : `run.sh` lance le disque et joue un scénario de `scenarios/` (manette injectée), `poll.sh` relève la puissance et les températures, les lignes `GpuFrame`/`VkFrameSync` du cœur donnent le temps GPU par image ; console refroidie avant chaque run (GPU < 40 °C), une capture par phase, logcat et plantages Android relevés à la fin (jamais de `logcat -c`). « fps » = images réellement affichées (SurfaceFlinger). Résolution interne native (1x) sauf mention.

## Jeux

| Jeu | Statut | Vu jusqu'à | Mesure | Notes | Testé le | Build |
|---|---|---|---|---|---|---|
| Gears of War 3 (World) | 🔵 Jouable | Acte 1 chapitre 1, première marche dans le navire | Patch Unlock FPS : 60,0 fps navire à l'arrêt, cinématique 59,1 à 59,3 ; GPU 9,7 à 13,7 ms par image ; 11,3 à 12,0 W, CPU 82-90 °C, GPU 68-71 °C. Endurance (9 min de marche après la cinématique) : 56,9 puis 58,8 à 59,4 fps, 13,2 à 14,2 W, CPU 102 °C, GPU 84 °C (avant 07aef3b10 : 52,1 à 58,1 fps, 15,7 à 17,4 W, GPU 94 °C). Sans patch : 30 fps verrouillés (cap du jeu), 6,1 à 7,8 W. Résolution 2x : 6,5 à 7,6 fps (GPU 63 à 79 ms par image) ; 3x (4K) : 3,6 à 3,8 fps (GPU 133 à 151 ms) | Seul le 1x (720p natif) tient 60 : la 4K60 demanderait ~10 fois le GPU de l'Adreno 830, la TV 4K du dock met l'image à l'échelle ; 60 fps seulement avec le patch « Unlock FPS » du catalogue (laissé actif sur la console) ; l'attente Direct3D de place dans le ring buffer (sub_82AF98D8, ~10 ms par image) est parquée par un quirk du titre : Guest CPU 3 94 % → 48 % d'un cœur, mêmes fps ; le présentateur recompilait son pipeline à chaque image (corrigé en 07aef3b10 : −3,9 à −4,8 W à lui seul) ; maths des pixel shaders relâchées et 7e3 en 32 bpp par quirk (GPU −1,1 et −0,6 ms, même image) ; en marche les creux restants viennent du CPU guest (Guest CPU 3 à 74 %, GPU à 7 ms) ; « Connection Lost » à l'écran titre = 360 sans câble réseau ; jamais vu en combat | 2026-10-29 | 07aef3b10 |
