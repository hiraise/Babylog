import SwiftUI
import UIKit

/**
 «Для врача» (ADR-0040): сводка болезни `text` (`doctorText`) — посмотреть перед отправкой и отдать
 системному «Поделиться» (ADR-0024): Telegram, WhatsApp, почта. Само приложение в сеть не ходит:
 текст уходит только по нажатию и только туда, куда его отправили.
 */
struct DoctorSheet: View {
    @Environment(\.babyLogTheme) private var theme
    @Binding var isPresented: Bool
    @State private var share = false
    let text: String
    let onDismiss: () -> Void

    var body: some View {
        AppSheet(
            isPresented: $isPresented,
            onDismiss: onDismiss,
            title: "Для врача",
            icon: "ic_share",
            confirm: SheetAction(label: "Поделиться") { share = true }
        ) { _ in
            Text("Уйдёт текстом туда, куда выберете: Telegram, WhatsApp, почта")
                .appTextStyle(theme.typography.bodyMedium)
                .foregroundStyle(theme.colors.onSurfaceVariant)
            AppCard(containerColor: theme.colors.surfaceContainerHighest) {
                Text(text)
                    .appTextStyle(theme.typography.bodyMedium)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .sheet(isPresented: $share, onDismiss: onDismiss) {
            DoctorShareSheet(text: text)
        }
    }
}

private struct DoctorShareSheet: UIViewControllerRepresentable {
    let text: String

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [text], applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
