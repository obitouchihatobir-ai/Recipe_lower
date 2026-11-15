#!/usr/bin/env bash
# Pruebas de create-keystore.sh y decrypt-keystore.sh, con una frase de prueba
# y en un directorio temporal que se borra al terminar.
#
# Uso: bash scripts/signing/test-signing-scripts.sh
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
trap 'exit 1' INT TERM HUP

# Frases de prueba (nunca la real). La buena tiene justo el mínimo: 20 caracteres.
export SIGNING_PASSPHRASE='prueba de firma 2026'
short_phrase='prueba de firma 202'
wrong_phrase='otra frase distinta 2026'

enc="$work/release.p12.enc"
log="$work/salida.log"
failures=0

# Ejecuta un script con la frase indicada. Su salida va a $log y sus archivos
# temporales a $work/tmp, para poder comprobar que no deja nada en claro.
run() {
  local phrase="$1"
  shift
  mkdir -p "$work/tmp"
  SIGNING_PASSPHRASE="$phrase" TMPDIR="$work/tmp" bash "$@" >"$log" 2>&1
}

# Éxito solo si el script se niega de forma controlada (código 1). Así un script
# que no existe (código 127) no pasa por un rechazo correcto.
rejected() {
  local status=0
  run "$@" || status=$?
  [ "$status" -eq 1 ]
}

# Los scripts no deben dejar nada en su directorio temporal.
tmp_is_empty() {
  [ -z "$(ls -A "$work/tmp")" ]
}

passphrase_not_printed() {
  ! grep -qF -- "$SIGNING_PASSPHRASE" "$log"
}

check() {
  local name="$1"
  shift
  : >"$log"
  if "$@"; then
    echo "OK    $name"
  else
    echo "FALLO $name"
    sed 's/^/      | /' "$log" 2>/dev/null || true
    failures=$((failures + 1))
  fi
}

check_a() {
  run "$SIGNING_PASSPHRASE" "$here/create-keystore.sh" "$enc" &&
    [ -s "$enc" ] &&
    [ "$(head -c 8 "$enc")" = "Salted__" ] &&
    [ -z "$(find "$work" -name '*.p12' -print)" ] &&
    tmp_is_empty &&
    passphrase_not_printed
}

check_b() {
  local out="$work/descifrada/release.p12" status=0
  {
    run "$SIGNING_PASSPHRASE" "$here/decrypt-keystore.sh" "$enc" "$out" &&
      tmp_is_empty &&
      passphrase_not_printed &&
      keytool -list -keystore "$out" -storepass "$SIGNING_PASSPHRASE" 2>/dev/null |
      grep -q '^recetario,.*PrivateKeyEntry'
  } || status=1
  rm -rf "$work/descifrada"
  return "$status"
}

check_c() {
  rejected "$wrong_phrase" "$here/decrypt-keystore.sh" "$enc" "$work/mal/release.p12" &&
    [ -z "$(find "$work/mal" -type f -print 2>/dev/null)" ] &&
    tmp_is_empty
}

check_d() {
  local before
  [ -s "$enc" ] || return 1
  before="$(sha256sum <"$enc")"
  rejected "$SIGNING_PASSPHRASE" "$here/create-keystore.sh" "$enc" &&
    [ "$(sha256sum <"$enc")" = "$before" ]
}

check_e() {
  local target="$work/corta/release.p12.enc"
  rejected "$short_phrase" "$here/create-keystore.sh" "$target" &&
    rejected "" "$here/create-keystore.sh" "$target" &&
    [ ! -e "$target" ]
}

check "(a) create-keystore.sh genera release.p12.enc cifrado, sin dejar ningún .p12 en claro" check_a
check "(b) decrypt-keystore.sh lo descifra y keytool muestra el alias recetario" check_b
check "(c) con una frase incorrecta, el descifrado falla y no deja archivo" check_c
check "(d) create-keystore.sh se niega si release.p12.enc ya existe y no lo cambia" check_d
check "(e) create-keystore.sh se niega con una frase vacía o de menos de 20 caracteres" check_e

if [ "$failures" -ne 0 ]; then
  echo "$failures comprobación(es) fallida(s)."
  exit 1
fi
echo "Las 5 comprobaciones pasan."
