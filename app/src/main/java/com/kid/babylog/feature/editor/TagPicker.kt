package com.kid.babylog.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import com.kid.babylog.R
import com.kid.babylog.core.model.MAX_TAG_LENGTH
import com.kid.babylog.core.model.normalizeTag
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.components.ChoiceChip
import com.kid.babylog.core.ui.components.HintChip
import com.kid.babylog.core.ui.theme.BabyLogTheme

/** Метки в листе хранятся строкой через «;»: список в Bundle при повороте так переживает надёжнее. */
internal fun List<String>.packed() = joinToString(";")
internal fun String.unpacked() = split(';').filter { it.isNotEmpty() }

/**
 * Чипы, из которых выбирают несколько ([ChoiceChip] `multiple`); у выбранного — галочка. Выбранное,
 * но не входящее в [options], тоже показано. «+ своя метка» открывает поле: набранное приводится
 * к общему виду ([normalize], у меток — [normalizeTag]) и сразу выбирается. Названия болезней
 * (ADR-0040) приводятся своим правилом: регистр у «ОРВИ» важен. Над полем — подсказки из [other] (метки записей
 * другого типа, ADR-0031) с подписью [otherTitle]: с набранного начинается метка или слово в ней, без
 * набранного — первые. Без подписи подсказки не отличить от чипов выше.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagPicker(
    title: String,
    options: List<String>,
    selected: List<String>,
    onChange: (List<String>) -> Unit,
    addLabel: String,
    other: List<String> = emptyList(),
    otherTitle: String = "",
    normalize: (String) -> String? = ::normalizeTag,
    maxLength: Int = MAX_TAG_LENGTH,
) {
    val spacing = BabyLogTheme.spacing
    var adding by rememberSaveable(title) { mutableStateOf(false) }
    var draft by rememberSaveable(title) { mutableStateOf("") }
    val add = {
        normalize(draft)?.let { tag -> if (selected.none { it.equals(tag, ignoreCase = true) }) onChange(selected + tag) }
        draft = ""
        adding = false
    }
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s), verticalArrangement = Arrangement.Center) {
            (options + selected).distinct().forEach { tag ->
                val on = tag in selected
                ChoiceChip(
                    selected = on,
                    onClick = { onChange(if (on) selected - tag else selected + tag) },
                    label = tag,
                    icon = if (on) R.drawable.ic_check else null,
                    multiple = true,
                )
            }
            if (!adding) {
                TextButton(onClick = { adding = true }, modifier = Modifier.align(Alignment.CenterVertically)) { Text("+ $addLabel") }
            }
        }
        if (adding) {
            // Подсказки — над полем: поле получает фокус, лист прокручивается к нему, и над клавиатурой
            // видно и подсказки, и поле; под полем их закрыли бы кнопки листа
            val typed = draft.trim().lowercase()
            val hints = other.filter { it !in selected && (typed.isEmpty() || it.split(' ').any { word -> word.startsWith(typed) }) }
                .take(MAX_HINTS)
            if (hints.isNotEmpty()) {
                Text(otherTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                    hints.forEach { tag ->
                        HintChip(label = tag, onClick = {
                            onChange(selected + tag)
                            draft = ""
                            adding = false
                        })
                    }
                }
            }
            val focus = remember { FocusRequester() }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(maxLength) },
                label = { Text(addLabel.replaceFirstChar(Char::titlecase)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                trailingIcon = {
                    IconButton(onClick = add) { AppIcon(R.drawable.ic_check, contentDescription = "Добавить") }
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            LaunchedEffect(Unit) { focus.requestFocus() }
        }
    }
}

/** Подсказок над полем своей метки — не больше стольких: одна строка-две, а не стена чипов. */
private const val MAX_HINTS = 5
