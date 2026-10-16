package com.kid.babylog.feature.children

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.kid.babylog.R
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.isNameTaken
import com.kid.babylog.core.model.isValidChildName
import com.kid.babylog.core.ui.BirthDatePickerDialog
import com.kid.babylog.core.ui.ChipIcon
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.formatDate
import com.kid.babylog.core.ui.theme.BabyLogTheme

/**
 * Добавление ([initial] = null) или правка ребёнка: имя и дата рождения.
 * Имя обязательно и не должно совпадать с именем другого ребёнка из [children].
 */
@Composable
fun ChildDialog(
    initial: Child?,
    children: List<Child>,
    onDismiss: () -> Unit,
    onSave: (name: String, birthDate: Long?) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var birthDate by rememberSaveable { mutableStateOf(initial?.birthDate) }
    var pickBirth by rememberSaveable { mutableStateOf(false) }
    val taken = children.isNameTaken(name, exceptId = initial?.id)
    val valid = isValidChildName(name) && !taken
    val spacing = BabyLogTheme.spacing

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { AppIcon(R.drawable.ic_child, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(if (initial == null) "Новый ребёнок" else "Ребёнок") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(Child.MAX_NAME_LENGTH) },
                    label = { Text("Имя") },
                    isError = taken,
                    // null, а не пустая лямбда: иначе под полем остаётся пустая строка
                    supportingText = if (taken) {
                        { Text("Такое имя уже есть") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                // Подпись над чипом: «28 сентября 2025» с иконкой рядом с подписью в диалог не влезает
                Column {
                    Text("Дата рождения:", style = MaterialTheme.typography.titleSmall)
                    AssistChip(
                        onClick = { pickBirth = true },
                        label = { Text(birthDate?.let { formatDate(it) } ?: "указать") },
                        leadingIcon = { ChipIcon(R.drawable.ic_calendar) },
                    )
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Удалить ребёнка…", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(name.trim(), birthDate) }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )

    if (pickBirth) {
        BirthDatePickerDialog(
            initial = birthDate,
            onDismiss = { pickBirth = false },
            onSave = {
                birthDate = it
                pickBirth = false
            },
        )
    }
}
