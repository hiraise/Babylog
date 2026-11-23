import Observation
import SwiftUI

/** Метки в листе можно сохранить строкой через «;». */
extension Array where Element == String {
    func packed() -> String { joined(separator: ";") }
}

extension String {
    func unpacked() -> [String] { split(separator: ";").map(String.init).filter { !$0.isEmpty } }
}

@MainActor @Observable final class TagPickerModel {
    let title: String
    let options: [String]
    var selected: [String]
    let addLabel: String
    let other: [String]
    let otherTitle: String
    let maxLength: Int
    @ObservationIgnored let normalize: (String) -> String?
    var adding = false
    var draft = ""

    init(
        title: String,
        options: [String],
        selected: [String],
        addLabel: String,
        other: [String] = [],
        otherTitle: String = "",
        normalize: @escaping (String) -> String? = normalizeTag,
        maxLength: Int = maxTagLength
    ) {
        self.title = title
        self.options = options
        self.selected = selected
        self.addLabel = addLabel
        self.other = other
        self.otherTitle = otherTitle
        self.normalize = normalize
        self.maxLength = maxLength
    }

    var shownTags: [String] {
        var seen: Set<String> = []
        return (options + selected).filter { seen.insert($0).inserted }
    }

    var hints: [String] {
        let typed = draft.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return other.filter { tag in
            !selected.contains(tag) && (typed.isEmpty || tag.split(separator: " ").contains { $0.hasPrefix(typed) })
        }.prefix(5).map { $0 }
    }

    func toggle(_ tag: String) {
        selected = selected.contains(tag) ? selected.filter { $0 != tag } : selected + [tag]
    }

    func beginAdding() { adding = true }

    func updateDraft(_ text: String) {
        draft = String(text.prefix(maxLength))
    }

    func add() {
        if let tag = normalize(draft), !selected.contains(where: { $0.lowercased() == tag.lowercased() }) {
            selected.append(tag)
        }
        draft = ""
        adding = false
    }

    func pick(_ tag: String) {
        selected.append(tag)
        draft = ""
        adding = false
    }
}

/**
 Чипы множественного выбора; у выбранного — галочка. Выбранное, но не входящее в `options`, тоже
 показано. «+ своя метка» открывает поле: набранное приводится к общему виду и сразу выбирается.
 Над полем — подсказки меток другого типа с отдельной подписью (ADR-0031).
 */
struct TagPicker: View {
    @Environment(\.babyLogTheme) private var theme
    @State private var model: TagPickerModel

    init(model: TagPickerModel) {
        _model = State(initialValue: model)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: theme.spacing.s) {
            Text(model.title).appTextStyle(theme.typography.titleSmall)
            FlowRow(spacing: theme.spacing.s) {
                ForEach(model.shownTags, id: \.self) { tag in
                    ChoiceChip(
                        selected: model.selected.contains(tag),
                        label: tag,
                        icon: model.selected.contains(tag) ? "ic_check" : nil,
                        multiple: true
                    ) {
                        model.toggle(tag)
                    }
                }
                if !model.adding {
                    Button("+ \(model.addLabel)") { model.beginAdding() }
                        .buttonStyle(.plain)
                        .foregroundStyle(theme.colors.primary)
                        .appTextStyle(theme.typography.labelLarge)
                        .frame(minHeight: BabyLogSizes.touchTarget)
                }
            }
            if model.adding {
                if !model.hints.isEmpty {
                    Text(model.otherTitle)
                        .appTextStyle(theme.typography.bodySmall)
                        .foregroundStyle(theme.colors.onSurfaceVariant)
                    FlowRow(spacing: theme.spacing.s) {
                        ForEach(model.hints, id: \.self) { tag in
                            HintChip(label: tag) { model.pick(tag) }
                        }
                    }
                }
                ZStack(alignment: .trailing) {
                    EditorTextField(
                        value: $model.draft,
                        label: model.addLabel.prefix(1).uppercased() + model.addLabel.dropFirst(),
                        autofocus: true,
                        onSubmit: model.add
                    )
                    .onChange(of: model.draft) { _, text in model.updateDraft(text) }
                    Button { model.add() } label: {
                        AppIcon("ic_check", contentDescription: "Добавить", tint: theme.colors.primary)
                            .frame(width: BabyLogSizes.touchTarget, height: BabyLogSizes.touchTarget)
                    }
                    .buttonStyle(.plain)
                    .padding(.trailing, theme.spacing.xs)
                }
            }
        }
    }
}
