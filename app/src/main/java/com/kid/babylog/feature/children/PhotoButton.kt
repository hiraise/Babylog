package com.kid.babylog.feature.children

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kid.babylog.R
import com.kid.babylog.core.model.ChildPhoto
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.components.Avatar

/**
 * Фото в листе ребёнка: аватар 96 dp со значком камеры на краю круга. Касание открывает
 * [com.kid.babylog.core.ui.photo.PhotoSourceSheet]. Подсветка нажатия — круг из центра (`bounded = false`): значок выступает
 * за круг аватара, и обрезать по кругу нельзя.
 */
@Composable
internal fun PhotoButton(sex: Sex?, photo: ChildPhoto?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(AVATAR_SIZE)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = AVATAR_SIZE / 2),
                onClickLabel = if (photo == null) "добавить фото" else "изменить фото",
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = if (photo == null) "Фото не выбрано" else "Фото ребёнка" },
    ) {
        Avatar(sex, photo, size = AVATAR_SIZE)
        // Кольцо цвета листа отделяет значок от фото
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(32.dp)
                .background(colors.primary, CircleShape)
                .border(2.dp, colors.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(R.drawable.ic_photo_camera, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.onPrimary)
        }
    }
}

private val AVATAR_SIZE = 96.dp
