package com.kid.babylog.feature.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.kid.babylog.R
import com.kid.babylog.core.model.Height
import com.kid.babylog.core.ui.components.AppSheet
import com.kid.babylog.core.ui.components.SheetAction
import com.kid.babylog.core.ui.formatHeight
import com.kid.babylog.core.ui.heightCentimeters

/** Лист роста — как лист веса, но рост вводят в сантиметрах с десятыми: «62,5» ([heightInput]). */
@Composable
internal fun HeightSheet(
    initial: Height?,
    onDismiss: () -> Unit,
    onSave: (millimeters: Int, time: Long, note: String) -> Unit,
) {
    val isEdit = initial != null
    var heightText by rememberSaveable { mutableStateOf(initial?.millimeters?.let(::heightCentimeters) ?: "") }
    var minutesAgo by rememberSaveable { mutableIntStateOf(0) }
    var editTime by rememberSaveable { mutableLongStateOf(initial?.start ?: 0L) }
    var note by rememberSaveable { mutableStateOf(initial?.note ?: "") }
    val resolveTime = rememberTimeResolver()
    val millimeters = parseHeight(heightText)
    AppSheet(
        onDismiss = onDismiss,
        title = "Рост",
        icon = R.drawable.ic_height,
        confirm = SheetAction("Сохранить", enabled = millimeters != null) {
            if (millimeters != null) onSave(millimeters, resolveTime(isEdit, minutesAgo, editTime), note.trim())
        },
    ) {
        OutlinedTextField(
            value = heightText,
            onValueChange = { heightText = heightInput(it) },
            label = { Text("Рост, см") },
            supportingText = {
                if (millimeters == null && heightText.isNotEmpty()) {
                    Text("От ${heightCentimeters(Height.RANGE.first)} до ${formatHeight(Height.RANGE.last)}")
                } else {
                    Text("Например, 62,5")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        TimeSection(isEdit, minutesAgo, { minutesAgo = it }, editTime, { editTime = it })
        NoteField(note, { note = it })
    }
}

/**
 * Ввод роста: сантиметры и не больше одной цифры после запятой — «62», «62,5». Точка становится
 * запятой (на части клавиатур запятой нет), остальное отбрасывается.
 */
internal fun heightInput(text: String): String {
    val normalized = text.replace('.', ',').filter { it.isDigit() || it == ',' }
    val whole = normalized.substringBefore(',').take(3)
    if (',' !in normalized) return whole
    return "$whole," + normalized.substringAfter(',').filter(Char::isDigit).take(1)
}

/** Миллиметры из ввода [heightInput]: «62,5» → 625, «62» и «62,» → 620; пусто или вне [Height.RANGE] — null. */
internal fun parseHeight(text: String): Int? {
    val whole = text.substringBefore(',').toIntOrNull() ?: return null
    val tenths = text.substringAfter(',', "").toIntOrNull() ?: 0
    return (whole * 10 + tenths).takeIf { it in Height.RANGE }
}
