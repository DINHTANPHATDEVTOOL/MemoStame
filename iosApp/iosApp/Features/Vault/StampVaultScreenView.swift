import SwiftUI
#if canImport(UIKit)
import UIKit
import Photos
#endif
import shared

struct StampVaultScreenView: View {
    let repository: SharedMemoStampRepository
    var onNavigateToCamera: () -> Void

    @State private var searchText: String = ""
    @State private var selectedFilter: String = "Tất cả"
    @State private var activeModal: VaultModalItem? = nil
    @State private var showCreateAlbumModal: Bool = false

    let columns = [
        GridItem(.flexible(), spacing: 14),
        GridItem(.flexible(), spacing: 14)
    ]

    var stamps: [StampItem] {
        (repository.stamps.value as? [StampItem]) ?? []
    }

    private func formatDate(_ timestamp: Int64) -> String {
        guard timestamp > 0 else {
            let formatter = DateFormatter()
            formatter.dateFormat = "yyyy.MM.dd"
            return formatter.string(from: Date())
        }
        let date = Date(timeIntervalSince1970: TimeInterval(timestamp) / 1000.0)
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy.MM.dd"
        return formatter.string(from: date)
    }

    let filters = ["Tất cả", "Mới nhất", "Cũ nhất", "Yêu thích", "Du lịch", "Cà phê"]

    var filteredStamps: [StampItem] {
        var list = stamps
        if !searchText.isEmpty {
            list = list.filter {
                $0.title.localizedCaseInsensitiveContains(searchText) ||
                ($0.location?.localizedCaseInsensitiveContains(searchText) ?? false) ||
                $0.note.localizedCaseInsensitiveContains(searchText)
            }
        }
        switch selectedFilter {
        case "Mới nhất":
            list = list.sorted(by: { $0.createdAt > $1.createdAt })
        case "Cũ nhất":
            list = list.sorted(by: { $0.createdAt < $1.createdAt })
        case "Yêu thích":
            list = list.filter { $0.favorite }
        case "Du lịch":
            list = list.filter { $0.collectionId == "col_travel" || ($0.mood ?? "").contains("Travel") }
        case "Cà phê":
            list = list.filter { $0.collectionId == "col_coffee" || ($0.mood ?? "").contains("Chill") }
        default:
            break
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
                                    Image(systemName: "book.fill")
                                        .font(.caption.bold())
                                        .foregroundColor(MSColors.gold)
                                    Text("SỔ TAY BỘ SƯU TẬP TEM KỶ NIỆM")
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
                    }
                    .padding(14)
                    .background(Color.white)
                    .cornerRadius(16)

                    // Album Collections & Privacy Management Row
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("BỘ SƯU TẬP & ALBUM CỦA TÔI")
                                .font(.caption.bold())
                                .foregroundColor(MSColors.ink)
                            Spacer()
                            Button(action: { showCreateAlbumModal = true }) {
                                HStack(spacing: 3) {
                                    Image(systemName: "plus.circle.fill")
                                        .font(.system(size: 11))
                                    Text("Tạo Album")
                                        .font(.caption2.bold())
                                }
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(MSColors.stamp)
                                .foregroundColor(.white)
                                .cornerRadius(10)
                            }
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
                                                IOSLocalPersistenceStore.shared.saveData(repository: repository)
                                            }) {
                                                HStack(spacing: 3) {
                                                    Image(systemName: col.privacy == "ONLY_ME" ? "lock.fill" : "person.2.fill")
                                                        .font(.system(size: 9))
                                                    Text(col.privacy == "ONLY_ME" ? "Mình tôi" : "Bạn bè")
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
                                    dateStr: formatDate(stamp.createdAt),
                                    note: stamp.note,
                                    shape: stamp.shape,
                                    isInteractive: false,
                                    showMoldOverlay: false,
                                    fittedInGrid: true
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
                    repository: repository,
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
        .sheet(isPresented: $showCreateAlbumModal) {
            CreateAlbumSheetView(repository: repository)
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
    let repository: SharedMemoStampRepository
    var onShare: () -> Void
    var onDismiss: () -> Void

    @State private var isFavorite: Bool
    @State private var showDeleteConfirm: Bool = false
    @State private var showExportToast: Bool = false

    init(stamp: StampItem, repository: SharedMemoStampRepository, onShare: @escaping () -> Void, onDismiss: @escaping () -> Void) {
        self.stamp = stamp
        self.repository = repository
        self.onShare = onShare
        self.onDismiss = onDismiss
        _isFavorite = State(initialValue: stamp.favorite)
    }

    var body: some View {
        VStack(spacing: 16) {
            Capsule()
                .fill(Color.gray.opacity(0.3))
                .frame(width: 40, height: 5)
                .padding(.top, 10)

            HStack {
                Text(stamp.title)
                    .font(.title3.bold())
                Spacer()

                Button(action: {
                    _ = repository.toggleFavorite(stampId: stamp.id)
                    isFavorite.toggle()
                    IOSLocalPersistenceStore.shared.saveData(repository: repository)
                    HapticFeedbackManager.shared.playSuccess()
                }) {
                    Image(systemName: isFavorite ? "heart.fill" : "heart")
                        .font(.title3)
                        .foregroundColor(isFavorite ? Color(red: 0.85, green: 0.25, blue: 0.20) : .gray)
                        .padding(6)
                }

                Button(action: onDismiss) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.gray)
                        .padding(6)
                }
            }
            .padding(.horizontal)

            let formattedMemoryDate: String = {
                let date = Date(timeIntervalSince1970: TimeInterval(stamp.memoryDate) / 1000.0)
                let formatter = DateFormatter()
                formatter.dateFormat = "yyyy.MM.dd"
                return formatter.string(from: date)
            }()

            DieCutStampView(
                title: stamp.title,
                imageUrl: stamp.stampImagePath,
                location: stamp.location,
                dateStr: formattedMemoryDate,
                note: stamp.note,
                shape: stamp.shape,
                isInteractive: true
            )
            .padding(.horizontal)

            HStack(spacing: 4) {
                Text("Tap stamp to flip & view memory note")
                    .font(.caption)
                    .foregroundColor(.secondary)
                Image(systemName: "arrow.triangle.2.circlepath")
                    .font(.caption)
                    .foregroundColor(MSColors.stamp)
            }

            if showExportToast {
                Text("✓ Đã lưu con tem vào Thư viện ảnh!")
                    .font(.caption.bold())
                    .foregroundColor(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 6)
                    .background(Color.black.opacity(0.75))
                    .cornerRadius(12)
            }

            VStack(spacing: 10) {
                HStack(spacing: 12) {
                    Button(action: {
                        #if canImport(UIKit)
                        var inputImage: UIImage? = nil
                        if let url = URL(string: stamp.stampImagePath), let data = try? Data(contentsOf: url) {
                            inputImage = UIImage(data: data)
                        } else if let img = UIImage(contentsOfFile: stamp.stampImagePath) {
                            inputImage = img
                        }
                        
                        guard let imageToSave = inputImage else { return }
                        
                        PHPhotoLibrary.requestAuthorization { status in
                            if status == .authorized || status == .limited {
                                PHPhotoLibrary.shared().performChanges({
                                    PHAssetChangeRequest.creationRequestForAsset(from: imageToSave)
                                }) { success, error in
                                    DispatchQueue.main.async {
                                        if success {
                                            showExportToast = true
                                            HapticFeedbackManager.shared.playSuccess()
                                            DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
                                                showExportToast = false
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        #endif
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "square.and.arrow.down")
                            Text("Export PNG")
                                .font(.subheadline.bold())
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Color.blue.opacity(0.12))
                        .foregroundColor(.blue)
                        .cornerRadius(12)
                    }

                    Button(action: {
                        #if canImport(UIKit)
                        var itemsToShare: [Any] = [stamp.title]
                        if let url = URL(string: stamp.stampImagePath), let data = try? Data(contentsOf: url), let img = UIImage(data: data) {
                            itemsToShare.append(img)
                        } else if let img = UIImage(contentsOfFile: stamp.stampImagePath) {
                            itemsToShare.append(img)
                        }
                        let av = UIActivityViewController(activityItems: itemsToShare, applicationActivities: nil)
                        if let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                           let root = scene.windows.first?.rootViewController {
                            root.present(av, animated: true)
                        }
                        #endif
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "square.and.arrow.up")
                            Text("Share Native")
                                .font(.subheadline.bold())
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Color.green.opacity(0.12))
                        .foregroundColor(.green)
                        .cornerRadius(12)
                    }

                    Button(action: onShare) {
                        HStack(spacing: 6) {
                            Image(systemName: "envelope.fill")
                            Text("Bao thư")
                                .font(.subheadline.bold())
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Color(red: 0.85, green: 0.25, blue: 0.20))
                        .foregroundColor(.white)
                        .cornerRadius(12)
                    }
                }

                Button(action: { showDeleteConfirm = true }) {
                    HStack(spacing: 6) {
                        Image(systemName: "trash")
                        Text("Xóa con tem này")
                            .font(.caption.bold())
                    }
                    .foregroundColor(.red.opacity(0.8))
                    .padding(.top, 4)
                }
            }
            .padding(.horizontal)
            .padding(.bottom, 20)
        }
        .alert(isPresented: $showDeleteConfirm) {
            Alert(
                title: Text("Xóa ký ức này?"),
                message: Text("Hành động này sẽ xóa con tem khỏi Bộ sưu tập của bạn."),
                primaryButton: .destructive(Text("Xóa")) {
                    _ = repository.deleteStamp(stampId: stamp.id)
                    IOSLocalPersistenceStore.shared.saveData(repository: repository)
                    onDismiss()
                },
                secondaryButton: .cancel()
            )
        }
    }
}


struct CreateAlbumSheetView: View {
    let repository: SharedMemoStampRepository
    @Environment(\.presentationMode) var presentationMode
    @State private var albumName: String = ""
    @State private var albumDesc: String = ""
    @State private var selectedEmoji: String = "🏞️"
    @State private var selectedPrivacy: String = "FRIENDS"

    let emojis = ["🏞️", "☕", "✈️", "📸", "💖", "🌲", "🎨", "👑", "🌸", "🍔"]

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("TẠO ALBUM / BỘ SƯU TẬP MỚI")
                        .font(.headline.bold())
                        .foregroundColor(MSColors.ink)
                        .padding(.top, 8)

                    VStack(alignment: .leading, spacing: 6) {
                        Text("Tên Album")
                            .font(.caption.bold())
                            .foregroundColor(MSColors.ink)
                        TextField("Ví dụ: Chuyến đi Hà Nội 2026", text: $albumName)
                            .font(.subheadline)
                            .foregroundColor(MSColors.ink)
                            .padding(12)
                            .background(Color.white)
                            .cornerRadius(10)
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.gray.opacity(0.25), lineWidth: 1))
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Text("Mô tả Album")
                            .font(.caption.bold())
                            .foregroundColor(MSColors.ink)
                        TextField("Mô tả ngắn gọn...", text: $albumDesc)
                            .font(.subheadline)
                            .foregroundColor(MSColors.ink)
                            .padding(12)
                            .background(Color.white)
                            .cornerRadius(10)
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.gray.opacity(0.25), lineWidth: 1))
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Text("Biểu tượng Emoji")
                            .font(.caption.bold())
                            .foregroundColor(MSColors.ink)
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 10) {
                                ForEach(emojis, id: \.self) { emoji in
                                    Text(emoji)
                                        .font(.title2)
                                        .padding(8)
                                        .background(selectedEmoji == emoji ? MSColors.stamp.opacity(0.2) : Color.white)
                                        .cornerRadius(10)
                                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(selectedEmoji == emoji ? MSColors.stamp : Color.gray.opacity(0.2), lineWidth: 1.5))
                                        .onTapGesture {
                                            selectedEmoji = emoji
                                        }
                                }
                            }
                        }
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Text("Quyền riêng tư")
                            .font(.caption.bold())
                            .foregroundColor(MSColors.ink)
                        HStack(spacing: 12) {
                            Button(action: { selectedPrivacy = "FRIENDS" }) {
                                HStack(spacing: 4) {
                                    Image(systemName: "person.2.fill")
                                    Text("Bạn bè")
                                }
                                .font(.caption.bold())
                                .foregroundColor(selectedPrivacy == "FRIENDS" ? .white : MSColors.ink)
                                .padding(.vertical, 8)
                                .frame(maxWidth: .infinity)
                                .background(selectedPrivacy == "FRIENDS" ? MSColors.stamp : Color.white)
                                .cornerRadius(10)
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(MSColors.stamp, lineWidth: 1))
                            }

                            Button(action: { selectedPrivacy = "ONLY_ME" }) {
                                HStack(spacing: 4) {
                                    Image(systemName: "lock.fill")
                                    Text("Mình tôi")
                                }
                                .font(.caption.bold())
                                .foregroundColor(selectedPrivacy == "ONLY_ME" ? .white : MSColors.ink)
                                .padding(.vertical, 8)
                                .frame(maxWidth: .infinity)
                                .background(selectedPrivacy == "ONLY_ME" ? MSColors.stamp : Color.white)
                                .cornerRadius(10)
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(MSColors.stamp, lineWidth: 1))
                            }
                        }
                    }

                    Spacer().frame(height: 20)

                    Button(action: {
                        let name = albumName.trimmingCharacters(in: .whitespacesAndNewlines)
                        if !name.isEmpty {
                            _ = repository.createCollection(name: name, description: albumDesc, iconEmoji: selectedEmoji, privacy: selectedPrivacy)
                            IOSLocalPersistenceStore.shared.saveData(repository: repository)
                            presentationMode.wrappedValue.dismiss()
                        }
                    }) {
                        Text("Tạo Album")
                            .font(.body.bold())
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(albumName.isEmpty ? Color.gray.opacity(0.4) : MSColors.stamp)
                            .cornerRadius(14)
                    }
                    .disabled(albumName.isEmpty)
                }
                .padding()
            }
            .background(MSColors.paper.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Hủy") {
                        presentationMode.wrappedValue.dismiss()
                    }
                    .foregroundColor(MSColors.stamp)
                }
            }
        }
    }
}
