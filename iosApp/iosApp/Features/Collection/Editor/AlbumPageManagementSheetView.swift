import SwiftUI

public struct AlbumPageManagementSheetView: View {
    let pages: [PersistedAlbumPageData]
    let placements: [PersistedStampPlacementData]
    let activePageIndex: Int32
    let onDismiss: () -> Void
    let onAddPage: () -> Void
    let onRemovePage: (_ page: PersistedAlbumPageData, _ placementsOnPage: [PersistedStampPlacementData]) -> Void
    let onReorderPage: (_ pageId: String, _ direction: Int) -> Void

    private let bookGold = Color(red: 209/255.0, green: 165/255.0, blue: 89/255.0)

    public init(
        pages: [PersistedAlbumPageData],
        placements: [PersistedStampPlacementData],
        activePageIndex: Int32,
        onDismiss: @escaping () -> Void,
        onAddPage: @escaping () -> Void,
        onRemovePage: @escaping (PersistedAlbumPageData, [PersistedStampPlacementData]) -> Void,
        onReorderPage: @escaping (String, Int) -> Void
    ) {
        self.pages = pages
        self.placements = placements
        self.activePageIndex = activePageIndex
        self.onDismiss = onDismiss
        self.onAddPage = onAddPage
        self.onRemovePage = onRemovePage
        self.onReorderPage = onReorderPage
    }

    private var sortedPages: [PersistedAlbumPageData] {
        pages.sorted { $0.pageIndex < $1.pageIndex }
    }

    public var body: some View {
        NavigationView {
            VStack(spacing: 16) {
                ScrollView {
                    LazyVStack(spacing: 10) {
                        ForEach(Array(sortedPages.enumerated()), id: \.element.id) { index, page in
                            let pagePlacements = placements.filter {
                                ($0.pageId != nil && $0.pageId == page.id) || $0.pageIndex == page.pageIndex
                            }
                            let isCurrent = page.pageIndex == activePageIndex
                            let isFirst = index == 0
                            let isLast = index == sortedPages.count - 1
                            let canRemove = pagePlacements.isEmpty && sortedPages.count > 1

                            HStack {
                                VStack(alignment: .leading, spacing: 4) {
                                    HStack(spacing: 6) {
                                        Text(String(format: NSLocalizedString("book_page_number_format", comment: ""), page.pageIndex + 1))
                                            .font(.headline)
                                            .foregroundColor(.primary)

                                        if isCurrent {
                                            Text(NSLocalizedString("album_editor_page_current", comment: ""))
                                                .font(.caption2.bold())
                                                .padding(.horizontal, 6)
                                                .padding(.vertical, 2)
                                                .background(bookGold.opacity(0.3))
                                                .foregroundColor(.primary)
                                                .cornerRadius(4)
                                        }
                                    }

                                    if pagePlacements.isEmpty {
                                        Text(NSLocalizedString("album_editor_empty_page", comment: ""))
                                            .font(.caption)
                                            .foregroundColor(.secondary)
                                    } else {
                                        Text(String(format: NSLocalizedString("album_editor_stamps_count", comment: ""), pagePlacements.count))
                                            .font(.caption)
                                            .foregroundColor(.secondary)
                                    }
                                }

                                Spacer()

                                HStack(spacing: 8) {
                                    Button(action: {
                                        onReorderPage(page.id, -1)
                                    }) {
                                        Image(systemName: "arrow.up")
                                            .font(.system(size: 14, weight: .bold))
                                            .frame(width: 32, height: 32)
                                            .background(Color(.systemGray6))
                                            .cornerRadius(8)
                                    }
                                    .disabled(isFirst)
                                    .accessibilityIdentifier("move_earlier_button_\(page.pageIndex)")

                                    Button(action: {
                                        onReorderPage(page.id, 1)
                                    }) {
                                        Image(systemName: "arrow.down")
                                            .font(.system(size: 14, weight: .bold))
                                            .frame(width: 32, height: 32)
                                            .background(Color(.systemGray6))
                                            .cornerRadius(8)
                                    }
                                    .disabled(isLast)
                                    .accessibilityIdentifier("move_later_button_\(page.pageIndex)")

                                    Button(action: {
                                        onRemovePage(page, pagePlacements)
                                    }) {
                                        Image(systemName: "trash")
                                            .font(.system(size: 14, weight: .semibold))
                                            .foregroundColor(canRemove ? .red : .gray.opacity(0.4))
                                            .frame(width: 32, height: 32)
                                            .background(Color(.systemGray6))
                                            .cornerRadius(8)
                                    }
                                    .disabled(!canRemove)
                                    .accessibilityIdentifier("remove_page_button_\(page.pageIndex)")
                                }
                            }
                            .padding()
                            .background(isCurrent ? bookGold.opacity(0.12) : Color(.secondarySystemBackground))
                            .cornerRadius(12)
                            .accessibilityIdentifier("page_item_\(page.pageIndex)")
                        }
                    }
                    .padding(.horizontal)
                }

                Button(action: onAddPage) {
                    HStack {
                        Image(systemName: "plus")
                        Text(sortedPages.count < 50 ? NSLocalizedString("album_editor_add_page", comment: "") : NSLocalizedString("album_editor_max_pages_reached", comment: ""))
                            .fontWeight(.semibold)
                    }
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(sortedPages.count < 50 ? bookGold : Color.gray.opacity(0.3))
                    .foregroundColor(.white)
                    .cornerRadius(12)
                }
                .disabled(sortedPages.count >= 50)
                .padding(.horizontal)
                .accessibilityIdentifier("add_page_button")
            }
            .navigationTitle(NSLocalizedString("album_editor_pages_title", comment: ""))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: onDismiss) {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.secondary)
                    }
                    .accessibilityIdentifier("close_page_management_button")
                }
            }
        }
        .accessibilityIdentifier("album_page_management_sheet")
    }
}
