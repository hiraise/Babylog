package com.kid.babylog.core.ui.photo

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Кэш фото (ADR-0024): папки отправок «Поделиться» живут сутки, свежие не трогаются. */
@RunWith(RobolectricTestRunner::class)
class PhotoCacheTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun `stale share folders are removed with their photos, fresh ones stay`() {
        val now = 10 * day
        val old = shareFolder("old", modified = now - 2 * day)
        val fresh = shareFolder("fresh", modified = now - 60_000)
        sweepPhotoCache(context, now)
        assertFalse(old.exists())
        assertTrue(fresh.resolve("photo-1.jpg").exists())
    }

    private fun shareFolder(name: String, modified: Long): File =
        File(context.cacheDir, "share/$name").apply {
            mkdirs()
            resolve("photo-1.jpg").writeBytes(byteArrayOf(1, 2, 3))
            setLastModified(modified)
        }
}
