#!/usr/bin/env bash
# Локальная отладка BabyLog: эмулятор, сборка+запуск debug-версии, логи, база, скриншоты.
# Работает с отладочной сборкой (com.kid.babylog.debug) — основное приложение и его данные не трогает.
#
# По умолчанию всё идёт на эмулятор. Чтобы работать с телефоном по USB:
#   ANDROID_SERIAL=<серийник из `adb devices`> scripts/dev.sh run
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP_ID="com.kid.babylog.debug"
ACTIVITY="com.kid.babylog.MainActivity"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
DB_NAME="babylog.db"

SDK="$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" 2>/dev/null || true)"
SDK="${SDK:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
ADB="$SDK/platform-tools/adb"
EMULATOR="$SDK/emulator/emulator"
EMULATOR_LOG="${TMPDIR:-/tmp/}babylog-emulator.log"
STUDIO_JDK="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

# Gradle 9 нужен JDK 17+. Если в JAVA_HOME Java ниже 17 (на этом Mac по умолчанию 11), берём JDK из Android Studio
run_gradle() {
    local version
    version="$("${JAVA_HOME:+$JAVA_HOME/bin/}java" -XshowSettings:properties -version 2>&1 |
        sed -n 's/^ *java\.specification\.version = //p' || true)"
    if [[ "${version%%.*}" -lt 17 ]]; then
        if [[ ! -x "$STUDIO_JDK/bin/java" ]]; then
            echo "Gradle нужен JDK 17 или новее — укажите его в JAVA_HOME" >&2
            exit 1
        fi
        export JAVA_HOME="$STUDIO_JDK"
    fi
    (cd "$ROOT" && ./gradlew "$@")
}

emulator_serial() {
    "$ADB" devices | awk '/^emulator-/ && $2 == "device" { print $1; exit }'
}

# Запускает эмулятор в фоне (окно останется открытым и после выхода из скрипта)
start_emulator() {
    [[ -n "${ANDROID_SERIAL:-}" || -n "$(emulator_serial)" ]] && return
    pgrep -qf "$SDK/emulator/.* -avd " && return  # уже стартует, но ещё не виден в adb
    local avd="${BABYLOG_AVD:-$("$EMULATOR" -list-avds 2>/dev/null | head -1)}"
    if [[ -z "$avd" ]]; then
        echo "Нет ни одного эмулятора (AVD). Создайте его в Android Studio → Device Manager." >&2
        exit 1
    fi
    echo "Запускаю эмулятор $avd (лог: $EMULATOR_LOG)"
    nohup "$EMULATOR" -avd "$avd" -cores 4 >"$EMULATOR_LOG" 2>&1 &
    disown
}

# Ждёт полной загрузки устройства и фиксирует его в ANDROID_SERIAL для всех следующих команд adb
wait_device() {
    if [[ -z "${ANDROID_SERIAL:-}" ]]; then
        local serial=""
        until serial="$(emulator_serial)"; [[ -n "$serial" ]]; do
            if ! pgrep -qf "$SDK/emulator/.* -avd "; then
                echo "Эмулятор не запустился, хвост лога:" >&2
                tail -20 "$EMULATOR_LOG" >&2
                exit 1
            fi
            sleep 2
        done
        export ANDROID_SERIAL="$serial"
    fi
    "$ADB" wait-for-device
    until [[ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; do
        sleep 2
    done
}

ensure_device() {
    start_emulator
    wait_device
}

cmd_emulator() {
    ensure_device
    echo "Устройство готово: $ANDROID_SERIAL"
}

cmd_run() {
    start_emulator  # эмулятор грузится параллельно со сборкой
    run_gradle assembleDebug
    wait_device
    "$ADB" install -r "$APK"
    "$ADB" shell am start -n "$APP_ID/$ACTIVITY"
}

# Литералы оформления, которым место только в теме core/ui/theme/ (ADR-0010, «Проверка»).
# Шаблоны — для grep -E без GNU-расширений (на macOS BSD grep); сообщения — в том же порядке.
TOKEN_PATTERNS=(
    'Color\(0x'
    '[0-9A-Za-z_)]\.sp([^A-Za-z0-9_]|$)'
    '(^|[^A-Za-z0-9_])(FontFamily|Font)\(|FontFamily\.'
    'dynamic(Light|Dark)ColorScheme'
    '((padding|Padding)[A-Za-z]*|spacedBy|Spacer)\([^)]*[0-9]\.dp'
    '^[[:space:]]*((horizontal|vertical|start|end|top|bottom|all|space)[[:space:]]*=[[:space:]]*)?[0-9.]+\.dp[[:space:]]*[,)]?[[:space:]]*$'
)
TOKEN_MESSAGES=(
    "цвет — только из MaterialTheme.colorScheme"
    "размер шрифта — только из MaterialTheme.typography"
    "шрифты подключает только тема"
    "динамический цвет (Material You) не используется"
    "отступ — из BabyLogTheme.spacing, литерал dp — только для размера элемента"
    "отступ в многострочном вызове — из BabyLogTheme.spacing"
)

# Ищет литералы оформления вне темы; строки-комментарии пропускает.
# Аргумент — каталог исходников (по умолчанию app/src/main/java): так проверку можно прогнать на копии.
check_design_tokens() {
    local src="${1:-$ROOT/app/src/main/java}"
    local failed=0 i hits rc
    # BSD grep на несуществующий каталог молча отвечает «не нашлось» — проверяем сами
    if [[ ! -d "$src" ]]; then
        echo "Проверка дизайн-токенов: нет каталога $src" >&2
        return 2
    fi
    for i in "${!TOKEN_PATTERNS[@]}"; do
        # grep: 0 — нашлось, 1 — не нашлось, 2 и выше — ошибка (её не глушим)
        rc=0
        hits="$(grep -rnE --include='*.kt' -- "${TOKEN_PATTERNS[$i]}" "$src")" || rc=$?
        if [[ "$rc" -gt 1 ]]; then
            echo "Проверка дизайн-токенов не смогла прочитать $src (grep вернул $rc)" >&2
            return "$rc"
        fi
        hits="$(grep -v '/core/ui/theme/' <<<"$hits" |
            grep -vE '^[^:]+:[0-9]+:[[:space:]]*(//|\*|/\*)' || true)"
        if [[ -n "$hits" ]]; then
            echo "ADR-0010: ${TOKEN_MESSAGES[$i]}:" >&2
            sed "s|^$src/|  |" <<<"$hits" >&2
            failed=1
        fi
    done
    if [[ "$failed" -ne 0 ]]; then
        echo "Оформление задаёт только тема core/ui/theme/ — см. docs/adr/0010-design-system.md" >&2
        return 1
    fi
}

# Проверка, без которой задача не считается выполненной (CLAUDE.md, «Сборка и проверка»)
cmd_check() {
    check_design_tokens
    run_gradle testDebugUnitTest assembleDebug
}

cmd_logcat() {
    ensure_device
    local uid
    until uid="$("$ADB" shell pm list packages -U "$APP_ID" | sed -n "s/^package:$APP_ID uid:\([0-9]*\).*/\1/p")"; [[ -n "$uid" ]]; do
        echo "Приложение $APP_ID ещё не установлено — запустите: scripts/dev.sh run"
        sleep 3
    done
    # Фильтр по uid, а не по pid: логи не теряются, когда приложение перезапускается или падает
    exec "$ADB" logcat -v color --uid="$uid"
}

cmd_db() {
    ensure_device
    local out="$ROOT/build/debug-db"
    mkdir -p "$out"
    rm -f "$out/$DB_NAME" "$out/$DB_NAME-wal" "$out/$DB_NAME-shm"
    local files
    files="$("$ADB" shell run-as "$APP_ID" ls databases 2>/dev/null | tr -d '\r' || true)"
    if ! grep -qx "$DB_NAME" <<<"$files"; then
        echo "База ещё не создана — откройте приложение и добавьте запись." >&2
        exit 1
    fi
    local f
    for f in "$DB_NAME" "$DB_NAME-wal" "$DB_NAME-shm"; do
        grep -qx "$f" <<<"$files" && "$ADB" exec-out run-as "$APP_ID" cat "databases/$f" >"$out/$f"
    done
    # Room пишет в WAL: вливаем журнал в основной файл, чтобы любой просмотрщик видел свежие данные
    sqlite3 "$out/$DB_NAME" 'PRAGMA journal_mode=DELETE;' >/dev/null
    rm -f "$out/$DB_NAME-wal" "$out/$DB_NAME-shm"
    echo "$out/$DB_NAME"
}

cmd_screenshot() {
    ensure_device
    local out="$ROOT/build/screenshots/$(date +%Y%m%d-%H%M%S).png"
    mkdir -p "$(dirname "$out")"
    "$ADB" exec-out screencap -p >"$out"
    echo "$out"
}

cmd_stop() {
    local serial="${ANDROID_SERIAL:-$(emulator_serial)}"
    if [[ "$serial" != emulator-* ]]; then
        echo "Эмулятор не запущен"
        return
    fi
    "$ADB" -s "$serial" emu kill
}

usage() {
    cat <<EOF
Использование: scripts/dev.sh <команда>

  run         собрать debug-версию, установить и открыть (эмулятор поднимется сам)
  check       дизайн-токены (ADR-0010), unit-тесты и debug-сборка (./gradlew testDebugUnitTest assembleDebug)
  emulator    запустить эмулятор и дождаться загрузки
  logcat      логи приложения (переживают перезапуски и падения)
  db          скачать базу в build/debug-db/$DB_NAME
  screenshot  снимок экрана в build/screenshots/
  stop        выключить эмулятор
EOF
}

case "${1:-}" in
    run | check | emulator | logcat | db | screenshot | stop) "cmd_$1" ;;
    *) usage ;;
esac
