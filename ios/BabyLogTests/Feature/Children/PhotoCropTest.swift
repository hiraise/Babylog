import CoreGraphics
import Testing
@testable import BabyLog

/** Кадр фото в круге: квадрат всегда внутри фото, точка под пальцами остаётся под ними. */
struct PhotoCropTest {
    @Test func initialFrameIsTheShortSideInTheMiddle() {
        #expect(CropFrame().square(width: 4000, height: 3000) == CropSquare(left: 500, top: 0, side: 3000))
        #expect(CropFrame().square(width: 3000, height: 4000) == CropSquare(left: 0, top: 500, side: 3000))
    }

    @Test func panMovesTheFrameOppositeToTheFingersAndStopsAtTheEdge() {
        let moved = CropFrame().transformed(
            width: 4000,
            height: 3000,
            viewport: 300,
            centroid: CGPoint(x: 150, y: 150),
            pan: CGPoint(x: 20, y: 0),
            zoomChange: 1
        )
        #expect(moved.square(width: 4000, height: 3000) == CropSquare(left: 300, top: 0, side: 3000))

        let atEdge = CropFrame().transformed(
            width: 4000,
            height: 3000,
            viewport: 300,
            centroid: CGPoint(x: 150, y: 150),
            pan: CGPoint(x: 200, y: 50),
            zoomChange: 1
        )
        #expect(atEdge.square(width: 4000, height: 3000) == CropSquare(left: 0, top: 0, side: 3000))
    }

    @Test func zoomKeepsThePointUnderTheFingersInPlace() {
        let zoomed = CropFrame().transformed(
            width: 4000,
            height: 3000,
            viewport: 300,
            centroid: CGPoint(x: 75, y: 75),
            pan: .zero,
            zoomChange: 2
        )
        #expect(zoomed.zoom == 2)
        let square = zoomed.square(width: 4000, height: 3000)
        #expect(abs(square.left + 75 * square.side / 300 - 1250) < 0.01)
        #expect(abs(square.top + 75 * square.side / 300 - 750) < 0.01)
    }

    @Test func zoomStaysBetweenWholeShortSideAndTheLimit() {
        let frame = CropFrame()
        #expect(frame.transformed(
            width: 400,
            height: 300,
            viewport: 1,
            centroid: CGPoint(x: 0.5, y: 0.5),
            pan: .zero,
            zoomChange: 0.5
        ).zoom == 1)
        #expect(frame.transformed(
            width: 400,
            height: 300,
            viewport: 1,
            centroid: CGPoint(x: 0.5, y: 0.5),
            pan: .zero,
            zoomChange: 10
        ).zoom == maxCropZoom)
    }
}
