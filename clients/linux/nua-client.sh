#!/usr/bin/env bash
# Thin Linux launcher around shared/protocol-consumers/python's nua_client
# package. See clients/linux/README.md.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PACKAGE_DIR="$SCRIPT_DIR/../../shared/protocol-consumers/python"

PYTHON_BIN="${NUA_CLIENT_PYTHON:-python3}"

if ! "$PYTHON_BIN" -c "import nua_client" >/dev/null 2>&1; then
    "$PYTHON_BIN" -m pip install --quiet -e "$PACKAGE_DIR"
fi

exec "$PYTHON_BIN" -m nua_client.cli "$@"
