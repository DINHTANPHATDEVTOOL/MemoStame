import SwiftUI

private let BookDarkWoodBg = Color(red: 0.17, green: 0.14, blue: 0.13)
private let BookSpineCreaseColor = Color(red: 0.12, green: 0.09, blue: 0.08)
private let BookPageCream = Color(red: 0.96, green: 0.92, blue: 0.87) // MSColors.creamCard
private let BookGold = Color(red: 0.82, green: 0.65, blue: 0.35)
private let BookPaperBorder = Color(red: 0.91, green: 0.89, blue: 0.85)

public struct StampBook3DRenderer: View {
    public let albumId: String
    public let albumTitle: String
    public let albumDescription: String
    public let curatorName: String
    public let coverColor: Color
    public let iconKey: String?
    public let stamps: [BookStampItem]
    public let placements: [PersistedStampPlacementData]
    public var onStampClick: (String) -> Void
    public var onDismiss: () -> Void

    @StateObject private var stateMachine: PageTurnStateMachine
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @ObservedObject private var langManager = AppLanguageManager.shared

    @State private var coverAngle: Double = 0.0
    @State private var dragTurnProgress: Double = 0.0
    @State private var isAnimating: Bool = false

    private var spreads: [BookSpread] {
        calculateSpreads(albumId: albumId, stamps: stamps, placements: placements)
    }

    private var totalSpreads: Int {
        spreads.count
    }

    public init(
        albumId: String,
        albumTitle: String,
        albumDescription: String,
        curatorName: String,
        coverColor: Color,
        iconKey: String?,
        stamps: [BookStampItem],
        placements: [PersistedStampPlacementData] = [],
        onStampClick: @escaping (String) -> Void = { _ in },
        onDismiss: @escaping () -> Void = {}
    ) {
        self.albumId = albumId
        self.albumTitle = albumTitle
        self.albumDescription = albumDescription
        self.curatorName = curatorName
        self.coverColor = coverColor
        self.iconKey = iconKey
        self.stamps = stamps
        self.placements = placements
        self.onStampClick = onStampClick
        self.onDismiss = onDismiss
        _stateMachine = StateObject(wrappedValue: PageTurnStateMachine(albumId: albumId))
    }

    public var body: some View {
        ZStack {
            BookDarkWoodBg
                .ignoresSafeArea()

            VStack(spacing: 0) {
                // Top Action Bar
                topActionBar

                // 2.5D Two-Page Book Arena
                GeometryReader { geometry in
                    let halfWidth = max(geometry.size.width / 2.0, 1.0)
                    ZStack {
                        // Stacked book thickness simulation
                        RoundedRectangle(cornerRadius: 8)
                            .fill(Color(red: 0.89, green: 0.84, blue: 0.76))
                            .shadow(color: Color.black.opacity(0.45), radius: 24, x: 0, y: 12)
                            .frame(width: geometry.size.width * 0.96, height: geometry.size.height * 0.96)

                        // Real Two-Page Spread: [LEFT PAGE | SPINE | RIGHT PAGE]
                        twoPageSpread(geometry: geometry)

                        // 3D Cover Opening / Closing Layer
                        if coverAngle > -180.0 {
                            coverLayer(geometry: geometry)
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .contentShape(Rectangle())
                    .gesture(
                        DragGesture(minimumDistance: 12)
                            .onChanged { value in
                                guard !reduceMotion, !isAnimating, stateMachine.bookState == .open else { return }
                                let isRight = value.startLocation.x >= halfWidth
                                if stateMachine.turnDirection == .none {
                                    let dir: TurnDirection = isRight ? .forward : .backward
                                    stateMachine.startTurn(direction: dir, totalSpreads: totalSpreads)
                                }
                                if stateMachine.turnDirection == .forward {
                                    let progress = min(max(-value.translation.width / halfWidth, 0.0), 1.0)
                                    dragTurnProgress = progress
                                    stateMachine.updateProgress(progress)
                                } else if stateMachine.turnDirection == .backward {
                                    let progress = min(max(value.translation.width / halfWidth, 0.0), 1.0)
                                    dragTurnProgress = progress
                                    stateMachine.updateProgress(progress)
                                }
                            }
                            .onEnded { value in
                                guard !reduceMotion, !isAnimating, stateMachine.turnDirection != .none else { return }
                                let progress = dragTurnProgress
                                let velocity = value.predictedEndTranslation.width
                                let shouldComplete: Bool
                                if stateMachine.turnDirection == .forward {
                                    shouldComplete = progress >= 0.45 || velocity < -120.0
                                } else {
                                    shouldComplete = progress >= 0.45 || velocity > 120.0
                                }

                                isAnimating = true
                                withAnimation(.spring(response: 0.35, dampingFraction: 0.78)) {
                                    dragTurnProgress = shouldComplete ? 1.0 : 0.0
                                    stateMachine.updateProgress(dragTurnProgress)
                                }
                                DispatchQueue.main.asyncAfter(deadline: .now() + 0.36) {
                                    stateMachine.finishTurn(completed: shouldComplete, totalSpreads: totalSpreads)
                                    dragTurnProgress = 0.0
                                    isAnimating = false
                                }
                            }
                    )
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)

                // Bottom Accessible Controls
                bottomNavigationControls
            }
        }
        .onAppear {
            openCover()
        }
    }

    // MARK: - Top Action Bar
    private var topActionBar: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(albumTitle)
                    .font(.headline.bold())
                    .foregroundColor(BookGold)
                    .lineLimit(1)

                let spreadText = String(
                    format: langManager.localized("book_spread_page_indicator"),
                    stateMachine.currentSpreadIndex + 1,
                    totalSpreads
                )
                Text(spreadText)
                    .font(.caption)
                    .foregroundColor(BookGold.opacity(0.8))
            }
            Spacer()
            Button(action: { requestClose() }) {
                Image(systemName: "xmark")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(BookGold)
                    .padding(8)
                    .background(Color.white.opacity(0.08))
                    .clipShape(Circle())
            }
            .accessibilityLabel(langManager.localized("book_close"))
            .disabled(isAnimating || stateMachine.interactionLocked)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }

    // MARK: - Two-Page Spread
    private func twoPageSpread(geometry: GeometryProxy) -> some View {
        let currentSpread = spreads[min(max(stateMachine.currentSpreadIndex, 0), totalSpreads - 1)]
        let nextSpread = (stateMachine.currentSpreadIndex + 1 < totalSpreads) ? spreads[stateMachine.currentSpreadIndex + 1] : nil
        let prevSpread = (stateMachine.currentSpreadIndex > 0) ? spreads[stateMachine.currentSpreadIndex - 1] : nil

        let activeProgress = (stateMachine.turnDirection != .none) ? dragTurnProgress : 0.0

        return HStack(spacing: 0) {
            // === LEFT PAGE ===
            ZStack {
                BookPageCream

                if stateMachine.turnDirection == .backward, let prev = prevSpread {
                    BookPageSurfaceView(
                        pageData: prev.leftPage,
                        albumTitle: albumTitle,
                        albumDesc: albumDescription,
                        curatorName: curatorName,
                        totalStampsCount: stamps.count,
                        onStampClick: onStampClick
                    )
                } else {
                    BookPageSurfaceView(
                        pageData: currentSpread.leftPage,
                        albumTitle: albumTitle,
                        albumDesc: albumDescription,
                        curatorName: curatorName,
                        totalStampsCount: stamps.count,
                        onStampClick: onStampClick
                    )
                }

                // Dynamic Backward Turning Page
                if stateMachine.turnDirection == .backward, let prev = prevSpread {
                    let visuals = calculateTurnVisuals(progress: activeProgress, isForward: false)
                    ZStack {
                        BookPageCream
                        if activeProgress < 0.5 {
                            BookPageSurfaceView(
                                pageData: currentSpread.leftPage,
                                albumTitle: albumTitle,
                                albumDesc: albumDescription,
                                curatorName: curatorName,
                                totalStampsCount: stamps.count,
                                onStampClick: onStampClick
                            )
                        } else {
                            BookPageSurfaceView(
                                pageData: prev.rightPage,
                                albumTitle: albumTitle,
                                albumDesc: albumDescription,
                                curatorName: curatorName,
                                totalStampsCount: stamps.count,
                                onStampClick: onStampClick
                            )
                            .rotation3DEffect(.degrees(180), axis: (x: 0, y: 1, z: 0))
                        }

                        // Cast Shadow
                        if visuals.shadowAlpha > 0 {
                            Color.black.opacity(visuals.shadowAlpha)
                        }
                    }
                    .rotation3DEffect(
                        .degrees(visuals.pageAngleDegrees),
                        axis: (x: 0, y: 1, z: 0),
                        anchor: .trailing,
                        perspective: 0.8
                    )
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .clipped()

            // === CENTRAL SPINE / GUTTER CREASE ===
            LinearGradient(
                colors: [
                    Color.black.opacity(0.32),
                    BookSpineCreaseColor.opacity(0.45),
                    Color.black.opacity(0.32)
                ],
                startPoint: .leading,
                endPoint: .trailing
            )
            .frame(width: 14)

            // === RIGHT PAGE ===
            ZStack {
                BookPageCream

                if stateMachine.turnDirection == .forward, let next = nextSpread {
                    BookPageSurfaceView(
                        pageData: next.rightPage,
                        albumTitle: albumTitle,
                        albumDesc: albumDescription,
                        curatorName: curatorName,
                        totalStampsCount: stamps.count,
                        onStampClick: onStampClick
                    )
                } else {
                    BookPageSurfaceView(
                        pageData: currentSpread.rightPage,
                        albumTitle: albumTitle,
                        albumDesc: albumDescription,
                        curatorName: curatorName,
                        totalStampsCount: stamps.count,
                        onStampClick: onStampClick
                    )
                }

                // Dynamic Forward Turning Page
                if stateMachine.turnDirection == .forward, let next = nextSpread {
                    let visuals = calculateTurnVisuals(progress: activeProgress, isForward: true)

                    // Cast shadow under turning page
                    if visuals.shadowAlpha > 0 {
                        LinearGradient(
                            colors: [Color.black.opacity(visuals.shadowAlpha), Color.clear],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    }

                    ZStack {
                        BookPageCream
                        if activeProgress < 0.5 {
                            BookPageSurfaceView(
                                pageData: currentSpread.rightPage,
                                albumTitle: albumTitle,
                                albumDesc: albumDescription,
                                curatorName: curatorName,
                                totalStampsCount: stamps.count,
                                onStampClick: onStampClick
                            )
                        } else {
                            BookPageSurfaceView(
                                pageData: next.leftPage,
                                albumTitle: albumTitle,
                                albumDesc: albumDescription,
                                curatorName: curatorName,
                                totalStampsCount: stamps.count,
                                onStampClick: onStampClick
                            )
                            .rotation3DEffect(.degrees(180), axis: (x: 0, y: 1, z: 0))
                        }

                        // Moving highlight
                        if visuals.highlightAlpha > 0 {
                            LinearGradient(
                                colors: [Color.white.opacity(visuals.highlightAlpha), Color.clear],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        }
                    }
                    .rotation3DEffect(
                        .degrees(visuals.pageAngleDegrees),
                        axis: (x: 0, y: 1, z: 0),
                        anchor: .leading,
                        perspective: 0.8
                    )
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .clipped()
        }
        .frame(width: geometry.size.width * 0.95, height: geometry.size.height * 0.95)
        .clipShape(RoundedRectangle(cornerRadius: 6))
    }

    // MARK: - 3D Cover Opening / Closing Layer
    private func coverLayer(geometry: GeometryProxy) -> some View {
        HStack(spacing: 0) {
            Spacer()
                .frame(width: geometry.size.width * 0.95 / 2.0)

            ZStack {
                if coverAngle > -90.0 {
                    // Front Cover Exterior
                    ZStack {
                        RoundedRectangle(cornerRadius: 8)
                            .fill(coverColor)
                            .overlay(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(BookGold.opacity(0.7), lineWidth: 1.5)
                            )
                            .shadow(color: Color.black.opacity(0.4), radius: 12, x: 4, y: 6)

                        VStack(spacing: 12) {
                            if let key = iconKey {
                                MemoStampIcon(key: key, size: 44, color: BookGold)
                            }
                            Text(albumTitle)
                                .font(.headline.bold())
                                .foregroundColor(BookGold)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 16)
                            Text(langManager.localized("book_tap_to_open"))
                                .font(.caption2)
                                .foregroundColor(.white.opacity(0.8))
                        }
                    }
                } else {
                    // Inside Cover Lining
                    BookPageCream
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .rotation3DEffect(.degrees(180), axis: (x: 0, y: 1, z: 0))
                }
            }
            .frame(width: geometry.size.width * 0.95 / 2.0, height: geometry.size.height * 0.95)
            .rotation3DEffect(
                .degrees(coverAngle),
                axis: (x: 0, y: 1, z: 0),
                anchor: .leading,
                perspective: 0.8
            )
        }
    }

    // MARK: - Bottom Navigation Controls
    private var bottomNavigationControls: some View {
        HStack {
            Button(action: { navigateSpread(forward: false) }) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(stateMachine.currentSpreadIndex > 0 ? BookGold : BookGold.opacity(0.3))
                    .padding(10)
                    .background(Color.white.opacity(0.08))
                    .clipShape(Circle())
            }
            .accessibilityLabel(langManager.localized("book_spread_prev"))
            .disabled(stateMachine.currentSpreadIndex <= 0 || isAnimating || stateMachine.interactionLocked)

            Spacer()

            Text(langManager.localized("book_drag_hint"))
                .font(.caption)
                .italic()
                .foregroundColor(BookGold.opacity(0.65))

            Spacer()

            Button(action: { navigateSpread(forward: true) }) {
                Image(systemName: "chevron.right")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(stateMachine.currentSpreadIndex < totalSpreads - 1 ? BookGold : BookGold.opacity(0.3))
                    .padding(10)
                    .background(Color.white.opacity(0.08))
                    .clipShape(Circle())
            }
            .accessibilityLabel(langManager.localized("book_spread_next"))
            .disabled(stateMachine.currentSpreadIndex >= totalSpreads - 1 || isAnimating || stateMachine.interactionLocked)
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 8)
    }

    // MARK: - Animation Triggers
    private func openCover() {
        stateMachine.open()
        coverAngle = 0.0
        if reduceMotion {
            coverAngle = -180.0
            stateMachine.settleOpen()
        } else {
            isAnimating = true
            withAnimation(.easeInOut(duration: 0.48)) {
                coverAngle = -180.0
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.50) {
                stateMachine.settleOpen()
                isAnimating = false
            }
        }
    }

    private func requestClose() {
        guard !isAnimating, !stateMachine.interactionLocked else { return }
        stateMachine.close()
        if reduceMotion {
            stateMachine.settleClosed()
            onDismiss()
        } else {
            isAnimating = true
            withAnimation(.easeInOut(duration: 0.38)) {
                coverAngle = 0.0
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.40) {
                stateMachine.settleClosed()
                isAnimating = false
                onDismiss()
            }
        }
    }

    private func navigateSpread(forward: Bool) {
        guard !isAnimating, !stateMachine.interactionLocked, stateMachine.bookState == .open else { return }
        let dir: TurnDirection = forward ? .forward : .backward
        guard stateMachine.startTurn(direction: dir, totalSpreads: totalSpreads) else { return }

        if reduceMotion {
            stateMachine.finishTurn(completed: true, totalSpreads: totalSpreads)
        } else {
            isAnimating = true
            dragTurnProgress = 0.0
            withAnimation(.easeInOut(duration: 0.36)) {
                dragTurnProgress = 1.0
                stateMachine.updateProgress(1.0)
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.38) {
                stateMachine.finishTurn(completed: true, totalSpreads: totalSpreads)
                dragTurnProgress = 0.0
                isAnimating = false
            }
        }
    }
}

// MARK: - Procedural Surface for a Single Book Page
public struct BookPageSurfaceView: View {
    public let pageData: BookPageData
    public let albumTitle: String
    public let albumDesc: String
    public let curatorName: String
    public let totalStampsCount: Int
    public var onStampClick: (String) -> Void

    @ObservedObject private var langManager = AppLanguageManager.shared

    public init(
        pageData: BookPageData,
        albumTitle: String,
        albumDesc: String,
        curatorName: String,
        totalStampsCount: Int,
        onStampClick: @escaping (String) -> Void = { _ in }
    ) {
        self.pageData = pageData
        self.albumTitle = albumTitle
        self.albumDesc = albumDesc
        self.curatorName = curatorName
        self.totalStampsCount = totalStampsCount
        self.onStampClick = onStampClick
    }

    public var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 4)
                .stroke(BookPaperBorder, lineWidth: 1)
                .padding(8)

            if pageData.isInsideCover {
                // Inside Cover Page
                VStack(spacing: 8) {
                    Text(langManager.localized("book_inside_cover_title"))
                        .font(.caption2.bold())
                        .foregroundColor(BookGold)
                        .tracking(1.0)
                    Text(albumTitle)
                        .font(.headline.bold())
                        .foregroundColor(MSColors.ink)
                        .multilineTextAlignment(.center)
                    if !albumDesc.isEmpty {
                        Text(albumDesc)
                            .font(.caption)
                            .foregroundColor(MSColors.grey)
                            .multilineTextAlignment(.center)
                            .lineLimit(3)
                    }
                    Divider()
                        .background(BookGold.opacity(0.3))
                        .padding(.horizontal, 24)
                    let curatorText = String(format: langManager.localized("book_curator_format"), curatorName)
                    Text(curatorText)
                        .font(.caption)
                        .foregroundColor(MSColors.grey)
                    let stampsText = String(format: langManager.localized("book_total_stamps_format"), totalStampsCount)
                    Text(stampsText)
                        .font(.caption.bold())
                        .foregroundColor(MSColors.grey)
                }
                .padding(16)
            } else if pageData.isBlankArchival {
                // Archival Blank Page
                VStack(spacing: 4) {
                    Text(langManager.localized("book_archival_blank_page"))
                        .font(.caption)
                        .italic()
                        .foregroundColor(MSColors.grey.opacity(0.45))
                    Text(langManager.localized("book_end_of_album"))
                        .font(.caption2)
                        .foregroundColor(MSColors.grey.opacity(0.3))
                }
            } else if !pageData.placements.isEmpty {
                // Persisted Placement Layout (Custom Physical Stamp Placement)
                GeometryReader { geo in
                    let pageWidth = geo.size.width
                    let pageHeight = geo.size.height
                    let stampDict = Dictionary(pageData.stamps.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
                    let sortedPlacements = pageData.placements.sorted {
                        if $0.zIndex != $1.zIndex { return $0.zIndex < $1.zIndex }
                        return $0.id < $1.id
                    }

                    ForEach(sortedPlacements, id: \.id) { placement in
                        if let stamp = stampDict[placement.stampId] {
                            let baseWidth: CGFloat = 84 * CGFloat(placement.scale)
                            let baseHeight: CGFloat = 104 * CGFloat(placement.scale)
                            let posX = pageWidth * CGFloat(placement.x)
                            let posY = pageHeight * CGFloat(placement.y)

                            BookStampCellView(stamp: stamp, onClick: { onStampClick(stamp.id) })
                                .frame(width: baseWidth, height: baseHeight)
                                .rotationEffect(.degrees(placement.rotationDegrees))
                                .position(x: posX, y: posY)
                                .zIndex(Double(placement.zIndex))
                        }
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .overlay(
                    VStack {
                        Spacer()
                        let pageNumText = String(format: langManager.localized("book_page_number_format"), pageData.pageIndex + 1)
                        Text(pageNumText)
                            .font(.caption2.bold())
                            .foregroundColor(MSColors.grey)
                            .padding(.bottom, 4)
                    }
                )
            } else if pageData.stamps.isEmpty {
                // Empty Album Page
                Text(langManager.localized("book_empty_page_hint"))
                    .font(.caption)
                    .foregroundColor(MSColors.grey)
                    .multilineTextAlignment(.center)
                    .padding(16)
            } else {
                // Stamps Grid (2x2 grid fallback)
                VStack(spacing: 6) {
                    VStack(spacing: 8) {
                        let rows = pageData.stamps.chunked(into: 2)
                        ForEach(0..<rows.count, id: \.self) { rowIndex in
                            HStack(spacing: 8) {
                                ForEach(rows[rowIndex]) { stamp in
                                    BookStampCellView(stamp: stamp, onClick: { onStampClick(stamp.id) })
                                }
                                if rows[rowIndex].count == 1 {
                                    Spacer()
                                }
                            }
                        }
                    }
                    .frame(maxHeight: .infinity)

                    let pageNumText = String(format: langManager.localized("book_page_number_format"), pageData.pageIndex + 1)
                    Text(pageNumText)
                        .font(.caption2.bold())
                        .foregroundColor(MSColors.grey)
                }
                .padding(10)
            }
        }
    }
}

// MARK: - Individual Stamp Cell
public struct BookStampCellView: View {
    public let stamp: BookStampItem
    public var onClick: () -> Void

    public var body: some View {
        Button(action: onClick) {
            VStack(spacing: 3) {
                ZStack {
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Color.white)
                        .overlay(
                            RoundedRectangle(cornerRadius: 4)
                                .stroke(BookPaperBorder, lineWidth: 1)
                        )
                        .shadow(color: Color.black.opacity(0.18), radius: 4, x: 0, y: 2)

                    if !stamp.imageUrl.isEmpty {
                        AsyncImage(url: URL(string: stamp.imageUrl)) { phase in
                            switch phase {
                            case .success(let image):
                                image
                                    .resizable()
                                    .scaledToFill()
                            case .failure:
                                Color.gray.opacity(0.1)
                                    .overlay(
                                        Image(systemName: "photo")
                                            .foregroundColor(.gray.opacity(0.4))
                                    )
                            case .empty:
                                ProgressView()
                            @unknown default:
                                EmptyView()
                            }
                        }
                        .clipShape(RoundedRectangle(cornerRadius: 2))
                        .padding(3)
                    } else {
                        Color.gray.opacity(0.1)
                            .overlay(
                                Image(systemName: "photo")
                                    .foregroundColor(.gray.opacity(0.4))
                            )
                            .padding(3)
                    }
                }
                .aspectRatio(0.85, contentMode: .fit)

                Text(stamp.name)
                    .font(.system(size: 9, weight: .medium))
                    .foregroundColor(MSColors.ink)
                    .lineLimit(1)
            }
        }
        .buttonStyle(PlainButtonStyle())
    }
}

private extension Array {
    func chunked(into size: Int) -> [[Element]] {
        var chunks: [[Element]] = []
        var current: [Element] = []
        for element in self {
            current.append(element)
            if current.count == size {
                chunks.append(current)
                current = []
            }
        }
        if !current.isEmpty {
            chunks.append(current)
        }
        return chunks
    }
}
