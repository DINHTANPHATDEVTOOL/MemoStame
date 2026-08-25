import SwiftUI
#if canImport(UIKit)
import UIKit
#endif
import shared

struct StampVaultScreenView: View {
    let repository: SharedMemoStampRepository
    var onNavigateToCamera: () -> Void

    @State private var searchText: String = ""
    @State private var selectedFilter: String = "All"
    @State private var activeModal: VaultModalItem? = nil

    let filters = ["All", "Vintage", "Travel", "Coffee", "Special"]
    
    let columns = [
        GridItem(.flexible(), spacing: 14),
        GridItem(.flexible(), spacing: 14)
    ]

    var stamps: [StampItem] {
        (repository.stamps.value as? [StampItem]) ?? []
    }

    var filteredStamps: [StampItem] {
        var list = stamps
        if !searchText.isEmpty {
            list = list.filter { $0.title.localizedCaseInsensitiveContains(searchText) || ($0.location?.localizedCaseInsensitiveContains(searchText) ?? false) }
        }
        if selectedFilter != "All" {
            if selectedFilter.contains("Travel") {
                list = list.filter { $0.collectionId == "col_travel" }
            } else if selectedFilter.contains("Coffee") {
                list = list.filter { $0.collectionId == "col_coffee" }
            }
        }
        return list
    }

    var body: some View {
        VStack(spacing: 0) {
            // Header Title & Search
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("STAMP VAULT")
                            .font(.title2.bold())
                            .foregroundColor(Color(red: 0.15, green: 0.15, blue: 0.18))
                        Text("\(stamps.count) Collected Memories")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }

                    Spacer()

                    Button(action: onNavigateToCamera) {
                        Image(systemName: "plus.circle.fill")
                            .font(.title2)
                            .foregroundColor(Color(red: 0.85, green: 0.25, blue: 0.20))
                    }
                }

                // Search Bar Input
                HStack {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(MSColors.grey)
                    TextField("Search stamps or places...", text: $searchText)
                        .font(.subheadline)
                        .foregroundColor(MSColors.ink)
                }
                .padding(10)
                .background(Color.white)
                .cornerRadius(12)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(MSColors.lightGrey, lineWidth: 1)
                )

                // Category Chips
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(filters, id: \.self) { item in
                            Button(action: { selectedFilter = item }) {
                                Text(item)
                                    .font(.caption.bold())
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 8)
                                    .background(selectedFilter == item ? MSColors.ink : Color.white)
                                    .foregroundColor(selectedFilter == item ? Color.white : MSColors.ink)
                                    .cornerRadius(14)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 14)
                                            .stroke(selectedFilter == item ? MSColors.ink : MSColors.lightGrey, lineWidth: 1)
                                    )
                            }
                        }
                    }
                }
            }
            .padding(.horizontal)
            .padding(.top, 8)
            .padding(.bottom, 12)

            Divider()

            // Stamp Grid View
            ScrollView {
                LazyVGrid(columns: columns, spacing: 14) {
                    ForEach(filteredStamps, id: \.id) { stamp in
                        VStack {
                            DieCutStampView(
                                title: stamp.title,
                                imageUrl: stamp.stampImagePath,
                                location: stamp.location,
                                dateStr: "2026.08.18",
                                note: stamp.note,
                                shape: stamp.shape,
                                isInteractive: false
                            )
                        }
                        .onTapGesture {
                            activeModal = .detail(stamp)
                        }
                    }
                }
                .padding()
                .padding(.bottom, 140)
            }
        }
        .background(MSColors.paper.ignoresSafeArea())
        .sheet(item: $activeModal) { item in
            switch item {
            case .detail(let stamp):
                StampDetailModalView(
                    stamp: stamp,
                    onShare: {
                        activeModal = nil
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                            activeModal = .share(stamp)
                        }
                    },
                    onDismiss: { activeModal = nil }
                )
            case .share(let stamp):
                EnvelopeShareModalView(
                    stampTitle: stamp.title,
                    stampUrl: stamp.stampImagePath,
                    onDismiss: { activeModal = nil }
                )
            }
        }
    }
}

enum VaultModalItem: Identifiable {
    case detail(StampItem)
    case share(StampItem)

    var id: String {
        switch self {
        case .detail(let s): return "detail_\(s.id)"
        case .share(let s): return "share_\(s.id)"
        }
    }
}


struct StampDetailModalView: View {
    let stamp: StampItem
    let onShare: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Capsule()
                .fill(Color.gray.opacity(0.3))
                .frame(width: 40, height: 5)
                .padding(.top, 10)

            HStack {
                Text(stamp.title)
                    .font(.title3.bold())
                Spacer()
                Button(action: onDismiss) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.gray)
                }
            }
            .padding(.horizontal)

            DieCutStampView(
                title: stamp.title,
                imageUrl: stamp.stampImagePath,
                location: stamp.location,
                dateStr: "2026.08.18",
                note: stamp.note,
                shape: stamp.shape,
                isInteractive: true
            )
            .padding(.horizontal)

            Text("Tap stamp to flip & view memory note 🔄")
                .font(.caption)
                .foregroundColor(.secondary)

            VStack(spacing: 12) {
                Button(action: onShare) {
                    HStack {
                        Image(systemName: "envelope.fill")
                        Text("Share via Vintage Envelope")
                            .font(.body.bold())
                    }
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color(red: 0.85, green: 0.25, blue: 0.20))
                    .foregroundColor(.white)
                    .cornerRadius(12)
                }
            }
            .padding(.horizontal)
            .padding(.bottom, 20)
        }
    }
}
