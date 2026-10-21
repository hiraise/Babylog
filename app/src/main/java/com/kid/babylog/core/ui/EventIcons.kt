package com.kid.babylog.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kid.babylog.R
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Height
import com.kid.babylog.core.model.Illness
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Note
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.Temperature
import com.kid.babylog.core.model.Weight
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.components.ItemRow
import com.kid.babylog.core.ui.components.LightDarkPreview
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Иконка типа записи (ADR-0010) — для строки истории, сводки и листов вместо эмодзи.
 * Сводка, листы и быстрые действия ссылаются на те же файлы `R.drawable.ic_<тип>` напрямую:
 * чтобы сменить символ типа, замени файл `res/drawable/ic_<тип>.xml`, а не ссылки в коде.
 * Подгузник показан по виду ([diaperIcon]); сторону кормления иконка не различает: она в тексте
 * записи ([eventTitle]). У бутылочки своя иконка (ADR-0014): в истории её видно сразу.
 */
@DrawableRes
fun eventIcon(event: BabyEvent): Int = when (event) {
    is Feeding -> R.drawable.ic_feeding
    is Bottle -> R.drawable.ic_bottle
    is Diaper -> diaperIcon(event.kind)
    is Sleep -> R.drawable.ic_sleep
    is Medicine -> R.drawable.ic_medicine
    is Weight -> R.drawable.ic_weight
    is Height -> R.drawable.ic_height
    is Crying -> R.drawable.ic_crying
    is Note -> R.drawable.ic_note
    is Illness -> R.drawable.ic_illness
    is Temperature -> R.drawable.ic_thermometer
}

/**
 * Иконка вида подгузника — свои иконки (ADR-0014): капля — пописал, волны запаха — покакал,
 * капля и волны — и то, и то. Одна иконка на все виды не годится: вид не различить без подписи.
 */
@DrawableRes
fun diaperIcon(kind: DiaperKind): Int = when (kind) {
    DiaperKind.WET -> R.drawable.ic_diaper_wet
    DiaperKind.DIRTY -> R.drawable.ic_diaper_dirty
    DiaperKind.MIXED -> R.drawable.ic_diaper_mixed
}

@Preview(name = "Иконки записей", widthDp = 720)
@Composable
private fun EventIconsPreview() = LightDarkPreview {
    val start = LocalDate.of(2026, 5, 20).atTime(LocalTime.of(14, 30))
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    listOf(
        Feeding(childId = 1, start = start, breast = Breast.LEFT),
        Diaper(childId = 1, start = start, kind = DiaperKind.WET),
        Diaper(childId = 1, start = start, kind = DiaperKind.DIRTY),
        Diaper(childId = 1, start = start, kind = DiaperKind.MIXED),
        Sleep(childId = 1, start = start, end = start + 85 * 60_000L),
        Medicine(childId = 1, start = start, name = "Витамин D", note = "3 капли"),
        Weight(childId = 1, start = start, grams = 4350),
        Height(childId = 1, start = start, millimeters = 625),
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
