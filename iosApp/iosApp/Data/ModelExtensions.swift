import SwiftUI
import shared

// ============================================================
// MEMOSTAMP DESIGN SYSTEM TOKENS (MSColors) - iOS SwiftUI Parity
// ============================================================

struct MSColors {
    static let ink = Color(red: 0.09, green: 0.09, blue: 0.09)         // #171717 - rich dark ink
    static let paper = Color(red: 1.0, green: 0.984, blue: 0.957)      // #FFFBF4 - warm paper bg
    static let cream = Color(red: 0.957, green: 0.922, blue: 0.867)     // #F4EBDD - warm cream card
    static let creamCard = Color(red: 0.957, green: 0.922, blue: 0.867) // #F4EBDD - alias for cream card
    static let stamp = Color(red: 0.847, green: 0.361, blue: 0.29)      // #D85C4A - terracotta postal red
    static let stampDark = Color(red: 0.725, green: 0.263, blue: 0.212) // #B94336 - dark postal red
    static let gold = Color(red: 0.82, green: 0.65, blue: 0.35)         // #D1A559 - vintage star/postmark gold
    static let mint = Color(red: 0.725, green: 0.847, blue: 0.8)        // #B9D8CC - soft mint avatar
    static let yellow = Color(red: 0.957, green: 0.788, blue: 0.365)     // #F4C95D - soft vintage yellow
    static let lavender = Color(red: 0.784, green: 0.757, blue: 0.91)   // #C8C1E8 - soft lavender
    static let lightGrey = Color(red: 0.91, green: 0.886, blue: 0.851)  // #E8E2D9 - border grey
    static let grey = Color(red: 0.549, green: 0.533, blue: 0.506)       // #8C8881 - muted caption grey
    static let white = Color.white
}

typealias SharedCircle = shared.Circle

#if compiler(>=6.0)
extension FeedPost: @retroactive Identifiable {}
extension FeedReply: @retroactive Identifiable {}
extension FeedComment: @retroactive Identifiable {}
extension StampItem: @retroactive Identifiable {}
extension CollectionItem: @retroactive Identifiable {}
extension FriendItem: @retroactive Identifiable {}
extension TradeRequest: @retroactive Identifiable {}
extension SharedCircle: @retroactive Identifiable {}
#else
extension FeedPost: Identifiable {}
extension FeedReply: Identifiable {}
extension FeedComment: Identifiable {}
extension StampItem: Identifiable {}
extension CollectionItem: Identifiable {}
extension FriendItem: Identifiable {}
extension TradeRequest: Identifiable {}
extension SharedCircle: Identifiable {}
#endif


