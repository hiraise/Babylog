package com.kid.babylog.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kid.babylog.R
import com.kid.babylog.core.ui.theme.BabyLogTheme

/**
 * Аватар ребёнка: круг `primaryContainer` с иконкой `ic_child` цвета `onPrimaryContainer`.
 * Когда у ребёнка появится фото, оно займёт этот круг; пока фото нет — иконка (ADR-0019).
 *
 * Используй рядом с именем ребёнка: в шапке, списке детей. Аватар декоративный — TalkBack его
 * пропускает, поэтому имя всегда показывай рядом текстом.
 */
@Composable
fun Avatar(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.primaryContainer)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(
            id = R.drawable.ic_child,
            contentDescription = null,
            modifier = Modifier.size(size * 0.55f), // 22 dp в круге 40 dp
            tint = colors.onPrimaryContainer,
        )
    }
}

@Preview(name = "Avatar", widthDp = 720)
@Composable
private fun AvatarPreview() = LightDarkPreview {
    Row(
        horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(size = 32.dp)
        Avatar()
        Avatar(size = 56.dp)
    }
}
