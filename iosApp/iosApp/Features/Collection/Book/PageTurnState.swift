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
    public var id: String { "\(albumId)_page_\(pageIndex)_\(isInsideCover ? "cover" : "inner")" }
    public let albumId: String
    public let pageIndex: Int
    public let stamps: [BookStampItem]
    public let placements: [PersistedStampPlacementData]
    public let isBlankArchival: Bool
    public let isInsideCover: Bool

    public init(
        albumId: String,
        pageIndex: Int,
        stamps: [BookStampItem],
        placements: [PersistedStampPlacementData] = [],
        isBlankArchival: Bool = false,
        isInsideCover: Bool = false
    ) {
        self.albumId = albumId
        self.pageIndex = pageIndex
        self.stamps = stamps
        self.placements = placements
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
/// Page Index Authority (Section 2):
/// pageIndex = 0 means first editable inner page (Spread 0 Right).
/// Inside Front Cover is non-editable (pageIndex = -1, isInsideCover = true).
///
/// Spread 0: Left = Inside Front Cover, Right = Page 0.
/// Spread S (S >= 1):
///   Left = Page (2 * S - 1)
///   Right = Page (2 * S) or Archival Blank Page if odd count.
public func calculateSpreads(
    albumId: String,
    pages: [PersistedAlbumPageData] = [],
    stamps: [BookStampItem] = [],
    placements: [PersistedStampPlacementData] = [],
    stampsPerPage: Int = 4
) -> [BookSpread] {
    // 1. CANONICAL PAGE AUTHORITY (Task #80)
    if !pages.isEmpty {
        let sortedPages = pages.sorted { $0.pageIndex < $1.pageIndex }
        let totalSpreads = (sortedPages.count / 2) + 1
        var spreads: [BookSpread] = []

        for spreadIdx in 0..<totalSpreads {
            if spreadIdx == 0 {
                let p0 = sortedPages[0]
                let p0Placements = placements.filter {
                    ($0.pageId != nil && $0.pageId == p0.id) || $0.pageIndex == p0.pageIndex
                }
                spreads.append(
                    BookSpread(
                        spreadIndex: 0,
                        leftPage: BookPageData(
                            albumId: albumId,
                            pageIndex: -1,
                            stamps: [],
                            placements: [],
                            isInsideCover: true
                        ),
                        rightPage: BookPageData(
                            albumId: albumId,
                            pageIndex: Int(p0.pageIndex),
                            stamps: stamps,
                            placements: p0Placements,
                            isBlankArchival: false
                        )
                    )
                )
            } else {
                let leftIdx = 2 * spreadIdx - 1
                let rightIdx = 2 * spreadIdx

                let leftPageData: BookPageData
                if leftIdx < sortedPages.count {
                    let lp = sortedPages[leftIdx]
                    let lpPlacements = placements.filter {
                        ($0.pageId != nil && $0.pageId == lp.id) || $0.pageIndex == lp.pageIndex
                    }
                    leftPageData = BookPageData(
                        albumId: albumId,
                        pageIndex: Int(lp.pageIndex),
                        stamps: stamps,
                        placements: lpPlacements,
                        isBlankArchival: false
                    )
                } else {
                    leftPageData = BookPageData(
                        albumId: albumId,
                        pageIndex: leftIdx,
                        stamps: [],
                        placements: [],
                        isBlankArchival: true
                    )
                }

                let rightPageData: BookPageData
                if rightIdx < sortedPages.count {
                    let rp = sortedPages[rightIdx]
                    let rpPlacements = placements.filter {
                        ($0.pageId != nil && $0.pageId == rp.id) || $0.pageIndex == rp.pageIndex
                    }
                    rightPageData = BookPageData(
                        albumId: albumId,
                        pageIndex: Int(rp.pageIndex),
                        stamps: stamps,
                        placements: rpPlacements,
                        isBlankArchival: false
                    )
                } else {
                    rightPageData = BookPageData(
                        albumId: albumId,
                        pageIndex: rightIdx,
                        stamps: [],
                        placements: [],
                        isBlankArchival: true
                    )
                }

                spreads.append(
                    BookSpread(
                        spreadIndex: spreadIdx,
                        leftPage: leftPageData,
                        rightPage: rightPageData
                    )
                )
            }
        }
        return spreads
    }

    if !placements.isEmpty {
        let maxPlacementPage = placements.map { Int($0.pageIndex) }.max() ?? 0
        let maxPage = max(0, maxPlacementPage)
        let totalSpreads = (maxPage / 2) + 1
        var spreads: [BookSpread] = []

        for spreadIdx in 0..<totalSpreads {
            if spreadIdx == 0 {
                spreads.append(
                    BookSpread(
                        spreadIndex: 0,
                        leftPage: BookPageData(
                            albumId: albumId,
                            pageIndex: -1,
                            stamps: [],
                            placements: [],
                            isInsideCover: true
                        ),
                        rightPage: BookPageData(
                            albumId: albumId,
                            pageIndex: 0,
                            stamps: stamps,
                            placements: placements.filter { $0.pageIndex == 0 },
                            isBlankArchival: false
                        )
                    )
                )
            } else {
                let leftPageIndex = 2 * spreadIdx - 1
                let rightPageIndex = 2 * spreadIdx
                let leftPlacements = placements.filter { Int($0.pageIndex) == leftPageIndex }
                let rightPlacements = placements.filter { Int($0.pageIndex) == rightPageIndex }
                let isRightArchival = rightPageIndex > maxPage && rightPlacements.isEmpty

                spreads.append(
                    BookSpread(
                        spreadIndex: spreadIdx,
                        leftPage: BookPageData(
                            albumId: albumId,
                            pageIndex: leftPageIndex,
                            stamps: stamps,
                            placements: leftPlacements,
                            isBlankArchival: false
                        ),
                        rightPage: BookPageData(
                            albumId: albumId,
                            pageIndex: rightPageIndex,
                            stamps: stamps,
                            placements: rightPlacements,
                            isBlankArchival: isRightArchival
                        )
                    )
                )
            }
        }
        return spreads
    }

    // Default fallback when no placements exist
    if stamps.isEmpty {
        return [
            BookSpread(
                spreadIndex: 0,
                leftPage: BookPageData(
                    albumId: albumId,
                    pageIndex: -1,
                    stamps: [],
                    isInsideCover: true
                ),
                rightPage: BookPageData(
                    albumId: albumId,
                    pageIndex: 0,
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

    // Spread 0: Inside cover on left (pageIndex = -1), Page 0 on right (chunks[0])
    spreads.append(
        BookSpread(
            spreadIndex: 0,
            leftPage: BookPageData(
                albumId: albumId,
                pageIndex: -1,
                stamps: [],
                isInsideCover: true
            ),
            rightPage: BookPageData(
                albumId: albumId,
                pageIndex: 0,
                stamps: chunks[0]
            )
        )
    )

    // Subsequent spreads (Spread 1: Page 1 & 2, Spread 2: Page 3 & 4...)
    var contentPageIdx = 1
    var spreadIdx = 1

    while contentPageIdx < totalContentPages {
        let leftStamps = chunks[contentPageIdx]
        let hasRight = contentPageIdx + 1 < totalContentPages
        let rightStamps = hasRight ? chunks[contentPageIdx + 1] : []
        let isRightArchival = !hasRight

        spreads.append(
            BookSpread(
                spreadIndex: spreadIdx,
                leftPage: BookPageData(
                    albumId: albumId,
                    pageIndex: contentPageIdx,
                    stamps: leftStamps
                ),
                rightPage: BookPageData(
                    albumId: albumId,
                    pageIndex: contentPageIdx + 1,
                    stamps: rightStamps,
                    isBlankArchival: isRightArchival
                )
            )
        )

        contentPageIdx += 2
        spreadIdx += 1
    }

    return spreads
}

public func calculateSpreads(
    albumId: String,
    stamps: [BookStampItem],
    placements: [PersistedStampPlacementData] = [],
    stampsPerPage: Int = 4
) -> [BookSpread] {
    return calculateSpreads(
        albumId: albumId,
        pages: [],
        stamps: stamps,
        placements: placements,
        stampsPerPage: stampsPerPage
    )
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
