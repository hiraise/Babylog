import SnapshotTesting
import SwiftUI
import UIKit
@testable import BabyLog

struct ComponentSnapshotFrame<Content: View>: View {
    @Environment(\.babyLogTheme) private var theme
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: theme.spacing.m) { content }
            .padding(theme.spacing.l)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background(theme.colors.background)
            .foregroundStyle(theme.colors.onBackground)
    }
}

@MainActor
func assertComponentSnapshot<Content: View>(
    name: String,
    style: UIUserInterfaceStyle,
    width: CGFloat = 393,
    height: CGFloat = 852,
    fileID: StaticString = #fileID,
    filePath: StaticString = #filePath,
    testName: String = #function,
    line: UInt = #line,
    clock: any Clock = TestClock(),
    @ViewBuilder content: () -> Content
) {
    let dark = style == .dark
    let root = BabyLogTheme(clock: clock, darkTheme: dark) {
        ComponentSnapshotFrame {
            content()
        }
        .environment(\.locale, Locale(identifier: "ru_RU"))
        .environment(\.timeZone, TimeZone(identifier: "Europe/Moscow")!)
    }
    let controller = UIHostingController(rootView: root)
    controller.overrideUserInterfaceStyle = style
    controller.view.frame = CGRect(x: 0, y: 0, width: width, height: height)
    controller.view.layoutIfNeeded()
    assertSnapshot(
        of: controller,
        as: .image,
        named: name,
        fileID: fileID,
        file: filePath,
        testName: testName,
        line: line
    )
}
