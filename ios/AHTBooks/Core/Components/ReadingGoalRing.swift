import SwiftUI

public struct ReadingGoalRing: View {
    public var progress: Double
    public var minutes: Int
    public var goalMinutes: Int
    public var size: CGFloat = 44
    public var lineWidth: CGFloat = 4.5
    
    public init(progress: Double, minutes: Int, goalMinutes: Int, size: CGFloat = 44, lineWidth: CGFloat = 4.5) {
        self.progress = progress
        self.minutes = minutes
        self.goalMinutes = goalMinutes
        self.size = size
        self.lineWidth = lineWidth
    }
    
    public var body: some View {
        ZStack {
            Circle()
                .stroke(Theme.brandPurpleSoft, lineWidth: lineWidth)
            
            Circle()
                .trim(from: 0.0, to: CGFloat(min(1.0, max(0.0, progress))))
                .stroke(
                    LinearGradient(
                        colors: [Theme.brandPurple, Theme.brandPurpleLight],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    style: StrokeStyle(lineWidth: lineWidth, lineCap: .round)
                )
                .rotationEffect(.degrees(-90))
                .animation(.easeOut(duration: 0.5), value: progress)
            
            VStack(spacing: 0) {
                Text("\(minutes)")
                    .font(.system(size: size * 0.32, weight: .bold, design: .rounded))
                    .foregroundColor(Theme.appTextPrimary)
                Text("m")
                    .font(.system(size: size * 0.2, weight: .semibold, design: .rounded))
                    .foregroundColor(Theme.appTextSecondary)
            }
        }
        .frame(width: size, height: size)
    }
}
