import Foundation

public struct Scheduler {
    public static let shared = Scheduler()
    
    private let learningStepsMin: [Float] = [1.0, 10.0]
    private let relearningStepMin: Float = 10.0
    private let graduatingDays: Float = 1.0
    private let easyDays: Float = 4.0
    private let minEase: Float = 1.3
    private let hardMultiplier: Float = 1.2
    private let easyBonus: Float = 1.3
    private let lapseMultiplier: Float = 0.5
    private let fuzzFactor: Float = 0.05
    private let dayRolloverHour: Int = 4
    
    public init() {}
    
    public func answer(schedule: CardSchedule, rating: Rating, now: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) -> CardSchedule {
        switch schedule.state {
        case .NEW, .LEARNING:
            return learning(schedule: schedule, rating: rating, now: now)
        case .RELEARNING:
            return relearning(schedule: schedule, rating: rating, now: now)
        case .REVIEW:
            return review(schedule: schedule, rating: rating, now: now)
        }
    }
    
    public func preview(schedule: CardSchedule, now: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) -> [Rating: String] {
        var results: [Rating: String] = [:]
        for rating in Rating.allCases {
            let next = answer(schedule: schedule, rating: rating, now: now)
            if next.state == .REVIEW {
                let days = max(1, Int64(round(next.intervalDays)))
                results[rating] = label(ms: days * 86_400_000)
            } else {
                results[rating] = label(ms: max(0, next.dueAt - now))
            }
        }
        return results
    }
    
    private func learning(schedule: CardSchedule, rating: Rating, now: Int64) -> CardSchedule {
        var s = schedule
        let step = (s.state == .NEW) ? 0 : s.step
        s.reps += 1
        
        switch rating {
        case .AGAIN:
            s.state = .LEARNING
            s.step = 0
            s.dueAt = now + minutesToMs(learningStepsMin[0])
            return s
        case .HARD:
            let delay: Float
            if step == 0 && learningStepsMin.count > 1 {
                delay = (learningStepsMin[0] + learningStepsMin[1]) / 2.0
            } else {
                delay = learningStepsMin[min(step, learningStepsMin.count - 1)]
            }
            s.state = .LEARNING
            s.step = step
            s.dueAt = now + minutesToMs(delay)
            return s
        case .GOOD:
            if step + 1 < learningStepsMin.count {
                s.state = .LEARNING
                s.step = step + 1
                s.dueAt = now + minutesToMs(learningStepsMin[step + 1])
                return s
            } else {
                return graduate(schedule: s, days: graduatingDays, now: now)
            }
        case .EASY:
            return graduate(schedule: s, days: easyDays, now: now)
        }
    }
    
    private func relearning(schedule: CardSchedule, rating: Rating, now: Int64) -> CardSchedule {
        var s = schedule
        s.reps += 1
        
        switch rating {
        case .AGAIN:
            s.step = 0
            s.dueAt = now + minutesToMs(relearningStepMin)
            return s
        case .HARD:
            s.dueAt = now + minutesToMs(relearningStepMin * 1.5)
            return s
        case .GOOD:
            return graduate(schedule: s, days: max(1.0, s.intervalDays), now: now)
        case .EASY:
            return graduate(schedule: s, days: max(1.0, s.intervalDays) + 1.0, now: now)
        }
    }
    
    private func review(schedule: CardSchedule, rating: Rating, now: Int64) -> CardSchedule {
        var s = schedule
        s.reps += 1
        let interval = max(1.0, s.intervalDays)
        
        if rating == .AGAIN {
            s.state = .RELEARNING
            s.step = 0
            s.ease = max(minEase, s.ease - 0.2)
            s.intervalDays = max(1.0, interval * lapseMultiplier)
            s.dueAt = now + minutesToMs(relearningStepMin)
            s.lapses += 1
            return s
        }
        
        let hard = max(interval + 1, interval * hardMultiplier)
        let good = max(hard + 1, interval * s.ease)
        let easy = max(good + 1, interval * s.ease * easyBonus)
        
        let (days, newEase): (Float, Float) = {
            switch rating {
            case .HARD: return (hard, s.ease - 0.15)
            case .GOOD: return (good, s.ease)
            case .EASY: return (easy, s.ease + 0.15)
            default: return (good, s.ease)
            }
        }()
        
        let fuzzed = fuzz(days: days, cardId: s.cardId, ordinal: s.ordinal, reps: s.reps)
        s.state = .REVIEW
        s.ease = max(minEase, newEase)
        s.intervalDays = fuzzed
        s.dueAt = dayStartAfter(now: now, days: max(1, Int64(round(fuzzed))))
        return s
    }
    
    private func graduate(schedule: CardSchedule, days: Float, now: Int64) -> CardSchedule {
        var s = schedule
        s.state = .REVIEW
        s.step = 0
        s.intervalDays = days
        s.dueAt = dayStartAfter(now: now, days: max(1, Int64(round(days))))
        return s
    }
    
    private func fuzz(days: Float, cardId: String, ordinal: Int, reps: Int) -> Float {
        if days < 3.0 { return days }
        let seed = abs(cardId.hashValue &* 31 &+ ordinal &* 31 &+ reps)
        var rng = SeededGenerator(seed: UInt64(seed))
        let randomFactor = Float.random(in: -1.0...1.0, using: &rng)
        return days * (1.0 + randomFactor * fuzzFactor)
    }
    
    public func dayStartAfter(now: Int64, days: Int64) -> Int64 {
        let base = reviewDayStart(now: now)
        return base + days * 86_400_000
    }
    
    public func reviewDayStart(now: Int64) -> Int64 {
        var calendar = Calendar.current
        calendar.timeZone = TimeZone.current
        let date = Date(timeIntervalSince1970: Double(now) / 1000.0)
        var components = calendar.dateComponents([.year, .month, .day, .hour], from: date)
        
        if (components.hour ?? 0) < dayRolloverHour {
            components.day = (components.day ?? 1) - 1
        }
        components.hour = dayRolloverHour
        components.minute = 0
        components.second = 0
        components.nanosecond = 0
        
        let rolloverDate = calendar.date(from: components) ?? date
        return Int64(rolloverDate.timeIntervalSince1970 * 1000)
    }
    
    public func isDue(schedule: CardSchedule, now: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) -> Bool {
        if schedule.state == .NEW { return true }
        let cutoff = dayStartAfter(now: now, days: 1)
        return schedule.dueAt <= cutoff
    }
    
    private func minutesToMs(_ min: Float) -> Int64 {
        Int64(min * 60 * 1000)
    }
    
    private func label(ms: Int64) -> String {
        let sec = ms / 1000
        if sec < 60 { return "<1m" }
        let min = sec / 60
        if min < 60 { return "\(min)m" }
        let hours = min / 60
        if hours < 24 { return "\(hours)h" }
        let days = hours / 24
        if days < 30 { return "\(days)d" }
        let months = String(format: "%.1fmo", Double(days) / 30.0)
        return months
    }
}

// Deterministic seedable RNG for stable fuzzing
private struct SeededGenerator: RandomNumberGenerator {
    private var state: UInt64
    init(seed: UInt64) { self.state = seed != 0 ? seed : 123456789 }
    mutating func next() -> UInt64 {
        state = state &* 6364136223846793005 &+ 1442695040888963407
        return state
    }
}
