package com.kid.babylog.feature.help

import com.kid.babylog.R
import com.kid.babylog.core.data.HelpProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** «Новое» и порядок тем справки (ADR-0043). */
class HelpModelTest {

    private val old = GuideStep("Шаг первой справки", StepDone.Next)
    private val added = GuideStep("Шаг из 1.11", StepDone.Next, since = "1.11")
    private val topic = HelpTopic("topic", "Тема", R.drawable.ic_help, "Кратко", listOf(old, added))
    private val other = HelpTopic("other", "Другая", R.drawable.ic_help, "Кратко", listOf(old))

    @Test
    fun `versions compare by numbers, build suffix ignored`() {
        assertTrue(compareVersions("1.10", "1.9") > 0)
        assertTrue(compareVersions("1.9-debug", "1.10") < 0)
        assertEquals(0, compareVersions("1.10-debug", "1.10"))
        assertEquals(0, compareVersions("2", "2.0"))
    }

    @Test
    fun `step newer than first launch with help is new until the topic is opened`() {
        assertEquals(setOf(added), topic.newSteps(HelpProgress(baseline = "1.10")))
        assertTrue(topic.newSteps(HelpProgress(baseline = "1.10", opened = mapOf("topic" to "1.11"))).isEmpty())
        // Поставили приложение сразу с 1.11 — шаг не новый
        assertTrue(topic.newSteps(HelpProgress(baseline = "1.11")).isEmpty())
    }

    @Test
    fun `steps of the first help are never new`() {
        assertFalse(other.isNew(HelpProgress(baseline = "1.9")))
    }

    @Test
    fun `badge until told where help is, then only for new steps`() {
        assertTrue(helpBadge(listOf(other), HelpProgress(baseline = "1.10")))
        assertFalse(helpBadge(listOf(other), HelpProgress(baseline = "1.10", pointerDone = true)))
        assertTrue(helpBadge(listOf(topic, other), HelpProgress(baseline = "1.10", pointerDone = true)))
    }

    @Test
    fun `in order continues from the first unfinished topic`() {
        val topics = listOf(topic, other)
        assertEquals(topic, nextUnfinished(topics, HelpProgress()))
        assertEquals(other, nextUnfinished(topics, HelpProgress(completed = setOf("topic"))))
        assertEquals(topic, nextUnfinished(topics, HelpProgress(completed = setOf("topic", "other"))))
        assertEquals(other, topicAfter(topics, topic))
        assertEquals(null, topicAfter(topics, other))
    }

    @Test
    fun `topic page hides guide-only steps`() {
        val skip = GuideStep("Перемотаем", StepDone.Next, demoOnly = true)
        assertEquals(listOf(old), topic.copy(steps = listOf(old, skip)).readingSteps)
    }
}
