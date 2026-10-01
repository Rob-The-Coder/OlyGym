#!/usr/bin/env bash
#
# Build the signed Android APK.
#
#   ./scripts/build-apk.sh            build, sign, verify  ->  ./OlyGym-<version>.apk
#   ./scripts/build-apk.sh --install  ...and install it on the phone attached over USB
#
# Needs the frontend deps installed, Java 21 and the Android SDK. The signing key is
# android-keys/olygym-release.jks and its password comes from $KS_PASS, else
# android-keys/password.txt, else a prompt. See android-keys/README.txt.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID="$ROOT/frontend/android"
KEYSTORE="$ROOT/android-keys/olygym-release.jks"
KEY_ALIAS="olygym"

INSTALL=0
for arg in "$@"; do
  case "$arg" in
    --install) INSTALL=1 ;;
    -h|--help) sed -n '3,10p' "${BASH_SOURCE[0]}" | cut -c3-; exit 0 ;;
    *) echo "unknown option: $arg (try --help)" >&2; exit 2 ;;
  esac
done

say() { printf '\n==> %s\n' "$*"; }

say "Checking the toolchain"
[ -d "$ROOT/frontend/node_modules" ] || {
  echo "frontend/node_modules is missing — run: cd frontend && npm install" >&2; exit 1; }
[ -f "$KEYSTORE" ] || {
  echo "no signing key at $KEYSTORE — see android-keys/README.txt" >&2; exit 1; }

# The SDK: environment first, then the path Gradle itself reads.
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[ -n "$SDK" ] || SDK="$(sed -n 's/^sdk\.dir=//p' "$ANDROID/local.properties" 2>/dev/null || true)"
if [ -z "$SDK" ] || [ ! -d "$SDK/build-tools" ]; then
  echo "Android SDK not found — set ANDROID_HOME or frontend/android/local.properties" >&2; exit 1
fi
BT="$(ls -d "$SDK"/build-tools/*/ | sort -V | tail -1)"
echo "SDK         $SDK"
echo "build-tools $(basename "$BT")"

say "Keystore password"
KS_PASS="${KS_PASS:-$(cat "$ROOT/android-keys/password.txt" 2>/dev/null || true)}"
if [ -z "$KS_PASS" ]; then
  read -rsp "password for $(basename "$KEYSTORE"): " KS_PASS
  echo
fi
export KS_PASS

say "Web build (mobile flavor) + cap sync"
(cd "$ROOT/frontend" && npm run build:mobile)

VERSION="$(node -p "require('$ROOT/frontend/package.json').version")"
GRADLE_VERSION="$(sed -n 's/.*versionName "\(.*\)"/\1/p' "$ANDROID/app/build.gradle")"
[ "$VERSION" = "$GRADLE_VERSION" ] || echo \
  "warning: package.json is $VERSION but app/build.gradle is $GRADLE_VERSION — the APK is named from package.json"
APK="$ROOT/OlyGym-$VERSION.apk"

say "Gradle release build"
(cd "$ANDROID" && ./gradlew assembleRelease)

say "Align and sign"
UNSIGNED="$ANDROID/app/build/outputs/apk/release/app-release-unsigned.apk"
"$BT/zipalign" -f -p 4 "$UNSIGNED" "$ANDROID/aligned.apk"
"$BT/apksigner" sign \
  --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
  --ks-pass env:KS_PASS --key-pass env:KS_PASS \
  --v4-signing-enabled false \
  --out "$APK" "$ANDROID/aligned.apk"
rm -f "$ANDROID/aligned.apk"

say "Verifying"
"$BT/apksigner" verify --print-certs "$APK" | grep -E 'certificate DN|SHA-256 digest'
"$BT/aapt2" dump badging "$APK" 2>/dev/null | sed -n 1p
sha256sum "$APK"

say "Done: $APK"
echo "Install: adb install -r \"$APK\""
echo "     or: copy it to the phone and tap it (allow installs from that app once)"

if [ "$INSTALL" = 1 ]; then
  say "Installing on the attached device"
  "$(command -v adb || echo "$SDK/platform-tools/adb")" install -r "$APK"
fi

cat <<'NOTE'

frontend/dist now holds the mobile bundle — run `npm run build` there before deploying
dist to a server (docs/MOBILE.md). To publish: bump `version` in frontend/package.json
and versionCode/versionName in frontend/android/app/build.gradle, then attach this file
to a GitHub release tagged v<version>; the in-app updater reads the newest release's
first .apk asset.
NOTE
