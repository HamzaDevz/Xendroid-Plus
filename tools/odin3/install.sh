#!/bin/zsh
# install.sh [apk]: installs the Odin 3 build (default tools/odin3/apk/xendroid-odin3.apk, from build.sh) and lets it
# read the discs on the SD card. Under the shared Odin lock.
#   PKG         package the apk installs as (default xendroid.compose.odin3)
#   LOCK_OWNER  owner in the shared Odin lock (default xbox360); HOLD=1 keeps the lock for the runs that follow
A=~/Library/Android/sdk/platform-tools/adb
D=${0:A:h}
LOCK=~/PhpstormProjects/vita3k/tools/odin3/lock.sh
APK=${1:-$D/apk/xendroid-odin3.apk}
PKG=${PKG:-xendroid.compose.odin3}
OWNER=${LOCK_OWNER:-xbox360}
[ -f $APK ] || { echo "no apk $APK"; exit 2; }
$LOCK take $OWNER || exit 1
trap '[ "$HOLD" = 1 ] || $LOCK release $OWNER' EXIT

$A install -r $APK || exit 1
$A shell appops set $PKG MANAGE_EXTERNAL_STORAGE allow
$A shell dumpsys package $PKG | grep -m1 versionName
