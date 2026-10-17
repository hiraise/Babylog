package com.kid.babylog.core.ui.photo

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.kid.babylog.R
import com.kid.babylog.core.ui.components.AppSheet
import com.kid.babylog.core.ui.components.SheetMenuItem

/**
 * Лист «Фото»: снять, выбрать из галереи, а если [onRemove] задан — убрать. Пункт закрывает лист;
 * камеру и галерею открывает [PhotoPicker] из composable, который этот лист пережидает.
 */
@Composable
fun PhotoSourceSheet(
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onDismiss: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    AppSheet(onDismiss = onDismiss, title = "Фото", icon = R.drawable.ic_photo_camera, edgeToEdge = true) { closer ->
        SheetMenuItem(R.drawable.ic_photo_camera, "Сделать фото", onClick = { closer.close(onCamera) })
        SheetMenuItem(R.drawable.ic_photo_library, "Выбрать из галереи", onClick = { closer.close(onGallery) })
        if (onRemove != null) {
            SheetMenuItem(
                R.drawable.ic_delete,
                "Убрать фото",
                onClick = { closer.close(onRemove) },
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
