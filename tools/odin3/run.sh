#!/bin/zsh
# run.sh <run name>: boots a disc in the Xendroid+ build on the Odin 3, plays the scenario and writes the measured
# segments to runs/<run name>/.
#   PKG         package (default: this branch's build, xendroid.compose.odin3)
#   SCENARIO    scenarios/<SCENARIO>.txt (default gow3); its `rom` line names the disc in $ROMS
#   ROM         disc in $ROMS to play instead of the scenario's `rom` line
#   TARGET      frame rate the game aims for, for the slow-frame count (default 30)
#   CONFIG      "Section.key=value ...": lines for the title's per-game config for this run only (A/B runs);
#               the title's own config is restored at the end
#   PATCHES=1   the game patches in patches/ (Unlock FPS...) are in the app's patches folder for this run;
#               otherwise they are taken out of it
#   KEEP=1      leave the game running at the end
#   HOLD=1      keep the lock for the next run of a series (the series releases it)
#   LOCK_OWNER  owner in the shared Odin lock (default xbox360)
# Scenario lines: rom <file> | title <TITLEID> | wait <s> | btn <name> [ms] | stick|rstick <x> <y> <ms> |
#   trig <lt|rt> <ms> | repeat <n> <line> | shot <name> | measure <label> ... end
A=~/Library/Android/sdk/platform-tools/adb
D=${0:A:h}
LOCK=~/PhpstormProjects/vita3k/tools/odin3/lock.sh
NAME=$1; shift
OUT=$D/runs/$NAME
PKG=${PKG:-xendroid.compose.odin3}
SC=$D/scenarios/${SCENARIO:-gow3}.txt
ROMS=/storage/FBDD-1F17/Emulations/ROMs/xbox360
DATA=/storage/emulated/0/Android/data/$PKG/files/compose
T=/data/local/tmp/x360
ACT=xendroid.compose.EmulatorHostActivity
[ -f $SC ] || { echo "no scenario $SC"; exit 2; }
ROM=${ROM:-$(sed -n 's/^rom \(.*\)$/\1/p' $SC)}
TITLE=$(sed -n 's/^title \(.*\)$/\1/p' $SC)
rm -rf $OUT; mkdir -p $OUT || exit 1

OWNER=${LOCK_OWNER:-xbox360}
$LOCK take $OWNER || exit 1
GC=$DATA/config/$TITLE.config.toml
trap '$A shell pkill -f $T/poll.sh; [ "$KEEP" = 1 ] || $A shell am force-stop $PKG
  if [ -n "$CONFIG" ]; then
    if [ -f $OUT/game.config.toml.0 ]; then $A push $OUT/game.config.toml.0 $GC >/dev/null; else $A shell rm -f $GC; fi
  fi
  [ "$HOLD" = 1 ] || $LOCK release $OWNER' EXIT

focused() { $A shell dumpsys window | grep mCurrentFocus | grep -q "$PKG/$ACT"; }
pad() { focused || { echo "ABORT: the game lost the focus" | tee -a $OUT/summary.txt; exit 1; }; $A shell sh $T/pad.sh "$@"; }
shot() {
  print "$1 $($A shell date +%T.%N | cut -c1-12)" >> $OUT/shots.txt
  # With the HDMI dock screencap prints a multiple-displays warning before the PNG
  $A exec-out screencap -p | python3 -I -c 'import sys; d = sys.stdin.buffer.read(); sys.stdout.buffer.write(d[d.find(b"\x89PNG"):])' \
    > $OUT/$1.png && sips -Z 960 $OUT/$1.png --out $OUT/$1.png >/dev/null
}

$A shell mkdir -p $T
$A push $D/pad.sh $D/poll.sh $T/ >/dev/null
$A shell am force-stop $PKG
if [ -n "$CONFIG" ]; then
  [ -n "$TITLE" ] || { echo "CONFIG needs a title line in the scenario"; exit 2; }
  $A exec-out "cat $GC 2>/dev/null" > $OUT/game.config.toml.0
  [ -s $OUT/game.config.toml.0 ] || rm -f $OUT/game.config.toml.0
  python3 -I - ${=CONFIG} > $OUT/game.config.toml <<'PY'
import sys
sections = {}
for kv in sys.argv[1:]:
    k, v = kv.split("=", 1)
    s, n = k.split(".", 1)
    sections.setdefault(s, []).append(f"{n} = {v}")
for s, lines in sections.items():
    print(f"[{s}]")
    print("\n".join(lines))
PY
  $A shell mkdir -p $DATA/config
  $A push $OUT/game.config.toml $GC >/dev/null
fi
# Start every run from a cool GPU and board so throttling does not depend on the previous run
for i in $(seq 90); do
  [ $($A shell cat /sys/class/kgsl/kgsl-3d0/temp | tr -d '\r') -lt 40000 ] &&
    [ $($A shell cat /sys/class/power_supply/battery/temp | tr -d '\r') -lt 340 ] && break
  sleep 2
done
$A shell appops set $PKG MANAGE_EXTERNAL_STORAGE allow 2>/dev/null
$A shell mkdir -p $DATA/patches
for p in $D/patches/*.patch.toml(N); do
  if [ "$PATCHES" = 1 ]; then $A push $p "$DATA/patches/" >/dev/null; else $A shell "rm -f '$DATA/patches/${p:t}'"; fi
done
$A shell input keyevent KEYCODE_WAKEUP; $A shell wm dismiss-keyguard
# Never logcat -c: the log also holds Hamza's own sessions. This run's lines are the ones after LC0.
LC0=$($A shell date "'+%m-%d %H:%M:%S.000'" | tr -d '\r')
$A shell "am start -n $PKG/$ACT -a xendroid.intent.action.xendroid --es game_uri '$ROMS/$ROM'" >/dev/null
for i in $(seq 60); do focused && break; sleep 1; done
focused || { echo "ABORT: the emulator never got the focus" | tee $OUT/summary.txt; exit 1; }
echo "pkg=$PKG version=$($A shell dumpsys package $PKG | sed -n 's/.*versionName=//p' | head -1 | tr -d '\r') rom=$ROM config='$CONFIG' patches=${PATCHES:-0} start=$(date +%T)" > $OUT/context.txt

# The activity may have several SurfaceViews: the game's is the one that presents
game_layer() {
  local n
  for n in ${(u)${(f)"$($A shell dumpsys SurfaceFlinger --list | tr -d '\r' |
      grep -oE "SurfaceView\[$PKG/$ACT\]\(BLAST\)#[0-9]+")"}}; do
    print -r -- "$($A shell "dumpsys SurfaceFlinger --latency '$n'" | awk 'NF==3 && $2>0 {c++} END {print c+0}') $n"
  done | sort -rn | head -1 | cut -d' ' -f2-
}
LAYER=""
SEG=""
seg_begin() {
  SEG=$OUT/$1; mkdir -p $SEG
  [ -n "$LAYER" ] || LAYER=$(game_layer)
  PID=$($A shell pidof ${PKG}:emu 2>/dev/null | tr -d '\r')
  $A shell "cat /proc/$PID/task/*/stat" > $SEG/threads_start.txt
  L0=$($A shell "wc -l < $DATA/xe.log" | tr -d '\r ')
  $A shell sh $T/poll.sh 3600 > $SEG/poll.csv &
  # SurfaceFlinger keeps the last 128 presents: sample every second
  { while [ ! -f $SEG/.stop ]; do $A shell "dumpsys SurfaceFlinger --latency '$LAYER'"; sleep 1; done } > $SEG/sf_latency.txt &
  T0=$SECONDS
}
seg_end() {
  $A shell "cat /proc/$PID/task/*/stat" > $SEG/threads_end.txt
  echo $((SECONDS - T0)) > $SEG/duration
  # The core's per-second frame lines (GpuFrame, VkFrameSync) written during the segment
  $A shell "tail -n +$((L0 + 1)) $DATA/xe.log" | grep -a -E 'GpuFrame|VkFrameSync|VkGpu|FPS' > $SEG/core.log
  touch $SEG/.stop; $A shell pkill -f $T/poll.sh; wait
  python3 -I $D/summarize.py $SEG ${TARGET:-30} | tee -a $OUT/summary.txt
  SEG=""
}
step() {
  case $1 in
    rom|title|""|\#*) ;;
    wait) sleep $2;;
    btn|stick|rstick|trig) pad "$@";;
    shot) [ -z "$SEG" ] || { echo "a screenshot during a measure stalls SurfaceFlinger" >&2; exit 2; }; shot $2;;
    repeat) local n=$2; shift 2; for i in $(seq $n); do step "$@"; done;;
    measure) seg_begin $2;;
    end) seg_end;;
    *) echo "unknown scenario line: $*" >&2; exit 2;;
  esac
}
# Read the whole file first: adb shell would swallow the rest of the scenario from stdin
for line in "${(@f)$(<$SC)}"; do step ${=line%%\#*}; done
[ -z "$SEG" ] || seg_end
$A shell "tail -c 4000000 $DATA/xe.log" > $OUT/xe.log
# The app's logcat lines and the crashes Android kept, since the launch
PIDS=$($A shell pidof ${PKG}:emu ${PKG} | tr -d '\r' | sed 's/ /|/g')
$A logcat -d -T "$LC0" 2>/dev/null | grep -a -E "$PKG| (${PIDS:-none}) " > $OUT/logcat.txt
for t in data_app_native_crash data_app_anr; do
  $A shell dumpsys dropbox --print $t 2>/dev/null | grep -a -B2 -A40 "Process: $PKG"
done > $OUT/crashes.txt
[ -s $OUT/crashes.txt ] || rm -f $OUT/crashes.txt
focused || echo "WARNING: the emulator no longer has the focus (crash?)" | tee -a $OUT/summary.txt
