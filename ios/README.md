# BabyLog для iOS

Версия для iPhone — отдельное приложение на SwiftUI ([ADR-0049](../docs/adr/0049-ios-swiftui.md)).
Поведение повторяет Android-версию: эталон — её код, тесты и ADR. Данные хранятся так, как
описано в [ADR-0050](../docs/adr/0050-ios-local-data.md). Bundle ID — `com.hiraise.babylog`.

Общие правила переноса — [AGENTS.md](AGENTS.md). Порядок сессий, зависимости, нужный effort и их
статус — [PLAN.md](PLAN.md); полное ТЗ каждой сессии хранится только в её файле `sessions/<ID>.md`.
Агент запускает ровно одну сессию скиллом `ios-port <ID>` из [.agents/skills/](../.agents/skills/).
Эти файлы по-английски по решению владельца — экономит токены сессий.

Сборка и отладка появятся в [docs/development.md](../docs/development.md) вместе с каркасом
(сессия S1a). Кнопок в шторке ([ADR-0042](../docs/adr/0042-shade-notification.md)) в версии для iOS нет.
