import SwiftUI

public struct PastelCollectionCard: View {
    public let collection: BookCollection
    public let bookCount: Int
    public var onSelect: () -> Void

    public init(collection: BookCollection, bookCount: Int, onSelect: @escaping () -> Void) {
        self.collection = collection
        self.bookCount = bookCount
        self.onSelect = onSelect
    }
    
    public var body: some View {
        Button(action: onSelect) {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    ZStack {
                        Circle()
                            .fill(Color.white.opacity(0.85))
                            .frame(width: 38, height: 38)
                        Image(systemName: collection.iconName)
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(Color(hex: "#3730A3"))
                    }
                    Spacer()
                    Text("\(bookCount)")
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .foregroundColor(Theme.onPastelSecondary)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Capsule().fill(Color.white.opacity(0.6)))
                }
                
                VStack(alignment: .leading, spacing: 3) {
                    Text(collection.name)
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(Theme.onPastelPrimary)
                        .lineLimit(1)
                    
                    Text(collection.description.isEmpty ? "Collection" : collection.description)
                        .font(.system(size: 12))
                        .foregroundColor(Theme.onPastelSecondary)
                        .lineLimit(1)
                }
            }
            .padding(14)
            .frame(width: 160, height: 110)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(Color(hex: collection.pastelColorHex))
            )
            .shadow(color: Color.black.opacity(0.04), radius: 4, x: 0, y: 2)
        }
        .buttonStyle(PlainButtonStyle())
    }
}
