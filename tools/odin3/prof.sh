#!/bin/zsh
# prof.sh <run name> [seconds]: samples the running game (start it with run.sh and KEEP=1) with simpleperf and
# writes per-thread and per-function reports to runs/<run name>/prof/. Under the shared Odin lock.
#   SYMS  directory holding the unstripped libe.so of the installed build (default apk/syms, see build.sh)
A=~/Library/Android/sdk/platform-tools/adb
D=${0:A:h}
LOCK=~/PhpstormProjects/vita3k/tools/odin3/lock.sh
SP=$(ls -d ~/Library/Android/sdk/ndk/*/simpleperf | sort -V | tail -1)
PKG=${PKG:-xendroid.compose.odin3}
OUT=$D/runs/$1/prof; mkdir -p $OUT || exit 1
SYMS=${SYMS:-$D/apk/syms}
$LOCK take ${LOCK_OWNER:-xbox360} || exit 1
PID=$($A shell pidof ${PKG}:emu | tr -d '\r')
[ -n "$PID" ] || { echo "no ${PKG}:emu process"; exit 1; }
$A push $SP/bin/android/arm64/simpleperf /data/local/tmp/ >/dev/null
$A shell chmod 755 /data/local/tmp/simpleperf
# The core writes a perf map: guest code shows as sub_<guest address> (dump it with CPU.dump_functions_at)
$A shell /data/local/tmp/simpleperf record --app $PKG -f 2000 -e cpu-clock --duration ${2:-20} \
  -o /data/local/tmp/x360.perf.data 2>&1 | tail -2
$A pull /data/local/tmp/x360.perf.data $OUT/perf.data >/dev/null || exit 1
$A shell rm -f /data/local/tmp/x360.perf.data
R=($SP/bin/darwin/x86_64/simpleperf report -i $OUT/perf.data)
# binary_cache matches the unstripped libraries to the recorded ones by build id
if [ -d $SYMS ]; then
  (cd $OUT && python3 $SP/binary_cache_builder.py -i perf.data -lib $SYMS >/dev/null 2>&1)
  R+=(--symfs $OUT/binary_cache)
fi
$R --sort comm > $OUT/threads.txt 2>/dev/null
$R --sort comm,dso > $OUT/threads_dso.txt 2>/dev/null
$R --sort dso,symbol --percent-limit 0.3 > $OUT/symbols.txt 2>/dev/null
$R --sort comm,symbol --percent-limit 0.3 > $OUT/threads_symbols.txt 2>/dev/null
rm -rf $OUT/binary_cache
head -40 $OUT/threads.txt | tail -32
