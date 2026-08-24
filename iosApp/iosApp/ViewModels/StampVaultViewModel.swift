import SwiftUI
import Combine
import shared

/// Native Swift ViewModel for StampVaultScreen managing search, category filtering, stamp selection and detail modals.
class StampVaultViewModel: ObservableObject {
    @Published var searchText: String = ""
    @Published var selectedFilter: String = "All"
    @Published var selectedStamp: StampItem? = nil
    @Published var showEnvelopeModal: Bool = false
    @Published var isGridColumnCompact: Bool = false

    let filters = ["All", "Vintage", "Travel", "Coffee", "Special"]

    private let repository: SharedMemoStampRepository

    init(repository: SharedMemoStampRepository) {
        self.repository = repository
    }

    var stamps: [StampItem] {
        (repository.stamps.value as? [StampItem]) ?? []
    }

    var filteredStamps: [StampItem] {
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

    func selectFilter(_ filter: String) {
        selectedFilter = filter
        HapticFeedbackManager.shared.playImpact(style: .light)
    }

    func selectStamp(_ stamp: StampItem) {
        selectedStamp = stamp
        HapticFeedbackManager.shared.playImpact(style: .medium)
    }

    func openEnvelopeModal() {
        showEnvelopeModal = true
        HapticFeedbackManager.shared.playImpact(style: .medium)
    }

    func dismissEnvelopeModal() {
        showEnvelopeModal = false
    }

    func toggleGridDensity() {
        isGridColumnCompact.toggle()
        HapticFeedbackManager.shared.playImpact(style: .light)
    }
}
