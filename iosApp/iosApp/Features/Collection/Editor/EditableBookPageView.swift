import SwiftUI

public struct EditableBookPageView: View {
    public let pageData: BookPageData
    public let stampsList: [BookStampItem]
    public let placements: [PersistedStampPlacementData]
    @ObservedObject public var editState: AlbumEditState
    public var onStampClick: (String) -> Void
    @ObservedObject private var langManager = AppLanguageManager.shared

    public init(
        pageData: BookPageData,
        stampsList: [BookStampItem],
        placements: [PersistedStampPlacementData],
        editState: AlbumEditState,
        onStampClick: @escaping (String) -> Void = { _ in }
    ) {
        self.pageData = pageData
        self.stampsList = stampsList
        self.placements = placements
        self.editState = editState
        self.onStampClick = onStampClick
    }

    public var body: some View {
        GeometryReader { geo in
            let pageWidth = geo.size.width
            let pageHeight = geo.size.height

            let stampDict = Dictionary(stampsList.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
            let pagePlacements = placements.filter { $0.pageIndex == pageData.pageIndex }
            let sortedPlacements = pagePlacements.sorted {
                if $0.zIndex != $1.zIndex { return $0.zIndex < $1.zIndex }
                return $0.id < $1.id
            }

            ZStack {
                // Background tap deselects active placement
                Color.clear
                    .contentShape(Rectangle())
                    .onTapGesture {
                        if editState.mode == .edit {
                            editState.activePageIndex = pageData.pageIndex
                            editState.selectPlacement(nil)
                        }
                    }

                if sortedPlacements.isEmpty {
                    Text(langManager.localized("book_empty_page_hint"))
                        .font(.caption)
                        .foregroundColor(MSColors.grey)
                        .multilineTextAlignment(.center)
                        .padding(16)
                } else {
                    ForEach(sortedPlacements, id: \.id) { placement in
                        if let stamp = stampDict[placement.stampId] {
                            EditableStampPlacementView(
                                placement: placement,
                                stamp: stamp,
                                pageWidth: pageWidth,
                                pageHeight: pageHeight,
                                editState: editState,
                                onStampClick: onStampClick
                            )
                        }
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
        }
    }
}
