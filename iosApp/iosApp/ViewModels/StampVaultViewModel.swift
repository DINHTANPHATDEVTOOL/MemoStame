import SwiftUI
import Combine
import shared

/// Native Swift ViewModel for StampVaultScreen managing search, category filtering, stamp selection and detail modals.
public class StampVaultViewModel: ObservableObject {
    @Published public var searchText: String = ""
    @Published public var selectedFilter: String = "All"
    @Published public var selectedStamp: StampItem? = nil
    @Published public var showEnvelopeModal: Bool = false
    @Published public var isGridColumnCompact: Bool = false

    public let filters = ["All", "Vintage", "Travel", "Coffee", "Special"]

    private let repository: SharedMemoStampRepository

    public init(repository: SharedMemoStampRepository) {
        self.repository = repository
    }

    public var stamps: [StampItem] {
        (repository.stamps.value as? [StampItem]) ?? []
    }

    public var filteredStamps: [StampItem] {
        var list = stamps
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if !query.isEmpty {
            list = list.filter { stamp in
                stamp.title.lowercased().contains(query) ||
                (stamp.location?.lowercased().contains(query) ?? false) ||
                stamp.note.lowercased().contains(query)
            }
        }
        if selectedFilter != "All" {
            if selectedFilter.contains("Travel") {
                list = list.filter { $0.collectionId == "col_travel" }
            } else if selectedFilter.contains("Coffee") {
                list = list.filter { $0.collectionId == "col_coffee" }
            } else if selectedFilter.contains("Special") {
                list = list.filter { $0.collectionId == "col_special" }
            }
        }
        return list
    }

    public func selectFilter(_ filter: String) {
        selectedFilter = filter
        HapticFeedbackManager.shared.playImpact(style: .light)
    }

    public func selectStamp(_ stamp: StampItem) {
        selectedStamp = stamp
        HapticFeedbackManager.shared.playImpact(style: .medium)
    }

    public func openEnvelopeModal() {
        showEnvelopeModal = true
        HapticFeedbackManager.shared.playImpact(style: .medium)
    }

    public func dismissEnvelopeModal() {
        showEnvelopeModal = false
    }

    public func toggleGridDensity() {
        isGridColumnCompact.toggle()
        HapticFeedbackManager.shared.playImpact(style: .light)
    }
}
