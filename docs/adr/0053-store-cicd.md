# ADR-0053: По тегу CI собирает релизы и загружает их в тестирование; Production запускает владелец

- **Статус:** Принято
- **Дата:** 2026-11-24
- **Затрагивает:** `.github/workflows/release.yml`, `scripts/dev.sh` (`release`, `build`), `fastlane/Fastfile`, `Gemfile`, `.github/dependabot.yml`, `docs/development.md`, `fastlane/metadata/`
- **Заменяет:** альтернативу «Автозагрузка в Play из CI» в [ADR-0047](0047-store-publishing.md)

## Контекст

Ручная загрузка каждой сборки повторяется и допускает расхождение кода с магазинами. Нужен один
воспроизводимый процесс, который не открывает Production автоматически и не хранит магазинные секреты
в репозитории.

## Решение

- Запускай публикацию только тегом `v<versionName>` на коммите из `main`. Один тег даёт APK, AAB и
  iOS-архив с одинаковыми `versionName` и `versionCode`.
- Собирай Android в Ubuntu: `scripts/dev.sh release` проверяет версию и отпечаток ключа у APK и AAB.
  APK с `.sha256` публикуй в GitHub Releases; AAB загружай Fastlane `supply` в закрытый трек `alpha`.
- Загружай описания, changelog, иконку и баннер из `fastlane/metadata/android/`. Скриншоты CI не
  загружает: они уже проверены вручную и лежат в `docs/screenshots/`.
- Собирай iOS на runner `xcode-27`: Fastlane `gym` экспортирует IPA для App Store, `pilot` загружает
  его только в TestFlight, без отправки на App Review.
- Используй Fastlane 2.242.0 на Ruby 3.2. Версию RubyGems фиксируй в `Gemfile`; обновления предлагает
  Dependabot. Зависимости приложения не меняются.
- Храни секреты только в защищённом окружении `release` GitHub: JSON сервисного аккаунта Play,
  distribution-сертификат и provisioning profile iOS, параметры App Store Connect API. Импортируй
  сертификат во временный keychain раннера и удаляй файлы после job.
- Production в Google Play и App Store запускает владелец после тестирования и проверки магазинов.
  CI не обходит review и не отправляет приложение в Production сам.

## Последствия

- Для реального запуска владельцу нужно один раз создать сервисный аккаунт Play, distribution-профиль
  iOS и App Store Connect API key и положить их в секреты окружения `release`.
- Ручная загрузка из `build/release/` остаётся запасным вариантом.

## Проверка

- `scripts/dev.sh release v<versionName>` собирает и проверяет подпись APK и AAB.
- `bundle exec fastlane lanes` показывает `play_closed` и `testflight` без обращения к магазинам.

## Альтернативы

- Автоматический Production — рискованнее: ошибка сборки сразу попадает пользователям.
- Fastlane Match для iOS — даёт общее хранилище подписи, но требует отдельного репозитория и настройки.
