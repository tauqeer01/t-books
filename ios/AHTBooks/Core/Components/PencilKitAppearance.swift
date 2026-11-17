import PencilKit
import UIKit

extension PKDrawing {
    /// Renders the ink in the colors it was drawn with.
    /// `image(from:scale:)` follows the current trait collection, so in dark mode PencilKit would
    /// lighten dark ink (black turns white) in saved signatures and exported pages.
    func trueColorImage(from rect: CGRect, scale: CGFloat) -> UIImage {
        var image = UIImage()
        UITraitCollection(userInterfaceStyle: .light).performAsCurrent {
            image = self.image(from: rect, scale: scale)
        }
        return image
    }
}
