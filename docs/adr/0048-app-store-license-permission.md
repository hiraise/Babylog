# ADR-0048: Лицензия — GPL-3.0-or-later с дополнительным разрешением распространять приложение через магазины

- **Статус:** Предложено
- **Дата:** 2026-11-07
- **Затрагивает:** `LICENSE`, `LICENSE-APP-STORES.md`, зависимости в `*.gradle.kts`, README «Лицензия», принцип 1 в `AGENTS.md`
- **Заменяет:** пункт [ADR-0001](0001-open-source-non-commercial.md) о лицензиях зависимостей (LGPL больше нельзя)

## Контекст

Условия App Store накладывают на получателя ограничения: правила использования, DRM, лимит
устройств. По позиции FSF это несовместимо с §10 GPL-3.0, который запрещает дополнительные
ограничения. Владелец как правообладатель может опубликовать свой код и так. Но первый же
внешний вклад без явного разрешения его автора ставит такую публикацию под вопрос.

## Решение

- Добавь к GPL-3.0-or-later дополнительное разрешение по §7 GPL-3.0. Его текст — в файле
  `LICENSE-APP-STORES.md`, `LICENSE` остаётся неизменным текстом GPL:

  > Additional permission under GNU GPL version 3 section 7. As a special exception, the
  > copyright holders of BabyLog give you permission to convey the Program, or a work based
  > on it, in object code form through an application store or other distribution service
  > (such as Google Play or the Apple App Store) whose terms of service, usage rules or
  > technical measures impose restrictions on recipients that are not permitted by this
  > License, provided that you comply with all other terms of this License for that
  > conveyance, including making the Corresponding Source available as described in section 6.

- Принимай вклады на тех же условиях: GPL-3.0-or-later с этим разрешением. CLA по-прежнему
  не требуй (ADR-0001).
- Разрешение действует только для нашего кода. Сторонний код под GPL или LGPL его не даёт,
  поэтому зависимости в сборке — только Apache-2.0, MIT, BSD, MPL-2.0, а для шрифтов — OFL-1.1.
  GPL и LGPL нельзя. Для тестовых зависимостей правило ADR-0001 не меняется.
- Запреты монетизации и приватизации из ADR-0001 действуют как прежде. Исходники каждой
  сборки из магазина лежат в этом репозитории под тегом её версии.
- В README «Лицензия» и в принципе 1 `AGENTS.md` назови разрешение и сошлись на этот ADR.

## Последствия

- По §7 любой может убрать разрешение из своего форка. Тогда этот форк нельзя публиковать
  в App Store, но на нашу публикацию это не влияет.
- Подключая зависимость, проверяй её лицензию по списку выше: если её нет в списке,
  зависимость не подходит.

## Альтернативы

- Без разрешения, публикация как правообладатель — работает, пока все авторы кода — владелец.
- Перейти на MPL-2.0 или Apache-2.0 целиком — разрешает закрытые части и форки, а это
  противоречит ADR-0001.
