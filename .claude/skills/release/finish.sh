#!/bin/bash
# Совместимый путь для существующих ссылок; вся логика — в каноническом скрипте .agents/skills.
set -euo pipefail
exec "$(cd "$(dirname "$0")" && pwd)/../../../.agents/skills/release/finish.sh" "$@"
