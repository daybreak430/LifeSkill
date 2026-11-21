#!/usr/bin/env bash
# 构建并签名 Release APK，输出到 release/LifeSkill-<版本>-release.apk。
# 沿用既有发布证书（默认本机调试证书），保证覆盖升级不被签名不一致拦住。
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION=$(grep -m1 'versionName = ' app/build.gradle.kts | sed -E 's/.*"(.*)".*/\1/')
CODE=$(grep -m1 'versionCode = ' app/build.gradle.kts | sed -E 's/[^0-9]*([0-9]+).*/\1/')

export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
KS="${RELEASE_KEYSTORE:-$HOME/.android/debug.keystore}"
KS_PASS="${RELEASE_KEYSTORE_PASS:-android}"
ALIAS="${RELEASE_KEY_ALIAS:-androiddebugkey}"
BUILD_TOOLS=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)

echo "版本 $VERSION ($CODE)，构建工具 $BUILD_TOOLS"
./gradlew :app:assembleRelease --console=plain -q

UNSIGNED=app/build/outputs/apk/release/app-release-unsigned.apk
OUT=release/LifeSkill-"$VERSION"-release.apk
mkdir -p release
"$BUILD_TOOLS"/zipalign -f -p 4 "$UNSIGNED" /tmp/lifeskill-aligned.apk
"$BUILD_TOOLS"/apksigner sign \
  --ks "$KS" --ks-key-alias "$ALIAS" \
  --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$OUT" /tmp/lifeskill-aligned.apk
rm -f /tmp/lifeskill-aligned.apk

"$BUILD_TOOLS"/apksigner verify --print-certs "$OUT" | grep -E "Signer #1 certificate (DN|SHA-256)"
echo "大小 $(stat -f%z "$OUT") bytes"
echo "SHA-256 $(shasum -a 256 "$OUT" | cut -d' ' -f1)"
echo "$OUT"
