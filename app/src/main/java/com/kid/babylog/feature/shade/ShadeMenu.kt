package com.kid.babylog.feature.shade

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.kid.babylog.R
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.theme.BabyLogTheme

/** Переключатель уведомления в шторке: [enabled] — включено в меню, [blocked] — но телефон его не покажет. */
class ShadeToggle(
    val enabled: Boolean,
    val blocked: Boolean,
    val toggle: () -> Unit,
    val openSettings: () -> Unit,
)

/**
 * Переключатель для пункта меню (ADR-0042). Запрос разрешения живёт на уровне экрана, а не в листе меню:
 * лист закрывается раньше, чем придёт ответ. Включение на Android 13+ спрашивает разрешение на уведомления;
 * отказ, выключенные уведомления или канал видны в пункте, пока их не включат в настройках телефона.
 */
@Composable
fun rememberShadeToggle(enabled: Boolean, onChange: (Boolean) -> Unit): ShadeToggle {
    val context = LocalContext.current
    var blocked by remember { mutableStateOf(ShadeNotifier.isBlocked(context)) }
    // Вернулись из настроек телефона — уведомления там могли включить
    LifecycleResumeEffect(Unit) {
        blocked = ShadeNotifier.isBlocked(context)
        onPauseOrDispose {}
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        blocked = ShadeNotifier.isBlocked(context)
    }
    return ShadeToggle(
        enabled = enabled,
        blocked = blocked,
        toggle = {
            onChange(!enabled)
            if (!enabled && needsPermission(context)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        openSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        },
    )
}

private fun needsPermission(context: Context) = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

/** Пункт меню «Кнопки в шторке»: переключает вся строка; подсказка о настройках открывает их. */
@Composable
fun ShadeMenuItem(toggle: ShadeToggle) {
    val spacing = BabyLogTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = toggle.enabled, role = Role.Switch, onValueChange = { toggle.toggle() })
            .padding(horizontal = spacing.l, vertical = spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        AppIcon(R.drawable.ic_notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text("Кнопки в шторке", style = MaterialTheme.typography.bodyLarge)
            if (toggle.enabled && toggle.blocked) {
                Text(
                    "Уведомления выключены в настройках телефона — открыть",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable(onClick = toggle.openSettings),
                )
            } else {
                Text(
                    "Сон, кормление и плач — и на экране блокировки",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = toggle.enabled, onCheckedChange = null)
    }
}
