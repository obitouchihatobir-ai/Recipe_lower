#!/usr/bin/env bash
# Genera la llave de firma de Recetario y guarda SOLO su versión cifrada.
#
# Uso: SIGNING_PASSPHRASE=… scripts/signing/create-keystore.sh <salida.p12.enc>
#
# - La frase (20 caracteres o más) es la contraseña del almacén PKCS12 y de la
#   llave (alias «recetario»), y también la del cifrado AES-256 del archivo.
# - Nunca sobrescribe una llave existente: si la llave cambiara, las versiones
#   nuevas de la app no se podrían instalar encima de las anteriores.
# - La llave en claro solo existe en un directorio temporal que se borra al salir.
# - La frase nunca aparece en la línea de órdenes ni en la salida: keytool y
#   openssl la leen de la variable de entorno.
set -euo pipefail
umask 077

if [ "$#" -ne 1 ]; then
  echo "Uso: SIGNING_PASSPHRASE=… $0 <salida.p12.enc>" >&2
  exit 2
fi
out="$1"

if [ -z "${SIGNING_PASSPHRASE:-}" ]; then
  echo "Error: falta la variable SIGNING_PASSPHRASE con la frase de la llave." >&2
  exit 1
fi
if [ "${#SIGNING_PASSPHRASE}" -lt 20 ]; then
  echo "Error: la frase SIGNING_PASSPHRASE debe tener 20 caracteres o más." >&2
  exit 1
fi
if [ -e "$out" ]; then
  echo "Error: la llave ya existe en $out. No se crea otra para no perder la anterior." >&2
  exit 1
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
trap 'exit 1' INT TERM HUP

keytool -genkeypair -storetype PKCS12 -keyalg RSA -keysize 4096 -validity 36500 \
  -alias recetario -dname "CN=Recetario" \
  -keystore "$tmp/release.p12" \
  -storepass:env SIGNING_PASSPHRASE -keypass:env SIGNING_PASSPHRASE

openssl enc -aes-256-cbc -pbkdf2 -iter 600000 -salt -pass env:SIGNING_PASSPHRASE \
  -in "$tmp/release.p12" -out "$tmp/release.p12.enc"

# Antes de darla por buena, comprueba que la copia cifrada se descifra igual.
if ! openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 -pass env:SIGNING_PASSPHRASE \
  -in "$tmp/release.p12.enc" | cmp -s - "$tmp/release.p12"; then
  echo "Error: la llave cifrada no se descifra igual que la original." >&2
  exit 1
fi

mkdir -p "$(dirname "$out")"
mv "$tmp/release.p12.enc" "$out"
echo "Llave de firma creada y cifrada en $out (alias «recetario»)."
