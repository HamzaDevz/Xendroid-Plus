# pcsetup.ps1: installs the Android build toolchain on the RTX 5080 PC in C:\x without admin rights (JDK 21, SDK
# cmdline tools, NDK 29, CMake 3.30.3, glslang 16.6; spirv-opt/spirv-dis from the NDK) and clones the fork in C:\x\xp.
# Run with: scp pcsetup.ps1 pc5080:C:/x/ && ssh pc5080 powershell -ExecutionPolicy Bypass -File C:\x\pcsetup.ps1
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
Add-Type -Namespace W -Name P -MemberDefinition '[DllImport("kernel32.dll")] public static extern uint SetThreadExecutionState(int f);'
[void][W.P]::SetThreadExecutionState(-2147483647)
$R = 'C:\x'; New-Item -ItemType Directory -Force $R, "$R\dl", "$R\tools" | Out-Null
function Get-Zip($url, $zip, $dest) {
  if (-not (Test-Path $dest)) {
    "download $url"
    Invoke-WebRequest $url -OutFile "$R\dl\$zip"
    Expand-Archive "$R\dl\$zip" -DestinationPath $dest
    Remove-Item "$R\dl\$zip"
  }
}
Get-Zip 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse' 'jdk.zip' "$R\tools\jdk"
$jdk = (Get-ChildItem "$R\tools\jdk" -Directory | Select -First 1).FullName
Get-Zip 'https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip' 'clt.zip' "$R\dl\clt"
if (-not (Test-Path "$R\sdk\cmdline-tools\latest")) {
  New-Item -ItemType Directory -Force "$R\sdk\cmdline-tools" | Out-Null
  Move-Item "$R\dl\clt\cmdline-tools" "$R\sdk\cmdline-tools\latest"
}
Get-Zip 'https://github.com/KhronosGroup/glslang/releases/download/16.6.0/glslang-16.6.0-windows-x86_64-release.zip' 'glslang.zip' "$R\tools\glslang"
$env:JAVA_HOME = $jdk; $env:Path = "$jdk\bin;$env:Path"
$sdkm = "$R\sdk\cmdline-tools\latest\bin\sdkmanager.bat"
$y = ('y' + "`n") * 20
$y | & $sdkm --sdk_root="$R\sdk" --licenses | Out-Null
& $sdkm --sdk_root="$R\sdk" 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0' 'ndk;29.0.14206865' 'cmake;3.30.3' 2>&1 | Select-String -NotMatch '\[=*' | Select -Last 5
# spirv-opt / spirv-dis from the NDK shader tools
New-Item -ItemType Directory -Force "$R\tools\bin" | Out-Null
Copy-Item "$R\sdk\ndk\29.0.14206865\shader-tools\windows-x86_64\spirv-*.exe" "$R\tools\bin\" -Force
Get-ChildItem "$R\tools\glslang" -Recurse -Filter 'glslang*.exe' | Select FullName
Get-ChildItem "$R\tools\bin" | Select Name
git config --global core.longpaths true
if (-not (Test-Path "$R\xp")) { git clone -q --branch odin3 https://github.com/HamzaDevz/Xendroid-Plus.git "$R\xp" }
git -C "$R\xp" log --oneline -1
"done"
