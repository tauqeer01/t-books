import SwiftUI

public struct BookFlowEmblem: View {
    public var size: CGFloat = 36
    
    public init(size: CGFloat = 36) {
        self.size = size
    }
    
    public var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.28, style: .continuous)
                .fill(
                    LinearGradient(
                        colors: [Theme.brandPurpleLight, Theme.brandPurple, Theme.brandPurpleDark],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .frame(width: size, height: size)
                .shadow(color: Theme.brandPurple.opacity(0.35), radius: size * 0.15, x: 0, y: size * 0.08)
            
            Image(systemName: "book.pages.fill")
                .font(.system(size: size * 0.52, weight: .bold))
                .foregroundColor(.white)
        }
    }
}
