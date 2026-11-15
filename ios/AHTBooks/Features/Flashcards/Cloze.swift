import Foundation

public struct Cloze {
    // Matches {{c1::answer}} or {{c1::answer::hint}}
    private static let regex: NSRegularExpression? = {
        try? NSRegularExpression(pattern: #"\{\{c(\d+)::(.*?)(?:::(.*?))?\}\}"#, options: [.dotMatchesLineSeparators])
    }()
    
    public static func ordinals(in text: String) -> [Int] {
        guard let regex = regex else { return [] }
        let nsString = text as NSString
        let matches = regex.matches(in: text, options: [], range: NSRange(location: 0, length: nsString.length))
        let numbers = matches.compactMap { match -> Int? in
            guard match.numberOfRanges > 1 else { return nil }
            return Int(nsString.substring(with: match.range(at: 1)))
        }
        return Array(Set(numbers)).sorted()
    }
    
    public static func render(text: String, ordinal: Int, revealed: Bool) -> String {
        guard let regex = regex else { return text }
        let nsString = text as NSString
        let matches = regex.matches(in: text, options: [], range: NSRange(location: 0, length: nsString.length))
        
        var result = ""
        var lastLocation = 0
        
        for match in matches {
            let matchRange = match.range
            if matchRange.location > lastLocation {
                result += nsString.substring(with: NSRange(location: lastLocation, length: matchRange.location - lastLocation))
            }
            
            let num = Int(nsString.substring(with: match.range(at: 1))) ?? 1
            let answer = nsString.substring(with: match.range(at: 2))
            let hint = match.numberOfRanges > 3 && match.range(at: 3).location != NSNotFound ? nsString.substring(with: match.range(at: 3)) : ""
            
            if num != ordinal {
                result += answer
            } else if revealed {
                result += "**\(answer)**"
            } else {
                let displayHint = hint.isEmpty ? "..." : hint
                result += "[\(displayHint)]"
            }
            
            lastLocation = matchRange.location + matchRange.length
        }
        
        if lastLocation < nsString.length {
            result += nsString.substring(from: lastLocation)
        }
        
        return result
    }
    
    public static func preview(text: String) -> String {
        guard let regex = regex else { return text }
        let nsString = text as NSString
        let range = NSRange(location: 0, length: nsString.length)
        return regex.stringByReplacingMatches(in: text, options: [], range: range, withTemplate: "[$2]")
    }
    
    public static func wrap(text: String, selectedRange: NSRange) -> String {
        let nsString = text as NSString
        guard selectedRange.location != NSNotFound,
              selectedRange.location + selectedRange.length <= nsString.length,
              selectedRange.length > 0 else {
            return text
        }
        let nextOrd = (ordinals(in: text).max() ?? 0) + 1
        let selected = nsString.substring(with: selectedRange)
        let replacement = "{{c\(nextOrd)::\(selected)}}"
        return nsString.replacingCharacters(in: selectedRange, with: replacement)
    }
}
