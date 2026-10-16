package com.kid.babylog.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kid.babylog.R
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.Weight
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.components.ItemRow
import com.kid.babylog.core.ui.components.LightDarkPreview
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Иконка типа записи (ADR-0010) — для строки истории, сводки и диалогов вместо эмодзи.
 * Сводка, диалоги и быстрые действия ссылаются на те же файлы `R.drawable.ic_<тип>` напрямую:
 * чтобы сменить символ типа, замени файл `res/drawable/ic_<тип>.xml`, а не ссылки в коде.
 * Вид подгузника и сторону кормления иконка не различает: они в тексте записи ([eventTitle]).
 */
@DrawableRes
fun eventIcon(event: BabyEvent): Int = when (event) {
    is Feeding -> R.drawable.ic_feeding
    is Diaper -> R.drawable.ic_diaper
    is Sleep -> R.drawable.ic_sleep
    is Medicine -> R.drawable.ic_medicine
    is Weight -> R.drawable.ic_weight
}

@Preview(name = "Иконки записей", widthDp = 720)
@Composable
private fun EventIconsPreview() = LightDarkPreview {
    val start = LocalDate.of(2026, 5, 20).atTime(LocalTime.of(14, 30))
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    listOf(
        Feeding(childId = 1, start = start, breast = Breast.LEFT),
        Diaper(childId = 1, start = start, kind = DiaperKind.MIXED),
        Sleep(childId = 1, start = start, end = start + 85 * 60_000L),
        Medicine(childId = 1, start = start, name = "Витамин D", note = "3 капли"),
        Weight(childId = 1, start = start, grams = 4350),
    ).forEach { event ->
        AppCard(Modifier.fillMaxWidth()) {
            ItemRow(
                icon = eventIcon(event),
                title = eventTitle(event, sex = null),
                subtitle = eventTimeLine(event),
                note = event.note,
            )
        }
    }
}
