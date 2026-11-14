#!/usr/bin/env bash
# Генерирует iOS-иконки из Android-векторов; --check только проверяет, что ресурсы свежие.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
exec /usr/bin/python3 "$ROOT/scripts/ios-icons.py" "$ROOT" "$@"
