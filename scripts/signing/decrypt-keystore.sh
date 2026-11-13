#!/usr/bin/env bash
# Descifra la llave de firma para que Gradle firme el APK release.
#
# Uso: SIGNING_PASSPHRASE=… scripts/signing/decrypt-keystore.sh <entrada.p12.enc> <salida.p12>
#
# La salida es la llave EN CLARO: escríbela fuera del repositorio (en GitHub
# Actions, en $RUNNER_TEMP) y bórrala en cuanto termine la compilación.
# Si la frase es incorrecta, sale con código 1 y no deja ningún archivo.
# La frase nunca aparece en la línea de órdenes ni en la salida.
set -euo pipefail
umask 077

if [ "$#" -ne 2 ]; then
  echo "Uso: SIGNING_PASSPHRASE=… $0 <entrada.p12.enc> <salida.p12>" >&2
  exit 2
fi
in="$1"
out="$2"

if [ -z "${SIGNING_PASSPHRASE:-}" ]; then
  echo "Error: falta la variable SIGNING_PASSPHRASE con la frase de la llave." >&2
  exit 1
fi
if [ ! -f "$in" ]; then
  echo "Error: no existe la llave cifrada $in." >&2
  exit 1
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
trap 'exit 1' INT TERM HUP

# Con una frase incorrecta, openssl casi siempre falla; keytool lo confirma en
# los raros casos en que el relleno sale válido por casualidad.
if ! openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 -pass env:SIGNING_PASSPHRASE \
  -in "$in" -out "$tmp/release.p12" 2>/dev/null ||
  ! keytool -list -storetype PKCS12 -keystore "$tmp/release.p12" -alias recetario \
    -storepass:env SIGNING_PASSPHRASE >/dev/null 2>&1; then
  echo "Error: no se pudo descifrar la llave. ¿Es correcta la frase SIGNING_PASSPHRASE?" >&2
  exit 1
fi

mkdir -p "$(dirname "$out")"
mv -f "$tmp/release.p12" "$out"
echo "Llave descifrada en $out."
