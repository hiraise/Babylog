package com.kid.babylog.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kid.babylog.R
import com.kid.babylog.core.ui.guide.GuideSheetHint
import com.kid.babylog.core.ui.guide.LocalSheetTarget
import com.kid.babylog.core.ui.guide.guideTarget
import com.kid.babylog.core.ui.theme.BabyLogTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Основная кнопка листа: «Сохранить», «Запустить таймер»; [destructive] — красная «Удалить». */
data class SheetAction(
    val label: String,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/** Закрывает лист с анимацией и только потом выполняет действие — иначе лист пропадает рывком. */
@OptIn(ExperimentalMaterial3Api::class)
@Stable
class SheetCloser internal constructor(private val scope: CoroutineScope, private val state: SheetState) {
    fun close(then: () -> Unit) {
        scope.launch { state.hide() }.invokeOnCompletion { then() }
    }
}

/**
 * Нижний лист — единственный вид всплывающего окна в приложении (ADR-0018): формы записей, меню,
 * выбор из списка, подтверждения и пикеры даты и времени. `AlertDialog`, `DropdownMenu` и
 * `DatePickerDialog` не используй.
 *
 * Сверху иконка `primary` и [title] (`headlineSmall`), под ними [content] с отступами `spacing.l` по
 * бокам и шагом `spacing.m`. С [confirm] внизу справа кнопки «Отмена» и основная. Без [confirm]
 * лист — меню или список: закрывается свайпом вниз, тапом мимо или «Назад», а выбор делается
 * в [content] через [SheetCloser.close].
 *
 * @param scrollable содержимое прокручивается, когда не помещается (с клавиатурой — почти всегда).
 *   `false` — для содержимого, которое прокручивается само: `LazyColumn`, пикеры Material 3.
 * @param edgeToEdge без боковых отступов и шага: строки во всю ширину листа ([SheetMenuItem],
 *   списки выбора) сами отступают от краёв, а подсветка нажатия доходит до краёв листа.
 * @param onDismiss лист закрыт без действия: свайп, тап мимо, «Назад», «Отмена».
 * @param secondary вместо «Отмена» — своё второе действие рядом с [confirm]: «Ещё плачет» у листа
 *   после плача (ADR-0030). Свайп, тап мимо и «Назад» по-прежнему вызывают [onDismiss].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    @DrawableRes icon: Int? = null,
    confirm: SheetAction? = null,
    scrollable: Boolean = true,
    edgeToEdge: Boolean = false,
    secondary: SheetAction? = null,
    content: @Composable ColumnScope.(SheetCloser) -> Unit,
) {
    val spacing = BabyLogTheme.spacing
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val closer = remember(state, scope) { SheetCloser(scope, state) }
    // Фон — surfaceContainerHigh, как у диалогов Material 3, а не surfaceContainerLow по умолчанию:
    // на нём чипы и карточки (surfaceContainerLow) видны и в тёмной теме, где у них нет тени
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        // Гайд справки (ADR-0043): лист закрывает его панель, поэтому текст шага — здесь
        GuideSheetHint(Modifier.padding(start = spacing.l, end = spacing.l, bottom = spacing.m))
        if (title != null) {
            Row(
                modifier = Modifier.padding(start = spacing.l, end = spacing.l, bottom = spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.m),
            ) {
                if (icon != null) AppIcon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.headlineSmall)
            }
        }
        // weight(fill = false): длинное содержимое прокручивается, а кнопки остаются видны над ним
        val guide = LocalSheetTarget.current
        Column(
            modifier = Modifier
                .then(if (guide != null) Modifier.guideTarget(guide) else Modifier)
                .weight(1f, fill = false)
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .then(if (edgeToEdge) Modifier else Modifier.padding(horizontal = spacing.l)),
            verticalArrangement = if (edgeToEdge) Arrangement.Top else Arrangement.spacedBy(spacing.m),
        ) {
            content(closer)
        }
        if (confirm != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = spacing.l, end = spacing.l, top = spacing.m),
                horizontalArrangement = Arrangement.spacedBy(spacing.s, Alignment.End),
            ) {
                if (secondary != null) {
                    TextButton(onClick = { closer.close(secondary.onClick) }, enabled = secondary.enabled) { Text(secondary.label) }
                } else {
                    TextButton(onClick = { closer.close(onDismiss) }) { Text("Отмена") }
                }
                val colors = MaterialTheme.colorScheme
                Button(
                    onClick = { closer.close(confirm.onClick) },
                    enabled = confirm.enabled,
                    colors = if (confirm.destructive) {
                        ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError)
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                ) { Text(confirm.label) }
            }
        }
        // Воздух под последней строкой: над жестовой панелью навигации отступ даёт сам лист
        Spacer(Modifier.height(spacing.l))
    }
}

/**
 * Пункт листа-меню ([AppSheet] с `edgeToEdge`): иконка и подпись во всю ширину, нажимается вся строка.
 * [badge] — точка после подписи: в пункте есть новое («Справка», ADR-0043).
 */
@Composable
fun SheetMenuItem(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
    badge: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val spacing = BabyLogTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        AppIcon(icon, contentDescription = null, tint = tint)
        Text(label, style = MaterialTheme.typography.bodyLarge)
        if (badge) Badge()
    }
}

@Preview(name = "AppSheet — содержимое", widthDp = 720)
@Composable
private fun AppSheetPreview() = LightDarkPreview {
    // Сам ModalBottomSheet в превью не открывается — показываем его строки
    SheetMenuItem(R.drawable.ic_add_child, "Добавить ребёнка", onClick = {})
    SheetMenuItem(R.drawable.ic_export, "Экспорт в файл", onClick = {})
}
