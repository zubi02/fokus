#!/usr/bin/env bash
# Baut fokus-waechter.apk ohne Gradle. Aufruf (Git Bash):  bash android/build.sh
set -euo pipefail
cd "$(dirname "$0")"
TOOLS="$(cd ../tools && pwd -W)"
export JAVA_HOME="$TOOLS/jdk/$(ls ../tools/jdk | head -1)"
export PATH="$(cd ../tools/jdk/* && pwd)/bin:$PATH"
BT="$TOOLS/sdk/build-tools/34.0.0"
JAR="$TOOLS/sdk/platforms/android-34/android.jar"
OUT=build
rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

mkdir -p res/drawable && cp ../icons/icon-192.png res/drawable/icon.png

"$BT/aapt2.exe" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2.exe" link -o "$OUT/base.apk" -I "$JAR" --manifest AndroidManifest.xml --java "$OUT/gen" \
  --min-sdk-version 29 --target-sdk-version 34 --version-code "${VERSION_CODE:-1}" --version-name "${VERSION_NAME:-1.0}" "$OUT/res.zip"

javac -nowarn -source 8 -target 8 -encoding UTF-8 -bootclasspath "$JAR;$BT/core-lambda-stubs.jar" -d "$OUT/classes" \
  $(find src "$OUT/gen" -name '*.java')
"$BT/d8.bat" --lib "$JAR" --min-api 29 --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')

(cd "$OUT/dex" && "$BT/aapt.exe" add ../base.apk classes.dex >/dev/null)
"$BT/zipalign.exe" -f 4 "$OUT/base.apk" "$OUT/aligned.apk"

# Der Schlüssel muss gleich bleiben, sonst lassen sich Updates nicht über die alte App installieren.
if [ ! -f fokus.keystore ]; then
  keytool -genkeypair -keystore fokus.keystore -storepass fokus-waechter -keypass fokus-waechter \
    -alias fokus -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Fokus Waechter"
fi
"$BT/apksigner.bat" sign --ks fokus.keystore --ks-pass pass:fokus-waechter --key-pass pass:fokus-waechter \
  --out ../fokus/fokus-waechter.apk "$OUT/aligned.apk"
"$BT/apksigner.bat" verify --verbose ../fokus/fokus-waechter.apk | head -4
ls -la ../fokus/fokus-waechter.apk
