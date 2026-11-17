import SwiftUI

private let ringStart = Theme.brandPurple
private let ringEnd = Color(hex: "#C084FC")
private let ringDone = Color(hex: "#10B981")
private let streakOrange = Color(hex: "#F97316")

/// Apple Books–style progress arc: a soft track with a gradient arc that turns green when the goal is met.
struct GoalArc<Content: View>: View {
    var progress: Double
    var lineWidth: CGFloat
    var trackColor: Color = Theme.brandPurpleSoft
    @ViewBuilder var content: () -> Content

    var body: some View {
        let clamped = min(1.0, max(0.0, progress))
        let done = progress >= 1.0
        ZStack {
            Circle()
                .stroke(trackColor, lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: CGFloat(clamped))
                .stroke(
                    done
                        ? AnyShapeStyle(ringDone)
                        : AnyShapeStyle(AngularGradient(colors: [ringStart, ringEnd, ringStart], center: .center)),
                    style: StrokeStyle(lineWidth: lineWidth, lineCap: .round)
                )
                .rotationEffect(.degrees(-90))
                .opacity(clamped > 0 ? 1 : 0)
                .animation(.easeOut(duration: 0.7), value: clamped)
            content()
        }
        .padding(lineWidth / 2)
    }
}

/// Header-sized ring showing today's minutes; a check once the goal is met.
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
        GoalArc(progress: progress, lineWidth: lineWidth) {
            if progress >= 1.0 {
                Image(systemName: "checkmark")
                    .font(.system(size: size * 0.36, weight: .bold))
                    .foregroundColor(ringDone)
            } else {
                Text("\(minutes)")
                    .font(.system(size: size * 0.32, weight: .bold, design: .rounded))
                    .foregroundColor(Theme.appTextPrimary)
            }
        }
        .frame(width: size, height: size)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Reading goal: \(minutes) of \(goalMinutes) minutes today")
    }
}

/// Today's Reading sheet: big ring, streak and week summary, the last seven days, and a goal stepper.
public struct ReadingGoalSheet: View {
    public var stats: ReadingStats
    public var onGoalChange: (Int) -> Void
    @Environment(\.dismiss) private var dismiss

    public init(stats: ReadingStats, onGoalChange: @escaping (Int) -> Void) {
        self.stats = stats
        self.onGoalChange = onGoalChange
    }

    public var body: some View {
        VStack(spacing: 0) {
            header
            Divider().overlay(Theme.appDivider)

            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 20) {
                    bigRing
                        .padding(.top, 16)

                    HStack(spacing: 10) {
                        StatTile(
                            label: "Streak",
                            value: stats.currentStreakDays == 1 ? "1 day" : "\(stats.currentStreakDays) days",
                            icon: "flame.fill",
                            tint: streakOrange
                        )
                        StatTile(
                            label: "This week",
                            value: "\(stats.secondsThisWeek / 60) min",
                            icon: "timer",
                            tint: Theme.brandPurple
                        )
                    }

                    WeekBars(stats: stats)

                    goalStepper
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 20)
            }
        }
        .background(Theme.appSurface.ignoresSafeArea())
    }

    private var header: some View {
        HStack(alignment: .center, spacing: 12) {
            Image(systemName: "timer")
                .font(.system(size: 22, weight: .semibold))
                .foregroundColor(Theme.brandPurple)
            VStack(alignment: .leading, spacing: 2) {
                Text("Today's Reading")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(Theme.appTextPrimary)
                Text(stats.isGoalMet ? "Goal complete. Nice work!" : "Time in the reader counts toward your daily goal")
                    .font(.system(size: 12))
                    .foregroundColor(Theme.appTextSecondary)
                    .lineLimit(2)
            }
            Spacer(minLength: 8)
            Button(action: { dismiss() }) {
                Image(systemName: "xmark")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(Theme.appTextSecondary)
                    .frame(width: 36, height: 36)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel("Close")
        }
        .padding(.horizontal, 20)
        .padding(.top, 24)
        .padding(.bottom, 12)
    }

    private var bigRing: some View {
        GoalArc(progress: stats.goalProgress, lineWidth: 14, trackColor: Theme.appSurfaceVariant) {
            VStack(spacing: 2) {
                Text(String(format: "%d:%02d", stats.secondsReadToday / 60, stats.secondsReadToday % 60))
                    .font(.system(size: 34, weight: .bold))
                    .monospacedDigit()
                    .foregroundColor(Theme.appTextPrimary)
                Text("of \(stats.dailyGoalMinutes) min")
                    .font(.system(size: 13))
                    .foregroundColor(Theme.appTextSecondary)
            }
        }
        .frame(width: 168, height: 168)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(stats.minutesReadToday) of \(stats.dailyGoalMinutes) minutes read today")
    }

    private var goalStepper: some View {
        let range = ReadingStats.goalRangeMinutes
        let goal = stats.dailyGoalMinutes
        return HStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Daily goal")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(Theme.appTextPrimary)
                Text("\(goal) minutes a day")
                    .font(.system(size: 12))
                    .foregroundColor(Theme.appTextSecondary)
            }
            Spacer()
            StepButton(systemName: "minus", label: "Decrease daily goal", enabled: goal > range.lowerBound) {
                onGoalChange(max(range.lowerBound, goal - 5))
            }
            Text("\(goal)")
                .font(.system(size: 18, weight: .bold))
                .monospacedDigit()
                .foregroundColor(Theme.appTextPrimary)
                .frame(width: 44)
            StepButton(systemName: "plus", label: "Increase daily goal", enabled: goal < range.upperBound) {
                onGoalChange(min(range.upperBound, goal + 5))
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Theme.appSurfaceVariant))
    }
}

private struct StepButton: View {
    let systemName: String
    let label: String
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(enabled ? Theme.appTextPrimary : Theme.appTextSecondary.opacity(0.4))
                .frame(width: 40, height: 40)
                .background(Circle().fill(Theme.appSurface))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityLabel(label)
    }
}

private struct StatTile: View {
    let label: String
    let value: String
    let icon: String
    let tint: Color

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(tint)
                .frame(width: 20)
            VStack(alignment: .leading, spacing: 1) {
                Text(value)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(Theme.appTextPrimary)
                Text(label)
                    .font(.system(size: 11))
                    .foregroundColor(Theme.appTextSecondary)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity)
        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Theme.appSurfaceVariant))
        .accessibilityElement(children: .combine)
    }
}

/// Seven slim bars, one per day; days that met the goal are filled, today is labelled in brand color.
private struct WeekBars: View {
    let stats: ReadingStats
    private let barAreaHeight: CGFloat = 74

    var body: some View {
        let days = stats.lastSevenDays
        let goal = max(stats.goalSeconds, 1)
        let tallest = max(goal, days.map(\.seconds).max() ?? 0)
        let calendar = Calendar.current
        let symbols = calendar.veryShortStandaloneWeekdaySymbols

        HStack(alignment: .bottom, spacing: 0) {
            ForEach(Array(days.enumerated()), id: \.element.id) { index, day in
                let isToday = index == days.count - 1
                let met = day.seconds >= goal
                let fraction = min(1.0, max(0.04, Double(day.seconds) / Double(tallest)))
                VStack(spacing: 6) {
                    RoundedRectangle(cornerRadius: 7, style: .continuous)
                        .fill(met ? Theme.brandPurple : Theme.brandPurple.opacity(0.22))
                        .frame(width: 14, height: barAreaHeight * fraction)
                        .frame(height: barAreaHeight, alignment: .bottom)
                    Text(symbols[calendar.component(.weekday, from: day.date) - 1])
                        .font(.system(size: 11, weight: isToday ? .bold : .regular))
                        .foregroundColor(isToday ? Theme.brandPurple : Theme.appTextSecondary)
                }
                .frame(maxWidth: .infinity)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(day.date.formatted(.dateTime.weekday(.wide))): \(day.minutes) minutes")
            }
        }
        .frame(height: 96, alignment: .bottom)
        .animation(.easeOut(duration: 0.5), value: stats.dailyGoalMinutes)
    }
}
