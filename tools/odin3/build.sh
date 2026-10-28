#!/bin/zsh
# build.sh [ref]: builds the Odin 3 APK on the RTX 5080 PC (ssh pc5080; toolchain in C:\x, see pcsetup.ps1) from
# the pushed odin3 branch, or from <ref> on origin, and copies it to tools/odin3/apk/xendroid-odin3.apk.
#   DIRTY=1   also applies this checkout's uncommitted changes (git diff HEAD) on the PC, for A/B builds
# The package is xendroid.compose.odin3: a release build (ThinLTO) that stays debuggable for run-as and simpleperf.
D=${0:A:h}
R=${D:h:h}
REF=${1:-odin3}
OUT=$D/apk; mkdir -p $OUT
PS=~/bin/pc5080-ps.sh
quiet() { grep -v -i -e quantum -e 'store now' -e pq.html -e 'server may'; }
if [ "$DIRTY" = 1 ]; then
  git -C $R diff HEAD > $OUT/dirty.patch
  scp -q $OUT/dirty.patch pc5080:C:/x/dirty.patch 2>&1 | quiet
fi
$PS <<EOF | LC_ALL=C tr -d '\000\r' | quiet | tee $OUT/build.log | grep -E '^(BUILD|FAILURE|ERROR|error:|.*error:|> Task .*FAILED|apk |==)' | head -40
\$ErrorActionPreference = 'Continue'
Add-Type -Namespace W -Name P -MemberDefinition '[DllImport("kernel32.dll")] public static extern uint SetThreadExecutionState(int f);'
[void][W.P]::SetThreadExecutionState(-2147483647)
\$jdk = (Get-ChildItem C:\x\tools\jdk -Directory | Select -First 1).FullName
\$gl = (Get-ChildItem C:\x\tools\glslang -Recurse -Filter glslang.exe | Select -First 1).DirectoryName
if (-not (Test-Path "\$gl\glslangValidator.exe")) { Copy-Item "\$gl\glslang.exe" "\$gl\glslangValidator.exe" }
\$env:JAVA_HOME = \$jdk
\$env:Path = "\$jdk\bin;\$gl;C:\x\tools\bin;\$env:Path"
\$env:XENDROID_DEBUG_INFO = '-gline-tables-only'
Set-Location C:\x\xp
git fetch -q origin
git reset -q --hard
git checkout -q --detach "origin/$REF" 2>&1
if ("$DIRTY" -eq "1") { git apply --whitespace=nowarn C:\x\dirty.patch; "== dirty patch applied: \$LASTEXITCODE" }
"== building \$(git log --oneline -1)"
Set-Content local.properties "sdk.dir=C:/x/sdk\`ncmake.dir=C:/x/sdk/cmake/3.30.3"
cmd /c "gradlew.bat --console=plain :app:assembleRelease -PxendroidReleaseDebuggable -PxendroidReleaseIdSuffix=.odin3 > C:\x\gradle.log 2>&1"
Get-Content C:\x\gradle.log | Select-String -NotMatch '^w: ' | Select -Last 60
\$apk = Get-ChildItem app\build\outputs\apk\release\*.apk | Sort LastWriteTime | Select -Last 1
"apk \$(\$apk.FullName) \$(\$apk.Length)"
EOF
APK=$(sed -n 's/^apk \(.*\.apk\) [0-9]*$/\1/p' $OUT/build.log | tail -1)
[ -n "$APK" ] || { echo "no apk, see $OUT/build.log"; exit 1; }
scp -q "pc5080:${APK//\\//}" $OUT/unsigned.apk 2>&1 | quiet
# The unstripped core of this build, for prof.sh
mkdir -p $OUT/syms
scp -q 'pc5080:C:/x/xp/app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/arm64-v8a/libe.so' \
  $OUT/syms/libe.so 2>&1 | quiet
# Signed with this Mac's debug key, so updates install over each other
BT=~/Library/Android/sdk/build-tools/35.0.0
$BT/zipalign -f -p 4 $OUT/unsigned.apk $OUT/aligned.apk &&
  $BT/apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android --key-pass pass:android \
    --out $OUT/xendroid-odin3.apk $OUT/aligned.apk && rm -f $OUT/unsigned.apk $OUT/aligned.apk $OUT/*.idsig &&
  ls -la $OUT/xendroid-odin3.apk
