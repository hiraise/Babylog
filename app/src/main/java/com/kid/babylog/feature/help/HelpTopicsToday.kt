package com.kid.babylog.feature.help

import com.kid.babylog.R
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Height
import com.kid.babylog.core.model.Illness
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Note
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.Temperature
import com.kid.babylog.core.model.Weight
import com.kid.babylog.core.ui.guide.GuideTarget

// Темы справки о плитках «Сегодня» (ADR-0043); порядок тем — в HELP_TOPICS.

internal val SLEEP_FEEDING = HelpTopic(
    id = "sleep-feeding",
    title = "Сон и кормление",
    icon = R.drawable.ic_sleep,
    summary = "Таймеры: запустить, остановить, вернуть остановленный случайно, сменить грудь, бутылочка",
    steps = listOf(
        GuideStep(
            text = "Коснитесь плитки «Сон» и нажмите «Запустить таймер». Если ребёнок уснул раньше, " +
                "сначала выберите, сколько минут назад.",
            target = GuideTarget.SLEEP_TILE,
            sheetHint = "Уснул раньше — выберите, сколько минут назад. Потом «Запустить таймер».",
            done = StepDone.Records { now, _ -> now.any { it is Sleep && it.isActive } },
        ),
        GuideStep(
            text = "Пока ребёнок спит, время сна идёт на плитке. Таймер не собьётся, даже если закрыть " +
                "приложение или перезагрузить телефон.",
            button = "Прошло 40 минут",
            skipMinutes = 40,
            done = StepDone.Next,
        ),
        GuideStep(
            text = "Проснулся — коснитесь идущей плитки «Сон»: таймер остановится.",
            target = GuideTarget.SLEEP_TILE,
            done = StepDone.Records { now, _ -> now.none { it is Sleep && it.isActive } },
        ),
        GuideStep(
            text = "Остановили случайно — нажмите «Отменить» в сообщении внизу экрана, и таймер пойдёт дальше.",
            button = "Понятно",
            done = StepDone.Next,
        ),
        GuideStep(
            text = "Коснитесь плитки «Кормление», выберите грудь и нажмите «Запустить таймер». Сон и кормление " +
                "одновременно не идут: если идёт сон, он закончится.",
            target = GuideTarget.FEEDING_TILE,
            sheetHint = "Выберите грудь — левую или правую — и нажмите «Запустить таймер».",
            done = StepDone.Records { now, _ -> now.any { it is Feeding && it.isActive } },
        ),
        GuideStep(
            text = "Перешли на другую грудь — коснитесь ⇄ в углу идущей плитки. В «Дневнике» это будут две " +
                "записи, а в сводке дня — одно кормление.",
            target = GuideTarget.SWITCH_BREAST,
            done = added<Feeding>(),
        ),
        GuideStep(
            text = "Кормление идёт. Перемотаем время вперёд.",
            button = "Прошло 15 минут",
            skipMinutes = 15,
            demoOnly = true,
            done = StepDone.Next,
        ),
        GuideStep(
            text = "Закончили — коснитесь плитки ещё раз. На ней останется, какой грудью кормили и сколько " +
                "прошло с конца: с какой груди начать и когда кормить снова.",
            target = GuideTarget.FEEDING_TILE,
            done = StepDone.Records { now, _ -> now.none { it is Feeding && it.isActive } },
        ),
        GuideStep(
            text = "Бутылочка — в том же листе: коснитесь «Кормление», выберите «Бутылочка», объём и нажмите " +
                "«Сохранить». В следующий раз объём подставится сам.",
            target = GuideTarget.FEEDING_TILE,
            sheetHint = "Выберите «Бутылочка», объём — 60, 90, 120, 150 мл или своё число — и «Сохранить».",
            done = added<Bottle>(),
        ),
    ),
)

internal val RECORDS = HelpTopic(
    id = "records",
    title = "Подгузник, вес и рост",
    icon = R.drawable.ic_diaper,
    summary = "Быстрые записи: подгузник с фото для врача, вес и рост",
    steps = listOf(
        GuideStep(
            text = "Коснитесь «Подгузник», выберите, что было, и нажмите «Сохранить». Меняли раньше — выберите, " +
                "сколько минут назад.",
            target = GuideTarget.DIAPER_TILE,
            sheetHint = "Выберите, что было в подгузнике, и нажмите «Сохранить».",
            done = added<Diaper>(),
        ),
        GuideStep(
            text = "Под плиткой — сколько прошло с последней смены. Через 3 часа подпись выделится: пора менять. " +
                "Пока ребёнок спит, она не выделяется — будить ради подгузника не нужно.",
            target = GuideTarget.DIAPER_TILE,
            done = StepDone.Next,
        ),
        GuideStep(
            text = "К подгузнику можно приложить до 6 фото — снять или выбрать в галерее — и кнопкой «Поделиться» " +
                "отправить врачу: подпись и фото уйдут одним сообщением в приложение, которое вы выберете.",
            done = StepDone.Next,
        ),
        GuideStep(
            text = "Коснитесь «Вес», введите вес в граммах и нажмите «Сохранить». Вес появится в шапке под именем.",
            target = GuideTarget.WEIGHT_TILE,
            sheetHint = "Введите вес в граммах — например, 6450 — и нажмите «Сохранить».",
            done = added<Weight>(),
        ),
        GuideStep(
            text = "Рост — так же, в сантиметрах с десятыми: «64,5». На плитках «Вес» и «Рост» видна дата последнего " +
                "замера — не пора ли измериться снова.",
            target = GuideTarget.HEIGHT_TILE,
            sheetHint = "Введите рост в сантиметрах — например, 64,5 — и нажмите «Сохранить».",
            done = added<Height>(),
        ),
    ),
)

internal val CRYING = HelpTopic(
    id = "crying",
    title = "Плач",
    icon = R.drawable.ic_crying,
    summary = "Таймер плача, причины и что помогло — чтобы увидеть закономерности",
    steps = listOf(
        GuideStep(
            text = "Ребёнок заплакал — коснитесь плитки «Плач»: таймер пойдёт сразу.",
            target = GuideTarget.CRYING_TILE,
            done = StepDone.Records { now, _ -> now.any { it is Crying && it.isActive } },
        ),
        GuideStep(
            text = "Плач идёт. Перемотаем время вперёд.",
            button = "Прошло 10 минут",
            skipMinutes = 10,
            demoOnly = true,
            done = StepDone.Next,
        ),
        GuideStep(
            text = "Успокоился — коснитесь плитки ещё раз. Откроется лист: что было до плача — сон и кормления " +
                "из дневника, — почему, как думаете, и что помогло.",
            target = GuideTarget.CRYING_TILE,
            done = StepDone.Records { now, _ -> now.none { it is Crying && it.isActive } },
        ),
        GuideStep(
            text = "Отметьте причину и что помогло — метки, которые вы уже отмечали, будут первыми, — и нажмите " +
                "«Сохранить». Нажали поздно — «Начался раньше?» сдвинет начало. Остановили по ошибке — «Ещё плачет».",
            sheetHint = "Отметьте, почему плакал и что помогло, и нажмите «Сохранить».",
            done = StepDone.Records { now, atStart -> now.countDetailed() > atStart.countDetailed() },
        ),
        GuideStep(
            text = "Плач будит: его начало заканчивает сон, а засыпание — плач. С кормлением плач идёт одновременно. " +
                "Нажали «Плач» случайно — «Отменить» в сообщении внизу уберёт его.",
            done = StepDone.Next,
        ),
    ),
)

internal val NOTES = HelpTopic(
    id = "notes",
    title = "Заметки и метки",
    icon = R.drawable.ic_note,
    summary = "Что заметили — текстом, метками и фото; лента плача и заметок, сводка по метке",
    steps = listOf(
        GuideStep(
            text = "Коснитесь «Заметка» рядом с плачем: напишите, что заметили, отметьте метку и нажмите «Сохранить». " +
                "Можно приложить фото — например, сыпи.",
            target = GuideTarget.NOTE_TILE,
            sheetHint = "Напишите, что заметили, отметьте метку — например, «зубки» — и нажмите «Сохранить».",
            done = added<Note>(),
        ),
        GuideStep(
            text = "Плач и заметки собраны в «Дневнике» — откройте его в нижней панели…",
            target = GuideTarget.NAV_DIARY,
            done = StepDone.Shown(GuideTarget.NOTES_BUTTON),
        ),
        GuideStep(
            text = "…и выберите «Заметки» вверху: плач и заметки по дням, свежие сверху.",
            target = GuideTarget.NOTES_BUTTON,
            done = StepDone.Shown(GuideTarget.NOTES_FEED),
        ),
        GuideStep(
            text = "Коснитесь метки — на записи или в ряду фильтров: появится сводка — в какие дни она была, когда " +
                "обычно начинался плач, что было перед ним и что помогало.",
            done = StepDone.Shown(GuideTarget.TAG_SUMMARY),
        ),
        GuideStep(
            text = "Метки у плача и заметок общие: одна метка — например, «зубки» — связывает их. В «Аналитике» → " +
                "«Метки» видно, как сон и плач меняются в дни с меткой.",
            done = StepDone.Next,
        ),
    ),
)

internal val HEALTH = HelpTopic(
    id = "health",
    title = "Здоровье и болезнь",
    icon = R.drawable.ic_first_aid,
    summary = "Температура, лекарства с подсказками и болезнь, которая собирает их вместе",
    steps = listOf(
        GuideStep(
            text = "Коснитесь «Здоровье» и выберите «Температура»: введите значение и нажмите «Сохранить».",
            target = GuideTarget.HEALTH_TILE,
            sheetHint = "Выберите «Температура», введите, например, 37,8 и нажмите «Сохранить».",
            done = added<Temperature>(),
        ),
        GuideStep(
            text = "Лекарство — там же, в «Здоровье». Начните набирать название — подскажут прежние записи, " +
                "а доза из прошлого раза подставится сама.",
            target = GuideTarget.HEALTH_TILE,
            sheetHint = "Выберите «Лекарство», наберите «Ви», коснитесь подсказки «Витамин D» и нажмите «Сохранить».",
            done = added<Medicine>(),
        ),
        GuideStep(
            text = "Ребёнок заболел — «Здоровье» → «Заболел» или «Заболела»: температура, лекарства и заметки " +
                "соберутся в одну болезнь, а на «Сегодня» появится её карточка.",
            target = GuideTarget.HEALTH_TILE,
            sheetHint = "Выберите «Заболела» и нажмите «Начать» — остальное по желанию.",
            done = StepDone.Records { now, _ -> now.any { it is Illness && it.isActive } },
        ),
        GuideStep(
            text = "Коснитесь карточки болезни: на её экране — дни болезни с температурой и лекарствами и «Для врача» — " +
                "сводка, которую можно отправить.",
            target = GuideTarget.ILLNESS_CARD,
            done = StepDone.Shown(GuideTarget.ILLNESS_SCREEN),
        ),
        GuideStep(
            text = "Выздоровел — нажмите «Выздоровел» или «Выздоровела» на экране болезни.",
            target = GuideTarget.RECOVER_BUTTON,
            sheetHint = "Выберите день и нажмите «Выздоровела».",
            done = StepDone.Records { now, _ -> now.none { it is Illness && it.isActive } },
        ),
    ),
)

/** Плачей с причинами или «что помогло»: шаг засчитывается, когда подробности сохранили. */
private fun List<BabyEvent>.countDetailed(): Int =
    count { it is Crying && (it.reasons.isNotEmpty() || it.helped.isNotEmpty()) }
