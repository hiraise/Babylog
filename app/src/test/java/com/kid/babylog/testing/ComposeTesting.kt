package com.kid.babylog.testing

import android.content.ComponentName
import androidx.activity.ComponentActivity
import org.junit.rules.ExternalResource
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Регистрирует в Robolectric `ComponentActivity`, в котором `createComposeRule()` показывает
 * содержимое теста. В манифесте приложения его нет, а `ui-test-manifest` не подключаем: он добавил бы
 * этот экран в debug APK (ADR-0022). Ставь перед правилом Compose:
 *
 * ```
 * @get:Rule(order = 0) val activity = ComponentActivityRule()
 * @get:Rule(order = 1) val compose = createComposeRule()
 * ```
 */
class ComponentActivityRule : ExternalResource() {
    override fun before() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
    }
}
