import SwiftUI
import UIKit

/**
 Кадрирование фото ребёнка — лист, а не отдельный экран. Фото из файла `source` лежит под кругом
 аватара; «Готово» отдаёт кадр JPEG 512×512. Сначала кадр — весь короткий край фото по центру:
 для фото, где малыш в середине, хватает «Готово».
 */
struct PhotoCropSheet: View {
    @Environment(\.babyLogTheme) private var theme
    @Binding var isPresented: Bool
    let source: String
    let onDismiss: () -> Void
    let onDone: (ChildPhoto) -> Void
    @State private var loaded: Result<UIImage, Error>?
    @State private var frame = CropFrame()

    var body: some View {
        let image = loaded.flatMap { try? $0.get() }
        let windowHeight = UIScreen.main.bounds.height
        let side = min(max(windowHeight - sheetChrome, minCropSide), maxCropSide)
        AppSheet(
            isPresented: $isPresented,
            onDismiss: onDismiss,
            title: "Фото",
            icon: "ic_photo_camera",
            confirm: SheetAction(label: "Готово", enabled: image != nil) {
                if let image { onDone(cropPhoto(source: image, square: frame.square(width: image.pixelWidth, height: image.pixelHeight))) }
            }
        ) { _ in
            HStack {
                Spacer()
                Group {
                    if let image {
                        CropArea(image: image, frame: { frame }, onFrameChange: { frame = $0 })
                    } else if let loaded, case .failure = loaded {
                        Text("Не удалось открыть фото")
                            .foregroundStyle(theme.colors.error)
                    } else {
                        ProgressView()
                    }
                }
                .frame(maxWidth: side)
                .aspectRatio(1, contentMode: .fit)
                Spacer()
            }
            .frame(maxWidth: .infinity)
            Text("Двигайте фото пальцем, увеличивайте двумя")
                .appTextStyle(theme.typography.bodySmall)
                .foregroundStyle(theme.colors.onSurfaceVariant)
                .frame(maxWidth: .infinity, alignment: .center)
        }
        .task(id: source) {
            loaded = nil
            frame = CropFrame()
            do {
                let image = try await Task.detached(priority: .userInitiated) {
                    try loadUpright(path: source, maxSide: cropSourceMax)
                }.value
                loaded = .success(image)
            } catch {
                loaded = .failure(error)
            }
        }
    }
}

/**
 Окно кадрирования: `image` по кадру `frame`, вне круга — фон листа полупрозрачно. Жест сдвигает и
 увеличивает фото, а не лист: события, которые взяло окно, лист и прокрутка уже не получают.
 */
struct CropArea: View {
    @Environment(\.babyLogTheme) private var theme
    let image: UIImage
    let frame: () -> CropFrame
    let onFrameChange: (CropFrame) -> Void

    var body: some View {
        Canvas { context, size in
            let current = frame()
            let square = current.square(width: image.pixelWidth, height: image.pixelHeight)
            let scale = size.width / square.side
            let destination = CGRect(
                x: -square.left * scale,
                y: -square.top * scale,
                width: CGFloat(image.pixelWidth) * scale,
                height: CGFloat(image.pixelHeight) * scale
            )
            context.draw(Image(uiImage: image), in: destination)
            var outside = Path(CGRect(origin: .zero, size: size))
            outside.addPath(Path(ellipseIn: CGRect(origin: .zero, size: size)))
            context.fill(outside, with: .color(theme.colors.surfaceContainerHigh.opacity(0.7)), style: FillStyle(eoFill: true))
        }
        .overlay(CropGestureView(image: image, frame: frame, onFrameChange: onFrameChange))
        .clipped()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Кадр фото")
        .accessibilityAction(named: "Увеличить") { accessibilityTransform(pan: .zero, zoom: 1.25) }
        .accessibilityAction(named: "Уменьшить") { accessibilityTransform(pan: .zero, zoom: 0.8) }
        .accessibilityAction(named: "Сдвинуть фото вверх") { accessibilityTransform(pan: CGPoint(x: 0, y: -0.25), zoom: 1) }
        .accessibilityAction(named: "Сдвинуть фото вниз") { accessibilityTransform(pan: CGPoint(x: 0, y: 0.25), zoom: 1) }
        .accessibilityAction(named: "Сдвинуть фото влево") { accessibilityTransform(pan: CGPoint(x: -0.25, y: 0), zoom: 1) }
        .accessibilityAction(named: "Сдвинуть фото вправо") { accessibilityTransform(pan: CGPoint(x: 0.25, y: 0), zoom: 1) }
    }

    private func accessibilityTransform(pan: CGPoint, zoom: CGFloat) {
        onFrameChange(frame().transformed(
            width: image.pixelWidth,
            height: image.pixelHeight,
            viewport: 1,
            centroid: CGPoint(x: 0.5, y: 0.5),
            pan: pan,
            zoomChange: zoom
        ))
    }
}

/** Квадрат `square` из `source` — в JPEG `ChildPhoto.size`×`ChildPhoto.size`; маленький кадр растягивается. */
func cropPhoto(source: UIImage, square: CropSquare) -> ChildPhoto {
    let normalized = source.resizedPixels(width: source.pixelWidth, height: source.pixelHeight)
    let side = min(max(Int(square.side.rounded()), 1), min(normalized.pixelWidth, normalized.pixelHeight))
    let left = min(max(Int(square.left.rounded()), 0), normalized.pixelWidth - side)
    let top = min(max(Int(square.top.rounded()), 0), normalized.pixelHeight - side)
    guard let cropped = normalized.cgImage?.cropping(to: CGRect(x: left, y: top, width: side, height: side)) else {
        return ChildPhoto(jpeg: Data())
    }
    let fitted = UIImage(cgImage: cropped).scaledToFit(ChildPhoto.size)
    let sized = fitted.pixelWidth == ChildPhoto.size ? fitted : fitted.resizedPixels(width: ChildPhoto.size, height: ChildPhoto.size)
    return ChildPhoto(jpeg: sized.toJpeg(quality: jpegQuality))
}

private struct CropGestureView: UIViewRepresentable {
    let image: UIImage
    let frame: () -> CropFrame
    let onFrameChange: (CropFrame) -> Void

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .clear
        let pan = UIPanGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.pan(_:)))
        pan.minimumNumberOfTouches = 1
        pan.maximumNumberOfTouches = 2
        pan.delegate = context.coordinator
        let pinch = UIPinchGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.pinch(_:)))
        pinch.delegate = context.coordinator
        view.addGestureRecognizer(pan)
        view.addGestureRecognizer(pinch)
        return view
    }

    func updateUIView(_ view: UIView, context: Context) {
        context.coordinator.image = image
        context.coordinator.frame = frame
        context.coordinator.onFrameChange = onFrameChange
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(image: image, frame: frame, onFrameChange: onFrameChange)
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var image: UIImage
        var frame: () -> CropFrame
        var onFrameChange: (CropFrame) -> Void

        init(image: UIImage, frame: @escaping () -> CropFrame, onFrameChange: @escaping (CropFrame) -> Void) {
            self.image = image
            self.frame = frame
            self.onFrameChange = onFrameChange
        }

        @objc func pan(_ gesture: UIPanGestureRecognizer) {
            guard gesture.state == .changed, let view = gesture.view else { return }
            apply(gesture: gesture, pan: gesture.translation(in: view), zoom: 1)
            gesture.setTranslation(.zero, in: view)
        }

        @objc func pinch(_ gesture: UIPinchGestureRecognizer) {
            guard gesture.state == .changed, gesture.view != nil else { return }
            apply(gesture: gesture, pan: .zero, zoom: gesture.scale)
            gesture.scale = 1
        }

        private func apply(gesture: UIGestureRecognizer, pan: CGPoint, zoom: CGFloat) {
            guard let view = gesture.view, view.bounds.width > 0 else { return }
            onFrameChange(frame().transformed(
                width: image.pixelWidth,
                height: image.pixelHeight,
                viewport: view.bounds.width,
                centroid: gesture.location(in: view),
                pan: pan,
                zoomChange: zoom
            ))
        }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
            true
        }
    }
}

/** Длинная сторона фото в окне кадрирования: при `maxCropZoom` в кадре остаётся ~512 px. */
private let cropSourceMax = 2048
private let jpegQuality = 90
/** Всё в листе, кроме окна: ручка, заголовок, подсказка, кнопки и отступы. */
private let sheetChrome: CGFloat = 240
private let minCropSide: CGFloat = 160
private let maxCropSide: CGFloat = 360
