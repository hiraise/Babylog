package com.kid.babylog.feature.help

import com.kid.babylog.R
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.ui.guide.GuideTarget

/**
 * Темы справки по порядку прохождения (ADR-0043). Справка — главное описание того, что делает
 * пользователь: новая функция или изменённый жест приходит с шагом здесь и с `since` — версией,
 * в которой шаг появился. У каждой темы есть UI-тест, который проходит её гайд до конца.
 */
val HELP_TOPICS: List<HelpTopic> = listOf(
    HelpTopic(
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
                done = StepDone.Records { now, atStart -> now.countOf<Feeding>() > atStart.countOf<Feeding>() },
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
                done = StepDone.Records { now, atStart -> now.countOf<Bottle>() > atStart.countOf<Bottle>() },
            ),
        ),
    ),
)

private inline fun <reified T : BabyEvent> List<BabyEvent>.countOf(): Int = count { it is T }
