import Foundation
import SnapshotTesting
import SwiftUI
import Testing
import UIKit
@testable import BabyLog

@MainActor
@Suite(.serialized)
struct RootViewTests {
    @Test func light() {
        assertRoot(style: .light, named: "light")
    }

    @Test func dark() {
        assertRoot(style: .dark, named: "dark")
    }

    private func assertRoot(style: UIUserInterfaceStyle, named name: String) {
        let root = RootView()
            .environment(AppContainer(clock: TestClock()))
            .environment(\.locale, Locale(identifier: "ru_RU"))
            .environment(\.timeZone, TimeZone(identifier: "Europe/Moscow")!)
        let controller = UIHostingController(rootView: root)
        controller.overrideUserInterfaceStyle = style
        controller.view.backgroundColor = .systemBackground
        controller.view.frame = CGRect(x: 0, y: 0, width: 393, height: 852)
        controller.view.layoutIfNeeded()

        assertSnapshot(of: controller, as: .image, named: name)
    }
}
