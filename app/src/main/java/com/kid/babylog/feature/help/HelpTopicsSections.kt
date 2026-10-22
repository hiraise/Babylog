package com.kid.babylog.feature.help

import com.kid.babylog.R
import com.kid.babylog.core.ui.guide.GuideTarget

// Темы справки о разделах нижней панели, детях и меню (ADR-0043); порядок тем — в HELP_TOPICS.

internal val DIARY = HelpTopic(
    id = "diary",
    title = "Дневник по дням",
    icon = R.drawable.ic_diary,
    summary = "Все записи по дням: листать, выбрать день, исправить или удалить запись",
    steps = listOf(
        GuideStep(
            text = "Откройте «Дневник» в нижней панели: сводка дня и все записи, по умолчанию — за сегодня.",
            target = GuideTarget.NAV_DIARY,
            done = StepDone.Shown(GuideTarget.HISTORY),
        ),
        GuideStep(
            text = "Свайп вправо — предыдущий день с записями, влево — следующий. Пролистайте на вчера.",
            done = StepDone.Shown(GuideTarget.PAST_DAY),
        ),
        GuideStep(
            text = "Коснитесь даты вверху: в списке — дни с записями и сколько их было. Выберите любой.",
            target = GuideTarget.DAY_PICKER,
            sheetHint = "Выберите день — откроются его записи.",
            done = StepDone.Visited(GuideTarget.DAY_SELECTOR),
        ),
        GuideStep(
            text = "Коснитесь записи — откроется правка: время, детали и комментарий. Корзина справа удаляет запись.",
            sheetHint = "Поменяйте что нужно и нажмите «Сохранить» — или «Отмена».",
            done = StepDone.Visited(GuideTarget.EVENT_EDITOR),
        ),
    ),
)

internal val ANALYTICS = HelpTopic(
    id = "analytics",
    title = "Аналитика",
    icon = R.drawable.ic_analytics,
    summary = "Графики сна, кормлений, подгузников, плача, веса и роста — за неделю, месяц или всё время",
    steps = listOf(
        GuideStep(
            text = "Откройте «Аналитику» в нижней панели.",
            target = GuideTarget.NAV_ANALYTICS,
            done = StepDone.Shown(GuideTarget.ANALYTICS_OVERVIEW),
        ),
        GuideStep(
            text = "Каждая плитка — главное число за период, сравнение с прошлым и мини-график. Период — 7 дней, " +
                "30 дней или всё время — выбирается сверху.",
            done = StepDone.Next,
        ),
        GuideStep(
            text = "Коснитесь плитки «Сон» — откроется раздел с подробными графиками.",
            target = GuideTarget.ANALYTICS_SLEEP_TILE,
            done = StepDone.Shown(GuideTarget.ANALYTICS_DETAIL),
        ),
        GuideStep(
            text = "Коснитесь столбика — под графиком появятся итоги дня, а «В историю» откроет этот день в «Дневнике». " +
                "«?» у графика объясняет, как сон делится на ночной и дневной.",
            done = StepDone.Next,
        ),
        GuideStep(
            text = "«Назад» возвращает к плиткам. Всё считается на телефоне из ваших записей — в сеть ничего не уходит.",
            done = StepDone.Shown(GuideTarget.ANALYTICS_OVERVIEW),
        ),
    ),
)

internal val DEVELOPMENT = HelpTopic(
    id = "development",
    title = "Развитие",
    icon = R.drawable.ic_development,
    summary = "Советы на неделю жизни и окна навыков по таблицам ВОЗ",
    steps = listOf(
        GuideStep(
            text = "Откройте «Развитие» в нижней панели: советы на эту неделю жизни — сон, кормление, развитие, " +
                "безопасность, — у каждого источник.",
            target = GuideTarget.NAV_DEVELOPMENT,
            done = StepDone.Shown(GuideTarget.DEVELOPMENT),
        ),
        GuideStep(
            text = "Касание совета открывает его целиком. Новые советы — каждую неделю: пока их не открыли, " +
                "на «Развитии» точка, а на «Сегодня» у полосы советов недели — пометка «новые».",
            done = StepDone.Next,
        ),
        GuideStep(
            text = "Коснитесь «подробнее» у навыков: окна, в которые дети обычно осваивают навыки, по таблицам ВОЗ. " +
                "Это ориентир, а не норма.",
            target = GuideTarget.SKILLS_BUTTON,
            done = StepDone.Shown(GuideTarget.SKILLS),
        ),
    ),
)

internal val CHILDREN = HelpTopic(
    id = "children",
    title = "Дети и фото",
    icon = R.drawable.ic_child,
    summary = "Несколько детей, у каждого свои записи; имя, дата рождения, пол и фото",
    steps = listOf(
        GuideStep(
            text = "Коснитесь шапки с именем — откроется список детей.",
            target = GuideTarget.CHILD_SELECTOR,
            done = StepDone.Shown(GuideTarget.CHILDREN_SHEET),
        ),
        GuideStep(
            text = "«Добавить ребёнка»: имя, дата рождения и пол — по полу приложение пишет «уснул» или «уснула». " +
                "У каждого ребёнка свои записи, таймеры и сводка.",
            target = GuideTarget.ADD_CHILD,
            sheetHint = "Нажмите «Добавить ребёнка», введите имя и «Сохранить» — дату рождения и пол можно " +
                "указать сразу или потом.",
            done = StepDone.Children { now, atStart -> now.size > atStart.size },
        ),
        GuideStep(
            text = "Новый ребёнок выбран сразу. Вернуться к другому — коснитесь шапки и выберите его в списке.",
            target = GuideTarget.CHILD_SELECTOR,
            sheetHint = "Выберите ребёнка в списке.",
            done = StepDone.Visited(GuideTarget.CHILDREN_SHEET),
        ),
        GuideStep(
            text = "Фото, имя, дату рождения и пол меняет карандаш у выбранного ребёнка в том же списке. Касание " +
                "круга — «Сделать фото» или «Выбрать из галереи»; фото двигают и увеличивают, чтобы в круг попало лицо.",
            done = StepDone.Next,
        ),
    ),
)

internal val SETTINGS = HelpTopic(
    id = "settings",
    title = "Тема, шторка и ваши данные",
    icon = R.drawable.ic_theme,
    summary = "Светлая или тёмная тема, кнопки в шторке, перенос записей и где они хранятся",
    steps = listOf(
        GuideStep(
            text = "Откройте меню ⋮ справа вверху и выберите «Тема».",
            target = GuideTarget.MENU_BUTTON,
            sheetHint = "Выберите «Тема».",
            done = StepDone.Shown(GuideTarget.THEME_SHEET),
        ),
        GuideStep(
            text = "Тема — как в системе, светлая или тёмная, а у тёмной — пять палитр. Выбор меняется сразу " +
                "и по-настоящему.",
            sheetHint = "Выберите тему или палитру — или закройте лист свайпом вниз.",
            done = StepDone.Visited(GuideTarget.THEME_SHEET),
        ),
        GuideStep(
            text = "«Кнопки в шторке» — в том же меню: уведомление с кнопками «Сон», «Кормление» и «Плач» запускает " +
                "таймеры, не открывая приложения. Включается для вашего дневника по-настоящему.",
            target = GuideTarget.MENU_BUTTON,
            done = StepDone.Next,
        ),
        GuideStep(
            text = "«Экспорт в CSV» и «Импорт из CSV» — тоже в меню ⋮ вашего дневника. Экспорт сохраняет записи всех " +
                "детей в файл, импорт добавляет записи из файла и пропускает повторы. Фото в файл не входят. " +
                "В примере этих пунктов нет: он не сохраняется.",
            done = StepDone.Next,
        ),
        GuideStep(
            text = "Записи хранятся только на телефоне: у приложения нет сети, аккаунтов и рекламы. Раз в день оно " +
                "само копирует дневник и восстановит его из копии, если база повредится.",
            done = StepDone.Next,
        ),
    ),
)
