#!/usr/bin/env bash
# Build sans Gradle : aapt2 + javac + d8 + apksigner.
set -euo pipefail
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:-$HOME/Android/Sdk}
BT=$SDK/build-tools/35.0.0
JAR=$SDK/platforms/android-35/android.jar

rm -rf build && mkdir -p build/gen build/classes
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml --java build/gen \
    --min-sdk-version 31 --target-sdk-version 35 -o build/unsigned.apk build/res.zip
javac --release 17 -classpath "$JAR" -d build/classes \
    $(find src build/gen -name '*.java')
"$BT/d8" --lib "$JAR" --min-api 31 --output build $(find build/classes -name '*.class')
(cd build && zip -q unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk

KS=ghostguard.keystore
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias ghostguard -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=GhostGuard" >/dev/null
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --out GhostGuard.apk build/aligned.apk
echo "OK -> $(pwd)/GhostGuard.apk"
