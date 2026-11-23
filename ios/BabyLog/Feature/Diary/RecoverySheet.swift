import SwiftUI

/**
 Лист после восстановления повреждённой базы (ADR-0033): что случилось и что делать. Лист, а не снекбар:
 записи после копии придётся внести заново. «Импорт из файла» вернёт их из свежего экспорта.
 */
struct DatabaseRecoveredSheet: View {
    @Binding var isPresented: Bool
    let recovery: DatabaseRecovery
    let onDismiss: () -> Void
    let onImport: () -> Void

    var body: some View {
        AppSheet(
            isPresented: $isPresented,
            onDismiss: onDismiss,
            title: recoveryTitle(recovery),
            confirm: SheetAction(label: "Понятно", onClick: onDismiss),
            secondary: SheetAction(label: "Импорт из файла", onClick: onImport)
        ) { _ in
            DatabaseRecoveredSheetContent(recovery: recovery)
        }
    }
}

struct DatabaseRecoveredSheetContent: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.timeZone) private var timeZone
    let recovery: DatabaseRecovery

    var body: some View {
        Text(recoveryText(recovery, timeZone: timeZone))
            .appTextStyle(theme.typography.bodyMedium)
    }
}

func recoveryTitle(_ recovery: DatabaseRecovery) -> String {
    recovery.backupTime == nil ? "Дневник начат заново" : "Дневник восстановлен"
}

func recoveryText(_ recovery: DatabaseRecovery, timeZone: TimeZone = .current) -> String {
    let cause = "Файл дневника в памяти телефона повредился — так бывает при сбое памяти или внезапном выключении."
    if let backup = recovery.backupTime {
        return "\(cause) Записи восстановлены из копии, которую приложение сделало \(formatDate(backup, timeZone: timeZone)) в \(formatClock(backup, timeZone: timeZone)). " +
            "Что записали позже, внесите заново или импортируйте из файла, если делали экспорт."
    }
    return "\(cause) Копии ещё не было: приложение делает её раз в сутки. Если делали экспорт в файл, импортируйте его."
}
