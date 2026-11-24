#!/bin/bash
# Carga la llave de subida en los secretos del repo de GitHub, para el CD
# (.github/workflows/publicar.yml). Se corre UNA vez, desde la raíz del repo, en el
# Mac que tiene `calarmi-upload.jks` y `keystore.properties`:
#
#   tools/ci/configurar-secretos.sh
#
# Los valores van por la entrada estándar de `gh secret set`: no se imprimen ni
# quedan en el historial de la terminal.
set -euo pipefail
cd "$(dirname "$0")/../.."
REPO=xMathyu/calarmi-android
[ -f keystore.properties ] || { echo "Falta keystore.properties"; exit 1; }
prop() { grep "^$1=" keystore.properties | cut -d= -f2-; }
STORE_FILE=$(prop storeFile)
[ -f "$STORE_FILE" ] || { echo "Falta la llave $STORE_FILE"; exit 1; }

base64 -i "$STORE_FILE" | tr -d '\n' | gh secret set UPLOAD_KEYSTORE_BASE64 --repo "$REPO"
prop storePassword | tr -d '\n' | gh secret set UPLOAD_STORE_PASSWORD --repo "$REPO"
prop keyAlias      | tr -d '\n' | gh secret set UPLOAD_KEY_ALIAS --repo "$REPO"
prop keyPassword   | tr -d '\n' | gh secret set UPLOAD_KEY_PASSWORD --repo "$REPO"
gh secret list --repo "$REPO"
