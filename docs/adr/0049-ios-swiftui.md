# ADR-0049: Версия для iOS — отдельное приложение на SwiftUI в `ios/` этого репозитория; поведение — по тем же ADR, Android — эталон

- **Статус:** Предложено
- **Дата:** 2026-11-07
- **Затрагивает:** `ios/`, общие ресурсы (`app/src/main/assets/development/`, `app/src/main/res/font/`, `res/drawable/ic_*.xml`), `versionName` и `versionCode`, `fixtures/export/`, `.github/workflows/ios.yml`, `.github/dependabot.yml`, `scripts/dev.sh`

## Контекст

В фокус-группе есть владельцы iPhone. Android-версия закрывает базовые потребности, активной
доработки не ожидается. Поэтому нужна разовая копия поведения, а не общая кодовая база ценой
перестройки готового Android-приложения.

## Решение

- Делай версию для iOS отдельным приложением на Swift и SwiftUI в `ios/` этого репозитория.
  Kotlin Multiplatform не используй: Android-версия не меняется ради iOS.
- Проект — `ios/BabyLog.xcodeproj` с синхронизируемыми папками: новый файл попадает в сборку
  без правки `project.pbxproj`. Минимальная версия — iOS 17. Bundle ID — `com.hiraise.babylog`
  (зарегистрирован в Apple Developer; у Android `applicationId` свой, `com.kid.babylog`, — не выравнивай).
- Зависимости подключай через Swift Package Manager. Допустимы только GRDB (база),
  ZIPFoundation (архив экспорта) и swift-snapshot-testing (только тесты). Остальное — фреймворки
  iOS. Новую зависимость добавляй только с ADR; лицензии — по [ADR-0048](0048-app-store-license-permission.md).
- ADR о поведении, данных, оформлении и приватности обязательны и для iOS. ADR о реализации
  на Android (Gradle, Room, Compose, Robolectric) к iOS не относятся. Отступление iOS от общего
  поведения оформляй ADR.
- Android-код и его тесты — эталон поведения. Переноси функцию вместе с её тестами, а при
  расхождении считай правым Android, если ADR не говорит иного.
- Общие ресурсы не копируй: сборка iOS берёт их по путям Android. Это советы и нормы ВОЗ из
  `app/src/main/assets/development/` и шрифты из `app/src/main/res/font/`. Иконки получай
  скриптом из `res/drawable/ic_*.xml`, вручную не перерисовывай.
- Оформление задаёт `BabyLogTheme` на Swift с теми же значениями, что в `core/ui/theme/`
  ([ADR-0010](0010-design-system.md)). Графики рисуй своими компонентами ([ADR-0028](0028-analytics-tab.md)).
- Формат обмена общий ([ADR-0045](0045-export-archive-with-photos.md)): архив из Android
  открывается на iOS и наоборот. Это проверяют тесты обеих платформ на файлах `fixtures/export/`.
- Версия одна: `MARKETING_VERSION` и `CURRENT_PROJECT_VERSION` в `ios/Version.xcconfig` равны
  `versionName` и `versionCode`.
- Не делай кнопки в шторке ([ADR-0042](0042-shade-notification.md)): их замена на iOS (Live
  Activities, виджеты) — отдельный ADR по слову владельца.
- Выпускай так: владелец архивирует сборку в Xcode из тега `v<versionName>` и загружает её
  в App Store Connect ([ADR-0047](0047-store-publishing.md)). Если iOS ещё не догнала эту
  версию, эта версия в App Store просто не выходит.
- Подпись — автоматическая подпись Xcode. Team ID hiraise храни в `ios/Signing.xcconfig`
  в репозитории: это публичный идентификатор, он есть в подписи каждой сборки. Форк задаёт
  свою команду в `ios/Local.xcconfig`, который в git не попадает. Сборки для CI и тестов
  идут без подписи.
- Не клади в репозиторий сертификаты и ключи (`*.p12`, `*.cer`), профили (`*.mobileprovision`),
  ключи App Store Connect API (`*.p8`), `xcuserdata/` ([ADR-0001](0001-open-source-non-commercial.md)).
  SKU и номер приложения в App Store Connect сборке не нужны: в репозиторий их не добавляй.
- Общие правила переноса держи в `ios/AGENTS.md`, статус и дорожную карту — в `ios/PLAN.md`,
  а полное ТЗ каждой сессии — только в `ios/sessions/<ID>.md`. Не дублируй эти тексты друг в друге.

## Последствия

- Общие файлы (советы, шрифты, `fixtures/export/`) меняются для обеих платформ: CI проверяет обе.
- Новая функция после выхода iOS-версии — два PR, или один с пометкой в `ios/PLAN.md`, что iOS отстаёт.

## Проверка

- `scripts/dev.sh ios` — сборка и тесты на симуляторе со сверкой скриншотов. В CI это делает
  `ios.yml` на macOS-раннере для PR, которые меняют `ios/` или общие файлы.
- `scripts/dev.sh rules` сверяет версию в `ios/Version.xcconfig` с `app/build.gradle.kts`.

## Альтернативы

- Kotlin Multiplatform и Compose Multiplatform — общая логика, но ценой перестройки готового
  Android-приложения. Это выгодно при активной доработке, а её не ожидается.
- Отдельный репозиторий — общие ADR, ресурсы, файлы тестов и версию пришлось бы копировать.
