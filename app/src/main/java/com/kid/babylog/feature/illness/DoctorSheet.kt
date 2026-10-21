package com.kid.babylog.feature.illness

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.kid.babylog.R
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.components.AppSheet
import com.kid.babylog.core.ui.components.SheetAction
import com.kid.babylog.core.ui.photo.shareWithPhotos

/**
 * «Для врача» (ADR-0040): сводка болезни [text] ([doctorText]) — посмотреть перед отправкой и отдать
 * системному «Поделиться» (ADR-0024): Telegram, WhatsApp, почта. Само приложение в сеть не ходит:
 * текст уходит только по нажатию и только туда, куда его отправили.
 */
@Composable
fun DoctorSheet(text: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AppSheet(
        onDismiss = onDismiss,
        title = "Для врача",
        icon = R.drawable.ic_share,
        confirm = SheetAction("Поделиться") {
            shareWithPhotos(context, text, jpegs = emptyList())
            onDismiss()
        },
    ) {
        Text(
            "Уйдёт текстом туда, куда выберете: Telegram, WhatsApp, почта",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
