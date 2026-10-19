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

# Печатает нарушения правила (пути — от корня проекта); код 1, если нарушения есть
report() {
    [[ -z "$2" ]] && return 0
    echo "$1:" >&2
    sed "s|$ROOT/||; s|^|  |" <<<"$2" >&2
    return 1
}

# Правила из разделов «Проверка» ADR, которые ловятся поиском по коду (ADR-0011).
# Меняешь правило в ADR — меняй проверку здесь в том же изменении.
check_adr_rules() {
    local main="$ROOT/app/src/main" code="$ROOT/app/src/main/java/com/kid/babylog" failed=0 hits
    hits="$(grep -nHE 'com\.google\.(android\.gms|firebase)' "$ROOT"/*.gradle.kts "$ROOT"/app/*.gradle.kts || true)"
    report "ADR-0001: закрытые SDK (Play Services, Firebase) не подключаются" "$hits" || failed=1
    # INTERNET появится только вместе с ADR сетевой интеграции — тогда поправь и эту проверку
    hits="$(grep -nH 'android.permission.INTERNET' "$main/AndroidManifest.xml" || true)"
    report "ADR-0002: permission INTERNET без ADR сетевой интеграции" "$hits" || failed=1
    grep -q 'android:allowBackup="false"' "$main/AndroidManifest.xml" ||
        report "ADR-0002: нужен android:allowBackup=\"false\"" "$main/AndroidManifest.xml" || failed=1
    hits="$(grep -rnH 'System\.currentTimeMillis' "$code" | grep -v '/AppContainer\.kt:' || true)"
    report "ADR-0007: текущее время — только через Clock, System.currentTimeMillis — только в AppContainer.kt" "$hits" || failed=1
    hits="$(grep -rnHE '^import androidx\.room' "$code" | grep -v '/core/data/room/' || true)"
    report "ADR-0007: Room импортируется только в core/data/room/" "$hits" || failed=1
    hits="$(grep -nHE '^import android\.' "$code"/feature/*/*ViewModel.kt 2>/dev/null || true)"
    report "ADR-0007: ViewModel не зависят от Android" "$hits" || failed=1
    hits="$(grep -L -e materialsymbolsrounded -e 'ADR-0014' "$main"/res/drawable/ic_*.xml | grep -v '/ic_launcher' || true)"
    report "ADR-0014: иконка не из Material Symbols Rounded и без пометки «ADR-0014» своей иконки" "$hits" || failed=1
    hits="$(grep -rnHE '\b(AlertDialog|BasicAlertDialog|DatePickerDialog|DropdownMenu|DropdownMenuItem)\(|^import androidx\.compose\.ui\.window\.Dialog' "$code" || true)"
    report "ADR-0018: всплывающие окна — только нижние листы AppSheet (без AlertDialog, DropdownMenu, DatePickerDialog)" "$hits" || failed=1
    hits="$(grep -nH 'ui-test-manifest' "$ROOT"/*.gradle.kts "$ROOT"/app/*.gradle.kts || true)"
    report "ADR-0022: ui-test-manifest не подключается — ComponentActivity регистрирует ComponentActivityRule" "$hits" || failed=1
    hits="$(grep -nHE 'android\.permission\.(CAMERA|READ_MEDIA_|READ_EXTERNAL_STORAGE|WRITE_EXTERNAL_STORAGE)' "$main/AndroidManifest.xml" || true)"
    report "ADR-0023: фото снимают и выбирают системные камера и пикер — без разрешений на камеру и память" "$hits" || failed=1
    hits="$(grep -nHE 'io\.coil-kt|com\.github\.bumptech|com\.squareup\.picasso|com\.yalantis' "$ROOT"/*.gradle.kts "$ROOT"/app/*.gradle.kts || true)"
    report "ADR-0023: библиотеки загрузки и кадрирования фото (Coil, Glide, Picasso, uCrop) не подключаются" "$hits" || failed=1
    hits="$(grep -nH 'OnConflictStrategy.REPLACE' "$code/core/data/room/DiaryDao.kt" || true)"
    report "ADR-0024: записи меняются только UPDATE — REPLACE удалил бы их фото каскадом" "$hits" || failed=1
    hits="$(grep -nHiE 'patrykandpatrick|vico|MPAndroidChart|ycharts' "$ROOT"/*.gradle.kts "$ROOT"/app/*.gradle.kts || true)"
    report "ADR-0028: графики рисуют свои компоненты core/ui/components/chart/ — библиотеки графиков не подключаются" "$hits" || failed=1
    return "$failed"
}

# Документация: каждый ADR есть в индексе, номера не повторяются, относительные ссылки в .md не битые
check_docs() {
    local adr="$ROOT/docs/adr" failed=0 f link target hits=""
    for f in "$adr"/[0-9][0-9][0-9][0-9]-*.md; do
        grep -qF "]($(basename "$f"))" "$adr/README.md" || hits+="$f"$'\n'
    done
    report "ADR без строки в индексе docs/adr/README.md" "${hits%$'\n'}" || failed=1
    hits="$(ls "$adr" | sed -n 's/^\([0-9]\{4\}\)-.*\.md$/\1/p' | sort | uniq -d)"
    report "Номера ADR повторяются" "$hits" || failed=1
    hits=""
    # template.md пропускаем: в нём ссылки-заготовки вида NNNN-slug.md
    while IFS= read -r f; do
        while IFS= read -r link; do
            target="${link%%#*}"
            [[ -z "$target" || "$target" == *://* || "$target" == mailto:* ]] && continue
            [[ -e "$(dirname "$f")/$target" ]] || hits+="$f → $link"$'\n'
        done < <(grep -oE '\]\([^) ]+\)' "$f" | sed 's/^](//; s/)$//')
    done < <(find "$ROOT" -name '*.md' ! -name template.md \
        ! -path '*/build/*' ! -path '*/.gradle/*' ! -path '*/.git/*' ! -path '*/.claude/*')
    report "Битые ссылки в документации" "${hits%$'\n'}" || failed=1
    return "$failed"
}

# Gradle-часть проверки — общая для check и CI. verifyRoborazziDebug — это все unit-тесты
# плюс сверка скриншотов с эталонами в app/src/test/screenshots/ (ADR-0013)
CHECK_TASKS=(verifyRoborazziDebug lintDebug assembleDebug)

# Перезаписывает эталонные скриншоты после намеренной правки UI. Изменившиеся PNG закоммить вместе с правкой
cmd_goldens() {
    run_gradle recordRoborazziDebug
    (cd "$ROOT" && git status --short -- app/src/test/screenshots)
}

# Быстрые проверки правил без Gradle; сообщает обо всех нарушениях сразу, а не о первом
cmd_rules() {
    local failed=0
    check_design_tokens || failed=1
    check_adr_rules || failed=1
    check_docs || failed=1
    [[ "$failed" -eq 0 ]] && echo "Правила проекта соблюдены"
    return "$failed"
}

# Проверка, без которой задача не считается выполненной (CLAUDE.md, «Сборка и проверка»)
cmd_check() {
    cmd_rules
    run_gradle "${CHECK_TASKS[@]}"
}

# Gradle-шаг CI: всё из check и release-сборка — по ней README учит собирать APK (ADR-0011)
cmd_build() {
    run_gradle "${CHECK_TASKS[@]}" assembleRelease
}

# Шаг CI после сборки: Room не должен был изменить схемы в app/schemas/ (ADR-0008).
# Локально падает и на незакоммиченной новой схеме — это и есть напоминание её закоммитить.
cmd_schemas() {
    local changes
    changes="$(cd "$ROOT" && git status --porcelain -- app/schemas)"
    if [[ -n "$changes" ]]; then
        echo "Сборка изменила схемы Room — нужна новая версия AppDatabase с миграцией и закоммиченная схема (ADR-0008):" >&2
        echo "$changes" >&2
        return 1
    fi
    echo "Схемы Room совпадают с закоммиченными"
}

# SHA-256 сертификата ключа релизов (ADR-0012); тот же отпечаток — в README «Установка на телефон»
RELEASE_CERT_SHA256="e43b34e322438744830268de4b5579cafae46c4b549aceb0265a910a4d77f395"

# Подписанный APK для GitHub Releases (ADR-0012). Аргумент — тег v<versionName>.
# Ключ — BABYLOG_KEYSTORE (путь к .jks) и BABYLOG_KEYSTORE_PASSWORD; в CI их даёт окружение release.
cmd_release() {
    local tag="${1:-}" version apk tools cert out
    version="$(sed -n 's/^ *versionName = "\(.*\)"$/\1/p' "$ROOT/app/build.gradle.kts")"
    if [[ "$tag" != "v$version" ]]; then
        echo "Тег «${tag}» должен совпадать с versionName: v$version" >&2
        return 1
    fi
    if [[ -z "${BABYLOG_KEYSTORE:-}" || -z "${BABYLOG_KEYSTORE_PASSWORD:-}" ]]; then
        echo "Нет ключа релизов: задайте BABYLOG_KEYSTORE и BABYLOG_KEYSTORE_PASSWORD" >&2
        return 1
    fi
    cmd_rules
    run_gradle "${CHECK_TASKS[@]}" assembleRelease
    apk="$ROOT/app/build/outputs/apk/release/app-release.apk"
    tools="$(ls -d "$SDK"/build-tools/*/ | LC_ALL=C sort | tail -1)"
    # apksigner.jar напрямую: обёртка apksigner берёт java из PATH, а там может быть Java ниже 17.
    # Формат строки зависит от версии build-tools («Signer #1 certificate …» до 36, «V2 Signer: certificate …»
    # с 37), поэтому берём все отпечатки подписантов: должен остаться ровно один — ключ проекта
    cert="$("${JAVA_HOME:+$JAVA_HOME/bin/}java" -jar "${tools}lib/apksigner.jar" verify --print-certs "$apk" 2>/dev/null |
        sed -n 's/.*certificate SHA-256 digest: //p' | LC_ALL=C sort -u)"
    if [[ "$cert" != "$RELEASE_CERT_SHA256" ]]; then
        echo "APK подписан не ключом проекта (сертификат: ${cert:-нет подписи}) — ADR-0012" >&2
        return 1
    fi
    out="$ROOT/build/release"
    rm -rf "$out"
    mkdir -p "$out"
    cp "$apk" "$out/babylog-$version.apk"
    (cd "$out" && shasum -a 256 "babylog-$version.apk" >"babylog-$version.apk.sha256")
    echo "$out/babylog-$version.apk"
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

# Убирает worktree веток, чей PR уже слит в main (CLAUDE.md, «Ветки и PR»): исходники остаются
# в истории git, сборку Gradle пересоздаст. Главную папку, текущий worktree и папки
# с незакоммиченными правками не трогает.
cmd_cleanup() {
    local main_tree wt br pr
    command -v gh >/dev/null || { echo "Нужен GitHub CLI (gh): по нему видно, что PR ветки слит" >&2; return 1; }
    git -C "$ROOT" fetch -q --prune origin
    main_tree="$(git -C "$ROOT" worktree list --porcelain | sed -n '1s/^worktree //p')"
    while IFS=$'\t' read -r wt br; do
        [[ "$wt" == "$main_tree" || "$wt" == "$ROOT" ]] && continue
        git -C "$ROOT" merge-base --is-ancestor "$br" origin/main || continue
        # Только что созданная ветка без коммитов тоже «в main» — поэтому нужен ещё и слитый PR
        pr="$(gh pr list --state merged --head "$br" --json number -q '.[0].number')"
        [[ -n "$pr" ]] || continue
        if [[ -n "$(git -C "$wt" status --porcelain)" ]]; then
            echo "Оставлен $wt: в нём незакоммиченные правки" >&2
            continue
        fi
        git -C "$ROOT" worktree remove "$wt" || { echo "Не удалось убрать $wt" >&2; continue; }
        git -C "$ROOT" branch -d "$br" >/dev/null || echo "Ветка $br оставлена: git не считает её слитой" >&2
        echo "Убран $wt ($br, PR #$pr)"
    done < <(git -C "$ROOT" worktree list --porcelain |
        awk '/^worktree /{p=substr($0, 10)} /^branch refs\/heads\//{print p "\t" substr($0, 19)}')
    git -C "$ROOT" worktree prune
}

usage() {
    cat <<EOF
Использование: scripts/dev.sh <команда>

  run         собрать debug-версию, установить и открыть (эмулятор поднимется сам)
  check       обязательная проверка: rules, unit-тесты со сверкой скриншотов, lint и debug-сборка
  rules       правила проекта без Gradle: дизайн-токены (ADR-0010), правила ADR, ссылки в документации
  goldens     перезаписать эталонные скриншоты (app/src/test/screenshots/) после намеренной правки UI
  build       шаг CI: unit-тесты со сверкой скриншотов, lint, debug- и release-сборка
  schemas     шаг CI после сборки: схемы Room в app/schemas/ совпадают с закоммиченными
  release v<версия>  подписанный APK и .sha256 в build/release/ (нужен ключ релизов, ADR-0012)
  emulator    запустить эмулятор и дождаться загрузки
  logcat      логи приложения (переживают перезапуски и падения)
  db          скачать базу в build/debug-db/$DB_NAME
  screenshot  снимок экрана в build/screenshots/
  stop        выключить эмулятор
  cleanup     убрать worktree веток, чей PR уже слит в main
EOF
}

case "${1:-}" in
    run | check | rules | goldens | build | schemas | release | emulator | logcat | db | screenshot | stop | cleanup) "cmd_$1" "${@:2}" ;;
    *) usage ;;
esac
