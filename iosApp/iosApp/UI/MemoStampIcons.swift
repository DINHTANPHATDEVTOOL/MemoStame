import SwiftUI

/**
 * Platform-neutral semantic icon vocabulary for MemoStamp (iOS).
 * Resolves semantic icon keys into native SF Symbols or custom SwiftUI vectors.
 *
 * Requirements:
 * - Scalable, tintable vector iconography
 * - Dark Mode safe and Dynamic Type layout safe
 * - No emoji fallback for first-party controls
 */
public enum MemoStampIconKey: String, CaseIterable {
    // Platform & Actions
    case location = "location"
    case search = "search"
    case camera = "camera"
    case gallery = "gallery"
    case share = "share"
    case favorite = "favorite"
    case favoriteFilled = "favorite_filled"
    case comment = "comment"
    case chat = "chat"
    case settings = "settings"
    case notification = "notification"
    case lock = "lock"
    case privacy = "privacy"
    case delete = "delete"
    case edit = "edit"
    case check = "check"
    case retry = "retry"
    case success = "success"
    case warning = "warning"
    case error = "error"
    case info = "info"
    case cloud = "cloud"
    case calendar = "calendar"
    case map = "map"
    case qrCode = "qr_code"
    case report = "report"
    case block = "block"
    case close = "close"
    case back = "back"
    case filter = "filter"
    case more = "more"

    // Postal & MemoStamp Identity
    case stamp = "stamp"
    case postmark = "postmark"
    case mail = "mail"
    case replyStamp = "reply_stamp"
    case albumBook = "album_book"
    case collection = "collection"
    case passport = "passport"
    case trade = "trade"
    case friends = "friends"
    case profile = "profile"
    case theme = "theme"

    // Categories & Collections
    case travel = "travel"
    case cafe = "cafe"
    case beach = "beach"
    case education = "education"
    case nature = "nature"
    case heart = "heart"
    case art = "art"
    case special = "special"
    case flower = "flower"
    case food = "food"
    case lifestyle = "lifestyle"
    case celebration = "celebration"

    // Moods
    case happy = "happy"
    case love = "love"
    case chill = "chill"
    case excited = "excited"
    case nostalgic = "nostalgic"
    case peaceful = "peaceful"

    public var key: String { rawValue }

    public static func fromKey(_ raw: String?) -> MemoStampIconKey? {
        guard let clean = raw?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() else { return nil }
        return MemoStampIconKey(rawValue: clean)
    }

    public static func isKnown(_ raw: String?) -> Bool {
        return fromKey(raw) != nil
    }
}

/**
 * Deterministic legacy migration mapping for MemoStamp on iOS.
 * Maps legacy emoji representations and string variants to bounded semantic icon keys.
 */
public struct MemoStampLegacyMigration {

    public static func mapLegacyCollectionIcon(_ legacy: String?) -> String {
        guard let raw = legacy?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else {
            return MemoStampIconKey.collection.key
        }

        if let direct = MemoStampIconKey.fromKey(raw) {
            return direct.key
        }

        switch raw {
        case "📁", "folder", "album", "NORMAL": return MemoStampIconKey.collection.key
        case "✈️", "✈", "plane", "flight": return MemoStampIconKey.travel.key
        case "☕", "coffee", "cafe": return MemoStampIconKey.cafe.key
        case "🏖️", "🏖", "beach": return MemoStampIconKey.beach.key
        case "🎓", "graduation", "education": return MemoStampIconKey.education.key
        case "📮", "stamp", "post", "mailbox": return MemoStampIconKey.stamp.key
        case "🏞️", "🏞", "mountain", "nature", "landscape": return MemoStampIconKey.nature.key
        case "📸", "📷", "camera", "photo": return MemoStampIconKey.camera.key
        case "💖", "♡", "❤️", "heart", "love": return MemoStampIconKey.heart.key
        case "🌲", "tree", "forest": return MemoStampIconKey.nature.key
        case "🎨", "art", "palette": return MemoStampIconKey.art.key
        case "👑", "crown", "star", "SERIES": return MemoStampIconKey.special.key
        case "🌸", "✿", "flower", "cherry", "sakura": return MemoStampIconKey.flower.key
        case "🍔", "food", "burger": return MemoStampIconKey.food.key
        case "🌿", "leaf", "plant", "sage", "daily": return MemoStampIconKey.lifestyle.key
        case "🎉", "celebration", "party", "milestone": return MemoStampIconKey.celebration.key
        default: return MemoStampIconKey.collection.key
        }
    }

    public static func mapLegacyMood(_ legacy: String?) -> String {
        guard let raw = legacy?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else {
            return MemoStampIconKey.special.key
        }

        if let direct = MemoStampIconKey.fromKey(raw) {
            switch direct {
            case .happy, .love, .travel, .chill, .excited, .nostalgic, .peaceful, .special:
                return direct.key
            default: break
            }
        }

        let upper = raw.uppercased()
        if raw.contains("😊") || upper.contains("HAPPY") { return MemoStampIconKey.happy.key }
        if raw.contains("❤️") || raw.contains("💖") || upper.contains("LOVE") { return MemoStampIconKey.love.key }
        if raw.contains("✈️") || raw.contains("✈") || upper.contains("TRAVEL") { return MemoStampIconKey.travel.key }
        if raw.contains("☕") || upper.contains("CHILL") || upper.contains("COFFEE") { return MemoStampIconKey.chill.key }
        if raw.contains("🔥") || upper.contains("EXCITED") { return MemoStampIconKey.excited.key }
        if raw.contains("📜") || raw.contains("🕰️") || raw.contains("🕰") || upper.contains("NOSTALGIC") { return MemoStampIconKey.nostalgic.key }
        if raw.contains("🌿") || upper.contains("PEACEFUL") { return MemoStampIconKey.peaceful.key }
        if raw.contains("✨") || raw.contains("⭐") || upper.contains("SPECIAL") { return MemoStampIconKey.special.key }

        return MemoStampIconKey.special.key
    }

    public static func mapLegacyNotification(_ legacy: String?) -> String {
        guard let raw = legacy?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else {
            return MemoStampIconKey.notification.key
        }

        if let direct = MemoStampIconKey.fromKey(raw) {
            return direct.key
        }

        if raw.contains("💬") || raw.caseInsensitiveCompare("chat") == .orderedSame { return MemoStampIconKey.chat.key }
        if raw.contains("🤝") || raw.caseInsensitiveCompare("friend") == .orderedSame { return MemoStampIconKey.friends.key }
        if raw.contains("📮") || raw.caseInsensitiveCompare("stamp") == .orderedSame { return MemoStampIconKey.stamp.key }
        if raw.contains("🎉") || raw.caseInsensitiveCompare("success") == .orderedSame { return MemoStampIconKey.success.key }
        if raw.contains("⚠️") || raw.caseInsensitiveCompare("warning") == .orderedSame { return MemoStampIconKey.warning.key }
        if raw.contains("✉️") || raw.contains("✉") || raw.caseInsensitiveCompare("mail") == .orderedSame { return MemoStampIconKey.mail.key }

        return MemoStampIconKey.notification.key
    }
}

// MARK: - Custom MemoStamp Vector Shapes

/// Perforated postage stamp outline vector shape.
public struct CustomStampVectorShape: Shape {
    public init() {}

    public func path(in rect: CGRect) -> Path {
        var path = Path()
        let w = rect.width
        let h = rect.height
        let teethCountX = 5
        let teethCountY = 5
        let stepX = w / CGFloat(teethCountX)
        let stepY = h / CGFloat(teethCountY)
        let notchR = min(stepX, stepY) * 0.22

        // Outer perforated stamp boundary
        path.move(to: CGPoint(x: 0, y: 0))

        // Top edge with notches
        for i in 0..<teethCountX {
            let startX = CGFloat(i) * stepX
            let midX = startX + stepX * 0.5
            path.addLine(to: CGPoint(x: midX - notchR, y: 0))
            path.addArc(center: CGPoint(x: midX, y: 0), radius: notchR, startAngle: .degrees(180), endAngle: .degrees(0), clockwise: true)
            path.addLine(to: CGPoint(x: CGFloat(i + 1) * stepX, y: 0))
        }

        // Right edge with notches
        for i in 0..<teethCountY {
            let startY = CGFloat(i) * stepY
            let midY = startY + stepY * 0.5
            path.addLine(to: CGPoint(x: w, y: midY - notchR))
            path.addArc(center: CGPoint(x: w, y: midY), radius: notchR, startAngle: .degrees(270), endAngle: .degrees(90), clockwise: true)
            path.addLine(to: CGPoint(x: w, y: CGFloat(i + 1) * stepY))
        }

        // Bottom edge with notches (right to left)
        for i in (0..<teethCountX).reversed() {
            let startX = CGFloat(i + 1) * stepX
            let midX = CGFloat(i) * stepX + stepX * 0.5
            path.addLine(to: CGPoint(x: midX + notchR, y: h))
            path.addArc(center: CGPoint(x: midX, y: h), radius: notchR, startAngle: .degrees(0), endAngle: .degrees(180), clockwise: true)
            path.addLine(to: CGPoint(x: CGFloat(i) * stepX, y: h))
        }

        // Left edge with notches (bottom to top)
        for i in (0..<teethCountY).reversed() {
            let startY = CGFloat(i + 1) * stepY
            let midY = CGFloat(i) * stepY + stepY * 0.5
            path.addLine(to: CGPoint(x: 0, y: midY + notchR))
            path.addArc(center: CGPoint(x: 0, y: midY), radius: notchR, startAngle: .degrees(90), endAngle: .degrees(270), clockwise: true)
            path.addLine(to: CGPoint(x: 0, y: CGFloat(i) * stepY))
        }

        path.closeSubpath()
        return path
    }
}

/// Circular postmark cancellation mark with wave lines.
public struct CustomPostmarkVectorView: View {
    public init() {}

    public var body: some View {
        GeometryReader { geo in
            let s = min(geo.size.width, geo.size.height)
            ZStack {
                // Outer circle
                Circle()
                    .stroke(lineWidth: s * 0.08)
                    .frame(width: s * 0.65, height: s * 0.65)
                    .position(x: s * 0.35, y: s * 0.5)

                // Inner circle
                Circle()
                    .stroke(lineWidth: s * 0.04)
                    .frame(width: s * 0.48, height: s * 0.48)
                    .position(x: s * 0.35, y: s * 0.5)

                // Flight wavy lines on right
                Path { p in
                    let startX = s * 0.68
                    let endX = s * 0.98
                    let y1 = s * 0.36
                    let y2 = s * 0.50
                    let y3 = s * 0.64
                    for y in [y1, y2, y3] {
                        p.move(to: CGPoint(x: startX, y: y))
                        p.addCurve(
                            to: CGPoint(x: endX, y: y),
                            control1: CGPoint(x: startX + (endX - startX) * 0.33, y: y - s * 0.04),
                            control2: CGPoint(x: startX + (endX - startX) * 0.66, y: y + s * 0.04)
                        )
                    }
                }
                .stroke(style: StrokeStyle(lineWidth: s * 0.06, lineCap: .round))
            }
        }
    }
}

/// Reply stamp vector view.
public struct CustomReplyStampVectorView: View {
    public init() {}

    public var body: some View {
        GeometryReader { geo in
            let s = min(geo.size.width, geo.size.height)
            ZStack {
                // Mini stamp top-right
                CustomStampVectorShape()
                    .stroke(lineWidth: s * 0.07)
                    .frame(width: s * 0.52, height: s * 0.52)
                    .position(x: s * 0.68, y: s * 0.32)

                // Curving reply arrow
                Path { p in
                    p.move(to: CGPoint(x: s * 0.12, y: s * 0.58))
                    p.addLine(to: CGPoint(x: s * 0.32, y: s * 0.40))
                    p.addLine(to: CGPoint(x: s * 0.32, y: s * 0.50))
                    p.addQuadCurve(to: CGPoint(x: s * 0.60, y: s * 0.72), control: CGPoint(x: s * 0.50, y: s * 0.52))
                    p.addQuadCurve(to: CGPoint(x: s * 0.32, y: s * 0.62), control: CGPoint(x: s * 0.45, y: s * 0.60))
                    p.addLine(to: CGPoint(x: s * 0.32, y: s * 0.76))
                    p.closeSubpath()
                }
                .fill()
            }
        }
    }
}

// MARK: - MemoStampIcon Component

/// Universal MemoStamp Icon Component for SwiftUI.
/// Resolves a semantic icon key into native SF Symbols or custom vector shapes.
public struct MemoStampIcon: View {
    public let key: String
    public var size: CGFloat? = nil
    public var color: Color? = nil
    public var contentDescription: String? = nil

    public init(key: String, size: CGFloat? = nil, color: Color? = nil, contentDescription: String? = nil) {
        self.key = key
        self.size = size
        self.color = color
        self.contentDescription = contentDescription
    }

    public init(key: MemoStampIconKey, size: CGFloat? = nil, color: Color? = nil, contentDescription: String? = nil) {
        self.key = key.key
        self.size = size
        self.color = color
        self.contentDescription = contentDescription
    }

    public var body: some View {
        let resolved = MemoStampLegacyMigration.mapLegacyCollectionIcon(key)
        let base = iconView(for: resolved)
            .accessibilityLabel(Text(contentDescription ?? resolved))

        Group {
            if let s = size, let c = color {
                base.frame(width: s, height: s).foregroundColor(c)
            } else if let s = size {
                base.frame(width: s, height: s)
            } else if let c = color {
                base.foregroundColor(c)
            } else {
                base
            }
        }
    }

    @ViewBuilder
    private func iconView(for resolvedKey: String) -> some View {
        switch resolvedKey.lowercased() {
        // MemoStamp Identity Vectors
        case "stamp":
            ZStack {
                CustomStampVectorShape()
                    .stroke(lineWidth: 1.5)
                RoundedRectangle(cornerRadius: 1.5)
                    .stroke(lineWidth: 0.8)
                    .padding(3.5)
            }
            .aspectRatio(1, contentMode: .fit)

        case "postmark":
            CustomPostmarkVectorView()
                .aspectRatio(1, contentMode: .fit)

        case "reply_stamp":
            CustomReplyStampVectorView()
                .aspectRatio(1, contentMode: .fit)

        // Platform & Actions via SF Symbols
        case "location":
            Image(systemName: "mappin.and.ellipse")
                .resizable()
                .scaledToFit()
        case "search":
            Image(systemName: "magnifyingglass")
                .resizable()
                .scaledToFit()
        case "camera":
            Image(systemName: "camera")
                .resizable()
                .scaledToFit()
        case "gallery":
            Image(systemName: "photo.on.rectangle")
                .resizable()
                .scaledToFit()
        case "share":
            Image(systemName: "square.and.arrow.up")
                .resizable()
                .scaledToFit()
        case "favorite":
            Image(systemName: "heart")
                .resizable()
                .scaledToFit()
        case "favorite_filled", "heart":
            Image(systemName: "heart.fill")
                .resizable()
                .scaledToFit()
        case "comment":
            Image(systemName: "bubble.left")
                .resizable()
                .scaledToFit()
        case "chat":
            Image(systemName: "message")
                .resizable()
                .scaledToFit()
        case "settings":
            Image(systemName: "gearshape")
                .resizable()
                .scaledToFit()
        case "notification":
            Image(systemName: "bell")
                .resizable()
                .scaledToFit()
        case "lock":
            Image(systemName: "lock")
                .resizable()
                .scaledToFit()
        case "privacy":
            Image(systemName: "shield")
                .resizable()
                .scaledToFit()
        case "delete":
            Image(systemName: "trash")
                .resizable()
                .scaledToFit()
        case "edit":
            Image(systemName: "pencil")
                .resizable()
                .scaledToFit()
        case "check":
            Image(systemName: "checkmark")
                .resizable()
                .scaledToFit()
        case "retry":
            Image(systemName: "arrow.clockwise")
                .resizable()
                .scaledToFit()
        case "success":
            Image(systemName: "checkmark.circle")
                .resizable()
                .scaledToFit()
        case "warning":
            Image(systemName: "exclamationmark.triangle")
                .resizable()
                .scaledToFit()
        case "error":
            Image(systemName: "xmark.circle")
                .resizable()
                .scaledToFit()
        case "info":
            Image(systemName: "info.circle")
                .resizable()
                .scaledToFit()
        case "cloud":
            Image(systemName: "icloud")
                .resizable()
                .scaledToFit()
        case "calendar":
            Image(systemName: "calendar")
                .resizable()
                .scaledToFit()
        case "map":
            Image(systemName: "map")
                .resizable()
                .scaledToFit()
        case "qr_code":
            Image(systemName: "qrcode")
                .resizable()
                .scaledToFit()
        case "report":
            Image(systemName: "flag")
                .resizable()
                .scaledToFit()
        case "block":
            Image(systemName: "hand.raised")
                .resizable()
                .scaledToFit()
        case "close":
            Image(systemName: "xmark")
                .resizable()
                .scaledToFit()
        case "back":
            Image(systemName: "chevron.left")
                .resizable()
                .scaledToFit()
        case "filter":
            Image(systemName: "line.3.horizontal.decrease")
                .resizable()
                .scaledToFit()
        case "more":
            Image(systemName: "ellipsis")
                .resizable()
                .scaledToFit()

        // Postal & Brand
        case "mail":
            Image(systemName: "envelope")
                .resizable()
                .scaledToFit()
        case "album_book":
            Image(systemName: "books.vertical")
                .resizable()
                .scaledToFit()
        case "collection":
            Image(systemName: "folder")
                .resizable()
                .scaledToFit()
        case "passport":
            Image(systemName: "person.text.rectangle")
                .resizable()
                .scaledToFit()
        case "trade":
            Image(systemName: "arrow.2.squarepath")
                .resizable()
                .scaledToFit()
        case "friends":
            Image(systemName: "person.2")
                .resizable()
                .scaledToFit()
        case "profile":
            Image(systemName: "person.crop.circle")
                .resizable()
                .scaledToFit()
        case "theme":
            Image(systemName: "paintpalette")
                .resizable()
                .scaledToFit()

        // Categories
        case "travel":
            Image(systemName: "airplane")
                .resizable()
                .scaledToFit()
        case "cafe":
            Image(systemName: "cup.and.saucer")
                .resizable()
                .scaledToFit()
        case "beach":
            Image(systemName: "beach.umbrella")
                .resizable()
                .scaledToFit()
        case "education":
            Image(systemName: "graduationcap")
                .resizable()
                .scaledToFit()
        case "nature":
            Image(systemName: "leaf")
                .resizable()
                .scaledToFit()
        case "art":
            Image(systemName: "paintpalette")
                .resizable()
                .scaledToFit()
        case "special":
            Image(systemName: "sparkles")
                .resizable()
                .scaledToFit()
        case "flower":
            Image(systemName: "camera.macro")
                .resizable()
                .scaledToFit()
        case "food":
            Image(systemName: "fork.knife")
                .resizable()
                .scaledToFit()
        case "lifestyle":
            Image(systemName: "figure.walk")
                .resizable()
                .scaledToFit()
        case "celebration":
            Image(systemName: "party.popper")
                .resizable()
                .scaledToFit()

        // Moods
        case "happy":
            Image(systemName: "face.smiling")
                .resizable()
                .scaledToFit()
        case "love":
            Image(systemName: "heart.fill")
                .resizable()
                .scaledToFit()
        case "chill":
            Image(systemName: "cup.and.saucer.fill")
                .resizable()
                .scaledToFit()
        case "excited":
            Image(systemName: "flame")
                .resizable()
                .scaledToFit()
        case "nostalgic":
            Image(systemName: "clock.arrow.circlepath")
                .resizable()
                .scaledToFit()
        case "peaceful":
            Image(systemName: "leaf.fill")
                .resizable()
                .scaledToFit()

        default:
            Image(systemName: "folder")
                .resizable()
                .scaledToFit()
        }
    }
}
