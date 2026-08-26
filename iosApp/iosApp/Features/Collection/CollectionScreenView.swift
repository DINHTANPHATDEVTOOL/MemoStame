import SwiftUI
#if canImport(UIKit)
import UIKit
#endif
import shared

// Sample album data model matching user's spec
struct AlbumItem: Identifiable {
    let id: String
    let title: String
    let desc: String
    let progress: String
    let iconName: String
    let coverColor: Color
    let stamps: [AlbumStampItem]
}

struct AlbumStampItem: Identifiable {
    let id: String
    let name: String
    let imageUrl: String
}



struct CollectionScreenView: View {
    let repository: SharedMemoStampRepository
    @State private var selectedAlbum: AlbumItem? = nil

    var cloudStamps: [StampItem] {
        (repository.stamps.value as? [StampItem]) ?? []
    }

    var roomCollections: [CollectionItem] {
        (repository.collections.value as? [CollectionItem]) ?? []
    }

    var albums: [AlbumItem] {
        let coverColors: [Color] = [
            Color(red: 0.62, green: 0.24, blue: 0.18),
            Color(red: 0.43, green: 0.30, blue: 0.25),
            Color(red: 0.55, green: 0.43, blue: 0.39),
            Color(red: 0.22, green: 0.38, blue: 0.35)
        ]
        let icons = ["airplane", "cup.and.saucer.fill", "building.columns.fill", "map.fill", "sparkles"]

        if !roomCollections.isEmpty {
            return roomCollections.enumerated().map { (index: Int, col: CollectionItem) -> AlbumItem in
                let matchingStamps = cloudStamps.filter { $0.collectionId == col.id }
                return AlbumItem(
                    id: col.id,
                    title: col.name,
                    desc: col.description_ ?? "Bộ sưu tập tem kỷ niệm \(col.name)",
                    progress: "\(matchingStamps.count)/\(max(col.targetCount, matchingStamps.count))",
                    iconName: icons[index % icons.count],
                    coverColor: coverColors[index % coverColors.count],
                    stamps: matchingStamps.map { AlbumStampItem(id: $0.id, name: $0.title, imageUrl: $0.stampImagePath) }
                )
            }
        }

        if cloudStamps.isEmpty {
            return []
        }
        let grouped = Dictionary(grouping: cloudStamps) { stamp -> String in
            let loc = stamp.location ?? ""
            return loc.isEmpty ? "Kỷ niệm chung" : loc
        }
        return grouped.enumerated().map { index, entry in
            return AlbumItem(
                id: "album_\(index)",
                title: entry.key,
                desc: "Bộ sưu tập tem kỷ niệm tại \(entry.key)",
                progress: "\(entry.value.count)/\(entry.value.count)",
                iconName: icons[index % icons.count],
                coverColor: coverColors[index % coverColors.count],
                stamps: entry.value.map { AlbumStampItem(id: $0.id, name: $0.title, imageUrl: $0.stampImagePath) }
            )
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            // Top App Bar
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("STAMP ALBUMS")
                        .font(.title2.bold())
                        .foregroundColor(MSColors.ink)
                    Text("Curated Memory Collections • Chạm để mở sách")
                        .font(.caption)
                        .foregroundColor(MSColors.grey)
                }
                Spacer()
            }
            .padding(.horizontal)
            .padding(.top, 12)
            .padding(.bottom, 8)

            Divider()

            if albums.isEmpty {
                VStack(spacing: 16) {
                    Spacer()
                    Image(systemName: "books.vertical.fill")
                        .font(.system(size: 54))
                        .foregroundColor(MSColors.stamp.opacity(0.6))
                    Text("Chưa có bộ sưu tập tem nào")
                        .font(.headline.bold())
                        .foregroundColor(MSColors.ink)
                    Text("Tất cả tem dán từ cloud sẽ tự động nhóm thành album theo địa điểm tại đây!")
                        .font(.subheadline)
                        .foregroundColor(MSColors.grey)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                    Spacer()
                }
            } else {
                // PageView Carousel of Book Covers
                GeometryReader { geo in
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 20) {
                            ForEach(albums) { album in
                                BookCoverPreviewView(album: album)
                                    .frame(width: geo.size.width * 0.78, height: geo.size.height * 0.85)
                                    .onTapGesture {
                                        selectedAlbum = album
                                    }
                            }
                        }
                        .padding(.horizontal, geo.size.width * 0.11)
                        .padding(.vertical, 24)
                    }
                }
            }
        }
        .background(MSColors.paper.ignoresSafeArea())
        .fullScreenCover(item: $selectedAlbum) { album in
            StampBookViewerModalView(album: album, onDismiss: { selectedAlbum = nil })
        }
    }
}

// ==========================================
// 📕 BÌA CUỐN SÁCH NGOÀI DANH SÁCH (COVER PREVIEW)
// ==========================================
struct BookCoverPreviewView: View {
    let album: AlbumItem

    var body: some View {
        ZStack {
            // Leather Book Cover Shape
            BookCoverShape(
                topLeading: 4,
                bottomLeading: 4,
                topTrailing: 16,
                bottomTrailing: 16
            )
            .fill(album.coverColor)
            .shadow(color: Color.black.opacity(0.28), radius: 12, x: 8, y: 8)

            // Book Spine Shadow on Left
            HStack {
                LinearGradient(
                    colors: [
                        Color.black.opacity(0.4),
                        Color.white.opacity(0.1),
                        Color.black.opacity(0.2)
                    ],
                    startPoint: .leading,
                    endPoint: .trailing
                )
                .frame(width: 24)
                Spacer()
            }

            // Gold Foil Border & Content
            VStack(spacing: 16) {
                Image(systemName: album.iconName)
                    .font(.system(size: 48, weight: .bold))
                    .foregroundColor(MSColors.gold)
                    .padding(.top, 24)

                Text(album.title)
                    .font(.system(size: 20, weight: .bold, design: .serif))
                    .foregroundColor(MSColors.gold)
                    .multilineTextAlignment(.center)

                Text(album.desc)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(Color.white.opacity(0.85))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 8)

                Spacer()

                // Progress Badge Pill
                Text("\(album.progress) collected")
                    .font(.system(size: 12, weight: .bold, design: .monospaced))
                    .foregroundColor(MSColors.gold)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 6)
                    .background(Color.black.opacity(0.35))
                    .cornerRadius(20)
                    .overlay(
                        RoundedRectangle(cornerRadius: 20)
                            .stroke(MSColors.gold, lineWidth: 1)
                    )
                    .padding(.bottom, 24)
            }
            .padding(.horizontal, 24)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(MSColors.gold.opacity(0.6), lineWidth: 1.5)
                    .padding(.leading, 32)
                    .padding(.trailing, 16)
                    .padding(.vertical, 16)
            )
        }
    }
}

// ==========================================
// 📖 MÀN HÌNH MỞ SÁCH LẬT TỪNG TRANG TEM (STAMP BOOK VIEWER)
// ==========================================
struct StampBookViewerModalView: View {
    let album: AlbumItem
    let onDismiss: () -> Void

    @State private var currentPage: Int = 0

    var totalPages: Int {
        let stampPages = Int(ceil(Double(album.stamps.count) / 4.0))
        return max(1, stampPages) + 1 // +1 for Intro Page
    }

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Button(action: onDismiss) {
                    HStack(spacing: 6) {
                        Image(systemName: "chevron.left")
                        Text("Back")
                    }
                    .font(.headline.bold())
                    .foregroundColor(MSColors.gold)
                }

                Spacer()

                Text(album.title)
                    .font(.headline.bold())
                    .foregroundColor(MSColors.gold)

                Spacer()

                Button(action: onDismiss) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.title3)
                        .foregroundColor(MSColors.gold.opacity(0.7))
                }
            }
            .padding(.horizontal)
            .padding(.vertical, 12)

            // Book Canvas
            GeometryReader { geo in
                TabView(selection: $currentPage) {
                    ForEach(0..<totalPages, id: \.self) { pageIndex in
                        BookPageView(pageIndex: pageIndex, totalPages: totalPages, album: album)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 12)
                            .tag(pageIndex)
                    }
                }
                .tabViewStyle(PageTabViewStyle(indexDisplayMode: .never))
            }

            // Bottom Navigation Bar
            SafeAreaView {
                HStack(spacing: 24) {
                    Button(action: {
                        if currentPage > 0 {
                            withAnimation(.easeInOut(duration: 0.3)) {
                                currentPage -= 1
                            }
                        }
                    }) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(currentPage > 0 ? MSColors.gold : Color.gray.opacity(0.4))
                            .padding(10)
                            .background(Color.white.opacity(0.08))
                            .clipShape(Circle())
                    }
                    .disabled(currentPage == 0)

                    Text("Trang \(currentPage + 1) / \(totalPages)")
                        .font(.system(size: 14, weight: .bold, design: .serif))
                        .foregroundColor(MSColors.gold)

                    Button(action: {
                        if currentPage < totalPages - 1 {
                            withAnimation(.easeInOut(duration: 0.3)) {
                                currentPage += 1
                            }
                        }
                    }) {
                        Image(systemName: "chevron.right")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(currentPage < totalPages - 1 ? MSColors.gold : Color.gray.opacity(0.4))
                            .padding(10)
                            .background(Color.white.opacity(0.08))
                            .clipShape(Circle())
                    }
                    .disabled(currentPage == totalPages - 1)
                }
                .padding(.vertical, 12)
            }
        }
        .background(Color(red: 0.17, green: 0.14, blue: 0.13).ignoresSafeArea()) // Dark Wood #2C2421
    }
}

// Single Book Page
struct BookPageView: View {
    let pageIndex: Int
    let totalPages: Int
    let album: AlbumItem

    var body: some View {
        ZStack {
            // Book Page Cream Paper Base
            RoundedRectangle(cornerRadius: 12)
                .fill(MSColors.creamCard)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(MSColors.gold.opacity(0.4), lineWidth: 1.5)
                )
                .shadow(color: Color.black.opacity(0.45), radius: 16, x: 0, y: 8)

            // Book Spine Fold Shadow on Left
            HStack {
                LinearGradient(
                    colors: [Color.black.opacity(0.22), Color.clear],
                    startPoint: .leading,
                    endPoint: .trailing
                )
                .frame(width: 18)
                Spacer()
            }
            .clipShape(RoundedRectangle(cornerRadius: 12))

            // Page Content
            VStack {
                if pageIndex == 0 {
                    // Page 1: Intro Page
                    VStack(spacing: 16) {
                        Spacer()
                        Image(systemName: album.iconName)
                            .font(.system(size: 44, weight: .bold))
                            .foregroundColor(MSColors.stamp)

                        Text(album.title)
                            .font(.system(size: 22, weight: .bold, design: .serif))
                            .foregroundColor(MSColors.ink)

                        Text(album.desc)
                            .font(.system(size: 12, weight: .medium))
                            .italic()
                            .foregroundColor(MSColors.grey)
                            .multilineTextAlignment(.center)

                        Divider()
                            .padding(.horizontal, 32)
                            .padding(.vertical, 8)

                        Text("“Từng con tem lưu giữ một mảnh ký ức nguyên vẹn theo dòng thời gian.”")
                            .font(.system(size: 12, weight: .medium, design: .serif))
                            .foregroundColor(MSColors.ink)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 24)

                        Spacer()

                        HStack(spacing: 6) {
                            Text("Vuốt sang phải để mở tem")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(MSColors.grey)
                            Image(systemName: "arrow.right")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(MSColors.grey)
                        }
                        .padding(.bottom, 20)
                    }
                    .padding(24)
                } else {
                    // Page 2+: 2x2 Stamp Grid
                    let startIndex = (pageIndex - 1) * 4
                    let pageStamps = Array(album.stamps.dropFirst(startIndex).prefix(4))

                    let columns = [
                        GridItem(.flexible(), spacing: 14),
                        GridItem(.flexible(), spacing: 14)
                    ]

                    LazyVGrid(columns: columns, spacing: 14) {
                        ForEach(0..<4, id: \.self) { slotIndex in
                            if slotIndex < pageStamps.count {
                                let stamp = pageStamps[slotIndex]
                                StampBookSlotView(stamp: stamp)
                            } else {
                                EmptyStampSlotView()
                            }
                        }
                    }
                    .padding(20)
                }
            }
        }
    }
}

// Unlocked Stamp Slot
struct StampBookSlotView: View {
    let stamp: AlbumStampItem

    var body: some View {
        VStack(spacing: 6) {
            AsyncImage(url: URL(string: stamp.imageUrl)) { phase in
                if let img = phase.image {
                    img.resizable().aspectRatio(contentMode: .fill)
                } else {
                    Color.gray.opacity(0.2)
                }
            }
            .frame(height: 100)
            .clipShape(RoundedRectangle(cornerRadius: 6))
            .overlay(
                RoundedRectangle(cornerRadius: 6)
                    .stroke(Color.gray.opacity(0.2), lineWidth: 1)
            )

            Text(stamp.name)
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(MSColors.ink)
                .lineLimit(1)
        }
        .padding(8)
        .background(Color.white)
        .cornerRadius(8)
        .shadow(color: Color.black.opacity(0.08), radius: 4, x: 0, y: 2)
    }
}

// Locked Stamp Slot
struct EmptyStampSlotView: View {
    var body: some View {
        VStack {
            Spacer()
            Image(systemName: "lock")
                .font(.system(size: 22))
                .foregroundColor(MSColors.gold.opacity(0.7))
            Spacer()
        }
        .frame(height: 126)
        .frame(maxWidth: .infinity)
        .background(MSColors.creamCard.opacity(0.6))
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(MSColors.gold.opacity(0.4), style: StrokeStyle(lineWidth: 1, dash: [4]))
        )
    }
}

// Safe Area Helper View
struct SafeAreaView<Content: View>: View {
    let content: () -> Content
    init(@ViewBuilder content: @escaping () -> Content) {
        self.content = content
    }
    var body: some View {
        VStack(spacing: 0) {
            content()
        }
    }
}

// Custom Book Cover Shape compatible with iOS 15.0+
struct BookCoverShape: Shape {
    var topLeading: CGFloat = 4
    var bottomLeading: CGFloat = 4
    var topTrailing: CGFloat = 16
    var bottomTrailing: CGFloat = 16

    func path(in rect: CGRect) -> Path {
        var path = Path()
        let w = rect.width
        let h = rect.height

        path.move(to: CGPoint(x: topLeading, y: 0))
        path.addLine(to: CGPoint(x: w - topTrailing, y: 0))
        path.addQuadCurve(to: CGPoint(x: w, y: topTrailing), control: CGPoint(x: w, y: 0))

        path.addLine(to: CGPoint(x: w, y: h - bottomTrailing))
        path.addQuadCurve(to: CGPoint(x: w - bottomTrailing, y: h), control: CGPoint(x: w, y: h))

        path.addLine(to: CGPoint(x: bottomLeading, y: h))
        path.addQuadCurve(to: CGPoint(x: 0, y: h - bottomLeading), control: CGPoint(x: 0, y: h))

        path.addLine(to: CGPoint(x: 0, y: topLeading))
        path.addQuadCurve(to: CGPoint(x: topLeading, y: 0), control: CGPoint(x: 0, y: 0))

        return path
    }
}

