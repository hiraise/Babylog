package com.kid.babylog.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kid.babylog.R
import com.kid.babylog.core.model.ChildPhoto
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.ui.photo.decodeSampled
import com.kid.babylog.core.ui.theme.BabyLogTheme

/**
 * Аватар ребёнка: [photo] в круге (ADR-0023), а без фото — круг `primaryContainer` с иконкой по полу
 * [avatarIcon] цвета `onPrimaryContainer` (ADR-0019). Фото, которое не декодируется, — тоже иконка.
 *
 * Используй рядом с именем ребёнка: в шапке, списке детей. Аватар декоративный — TalkBack его
 * пропускает, поэтому имя всегда показывай рядом текстом.
 */
@Composable
fun Avatar(sex: Sex?, photo: ChildPhoto?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val colors = MaterialTheme.colorScheme
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    // 512 × 512 JPEG декодируется за миллисекунды — без фонового потока: аватар не мигает иконкой
    val image = remember(photo, sizePx) { photo?.let { decodeSampled(it.jpeg, sizePx) } }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.primaryContainer)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            AppIcon(
                id = avatarIcon(sex),
                contentDescription = null,
                modifier = Modifier.size(size * 0.55f), // 22 dp в круге 40 dp
                tint = colors.onPrimaryContainer,
            )
        }
    }
}

/**
 * Иконка аватара без фото — по полу; другие места берут иконку аватара только отсюда.
 *
 * Мальчик — малыш в кепке (`ic_child_boy`, символ `child_hat`). Девочка — малышка с бантиком
 * (`ic_child_girl`): в Material Symbols её нет — `girl` выглядит фигуркой с таблички, лица `face_*`
 * взрослые, — поэтому она своя (ADR-0014), с лицом в пропорциях `child_hat`. Пол не указан —
 * малыш `ic_child`, а не мальчик: мужской род в словах (ADR-0016) на картинку не переносится.
 */
@DrawableRes
fun avatarIcon(sex: Sex?): Int = when (sex) {
    Sex.BOY -> R.drawable.ic_child_boy
    Sex.GIRL -> R.drawable.ic_child_girl
    null -> R.drawable.ic_child
}

@Preview(name = "Avatar", widthDp = 720)
@Composable
private fun AvatarPreview() = LightDarkPreview {
    listOf(null, Sex.BOY, Sex.GIRL).forEach { sex ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(sex, photo = null, size = 32.dp)
            Avatar(sex, photo = null)
            Avatar(sex, photo = null, size = 56.dp)
        }
    }
}
