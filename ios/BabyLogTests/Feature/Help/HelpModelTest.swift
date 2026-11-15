import Foundation
import Testing
@testable import BabyLog

/** «Новое» и порядок тем справки (ADR-0043). */
struct HelpModelTest {
    private let old = GuideStep(text: "Шаг первой справки", done: .next)
    private let added = GuideStep(text: "Шаг из 1.11", done: .next, since: "1.11")

    private var topic: HelpTopic {
        HelpTopic(id: "topic", title: "Тема", icon: "ic_help", summary: "Кратко", steps: [old, added])
    }
    private var other: HelpTopic {
        HelpTopic(id: "other", title: "Другая", icon: "ic_help", summary: "Кратко", steps: [old])
    }

    @Test func versionsCompareByNumbersAndIgnoreBuildSuffix() {
        #expect(compareVersions("1.10", "1.9") > 0)
        #expect(compareVersions("1.9-debug", "1.10") < 0)
        #expect(compareVersions("1.10-debug", "1.10") == 0)
        #expect(compareVersions("2", "2.0") == 0)
    }

    @Test func stepNewerThanFirstLaunchIsNewUntilTopicOpened() {
        #expect(topic.newSteps(progress: HelpProgress(baseline: "1.10")) == [added])
        #expect(topic.newSteps(progress: HelpProgress(
            baseline: "1.10",
            opened: ["topic": "1.11"]
        )).isEmpty)
        #expect(topic.newSteps(progress: HelpProgress(baseline: "1.11")).isEmpty)
    }

    @Test func firstHelpStepsAreNeverNew() {
        #expect(!other.isNew(progress: HelpProgress(baseline: "1.9")))
    }

    @Test func badgeUntilPointerDoneThenOnlyForNewSteps() {
        #expect(helpBadge(topics: [other], progress: HelpProgress(baseline: "1.10")))
        #expect(!helpBadge(topics: [other], progress: HelpProgress(baseline: "1.10", pointerDone: true)))
        #expect(helpBadge(
            topics: [topic, other],
            progress: HelpProgress(baseline: "1.10", pointerDone: true)
        ))
    }

    @Test func inOrderContinuesFromFirstUnfinishedTopic() {
        let topics = [topic, other]
        #expect(nextUnfinished(topics: topics, progress: HelpProgress()) == topic)
        #expect(nextUnfinished(topics: topics, progress: HelpProgress(completed: ["topic"])) == other)
        #expect(nextUnfinished(topics: topics, progress: HelpProgress(completed: ["topic", "other"])) == topic)
        #expect(topicAfter(topics: topics, topic: topic) == other)
        #expect(topicAfter(topics: topics, topic: other) == nil)
    }

    @Test func topicPageHidesGuideOnlySteps() {
        let skip = GuideStep(text: "Перемотаем", done: .next, demoOnly: true)
        let page = HelpTopic(
            id: "topic",
            title: "Тема",
            icon: "ic_help",
            summary: "Кратко",
            steps: [old, skip]
        )
        #expect(page.readingSteps == [old])
    }

    @Test func topicsKeepAndroidOrderAndDropShadeStep() {
        #expect(HELP_TOPICS.map(\.id) == [
            "sleep-feeding", "records", "crying", "notes", "health",
            "diary", "analytics", "development", "children", "settings",
        ])
        #expect(!SETTINGS.steps.contains { $0.text.contains("Кнопки в шторке") })
    }

    @Test func navigationRouteIDsMatchAndroidSavedRoutes() {
        #expect(HelpRoute.topics.id == "topics")
        #expect(HelpRoute.topic("diary").id == "topic:diary")
    }
}
