import Foundation
import SnapshotTesting
import SwiftUI
import Testing
import UIKit
@testable import BabyLog

@MainActor
@Suite(.serialized)
struct RootViewTests {
    @Test func light() async {
        await assertRoot(style: .light, named: "light")
    }

    @Test func dark() async {
        await assertRoot(style: .dark, named: "dark")
    }

    private func assertRoot(style: UIUserInterfaceStyle, named name: String) async {
        let container = withTestUserDefaults { defaults in
            AppContainer(
                clock: TestClock(),
                selectedChild: MemorySelectedChildStore(),
                themeMode: PrefsThemeModeStore(defaults: defaults),
                recoveryNotices: FakeRecoveryNotices(),
                repository: FakeDiaryRepository(children: [
                    Child(id: 1, name: "Маша", birthDate: nil, sex: .girl),
                ]),
                documents: FakeDocumentStore()
            )
        }
        let root = RootView()
            .environment(container)
            .environment(\.locale, Locale(identifier: "ru_RU"))
            .environment(\.timeZone, TimeZone(identifier: "Europe/Moscow")!)
        let controller = UIHostingController(rootView: root)
        controller.overrideUserInterfaceStyle = style
        controller.view.backgroundColor = .systemBackground
        controller.view.frame = CGRect(x: 0, y: 0, width: 393, height: 852)
        controller.view.layoutIfNeeded()
        try? await Task.sleep(for: .milliseconds(200))
        controller.view.layoutIfNeeded()

        assertSnapshot(of: controller, as: .image, named: name)
    }
}
