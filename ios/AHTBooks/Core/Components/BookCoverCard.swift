import SwiftUI

public struct BookCoverCard: View {
    public let book: Book
    public var onOpen: () -> Void
    public var onFavoriteToggle: (() -> Void)?
    
    public init(book: Book, onOpen: @escaping () -> Void, onFavoriteToggle: (() -> Void)? = nil) {
        self.book = book
        self.onOpen = onOpen
        self.onFavoriteToggle = onFavoriteToggle
    }
    
    public var body: some View {
        Button(action: onOpen) {
            VStack(alignment: .leading, spacing: 8) {
                // Cover Thumbnail
                ZStack(alignment: .topTrailing) {
                    if let thumb = book.thumbnailPath, let uiImage = UIImage(contentsOfFile: thumb) {
                        Image(uiImage: uiImage)
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(height: 160)
                            .clipped()
                            .cornerRadius(10)
                            .shadow(color: Color.black.opacity(0.08), radius: 6, x: 0, y: 3)
                    } else {
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(
                                LinearGradient(
                                    colors: [Color(hex: book.coverColorHex), Color(hex: book.coverColorHex).opacity(0.75)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                            .frame(height: 160)
                            .overlay(
                                VStack(spacing: 8) {
                                    Image(systemName: "doc.text.fill")
                                        .font(.system(size: 32))
                                        .foregroundColor(.white.opacity(0.85))
                                    Text(book.title)
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundColor(.white)
                                        .multilineTextAlignment(.center)
                                        .lineLimit(3)
                                        .padding(.horizontal, 10)
                                }
                            )
                            .shadow(color: Color.black.opacity(0.08), radius: 6, x: 0, y: 3)
                    }
                    
                    if book.isFavorite {
                        Image(systemName: "star.fill")
                            .font(.system(size: 14))
                            .foregroundColor(.yellow)
                            .padding(6)
                            .background(Circle().fill(Color.black.opacity(0.4)))
                            .padding(6)
                    }
                }
                
                // Progress Bar
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule()
                            .fill(Theme.appDivider)
                            .frame(height: 4)
                        Capsule()
                            .fill(Theme.brandPurple)
                            .frame(width: max(4, geo.size.width * CGFloat(book.readingProgress)), height: 4)
                    }
                }
                .frame(height: 4)
                
                // Title & Author
                VStack(alignment: .leading, spacing: 2) {
                    Text(book.title)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(Theme.appTextPrimary)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                    
                    Text(book.author)
                        .font(.system(size: 12))
                        .foregroundColor(Theme.appTextSecondary)
                        .lineLimit(1)
                }
            }
        }
        .buttonStyle(PlainButtonStyle())
    }
}
