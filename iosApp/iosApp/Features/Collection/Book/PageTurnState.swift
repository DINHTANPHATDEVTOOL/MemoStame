import SwiftUI

/// Deterministic states for the 3D Stamp Book lifecycle.
public enum BookState: String, Equatable {
    case closed
    case opening
    case open
    case turningForward
    case turningBackward
    case closing
}

/// Direction of interactive page turn.
public enum TurnDirection: String, Equatable {
    case none
    case forward
    case backward
}

/// Visual stamp model for book pages.
public struct BookStampItem: Identifiable, Equatable {
    public let id: String
    public let name: String
    public let imageUrl: String

    public init(id: String, name: String, imageUrl: String) {
        self.id = id
        self.name = name
        self.imageUrl = imageUrl
    }
}

/// Data represented on an individual book page.
public struct BookPageData: Identifiable, Equatable {
    public var id: String { "\(albumId)_page_\(pageIndex)" }
    public let albumId: String
    public let pageIndex: Int
    public let stamps: [BookStampItem]
    public let isBlankArchival: Bool
    public let isInsideCover: Bool

    public init(
        albumId: String,
        pageIndex: Int,
        stamps: [BookStampItem],
        isBlankArchival: Bool = false,
        isInsideCover: Bool = false
    ) {
        self.albumId = albumId
        self.pageIndex = pageIndex
        self.stamps = stamps
        self.isBlankArchival = isBlankArchival
        self.isInsideCover = isInsideCover
    }
}

/// Future-canvas coordinate surface contract for a single page.
/// Coordinates are normalized: x in [0.0..1.0], y in [0.0..1.0].
public struct PageBounds: Equatable {
    public let width: CGFloat
    public let height: CGFloat

    public init(width: CGFloat = 1.0, height: CGFloat = 1.0) {
        self.width = width
        self.height = height
    }
}

public struct BookPageContent: Equatable {
    public let albumId: String
    public let pageIndex: Int
    public let pageBounds: PageBounds
    public let stamps: [BookStampItem]

    public init(
        albumId: String,
        pageIndex: Int,
        pageBounds: PageBounds = PageBounds(),
        stamps: [BookStampItem] = []
    ) {
        self.albumId = albumId
        self.pageIndex = pageIndex
        self.pageBounds = pageBounds
        self.stamps = stamps
    }
}

/// Two-page spread representation: LEFT PAGE | SPINE | RIGHT PAGE.
public struct BookSpread: Identifiable, Equatable {
    public var id: Int { spreadIndex }
    public let spreadIndex: Int
    public let leftPage: BookPageData
    public let rightPage: BookPageData

    public init(spreadIndex: Int, leftPage: BookPageData, rightPage: BookPageData) {
        self.spreadIndex = spreadIndex
        self.leftPage = leftPage
        self.rightPage = rightPage
    }
}

/// Calculates deterministic two-page spreads for an album.
///
/// Spread 0: Left = Inside Front Cover, Right = Page 1 (or empty title page).
/// Spread S (S >= 1):
///   Left = Page (2 * S)
///   Right = Page (2 * S + 1) or Archival Blank Page if odd count.
public func calculateSpreads(
    albumId: String,
    stamps: [BookStampItem],
    stampsPerPage: Int = 4
) -> [BookSpread] {
    if stamps.isEmpty {
        return [
            BookSpread(
                spreadIndex: 0,
                leftPage: BookPageData(
                    albumId: albumId,
                    pageIndex: 0,
                    stamps: [],
                    isInsideCover: true
                ),
                rightPage: BookPageData(
                    albumId: albumId,
                    pageIndex: 1,
                    stamps: [],
                    isBlankArchival: false
                )
            )
        ]
    }

    // Chunk stamps into pages
    var chunks: [[BookStampItem]] = []
    var currentChunk: [BookStampItem] = []
    for stamp in stamps {
        currentChunk.append(stamp)
        if currentChunk.count == stampsPerPage {
            chunks.append(currentChunk)
            currentChunk = []
        }
    }
    if !currentChunk.isEmpty {
        chunks.append(currentChunk)
    }

    let totalContentPages = chunks.count
    var spreads: [BookSpread] = []

    // Spread 0: Inside cover on left, Page 1 on right
    spreads.append(
        BookSpread(
            spreadIndex: 0,
            leftPage: BookPageData(
                albumId: albumId,
                pageIndex: 0,
                stamps: [],
                isInsideCover: true
            ),
            rightPage: BookPageData(
                albumId: albumId,
                pageIndex: 1,
                stamps: chunks[0]
            )
        )
    )

    // Subsequent spreads (Spread 1, Spread 2, ...)
    var currentContentPageIndex = 2
    var spreadIdx = 1

    while currentContentPageIndex <= totalContentPages {
        let leftStamps = chunks[currentContentPageIndex - 1]
        let hasRight = currentContentPageIndex < totalContentPages
        let rightStamps = hasRight ? chunks[currentContentPageIndex] : []
        let isRightArchival = !hasRight

        spreads.append(
            BookSpread(
                spreadIndex: spreadIdx,
                leftPage: BookPageData(
                    albumId: albumId,
                    pageIndex: currentContentPageIndex,
                    stamps: leftStamps
                ),
                rightPage: BookPageData(
                    albumId: albumId,
                    pageIndex: currentContentPageIndex + 1,
                    stamps: rightStamps,
                    isBlankArchival: isRightArchival
                )
            )
        )

        currentContentPageIndex += 2
        spreadIdx += 1
    }

    return spreads
}

/// Pure state machine tracking transient book state.
/// Never persists animation/gesture state to Room/Supabase.
public class PageTurnStateMachine: ObservableObject {
    public let albumId: String

    @Published public private(set) var bookState: BookState = .closed
    @Published public private(set) var currentSpreadIndex: Int = 0
    @Published public private(set) var turnProgress: Double = 0.0
    @Published public private(set) var turnDirection: TurnDirection = .none
    @Published public private(set) var interactionLocked: Bool = false

    public init(albumId: String, initialSpread: Int = 0) {
        self.albumId = albumId
        self.currentSpreadIndex = initialSpread
    }

    public func open() {
        guard bookState == .closed && !interactionLocked else { return }
        interactionLocked = true
        bookState = .opening
    }

    public func settleOpen() {
        bookState = .open
        turnProgress = 0.0
        turnDirection = .none
        interactionLocked = false
    }

    public func close() {
        guard bookState == .open && !interactionLocked else { return }
        interactionLocked = true
        bookState = .closing
    }

    public func settleClosed() {
        bookState = .closed
        currentSpreadIndex = 0
        turnProgress = 0.0
        turnDirection = .none
        interactionLocked = false
    }

    @discardableResult
    public func startTurn(direction: TurnDirection, totalSpreads: Int) -> Bool {
        guard bookState == .open && !interactionLocked else { return false }
        if direction == .forward && currentSpreadIndex >= totalSpreads - 1 { return false }
        if direction == .backward && currentSpreadIndex <= 0 { return false }

        turnDirection = direction
        turnProgress = 0.0
        bookState = (direction == .forward) ? .turningForward : .turningBackward
        return true
    }

    public func updateProgress(_ progress: Double) {
        guard bookState == .turningForward || bookState == .turningBackward else { return }
        if progress.isNaN {
            turnProgress = 0.0
        } else {
            turnProgress = min(max(progress, 0.0), 1.0)
        }
    }

    public func finishTurn(completed: Bool, totalSpreads: Int) {
        if completed {
            if turnDirection == .forward && currentSpreadIndex < totalSpreads - 1 {
                currentSpreadIndex += 1
            } else if turnDirection == .backward && currentSpreadIndex > 0 {
                currentSpreadIndex -= 1
            }
        }
        turnProgress = 0.0
        turnDirection = .none
        bookState = .open
        interactionLocked = false
    }

    public func reset() {
        bookState = .closed
        currentSpreadIndex = 0
        turnProgress = 0.0
        turnDirection = .none
        interactionLocked = false
    }
}

/// Visual 3D parameters calculated from turn progress [0..1].
public struct TurnVisualParams: Equatable {
    public let pageAngleDegrees: Double
    public let shadowAlpha: Double
    public let highlightAlpha: Double
    public let spineCreaseAlpha: Double

    public init(
        pageAngleDegrees: Double,
        shadowAlpha: Double,
        highlightAlpha: Double,
        spineCreaseAlpha: Double
    ) {
        self.pageAngleDegrees = pageAngleDegrees
        self.shadowAlpha = shadowAlpha
        self.highlightAlpha = highlightAlpha
        self.spineCreaseAlpha = spineCreaseAlpha
    }
}

public func calculateTurnVisuals(progress: Double, isForward: Bool) -> TurnVisualParams {
    let clamped = progress.isNaN ? 0.0 : min(max(progress, 0.0), 1.0)
    let angle: Double
    if isForward {
        angle = -clamped * 180.0
    } else {
        angle = -180.0 + (clamped * 180.0)
    }

    let shadowFactor = sin(clamped * .pi)
    let shadow = min(max(shadowFactor * 0.45, 0.0), 0.45)
    let highlight = min(max(shadowFactor * 0.20, 0.0), 0.20)
    let spine = min(max(0.25 + shadowFactor * 0.25, 0.0), 0.5)

    return TurnVisualParams(
        pageAngleDegrees: angle,
        shadowAlpha: shadow,
        highlightAlpha: highlight,
        spineCreaseAlpha: spine
    )
}
