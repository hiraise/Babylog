import SwiftUI
import Testing
import UIKit
@testable import BabyLog

@MainActor
struct AppSheetTests {
    @Test func presentedShortSheetUsesContentHeight() async throws {
        let host = try await presentAppSheet { _ in Text("Текст заметки") }
        defer { host.close() }

        #expect(host.card.frame.height > 100)
        #expect(host.card.frame.height < 350)
    }

    @Test func presentedLongSheetStaysWithinScreen() async throws {
        let host = try await presentAppSheet { _ in
            ForEach(0..<30, id: \.self) { index in
                Text("Строка \(index)")
            }
        }
        defer { host.close() }

        #expect(host.card.frame.height > 500)
        #expect(host.card.frame.height <= host.window.frame.height)
    }

    @Test func closerDismissesBeforeRunningAction() async {
        var presented = true
        var markedAsAction = false
        var actionRan = false
        let binding = Binding<Bool>(get: { presented }, set: { presented = $0 })
        let closer = SheetCloser(isPresented: binding, beforeDismiss: { markedAsAction = true })

        closer.close { actionRan = true }
        #expect(markedAsAction)
        #expect(!presented)
        #expect(!actionRan)
        try? await Task.sleep(for: .milliseconds(400))
        #expect(actionRan)
    }
}

@MainActor
private struct TestSheetHost {
    let window: UIWindow
    let previousKeyWindow: UIWindow?
    let card: UIView

    func close() {
        window.isHidden = true
        previousKeyWindow?.makeKeyAndVisible()
    }
}

@MainActor
private func presentAppSheet<Content: View>(
    @ViewBuilder content: @escaping (SheetCloser) -> Content
) async throws -> TestSheetHost {
    let previousKeyWindow = UIApplication.shared.connectedScenes
        .compactMap { $0 as? UIWindowScene }
        .flatMap(\.windows)
        .first { $0.isKeyWindow }
    let root = BabyLogTheme(clock: TestClock()) {
        AppSheet(
            isPresented: .constant(true),
            onDismiss: {},
            title: "Новая заметка",
            icon: "ic_note",
            confirm: SheetAction(label: "Сохранить", onClick: {}),
            content: content
        )
    }
    let controller = UIHostingController(rootView: root)
    let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 393, height: 852))
    window.rootViewController = controller
    window.makeKeyAndVisible()
    controller.view.layoutIfNeeded()

    let presented = await waitUntil { controller.presentedViewController != nil }
    #expect(presented)
    let sheet = try #require(controller.presentedViewController)
    try? await Task.sleep(for: .milliseconds(500))
    sheet.view.layoutIfNeeded()
    let card = try #require(sheet.view.subviews.first)
    return TestSheetHost(window: window, previousKeyWindow: previousKeyWindow, card: card)
}
