import SwiftUI

private let BookGold = Color(red: 0.82, green: 0.65, blue: 0.35)
private let EditorBarBg = Color(red: 0.12, green: 0.09, blue: 0.08).opacity(0.92)

public struct AlbumEditorTopControlsView: View {
    public let isVirtualAlbum: Bool
    @ObservedObject public var editState: AlbumEditState
    @ObservedObject private var langManager = AppLanguageManager.shared

    public init(isVirtualAlbum: Bool, editState: AlbumEditState) {
        self.isVirtualAlbum = isVirtualAlbum
        self.editState = editState
    }

    public var body: some View {
        HStack(spacing: 8) {
            if isVirtualAlbum {
                HStack(spacing: 4) {
                    Image(systemName: "lock.fill")
                        .font(.system(size: 11))
                        .foregroundColor(BookGold.opacity(0.7))
                    Text(langManager.localized("album_editor_virtual_readonly"))
                        .font(.system(size: 11))
                        .foregroundColor(BookGold.opacity(0.8))
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Color.black.opacity(0.4))
                .cornerRadius(12)
            } else {
                if editState.mode == .view {
                    Button(action: { editState.enterEditMode() }) {
                        HStack(spacing: 4) {
                            Image(systemName: "pencil")
                                .font(.system(size: 12, weight: .bold))
                            Text(langManager.localized("album_editor_edit"))
                                .font(.system(size: 13, weight: .semibold))
                        }
                        .foregroundColor(BookGold)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(BookGold.opacity(0.2))
                        .cornerRadius(8)
                    }
                } else {
                    Button(action: { editState.exitEditMode() }) {
                        Text(langManager.localized("album_editor_done"))
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(Color(red: 0.17, green: 0.14, blue: 0.13))
                            .padding(.horizontal, 12)
                            .padding(.vertical, 5)
                            .background(BookGold)
                            .cornerRadius(8)
                    }
                }
            }
        }
    }
}

public struct AlbumEditorBottomBarView: View {
    public let placements: [PersistedStampPlacementData]
    public let totalPagesCount: Int
    @ObservedObject public var editState: AlbumEditState
    @ObservedObject private var langManager = AppLanguageManager.shared

    public init(
        placements: [PersistedStampPlacementData],
        totalPagesCount: Int,
        editState: AlbumEditState
    ) {
        self.placements = placements
        self.totalPagesCount = totalPagesCount
        self.editState = editState
    }

    private var selectedPlacement: PersistedStampPlacementData? {
        placements.first(where: { $0.id == editState.selectedPlacementId })
    }

    public var body: some View {
        VStack(spacing: 0) {
            HStack {
                // Left Group: Add Stamp & Undo
                HStack(spacing: 8) {
                    Button(action: { editState.isVaultPickerOpen = true }) {
                        HStack(spacing: 4) {
                            Image(systemName: "plus")
                                .font(.system(size: 12, weight: .bold))
                            Text(langManager.localized("album_editor_add_stamp"))
                                .font(.system(size: 12, weight: .bold))
                        }
                        .foregroundColor(Color(red: 0.17, green: 0.14, blue: 0.13))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(BookGold)
                        .cornerRadius(6)
                    }

                    Button(action: { editState.undo() }) {
                        Image(systemName: "arrow.uturn.backward")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(editState.undoStack.isEmpty ? BookGold.opacity(0.3) : BookGold)
                            .padding(6)
                    }
                    .disabled(editState.undoStack.isEmpty)
                }

                Spacer()

                // Right Group: Selected Stamp Actions
                if let selected = selectedPlacement {
                    HStack(spacing: 6) {
                        // Bring Forward
                        Button(action: {
                            editState.bringForward(placementId: selected.id, currentPlacements: placements)
                        }) {
                            Image(systemName: "arrow.up.square")
                                .font(.system(size: 18))
                                .foregroundColor(BookGold)
                        }
                        .accessibilityLabel(langManager.localized("album_editor_bring_forward"))

                        // Send Backward
                        Button(action: {
                            editState.sendBackward(placementId: selected.id, currentPlacements: placements)
                        }) {
                            Image(systemName: "arrow.down.square")
                                .font(.system(size: 18))
                                .foregroundColor(BookGold)
                        }
                        .accessibilityLabel(langManager.localized("album_editor_send_backward"))

                        // Move Page
                        Button(action: { editState.isMovePageMenuOpen = true }) {
                            Image(systemName: "arrow.left.arrow.right")
                                .font(.system(size: 16))
                                .foregroundColor(BookGold)
                        }
                        .accessibilityLabel(langManager.localized("album_editor_move_page"))

                        // Delete
                        Button(action: {
                            editState.deletePlacement(placementId: selected.id, currentPlacement: selected)
                        }) {
                            Image(systemName: "trash")
                                .font(.system(size: 17))
                                .foregroundColor(Color.red.opacity(0.85))
                        }
                        .accessibilityLabel(langManager.localized("album_editor_remove_placement"))
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(EditorBarBg)
            .cornerRadius(12)
            .padding(.horizontal, 12)
            .padding(.bottom, 4)
        }
        .sheet(isPresented: $editState.isMovePageMenuOpen) {
            movePageSheet(selected: selectedPlacement)
        }
    }

    @ViewBuilder
    private func movePageSheet(selected: PersistedStampPlacementData?) -> some View {
        if let selected = selected {
            let maxPage = max(totalPagesCount, Int(selected.pageIndex) + 2)
            NavigationView {
                List(0..<maxPage, id: \.self) { p in
                    let isCurrent = p == Int(selected.pageIndex)
                    Button(action: {
                        editState.movePlacementToPage(
                            placementId: selected.id,
                            targetPageIndex: Int32(p),
                            currentPlacement: selected
                        )
                        editState.isMovePageMenuOpen = false
                    }) {
                        HStack {
                            Text(String(format: langManager.localized("book_page_number_format"), p + 1))
                                .foregroundColor(isCurrent ? BookGold : MSColors.ink)
                                .fontWeight(isCurrent ? .bold : .regular)
                            Spacer()
                            if isCurrent {
                                Image(systemName: "checkmark")
                                    .foregroundColor(BookGold)
                            }
                        }
                    }
                    .disabled(isCurrent)
                }
                .navigationTitle(langManager.localized("album_editor_move_page"))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button(langManager.localized("book_close")) {
                            editState.isMovePageMenuOpen = false
                        }
                    }
                }
            }
        }
    }
}
