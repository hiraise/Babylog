package com.kid.babylog.feature.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kid.babylog.R
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.components.AppSheet
import com.kid.babylog.core.ui.components.SheetAction
import com.kid.babylog.core.ui.photo.decodeSampled
import com.kid.babylog.core.ui.theme.BabyLogTheme

/**
 * Фото записи в форме (ADR-0024): миниатюры 72 dp в ряд с прокруткой и плитка «добавить фото»
 * в конце, пока можно добавить ещё ([canAdd]). Пока новые фото обрабатываются ([processing]), на
 * плитке индикатор. Касание миниатюры — [onOpen] с её номером. [jpegOf] — байты фото формы.
 *
 * Миниатюры декодируются сразу, без фонового потока: JPEG 1600 px уменьшается при декодировании
 * в 8 раз за миллисекунды, а скриншот-тест видит их без ожидания.
 */
@Composable
internal fun PhotoStrip(
    photos: List<FormPhoto>,
    jpegOf: (FormPhoto) -> ByteArray?,
    canAdd: Boolean,
    processing: Boolean,
    onAdd: () -> Unit,
    onOpen: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val sizePx = with(LocalDensity.current) { THUMB_SIZE.roundToPx() }
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s),
    ) {
        photos.forEachIndexed { index, photo ->
            val image = remember(photo, sizePx) { jpegOf(photo)?.let { decodeSampled(it, sizePx) } }
            Box(
                modifier = Modifier
                    .size(THUMB_SIZE)
                    .clip(shape)
                    .background(colors.surfaceVariant)
                    .clickable(onClickLabel = "открыть фото", role = Role.Button) { onOpen(index) }
                    .semantics { contentDescription = "Фото ${index + 1} из ${photos.size}" },
            ) {
                if (image != null) Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        if (canAdd || processing) {
            Box(
                modifier = Modifier
                    .size(THUMB_SIZE)
                    .clip(shape)
                    // Рамка: на фоне листа плитка без неё — одна иконка, не видно, что это место под фото
                    .border(1.dp, colors.outlineVariant, shape)
                    .clickable(enabled = !processing, onClickLabel = "добавить фото", role = Role.Button, onClick = onAdd)
                    .semantics { contentDescription = if (processing) "Фото добавляется" else "Добавить фото" },
                contentAlignment = Alignment.Center,
            ) {
                if (processing) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                } else {
                    AppIcon(R.drawable.ic_add_a_photo, contentDescription = null, tint = colors.primary)
                }
            }
        }
    }
}

private val THUMB_SIZE = 72.dp

/**
 * Фото крупно, во всю ширину листа. «Убрать фото» убирает его из формы, а из базы — по «Сохранить»
 * записи: передумали — «Отмена» в форме вернёт всё как было.
 */
@Composable
internal fun PhotoViewSheet(jpeg: ByteArray?, onRemove: () -> Unit, onDismiss: () -> Unit) {
    // Крупно — без уменьшения: одно фото 1600 px в памяти — несколько мегабайт
    val image = remember(jpeg) { jpeg?.let { decodeSampled(it, Int.MAX_VALUE) } }
    AppSheet(
        onDismiss = onDismiss,
        title = "Фото",
        icon = R.drawable.ic_photo_camera,
        confirm = SheetAction("Убрать фото", destructive = true, onClick = onRemove),
    ) {
        if (image != null) {
            Image(
                image,
                contentDescription = "Фото",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text("Не удалось открыть фото", color = MaterialTheme.colorScheme.error)
        }
    }
}
