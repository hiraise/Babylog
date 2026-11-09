# BabyLog для iOS

Версия для iPhone — отдельное приложение на SwiftUI ([ADR-0049](../docs/adr/0049-ios-swiftui.md)).
Поведение повторяет Android-версию: эталон — её код, тесты и ADR. Данные хранятся так, как
описано в [ADR-0050](../docs/adr/0050-ios-local-data.md). Bundle ID — `com.hiraise.babylog`.

План переноса по сессиям, их порядок, нужный effort и статус — [PLAN.md](PLAN.md)
(по-английски по решению владельца — экономит токены сессий).

Сборка и отладка появятся в [docs/development.md](../docs/development.md) вместе с каркасом
(сессия S1a). Кнопок в шторке ([ADR-0042](../docs/adr/0042-shade-notification.md)) в версии для iOS нет.
