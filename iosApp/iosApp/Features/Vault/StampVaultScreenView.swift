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

            // Stamp Collector Album Banner (Sổ Tay Bộ Sưu Tập Tem Kỷ Niệm)
            ScrollView {
                VStack(spacing: 16) {
                    // Collector Album Header Card
                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 6) {
                                    Text("📖 SỔ TAY BỘ SƯU TẬP TEM KỶ NIỆM")
                                        .font(.caption.bold())
                                        .foregroundColor(MSColors.gold)
                                    Text("• 2026 EDITION")
                                        .font(.caption2.bold())
                                        .foregroundColor(MSColors.grey)
                                }
                                Text("MEMOSTAMP COLLECTOR ALBUM")
                                    .font(.headline.bold())
                                    .foregroundColor(MSColors.ink)
                            }
                            Spacer()
                            ZStack {
                                Circle()
                                    .fill(MSColors.stamp.opacity(0.12))
                                    .frame(width: 44, height: 44)
                                Image(systemName: "book.closed.fill")
                                    .font(.system(size: 20))
                                    .foregroundColor(MSColors.stamp)
                            }
                        }

                        // Progress Bar & Stats
                        VStack(spacing: 6) {
                            HStack {
                                Text("Tiến độ sưu tập")
                                    .font(.caption.bold())
                                    .foregroundColor(MSColors.ink)
                                Spacer()
                                Text("\(filteredStamps.count) / 24 Tem")
                                    .font(.caption.bold())
                                    .foregroundColor(MSColors.stamp)
                            }

                            GeometryReader { geo in
                                ZStack(alignment: .leading) {
                                    RoundedRectangle(cornerRadius: 6)
                                        .fill(MSColors.lightGrey)
                                        .frame(height: 8)
                                    RoundedRectangle(cornerRadius: 6)
                                        .fill(MSColors.stamp)
                                        .frame(width: min(geo.size.width * CGFloat(filteredStamps.count) / 24.0, geo.size.width), height: 8)
                                }
                            }
                            .frame(height: 8)
                        }

                        HStack(spacing: 16) {
                            HStack(spacing: 4) {
                                Image(systemName: "mappin.and.ellipse")
                                    .font(.caption)
                                    .foregroundColor(MSColors.stamp)
                                Text("3 Địa điểm")
                                    .font(.caption)
                                    .foregroundColor(MSColors.grey)
                            }
                            HStack(spacing: 4) {
                                Image(systemName: "sparkles")
                                    .font(.caption)
                                    .foregroundColor(MSColors.gold)
                                Text("Tem Hiếm #2026")
                                    .font(.caption)
                                    .foregroundColor(MSColors.grey)
                            }
                        }
                    // Album Collections & Privacy Management Row
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("BỘ SƯU TẬP & ALBUM CỦA TÔI")
                                .font(.caption.bold())
                                .foregroundColor(MSColors.ink)
                            Spacer()
                            Text("Nhấn vào nhãn để chỉnh quyền xem")
                                .font(.caption2)
                                .foregroundColor(MSColors.grey)
                        }

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 10) {
                                ForEach((repository.collections.value as? [CollectionItem]) ?? [], id: \.id) { col in
                                    VStack(alignment: .leading, spacing: 6) {
                                        HStack {
                                            Text(col.iconEmoji)
                                                .font(.system(size: 18))
                                            Spacer()
                                            Button(action: {
                                                repository.toggleCollectionPrivacy(collectionId: col.id)
                                            }) {
                                                HStack(spacing: 3) {
                                                    Text(col.privacy == "ONLY_ME" ? "🔒 Mình tôi" : "👥 Bạn bè")
                                                        .font(.system(size: 9, weight: .bold))
                                                }
                                                .padding(.horizontal, 7)
                                                .padding(.vertical, 3)
                                                .background(col.privacy == "ONLY_ME" ? Color.red.opacity(0.12) : Color.green.opacity(0.12))
                                                .foregroundColor(col.privacy == "ONLY_ME" ? Color.red : Color.green)
                                                .cornerRadius(10)
                                            }
                                        }

                                        Text(col.name)
                                            .font(.caption.bold())
                                            .foregroundColor(MSColors.ink)
                                            .lineLimit(1)

                                        Text("\(col.targetCount) tem kỷ niệm")
                                            .font(.caption2)
                                            .foregroundColor(MSColors.grey)
                                    }
                                    .padding(10)
                                    .frame(width: 140)
                                    .background(Color.white)
                                    .cornerRadius(14)
                                    .shadow(color: Color.black.opacity(0.04), radius: 3, x: 0, y: 2)
                                }
                            }
                        }
                    }

                    // Stamp Grid View
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

                        // Placeholder Empty Album Slots for remaining collection
                        ForEach(0..<max(0, 6 - filteredStamps.count), id: \.self) { index in
                            VStack(spacing: 8) {
                                ZStack {
                                    RoundedRectangle(cornerRadius: 8)
                                        .stroke(style: StrokeStyle(lineWidth: 1.5, dash: [4]))
                                        .foregroundColor(MSColors.grey.opacity(0.5))
                                        .background(Color.black.opacity(0.02))

                                    VStack(spacing: 4) {
                                        Image(systemName: "plus.circle.fill")
                                            .font(.system(size: 24))
                                            .foregroundColor(MSColors.stamp.opacity(0.4))
                                        Text("Kỷ niệm #0\(filteredStamps.count + index + 1)")
                                            .font(.system(size: 9, weight: .bold, design: .monospaced))
                                            .foregroundColor(MSColors.grey)
                                    }
                                }
                                .frame(height: 200)
                            }
                            .onTapGesture {
                                onNavigateToCamera()
                            }
                        }
                    }
                }
                .padding(.horizontal)
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
