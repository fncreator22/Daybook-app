#!/usr/bin/env bash
# ---------------------------------------------------------------------------
#  Daybook - build an installable debug APK on Linux or macOS.
#
#  Usage:  ./scripts/build-apk.sh [--release]
#
#  Needs a JDK 17+ and an Android SDK. Downloads Gradle 8.13 into
#  ~/.gradle/daybook-tools if it is not already on PATH, verifying it against
#  its published SHA-256 first.
# ---------------------------------------------------------------------------
set -euo pipefail

GRADLE_VERSION="8.13"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
TOOLS_DIR="${HOME}/.gradle/daybook-tools"

TASK="assembleDebug"
VARIANT="debug"
if [[ "${1:-}" == "--release" ]]; then
    TASK="assembleRelease"
    VARIANT="release"
fi

step() { printf '\n==> %s\n' "$1"; }
ok()   { printf '    %s\n' "$1"; }
fail() { printf '\nCannot continue: %s\n' "$1" >&2; exit "${2:-1}"; }

# --- 1. JDK ----------------------------------------------------------------

step "Looking for a JDK 17 or newer"

if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    JAVA_EXE="${JAVA_HOME}/bin/java"
elif command -v java >/dev/null 2>&1; then
    JAVA_EXE="$(command -v java)"
else
    fail "no Java was found. Install a JDK 17+ (https://adoptium.net/temurin/releases/?version=17) or Android Studio, then retry." 2
fi

JAVA_VERSION="$("$JAVA_EXE" -XshowSettings:properties -version 2>&1 |
    sed -n 's/.*java\.version = \([0-9._]*\).*/\1/p' | head -n 1)"
JAVA_MAJOR="${JAVA_VERSION%%.*}"
if [[ "$JAVA_MAJOR" == "1" ]]; then
    JAVA_MAJOR="$(printf '%s' "$JAVA_VERSION" | cut -d. -f2)"
fi
if [[ -z "$JAVA_MAJOR" || "$JAVA_MAJOR" -lt 17 ]]; then
    fail "Java ${JAVA_VERSION:-unknown} at $JAVA_EXE is too old; JDK 17+ is required." 2
fi
ok "JDK $JAVA_MAJOR at $JAVA_EXE"

# --- 2. Android SDK --------------------------------------------------------

step "Looking for an Android SDK"

SDK_DIR=""
for candidate in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" \
                 "${HOME}/Android/Sdk" "${HOME}/Library/Android/sdk"; do
    if [[ -n "$candidate" && -d "$candidate" ]]; then
        SDK_DIR="$candidate"
        break
    fi
done
[[ -n "$SDK_DIR" ]] || fail "no Android SDK was found. Install Android Studio once, or set ANDROID_HOME. See INSTALL.md for a cloud build that needs nothing installed." 3
ok "SDK at $SDK_DIR"

printf '# Written by build-apk.sh. Machine-specific, never committed.\nsdk.dir=%s\n' \
    "$SDK_DIR" > "${PROJECT_ROOT}/local.properties"
ok "wrote local.properties"

# --- 3. Gradle -------------------------------------------------------------

step "Looking for Gradle ${GRADLE_VERSION}"

if command -v gradle >/dev/null 2>&1; then
    GRADLE_EXE="$(command -v gradle)"
    ok "using the gradle already on your PATH ($GRADLE_EXE)"
else
    GRADLE_EXE="${TOOLS_DIR}/gradle-${GRADLE_VERSION}/bin/gradle"
    if [[ ! -x "$GRADLE_EXE" ]]; then
        URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
        ZIP="${TOOLS_DIR}/gradle-${GRADLE_VERSION}-bin.zip"
        mkdir -p "$TOOLS_DIR"
        ok "downloading Gradle ${GRADLE_VERSION} (about 130 MB, one time only)"
        curl -fsSL "$URL" -o "$ZIP"

        ok "verifying the download"
        EXPECTED="$(curl -fsSL "${URL}.sha256" | tr -d '[:space:]')"
        if command -v sha256sum >/dev/null 2>&1; then
            ACTUAL="$(sha256sum "$ZIP" | cut -d' ' -f1)"
        else
            ACTUAL="$(shasum -a 256 "$ZIP" | cut -d' ' -f1)"
        fi
        if [[ "$ACTUAL" != "$EXPECTED" ]]; then
            rm -f "$ZIP"
            fail "the Gradle download did not match its published SHA-256. Nothing was installed." 4
        fi
        ok "checksum matches"

        unzip -q "$ZIP" -d "$TOOLS_DIR"
        rm -f "$ZIP"
    fi
    ok "using $GRADLE_EXE"
fi

# --- 4. Build --------------------------------------------------------------

step "Running gradle ${TASK}"
ok "the first run downloads the Android build tools and takes a few minutes"

cd "$PROJECT_ROOT"
"$GRADLE_EXE" "$TASK" --no-daemon --stacktrace

# --- 5. Hand over the APK --------------------------------------------------

step "Collecting the APK"

OUTPUT_DIR="${PROJECT_ROOT}/app/build/outputs/apk/${VARIANT}"
APK="$(ls -t "${OUTPUT_DIR}"/*.apk 2>/dev/null | head -n 1 || true)"
[[ -n "$APK" ]] || fail "gradle reported success but no APK was found in $OUTPUT_DIR." 5

HANDOVER="${PROJECT_ROOT}/Daybook-${VARIANT}.apk"
cp -f "$APK" "$HANDOVER"

printf '\n  Built successfully.\n'
printf '  APK:  %s\n\n' "$HANDOVER"
printf '  Copy it to your phone, open it there, and allow "install unknown apps".\n'
if [[ "$VARIANT" == "debug" ]]; then
    printf '  This debug build installs as "Daybook (debug)" and is signed with the\n'
    printf '  standard Android debug key, which is fine for your own testing.\n'
fi
