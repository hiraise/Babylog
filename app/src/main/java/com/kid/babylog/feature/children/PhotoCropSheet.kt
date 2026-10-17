package com.kid.babylog.feature.children

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import com.kid.babylog.R
import com.kid.babylog.core.model.ChildPhoto
import com.kid.babylog.core.ui.components.AppSheet
import com.kid.babylog.core.ui.components.SheetAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Кадрирование фото ребёнка (ADR-0023) — лист, а не отдельный экран (ADR-0018). Фото из файла
 * [source] ([rememberPhotoPicker]) лежит под кругом аватара; «Готово» отдаёт кадр JPEG 512×512.
 * Сначала кадр — весь короткий край фото по центру: для фото, где малыш в середине, хватает «Готово».
 */
@Composable
internal fun PhotoCropSheet(source: String, onDismiss: () -> Unit, onDone: (ChildPhoto) -> Unit) {
    val loaded by produceState<Result<Bitmap>?>(null, source) {
        value = withContext(Dispatchers.IO) { runCatching { loadForCrop(source) } }
    }
    val bitmap = loaded?.getOrNull()
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    var frame by rememberSaveable(source, stateSaver = CropFrameSaver) { mutableStateOf(CropFrame()) }
    // Квадрат во всю ширину, но так, чтобы лист с кнопками помещался по высоте и в ландшафте:
    // окно ловит касания, и прокрутить лист можно было бы только за заголовок
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val side = (windowHeight - SHEET_CHROME).coerceIn(MIN_CROP_SIDE, MAX_CROP_SIDE)

    AppSheet(
        onDismiss = onDismiss,
        title = "Фото",
        icon = R.drawable.ic_photo_camera,
        // Кадр 512 px режется и сжимается за десятки миллисекунд — прямо по нажатию
        confirm = SheetAction("Готово", enabled = bitmap != null) {
            bitmap?.let { onDone(cropPhoto(it, frame.square(it.width, it.height))) }
        },
    ) {
        // Во всю ширину листа: в ландшафте лист шире содержимого, и без этого квадрат прижался бы влево
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .widthIn(max = side)
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    image != null -> CropArea(image, frame = { frame }, onFrameChange = { frame = it })
                    loaded?.isFailure == true -> Text("Не удалось открыть фото", color = MaterialTheme.colorScheme.error)
                    else -> CircularProgressIndicator()
                }
            }
        }
        Text(
            "Двигайте фото пальцем, увеличивайте двумя",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

/**
 * Окно кадрирования: [image] по кадру [frame], вне круга — фон листа полупрозрачно. Жест сдвигает и
 * увеличивает фото, а не лист: события, которые взяло окно, лист и прокрутка уже не получают.
 *
 * [frame] — лямбда, а не значение: жест читает кадр в момент события. Значение с последней
 * рекомпозиции теряло бы шаги, когда события приходят чаще кадров.
 */
@Composable
internal fun CropArea(image: ImageBitmap, frame: () -> CropFrame, onFrameChange: (CropFrame) -> Unit, modifier: Modifier = Modifier) {
    val outside = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
    val currentFrame by rememberUpdatedState(frame)
    val change by rememberUpdatedState(onFrameChange)
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(image) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    change(currentFrame().transformed(image.width, image.height, size.width.toFloat(), centroid, pan, zoom))
                }
            }
            .semantics {
                contentDescription = "Кадр фото"
                customActions = cropActions { pan, zoom ->
                    // Окно — единичный квадрат: сдвиг на четверть окна, центр масштаба — середина
                    change(currentFrame().transformed(image.width, image.height, 1f, Offset(0.5f, 0.5f), pan, zoom))
                }
            },
    ) {
        val square = frame().square(image.width, image.height)
        val scale = size.width / square.side
        drawImage(
            image,
            dstOffset = IntOffset((-square.left * scale).roundToInt(), (-square.top * scale).roundToInt()),
            dstSize = IntSize((image.width * scale).roundToInt(), (image.height * scale).roundToInt()),
            filterQuality = FilterQuality.High,
        )
        clipPath(Path().apply { addOval(Rect(Offset.Zero, size)) }, ClipOp.Difference) { drawRect(outside) }
    }
}

/** Жесты кадрирования для TalkBack: масштаб и сдвиг фото кнопками действий. */
private fun cropActions(apply: (pan: Offset, zoom: Float) -> Unit): List<CustomAccessibilityAction> {
    fun action(label: String, pan: Offset = Offset.Zero, zoom: Float = 1f) =
        CustomAccessibilityAction(label) {
            apply(pan, zoom)
            true
        }
    return listOf(
        action("Увеличить", zoom = 1.25f),
        action("Уменьшить", zoom = 0.8f),
        action("Сдвинуть фото вверх", pan = Offset(0f, -0.25f)),
        action("Сдвинуть фото вниз", pan = Offset(0f, 0.25f)),
        action("Сдвинуть фото влево", pan = Offset(-0.25f, 0f)),
        action("Сдвинуть фото вправо", pan = Offset(0.25f, 0f)),
    )
}

/** Всё в листе, кроме окна: ручка, заголовок, подсказка, кнопки и отступы. */
private val SHEET_CHROME = 240.dp
private val MIN_CROP_SIDE = 160.dp
private val MAX_CROP_SIDE = 360.dp

private val CropFrameSaver = listSaver<CropFrame, Float>(
    save = { listOf(it.zoom, it.centerX, it.centerY) },
    restore = { CropFrame(zoom = it[0], centerX = it[1], centerY = it[2]) },
)
