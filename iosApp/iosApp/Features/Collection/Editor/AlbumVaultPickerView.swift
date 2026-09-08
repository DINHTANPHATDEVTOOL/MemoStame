import SwiftUI

private let GoldAccent = Color(red: 0.82, green: 0.65, blue: 0.35)
private let BookPaperBorder = Color(red: 0.91, green: 0.89, blue: 0.85)

public struct AlbumVaultPickerView: View {
    public let availableStamps: [BookStampItem]
    public let placements: [PersistedStampPlacementData]
    public let targetPageIndex: Int32
    @ObservedObject public var editState: AlbumEditState
    public var onDismiss: () -> Void
    @ObservedObject private var langManager = AppLanguageManager.shared

    public init(
        availableStamps: [BookStampItem],
        placements: [PersistedStampPlacementData],
        targetPageIndex: Int32,
        editState: AlbumEditState,
        onDismiss: @escaping () -> Void = {}
    ) {
        self.availableStamps = availableStamps
        self.placements = placements
        self.targetPageIndex = targetPageIndex
        self.editState = editState
        self.onDismiss = onDismiss
    }

    private var placedIds: Set<String> {
        Set(placements.map { $0.stampId })
    }

    public var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                if availableStamps.isEmpty {
                    Spacer()
                    Text(langManager.localized("album_editor_vault_empty"))
                        .font(.subheadline)
                        .foregroundColor(MSColors.grey)
                    Spacer()
                } else {
                    ScrollView {
                        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                            ForEach(availableStamps, id: \.id) { stamp in
                                let isPlaced = placedIds.contains(stamp.id)
                                Button(action: {
                                    if !isPlaced {
                                        editState.addPlacementFromVault(
                                            stampId: stamp.id,
                                            targetPageIndex: targetPageIndex,
                                            existingPlacements: placements
                                        )
                                    }
                                }) {
                                    VStack(spacing: 4) {
                                        ZStack {
                                            RoundedRectangle(cornerRadius: 6)
                                                .fill(Color.white)
                                                .overlay(
                                                    RoundedRectangle(cornerRadius: 6)
                                                        .stroke(isPlaced ? Color.gray.opacity(0.3) : GoldAccent.opacity(0.5), lineWidth: 1)
                                                )
                                                .aspectRatio(0.85, contentMode: .fit)

                                            if !stamp.imageUrl.isEmpty {
                                                AsyncImage(url: URL(string: stamp.imageUrl)) { phase in
                                                    switch phase {
                                                    case .success(let img):
                                                        img.resizable().scaledToFill()
                                                    default:
                                                        Color.gray.opacity(0.1)
                                                    }
                                                }
                                                .clipShape(RoundedRectangle(cornerRadius: 4))
                                                .padding(3)
                                            }

                                            if isPlaced {
                                                Color.black.opacity(0.35)
                                                    .clipShape(RoundedRectangle(cornerRadius: 6))
                                                Text(langManager.localized("album_editor_vault_placed"))
                                                    .font(.system(size: 10, weight: .bold))
                                                    .foregroundColor(.white)
                                                    .padding(.horizontal, 6)
                                                    .padding(.vertical, 2)
                                                    .background(Color.black.opacity(0.7))
                                                    .cornerRadius(4)
                                            }
                                        }

                                        Text(stamp.name)
                                            .font(.system(size: 10, weight: .medium))
                                            .foregroundColor(isPlaced ? MSColors.grey : MSColors.ink)
                                            .lineLimit(1)
                                    }
                                }
                                .disabled(isPlaced)
                                .buttonStyle(PlainButtonStyle())
                            }
                        }
                        .padding(16)
                    }
                }
            }
            .background(Color(red: 0.98, green: 0.96, blue: 0.94).ignoresSafeArea())
            .navigationTitle(langManager.localized("album_editor_vault_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(langManager.localized("book_close")) {
                        onDismiss()
                    }
                    .foregroundColor(GoldAccent)
                }
            }
        }
    }
}
