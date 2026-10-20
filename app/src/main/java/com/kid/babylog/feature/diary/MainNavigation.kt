package com.kid.babylog.feature.diary

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kid.babylog.R
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.theme.BabyLogTheme

/** Разделы главного экрана (ADR-0035); переключает их нижняя панель [MainNavigationBar]. */
enum class MainSection(val title: String, @DrawableRes val icon: Int) {
    TODAY("Сегодня", R.drawable.ic_today),
    DIARY("Дневник", R.drawable.ic_diary),
    DEVELOPMENT("Развитие", R.drawable.ic_development),
    ANALYTICS("Аналитика", R.drawable.ic_analytics),
}

/** Вид «Дневника» (ADR-0035): записи по дням или плач и заметки (ADR-0031). */
enum class DiaryView(val title: String) { DAYS("По дням"), NOTES("Заметки") }

/**
 * Нижняя панель разделов (ADR-0035): до неё дотягивается большой палец, когда ребёнок на руках.
 * Подписи видны у всех пунктов — четыре слова помещаются и на 320 dp. Точка на «Развитии»
 * ([developmentBadge]) — советы этой недели ещё не открывали; TalkBack читает её в описании иконки.
 */
@Composable
internal fun MainNavigationBar(selected: MainSection, onSelect: (MainSection) -> Unit, developmentBadge: Boolean) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        MainSection.entries.forEach { section ->
            NavigationBarItem(
                selected = section == selected,
                onClick = { onSelect(section) },
                icon = {
                    if (section == MainSection.DEVELOPMENT && developmentBadge) {
                        BadgedBox(badge = { Badge() }) { AppIcon(section.icon, contentDescription = "есть новые советы") }
                    } else {
                        AppIcon(section.icon, contentDescription = null)
                    }
                },
                label = { Text(section.title, maxLines = 1, softWrap = false) },
            )
        }
    }
}

/**
 * «Дневник» (ADR-0035): переключатель «По дням | Заметки» и под ним выбранный вид ([content]).
 * Это кнопки, а не вкладки: вкладки рядом с нижней панелью путают, к какому уровню они относятся.
 */
@Composable
internal fun DiarySection(
    view: DiaryView,
    onView: (DiaryView) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (DiaryView) -> Unit,
) {
    val spacing = BabyLogTheme.spacing
    Column(modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            Modifier
                .fillMaxWidth()
                .padding(start = spacing.l, top = spacing.m, end = spacing.l),
        ) {
            DiaryView.entries.forEachIndexed { index, item ->
                SegmentedButton(
                    selected = view == item,
                    onClick = { onView(item) },
                    shape = SegmentedButtonDefaults.itemShape(index, DiaryView.entries.size),
                    label = { Text(item.title) },
                )
            }
        }
        Box(Modifier.weight(1f)) { content(view) }
    }
}
