import SnapshotTesting
import SwiftUI
import Testing
import UIKit
@testable import BabyLog

/**
 Скриншоты App Store (`docs/screenshots/ios/`): дневник девочки примера, как в гайдах справки (ADR-0043).
 Размер 1320 × 2868 px принят App Store Connect для экрана 6,9"; обновление — `scripts/dev.sh ios-goldens`.
 */
@MainActor
@Suite(.serialized)
struct AppStoreScreenshotTest {
    private let zone = TimeZone(secondsFromGMT: 0)!
    private let clock = TestClock(
        date: ISO8601DateFormatter().date(from: "2026-10-15T09:15:00Z")!,
        timeZone: TimeZone(secondsFromGMT: 0)!
    )

    @Test func today() async throws {
        try await captureDiary(testName: "01_today")
    }

    @Test func diary() async throws {
        try await captureDiary(testName: "02_diary", section: .diary)
    }

    @Test func notes() async throws {
        try await captureDiary(testName: "03_notes", section: .diary, view: .notes)
    }

    @Test func analytics() async throws {
        try await captureDiary(testName: "04_analytics", section: .analytics)
    }

    @Test func development() async throws {
        try await captureDiary(testName: "05_development", section: .development)
    }

    @Test func help() {
        capture(testName: "06_help", dark: false) {
            HelpTopicScreen(
                topic: HELP_TOPICS[0],
                progress: HelpProgress(),
                next: HELP_TOPICS[1],
                onTry: {},
                onOpenTopic: { _ in },
                onBack: {}
            )
        }
    }

    @Test func todayDark() async throws {
        try await captureDiary(testName: "07_today_dark", dark: true)
    }

    private var now: Int64 { clock.millis() }

    private func captureDiary(
        testName: String,
        dark: Bool = false,
        section: MainSection = .today,
        view: DiaryView = .days
    ) async throws {
        let today = toLocalDate(now, timeZone: zone)
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        let birthDate = calendar.date(byAdding: .day, value: -sampleAgeDays, to: today)!
        let repository = FakeDiaryRepository(children: [
            Child(id: 1, name: sampleChildName, birthDate: Int64(birthDate.timeIntervalSince1970 * 1000), sex: .girl),
        ])
        let events = sampleMonths(childId: 1, today: today, now: now, timeZone: zone)
        for event in events { _ = try await repository.addEvent(event) }
        let model = DiaryViewModel(
            repository: repository,
            selection: MemorySelectedChildStore(),
            documents: FakeDocumentStore(),
            clock: clock
        )
        _ = await waitUntil { model.state.events.count == events.count }
        let development = DevelopmentViewModel(source: AssetTipsSource(), seen: MemoryDevelopmentSeenStore())
        _ = await waitUntil { development.data != nil }

        capture(testName: testName, dark: dark) {
            DiaryScreen(
                model: model,
                theme: ThemeChoice(),
                onThemeChange: { _ in },
                development: development,
                initialSection: section,
                initialDiaryView: view
            )
        }
    }

    private func capture<Content: View>(
        testName: String,
        dark: Bool,
        @ViewBuilder content: () -> Content
    ) {
        let root = BabyLogTheme(clock: clock, darkTheme: dark) {
            content()
                .environment(\.locale, Locale(identifier: "ru_RU"))
                .environment(\.timeZone, zone)
        }
        let controller = UIHostingController(rootView: root)
        controller.overrideUserInterfaceStyle = dark ? .dark : .light
        controller.view.frame = CGRect(origin: .zero, size: appStoreScreenshotSize)
        controller.view.layoutIfNeeded()
        withSnapshotRecording {
            if let failure = verifySnapshot(
                of: controller,
                as: .image(size: appStoreScreenshotSize, traits: UITraitCollection(displayScale: 3)),
                snapshotDirectory: appStoreScreenshotsDirectory,
                testName: testName
            ) {
                Issue.record("\(failure)")
            }
        }
    }
}

private let appStoreScreenshotSize = CGSize(width: 440, height: 956)
private let appStoreScreenshotsDirectory = URL(fileURLWithPath: #filePath)
    .deletingLastPathComponent()
    .deletingLastPathComponent()
    .deletingLastPathComponent()
    .appendingPathComponent("docs/screenshots/ios", isDirectory: true)
    .path
