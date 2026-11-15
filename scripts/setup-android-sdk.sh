#!/usr/bin/env bash
# Instala el SDK de Android mínimo para compilar Recetario en $HOME/android-sdk
# y escribe local.properties en la raíz del repositorio.
#
# Hay que aceptar la licencia del SDK de Google. Por defecto, sdkmanager la
# muestra y pregunta. Si el usuario ya la ha leído y lo autoriza, se puede
# aceptar sin preguntas con:
#   ANDROID_ACCEPT_LICENSES=yes scripts/setup-android-sdk.sh
set -euo pipefail

# Deben coincidir con app/build.gradle.kts (compileSdk) y con la versión de
# build-tools que pide AGP (9.5.1 → 36.0.0).
COMPILE_SDK=37
BUILD_TOOLS=36.0.0

# Paquete de arranque; después sdkmanager instala "cmdline-tools;latest".
BOOTSTRAP_URL="https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip"

SDK_DIR="$HOME/android-sdk"
REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LATEST_SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"

if [ ! -x "$LATEST_SDKMANAGER" ]; then
    tmp="$(mktemp -d)"
    trap 'rm -rf "$tmp"' EXIT
    echo "Descargando cmdline-tools de arranque…"
    curl -fsSL -o "$tmp/cmdline-tools.zip" "$BOOTSTRAP_URL"
    unzip -q "$tmp/cmdline-tools.zip" -d "$tmp"
    BOOTSTRAP_SDKMANAGER="$tmp/cmdline-tools/bin/sdkmanager"
else
    BOOTSTRAP_SDKMANAGER="$LATEST_SDKMANAGER"
fi

if [ "${ANDROID_ACCEPT_LICENSES:-}" = "yes" ]; then
    # `yes` termina con SIGPIPE al cerrarse sdkmanager; no debe romper pipefail.
    { yes || true; } | "$BOOTSTRAP_SDKMANAGER" --sdk_root="$SDK_DIR" --licenses >/dev/null
else
    "$BOOTSTRAP_SDKMANAGER" --sdk_root="$SDK_DIR" --licenses
fi

"$BOOTSTRAP_SDKMANAGER" --sdk_root="$SDK_DIR" \
    "cmdline-tools;latest" \
    "platform-tools" \
    "platforms;android-$COMPILE_SDK" \
    "build-tools;$BUILD_TOOLS"

echo "sdk.dir=$SDK_DIR" > "$REPO_DIR/local.properties"
echo "SDK listo en $SDK_DIR; escrito $REPO_DIR/local.properties"
