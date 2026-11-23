import SnapshotTesting
import SwiftUI
import Testing
import UIKit
@testable import BabyLog

/** Справка (ADR-0043): список тем и экран темы в светлой и тёмной теме. */
@MainActor
@Suite(.serialized)
struct HelpScreenshotTest {
    private let clock = TestClock(
        date: ISO8601DateFormatter().date(from: "2026-10-15T10:00:00Z")!,
        timeZone: TimeZone(secondsFromGMT: 0)!
    )

    @Test func topicsLight() {
        captureTopics(name: "help_topics", dark: false)
    }

    /** Экран справки вне дневника: собственный фон задаёт читаемый цвет текста в тёмной теме. */
    @Test func topicsDark() {
        captureTopics(name: "help_topics_dark", dark: true)
    }

    @Test func topicLight() {
        captureTopic(name: "help_topic", dark: false)
    }

    @Test func topicDark() {
        captureTopic(name: "help_topic_dark", dark: true)
    }

    private func captureTopics(name: String, dark: Bool) {
        capture(name: name, dark: dark) {
            HelpScreen(
                topics: HELP_TOPICS,
                progress: HelpProgress(baseline: "1.10"),
                onOpenTopic: { _ in },
                onStartGuide: { _ in },
                onBack: {}
            )
        }
    }

    private func captureTopic(name: String, dark: Bool) {
        capture(name: name, dark: dark) {
            HelpTopicScreen(
                topic: HELP_TOPICS[0],
                progress: HelpProgress(baseline: "1.10"),
                next: nil,
                onTry: {},
                onOpenTopic: { _ in },
                onBack: {}
            )
        }
    }

    private func capture<Content: View>(
        name: String,
        dark: Bool,
        @ViewBuilder content: () -> Content
    ) {
        let root = BabyLogTheme(clock: clock, darkTheme: dark) {
            content()
                .environment(\.locale, Locale(identifier: "ru_RU"))
                .environment(\.timeZone, TimeZone(secondsFromGMT: 0)!)
        }
        let controller = UIHostingController(rootView: root)
        controller.overrideUserInterfaceStyle = dark ? .dark : .light
        controller.view.frame = CGRect(x: 0, y: 0, width: 393, height: 852)
        controller.view.layoutIfNeeded()
        assertSnapshot(of: controller, as: .image, named: name)
    }
}
