import SwiftUI
#if canImport(UIKit)
import UIKit
#endif
import shared

// Album data model with canonical identity
struct AlbumItem: Identifiable {
    let id: String
    let title: String
    let desc: String
    let progress: String
    let iconName: String
    let coverColor: Color
    let stamps: [BookStampItem]
    let curatorName: String
}

struct CollectionScreenView: View {
    let repository: SharedMemoStampRepository
    @State private var selectedAlbum: AlbumItem? = nil
    @ObservedObject private var langManager = AppLanguageManager.shared

    var cloudStamps: [StampItem] {
        (repository.stamps.value as? [StampItem]) ?? []
    }

    var roomCollections: [CollectionItem] {
        (repository.collections.value as? [CollectionItem]) ?? []
    }

    var currentUserName: String {
        if let user = repository.currentUser.value as? UserProfile {
            let name = user.name.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
            if !name.isEmpty { return name }
        }
        return "Collector"
    }

    var albums: [AlbumItem] {
        let coverColors: [Color] = [
            Color(red: 0.62, green: 0.24, blue: 0.18),
            Color(red: 0.43, green: 0.30, blue: 0.25),
            Color(red: 0.55, green: 0.43, blue: 0.39),
            Color(red: 0.22, green: 0.38, blue: 0.35)
        ]
        let defaultIcons = ["travel", "cafe", "lifestyle", "special", "art"]

        if !roomCollections.isEmpty {
            // Priority 1: Real Persisted Collections with Stable Canonical IDs
            return roomCollections.indices.map { index in
                let col = roomCollections[index]
                let matchingStamps = cloudStamps.filter { $0.collectionId == col.id }
                let target = max(Int(col.targetCount), matchingStamps.count)
                let icon = col.iconKey ?? defaultIcons[index % defaultIcons.count]
                return AlbumItem(
                    id: col.id,
                    title: col.name,
                    desc: col.description_ ?? col.name,
                    progress: "\(matchingStamps.count)/\(target)",
                    iconName: icon,
                    coverColor: coverColors[index % coverColors.count],
                    stamps: matchingStamps.map { BookStampItem(id: $0.id, name: $0.title, imageUrl: $0.stampImagePath) },
                    curatorName: currentUserName
                )
            }
        }

        if cloudStamps.isEmpty {
            return []
        }

        // Fallback: Group by location ONLY when 0 persisted collections exist
        // Deterministic fallback IDs across launches (no unstable album_0, album_1)
        let defaultLoc = langManager.localized("book_legacy_default_location")
        let descFormat = langManager.localized("book_legacy_desc_format")

        let grouped = Dictionary(grouping: cloudStamps) { stamp -> String in
            let loc = stamp.location ?? ""
            return loc.isEmpty ? defaultLoc : loc
        }
        let sortedKeys = grouped.keys.sorted()
        return sortedKeys.indices.map { index in
            let key = sortedKeys[index]
            let stampList = grouped[key] ?? []
            let locHash = String(abs(key.hashValue), radix: 16)
            return AlbumItem(
                id: "loc_\(locHash)",
                title: key,
                desc: String(format: descFormat, key),
                progress: "\(stampList.count)/\(stampList.count)",
                iconName: defaultIcons[index % defaultIcons.count],
                coverColor: coverColors[index % coverColors.count],
                stamps: stampList.map { BookStampItem(id: $0.id, name: $0.title, imageUrl: $0.stampImagePath) },
                curatorName: currentUserName
            )
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            // Top App Bar
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(langManager.localized("book_shelf_title"))
                        .font(.title2.bold())
                        .foregroundColor(MSColors.ink)
                    Text(langManager.localized("book_shelf_subtitle"))
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
                    Text(langManager.localized("book_shelf_empty_title"))
                        .font(.headline.bold())
                        .foregroundColor(MSColors.ink)
                    Text(langManager.localized("book_shelf_empty_desc"))
                        .font(.subheadline)
                        .foregroundColor(MSColors.grey)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                    Spacer()
                }
            } else {
                // Carousel of Book Covers on Shelf
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
            // 📖 Production 2.5D Two-Page Stamp Book Renderer (replaces old flat TabView)
            StampBook3DRenderer(
                albumId: album.id,
                albumTitle: album.title,
                albumDescription: album.desc,
                curatorName: album.curatorName,
                coverColor: album.coverColor,
                iconKey: album.iconName,
                stamps: album.stamps,
                onStampClick: { _ in },
                onDismiss: { selectedAlbum = nil }
            )
        }
    }
}

// ==========================================
// 📕 BÌA CUỐN SÁCH NGOÀI DANH SÁCH (COVER PREVIEW)
// ==========================================
struct BookCoverPreviewView: View {
    let album: AlbumItem
    @ObservedObject private var langManager = AppLanguageManager.shared

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
                MemoStampIcon(key: album.iconName, size: 48, color: MSColors.gold)
                    .padding(.top, 24)

                Text(album.title)
                    .font(.system(size: 20, weight: .bold, design: .serif))
                    .foregroundColor(MSColors.gold)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)

                Text(album.desc)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(Color.white.opacity(0.85))
                    .multilineTextAlignment(.center)
                    .lineLimit(3)
                    .padding(.horizontal, 8)

                Spacer()

                // Progress Badge Pill
                let collectedText = String(format: langManager.localized("book_shelf_collected_format"), album.progress)
                Text(collectedText)
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

        path.closeSubpath()
        return path
    }
}
