#!/usr/bin/env bash
#
# Password Generator v2.0 — Manual Build Script
# Requires: Android SDK, kotlinc, d8, aapt2, apksigner, zip
#
# Usage: export ANDROID_HOME=/path/to/android-sdk && bash build.sh
#

set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
BUILD_TOOLS="${ANDROID_HOME}/build-tools/37.0.0"
PLATFORM="${ANDROID_HOME}/platforms/android-37"
PLATFORM_LINK="${ANDROID_HOME}/platforms/android-34"  # for older aapt2 compatibility
KOTLIN_STDLIB="/usr/share/maven-repo/org/jetbrains/kotlin/kotlin-stdlib/1.3.31/kotlin-stdlib-1.3.31.jar"
KOTLIN_STDLIB_JDK7="/usr/share/maven-repo/org/jetbrains/kotlin/kotlin-stdlib-jdk7/1.3.31/kotlin-stdlib-jdk7-1.3.31.jar"
KEYSTORE="release.keystore"
KEYSTORE_PASS="android"
KEY_ALIAS="passwordgen"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Password Generator v2.0 Build ==="

# Clean
rm -rf app/build/kotlin_classes app/build/compiled_res app/build/tmpdex app/build/classes.dex app/build/unsigned.apk
mkdir -p app/build/kotlin_classes app/build/compiled_res app/build/tmpdex

# Step 1: Compile Kotlin → .class
echo "[1/5] Compiling Kotlin..."
kotlinc -cp "${PLATFORM}/android.jar" \
  -d app/build/kotlin_classes \
  app/src/main/java/com/passwordgen/MainActivity.kt

# Step 2: Convert .class → classes.dex (with Kotlin stdlib)
echo "[2/5] Converting to DEX..."
"${BUILD_TOOLS}/d8" --lib "${PLATFORM}/android.jar" \
  --output app/build/tmpdex/ \
  app/build/kotlin_classes/com/passwordgen/*.class \
  "${KOTLIN_STDLIB}" \
  "${KOTLIN_STDLIB_JDK7}"

# Step 3: Compile Android resources
echo "[3/5] Compiling resources..."
aapt2 compile --dir app/src/main/res/ -o app/build/compiled_res/all.zip
cd app/build/compiled_res && unzip -o all.zip && cd "$SCRIPT_DIR"

# Step 4: Link APK (use android-34 jar for older aapt2 compatibility)
echo "[4/5] Linking APK..."
aapt2 link -o app/build/unsigned.apk \
  -I "${PLATFORM_LINK}/android.jar" \
  --manifest app/src/main/AndroidManifest.xml \
  app/build/compiled_res/*.flat

# Step 5: Add dex & sign
echo "[5/5] Signing APK..."
cd app/build && cp tmpdex/classes.dex classes.dex
zip unsigned.apk classes.dex
apksigner sign --ks "${KEYSTORE}" \
  --ks-pass "pass:${KEYSTORE_PASS}" \
  --ks-key-alias "${KEY_ALIAS}" \
  unsigned.apk

# Copy result
cp unsigned.apk apk/PasswordGen_v2.apk
cd "$SCRIPT_DIR"

echo ""
echo "=== Build Complete! ==="
echo "APK: app/build/apk/PasswordGen_v2.apk"
echo "Install: adb install app/build/apk/PasswordGen_v2.apk"