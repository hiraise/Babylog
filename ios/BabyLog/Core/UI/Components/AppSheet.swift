import SwiftUI
import UIKit

private struct AppSheetDragHandle: UIViewRepresentable {
    let tint: UIColor
    let onDrag: (CGFloat) -> Void
    let onEnd: (CGFloat) -> Void

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.accessibilityIdentifier = "app-sheet-drag-handle"
        let handle = UIView()
        handle.backgroundColor = tint
        handle.layer.cornerRadius = BabyLogSizes.sheetDragIndicatorHeight / 2
        view.addSubview(handle)
        let pan = UIPanGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.drag(_:)))
        pan.delegate = context.coordinator
        view.addGestureRecognizer(pan)
        context.coordinator.handle = handle
        return view
    }

    func updateUIView(_ view: UIView, context: Context) {
        context.coordinator.onDrag = onDrag
        context.coordinator.onEnd = onEnd
        context.coordinator.handle?.backgroundColor = tint
        context.coordinator.handle?.frame = CGRect(
            x: (view.bounds.width - BabyLogSizes.sheetDragIndicatorWidth) / 2,
            y: BabyLogSizes.sheetDragIndicatorTop,
            width: BabyLogSizes.sheetDragIndicatorWidth,
            height: BabyLogSizes.sheetDragIndicatorHeight
        )
    }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var onDrag: (CGFloat) -> Void = { _ in }
        var onEnd: (CGFloat) -> Void = { _ in }
        var handle: UIView?

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            true
        }

        @objc func drag(_ recognizer: UIPanGestureRecognizer) {
            let height = max(0, recognizer.translation(in: recognizer.view).y)
            switch recognizer.state {
            case .changed:
                onDrag(height)
            case .ended, .cancelled:
                onEnd(height)
            default:
                break
            }
        }
    }
}

private enum AppSheetHeightPart: Hashable {
    case top, guide, title, content, actions, bottom
}

private struct AppSheetHeightPreferenceKey: PreferenceKey {
    static let defaultValue: [AppSheetHeightPart: CGFloat] = [:]

    static func reduce(value: inout Value, nextValue: () -> Value) {
        value.merge(nextValue()) { _, new in new }
    }
}

private extension View {
    func measureAppSheetHeight(_ part: AppSheetHeightPart) -> some View {
        background(
            GeometryReader { geometry in
                Color.clear.preference(key: AppSheetHeightPreferenceKey.self, value: [part: geometry.size.height])
            }
        )
    }
}

/** Основная кнопка листа; `destructive` делает действие красным. */
struct SheetAction {
    let label: String
    let enabled: Bool
    let destructive: Bool
    let onClick: () -> Void

    init(label: String, enabled: Bool = true, destructive: Bool = false, onClick: @escaping () -> Void) {
        self.label = label
        self.enabled = enabled
        self.destructive = destructive
        self.onClick = onClick
    }
}

/** Сначала закрывает лист с анимацией, затем выполняет действие. */
@MainActor
struct SheetCloser {
    @Binding var isPresented: Bool
    var beforeDismiss: () -> Void = {}

    func close(then action: @escaping () -> Void = {}) {
        beforeDismiss()
        isPresented = false
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3, execute: action)
    }
}

/**
 Нижний лист — единственный вид всплывающего окна (ADR-0018, ADR-0054). Внизу справа — «Отмена»
 и необязательное основное действие. `secondary` заменяет «Отмена» действием, а `secondaryContent` —
 системным элементом вроде `ShareLink`. `edgeToEdge` убирает боковые отступы содержимого для меню;
 `scrollable = false` используй для содержимого, которое прокручивается само.
 Лист показан через `fullScreenCover` с прозрачным фоном и собственной нижней карточкой: системной подложки нет.
 Перед презентацией обычное содержимое измеряется без `ScrollView`, а самопрокручиваемое — с доступной высотой.
 Длинное содержимое прокручивается, кнопки остаются видимыми. Сверху — drag indicator и отступ под него.
 */
struct AppSheet<Content: View>: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.guide) private var guide
    @Environment(\.sheetTarget) private var sheetTarget
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.locale) private var locale
    @Environment(\.timeZone) private var timeZone
    @Environment(\.clock) private var clock
    @Environment(\.colorScheme) private var colorScheme
    @Binding var isPresented: Bool
    @State private var actionDismissal = false
    @State private var contentHeight: CGFloat = 1
    @State private var presentationReady = false
    @State private var dragOffset: CGFloat = 0

    let onDismiss: () -> Void
    let title: String?
    let icon: String?
    let confirm: SheetAction?
    let scrollable: Bool
    let edgeToEdge: Bool
    let secondary: SheetAction?
    let secondaryContent: AnyView?
    let content: (SheetCloser) -> Content

    init(
        isPresented: Binding<Bool>,
        onDismiss: @escaping () -> Void,
        title: String? = nil,
        icon: String? = nil,
        confirm: SheetAction? = nil,
        scrollable: Bool = true,
        edgeToEdge: Bool = false,
        secondary: SheetAction? = nil,
        secondaryContent: AnyView? = nil,
        @ViewBuilder content: @escaping (SheetCloser) -> Content
    ) {
        _isPresented = isPresented
        self.onDismiss = onDismiss
        self.title = title
        self.icon = icon
        self.confirm = confirm
        self.scrollable = scrollable
        self.edgeToEdge = edgeToEdge
        self.secondary = secondary
        self.secondaryContent = secondaryContent
        self.content = content
    }

    var body: some View {
        GeometryReader { geometry in
            Color.clear
                .fullScreenCover(
                    isPresented: Binding(
                        get: { isPresented && presentationReady },
                        set: { isPresented = $0 }
                    ),
                    onDismiss: {
                        if !actionDismissal { onDismiss() }
                        actionDismissal = false
                    }
                ) {
                    GeometryReader { overlay in
                        ZStack(alignment: .bottom) {
                            theme.colors.scrim.opacity(0.35)
                                .ignoresSafeArea()
                                .onTapGesture { isPresented = false }
                            sheetCard(maxHeight: overlay.size.height - overlay.safeAreaInsets.top)
                        }
                        .ignoresSafeArea(.container, edges: .bottom)
                        .presentationBackground(.clear)
                        .interactiveDismissDisabled(true)
                        .transition(.opacity)
                    }
                }
                .onAppear { preparePresentation(width: geometry.size.width) }
                .onChange(of: isPresented) { _, presented in
                    if presented {
                        preparePresentation(width: geometry.size.width)
                    } else {
                        presentationReady = false
                        dragOffset = 0
                    }
                }
        }
        .frame(height: 0)
    }

    private func sheetCard(maxHeight: CGFloat) -> some View {
        withSheetEnvironment(sheetContent(scrollable: scrollable))
            .onPreferenceChange(AppSheetHeightPreferenceKey.self) { heights in
                contentHeight = heights.values.reduce(0, +)
            }
            .frame(height: min(contentHeight, maxHeight))
            .background(theme.colors.surfaceContainerHigh)
            .clipShape(UnevenRoundedRectangle(
                topLeadingRadius: theme.shapes.extraLarge,
                bottomLeadingRadius: 0,
                bottomTrailingRadius: 0,
                topTrailingRadius: theme.shapes.extraLarge,
                style: .continuous
            ))
            .overlay(alignment: .top) {
                AppSheetDragHandle(
                    tint: UIColor(theme.colors.onSurfaceVariant.opacity(0.5)),
                    onDrag: { dragOffset = $0 },
                    onEnd: { height in
                        if height > 30 { isPresented = false }
                        dragOffset = 0
                    }
                )
                .frame(height: BabyLogSizes.touchTarget)
            }
            .offset(y: dragOffset)
            .animation(.easeInOut(duration: 0.2), value: dragOffset)
    }

    private func sheetContent(scrollable: Bool) -> AppSheetContent<Content> {
        AppSheetContent(
            isPresented: $isPresented,
            title: title,
            icon: icon,
            confirm: confirm,
            scrollable: scrollable,
            edgeToEdge: edgeToEdge,
            secondary: secondary,
            secondaryContent: secondaryContent,
            onActionDismiss: { actionDismissal = true },
            content: content
        )
    }

    private func withSheetEnvironment<V: View>(_ view: V) -> some View {
        view
            .environment(\.babyLogTheme, theme)
            .environment(\.guide, guide)
            .environment(\.sheetTarget, sheetTarget)
            .environment(\.dynamicTypeSize, dynamicTypeSize)
            .environment(\.locale, locale)
            .environment(\.timeZone, timeZone)
            .environment(\.clock, clock)
            .environment(\.colorScheme, colorScheme)
    }

    private func preparePresentation(width: CGFloat) {
        guard isPresented, !presentationReady, width > 0 else { return }
        let controller = UIHostingController(
            rootView: withSheetEnvironment(sheetContent(scrollable: false))
        )
        let fittingHeight: CGFloat = scrollable ? 0 : UIScreen.main.bounds.height
        let height = controller.sizeThatFits(in: CGSize(width: width, height: fittingHeight)).height
        guard height > 0 else { return }
        contentHeight = height
        presentationReady = true
    }
}

struct AppSheetContent<Content: View>: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.guide) private var guide
    @Environment(\.sheetTarget) private var sheetTarget
    @Binding var isPresented: Bool

    let title: String?
    let icon: String?
    let confirm: SheetAction?
    let scrollable: Bool
    let edgeToEdge: Bool
    let secondary: SheetAction?
    let secondaryContent: AnyView?
    let onActionDismiss: () -> Void
    let content: (SheetCloser) -> Content

    init(
        isPresented: Binding<Bool>,
        title: String? = nil,
        icon: String? = nil,
        confirm: SheetAction? = nil,
        scrollable: Bool = true,
        edgeToEdge: Bool = false,
        secondary: SheetAction? = nil,
        secondaryContent: AnyView? = nil,
        onActionDismiss: @escaping () -> Void = {},
        @ViewBuilder content: @escaping (SheetCloser) -> Content
    ) {
        _isPresented = isPresented
        self.title = title
        self.icon = icon
        self.confirm = confirm
        self.scrollable = scrollable
        self.edgeToEdge = edgeToEdge
        self.secondary = secondary
        self.secondaryContent = secondaryContent
        self.onActionDismiss = onActionDismiss
        self.content = content
    }

    var body: some View {
        let spacing = theme.spacing
        let closer = SheetCloser(isPresented: $isPresented, beforeDismiss: onActionDismiss)
        VStack(alignment: .leading, spacing: 0) {
            Spacer().frame(height: spacing.l)
                .measureAppSheetHeight(.top)
            if guide?.sheetHint != nil {
                GuideSheetHint()
                    .padding(.horizontal, spacing.l)
                    .padding(.bottom, spacing.m)
                    .measureAppSheetHeight(.guide)
            }
            if let title {
                HStack(spacing: spacing.m) {
                    if let icon {
                        AppIcon(icon, tint: theme.colors.primary)
                    }
                    Text(title).appTextStyle(theme.typography.headlineSmall)
                }
                .padding(.horizontal, spacing.l)
                .padding(.bottom, spacing.m)
                .measureAppSheetHeight(.title)
            }
            Group {
                if scrollable {
                    ScrollView { sheetContent(closer) }
                } else {
                    sheetContent(closer)
                }
            }
            .guideTarget(sheetTarget)
            if let confirm {
                HStack(spacing: spacing.s) {
                    Spacer()
                    if let secondaryContent {
                        secondaryContent
                    } else if let secondary {
                        Button(secondary.label) { closer.close(then: secondary.onClick) }
                            .disabled(!secondary.enabled)
                            .buttonStyle(.plain)
                            .foregroundStyle(theme.colors.primary)
                            .appTextStyle(theme.typography.labelLarge)
                    } else {
                        Button("Отмена") { closer.close() }
                            .buttonStyle(.plain)
                            .foregroundStyle(theme.colors.primary)
                            .appTextStyle(theme.typography.labelLarge)
                    }
                    Button(confirm.label) { closer.close(then: confirm.onClick) }
                        .disabled(!confirm.enabled)
                        .buttonStyle(.borderedProminent)
                        .buttonBorderShape(.capsule)
                        .tint(confirm.destructive ? theme.colors.error : theme.colors.primary)
                        .appTextStyle(theme.typography.labelLarge)
                }
                .padding(.horizontal, spacing.l)
                .padding(.top, spacing.m)
                .measureAppSheetHeight(.actions)
            }
            Spacer().frame(height: spacing.l)
                .measureAppSheetHeight(.bottom)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityIdentifier("app-sheet")
        .background(theme.colors.surfaceContainerHigh)
        .foregroundStyle(theme.colors.onSurface)
    }

    @ViewBuilder private func sheetContent(_ closer: SheetCloser) -> some View {
        VStack(alignment: .leading, spacing: edgeToEdge ? 0 : theme.spacing.m) {
            content(closer)
        }
        .padding(.horizontal, edgeToEdge ? 0 : theme.spacing.l)
        .frame(maxWidth: .infinity, alignment: .leading)
        .measureAppSheetHeight(.content)
    }
}

/** Пункт листа-меню: нажимается вся строка, `badge` показывает точку нового. */
struct SheetMenuItem: View {
    @Environment(\.babyLogTheme) private var theme

    let icon: String
    let label: String
    let onClick: () -> Void
    let tint: Color?
    let badge: Bool

    init(icon: String, label: String, tint: Color? = nil, badge: Bool = false, onClick: @escaping () -> Void) {
        self.icon = icon
        self.label = label
        self.tint = tint
        self.badge = badge
        self.onClick = onClick
    }

    var body: some View {
        Button(action: onClick) {
            HStack(spacing: theme.spacing.m) {
                AppIcon(icon, tint: tint ?? theme.colors.primary)
                Text(label).appTextStyle(theme.typography.bodyLarge)
                if badge {
                    Circle().fill(theme.colors.primary).frame(width: BabyLogSizes.badge, height: BabyLogSizes.badge)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, theme.spacing.l)
            .frame(maxWidth: .infinity, minHeight: BabyLogSizes.menuMinHeight, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
