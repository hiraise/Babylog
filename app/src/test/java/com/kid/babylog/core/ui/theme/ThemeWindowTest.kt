package com.kid.babylog.core.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Окно до первой отрисовки Compose красится цветом `window_background` из `res/values/colors.xml`
 * и `res/values-night/colors.xml` (фон окна, строка состояния и навигационная панель). Он повторяет
 * `background` темы: иначе при запуске мелькнёт другой цвет, а системные панели разойдутся с экраном.
 */
class ThemeWindowTest {

    @Test
    fun `window background matches theme background`() {
        assertEquals(
            "res/values/colors.xml: window_background должен равняться background светлой темы (Color.kt)",
            LightColors.background.hex(),
            windowBackground("values"),
        )
        assertEquals(
            "res/values-night/colors.xml: window_background должен равняться background тёмной темы (Color.kt)",
            DarkColors.background.hex(),
            windowBackground("values-night"),
        )
    }
}

/** Unit-тесты Gradle запускаются из каталога модуля `app/`; из корня репозитория путь длиннее. */
private val RES_DIR: File = listOf(File("src/main/res"), File("app/src/main/res")).firstOrNull(File::isDirectory)
    ?: error("Не найден каталог ресурсов src/main/res, рабочий каталог: ${File("").absolutePath}")

/** Значение `<color name="window_background">` в `res/<qualifier>/colors.xml` как `#RRGGBB`. */
private fun windowBackground(qualifier: String): String {
    val colors = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(File(RES_DIR, "$qualifier/colors.xml"))
        .getElementsByTagName("color")
    val value = (0 until colors.length).map(colors::item)
        .single { it.attributes.getNamedItem("name")?.nodeValue == "window_background" }
        .textContent.trim().uppercase()
    return when {
        Regex("#[0-9A-F]{6}").matches(value) -> value
        Regex("#FF[0-9A-F]{6}").matches(value) -> "#" + value.substring(3)
        else -> error("$qualifier/colors.xml: window_background — ожидается непрозрачный #RRGGBB, а не $value")
    }
}
