import SwiftUI
#if canImport(UIKit)
import UIKit
#endif
import shared

struct GroundedPlaceItem: Identifiable {
    let id = UUID()
    let name: String
    let address: String
    let category: String
    let stampTitle: String
    let rating: String
}

struct MemoryNoteScreenView: View {
    let imageUrl: String
    var shape: String = "classic"
    var stampColorHex: String = "#D32F2F"
    var replyToPostId: String? = nil
    let repository: SharedMemoStampRepository
    var onSavedSuccess: () -> Void
    var onCancel: () -> Void

    @State private var title: String = ""
    @State private var caption: String = ""
    @State private var locationSearch: String = ""
    @State private var selectedCategory: String = "Tất cả"
    @State private var selectedAudience: String = "Friends"
    @State private var selectedCollectionId: String? = nil
    @State private var selectedMood: String = "😊 Happy"
    @State private var memoryDate: Date = Date()
    @State private var isGpsLocating: Bool = false

    let moodOptions = ["😊 Happy", "❤️ Love", "✈️ Travel", "☕ Chill", "🔥 Excited", "🕰️ Nostalgic", "🌿 Peaceful", "⭐ Special"]
    let locationCategories = ["Tất cả", "Biểu tượng", "Cà phê", "Thiên nhiên", "Di tích"]
    
    var groundedPlaces: [GroundedPlaceItem] {
        return []
    }

    var filteredPlaces: [GroundedPlaceItem] {
        return groundedPlaces
    }

    let audienceTypes = ["Friends", "Only Me"]

    private var formattedDate: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy.MM.dd"
        return formatter.string(from: memoryDate)
    }

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Button(action: onCancel) {
                    Text("Hủy")
                        .foregroundColor(.gray)
                }

                Spacer()

                Text("Tạo Tem Kỷ Niệm")
                    .font(.headline.bold())

                Spacer()

                Button(action: {
                    var audience = AudienceType.friends
                    if selectedAudience.contains("Only Me") {
                        audience = AudienceType.onlyMe
                    } else {
                        audience = AudienceType.friends
                    }

                    let finalTitle = title.isEmpty ? "" : title

                    #if canImport(UIKit)
                    var inputImage: UIImage? = nil
                    if let url = URL(string: imageUrl), let data = try? Data(contentsOf: url) {
                        inputImage = UIImage(data: data)
                    }
                    let finalRenderedUrl = StampRenderEngine.shared.saveStampPng(
                        photo: inputImage,
                        title: finalTitle,
                        location: locationSearch.isEmpty ? nil : locationSearch,
                        dateStr: formattedDate,
                        stampColorHex: stampColorHex,
                        shape: shape
                    )?.absoluteString ?? imageUrl
                    #else
                    let finalRenderedUrl = imageUrl
                    #endif

                    let stampItemTitle = finalTitle.isEmpty ? "Memory Stamp" : finalTitle

                    if let replyId = replyToPostId, !replyId.isEmpty {
                        repository.addStampReply(
                            postId: replyId,
                            stampTitle: stampItemTitle,
                            note: caption,
                            shape: shape,
                            imageUrl: finalRenderedUrl
                        )
                    }

                    _ = repository.addStamp(
                        title: stampItemTitle,
                        note: caption,
                        location: locationSearch.isEmpty ? nil : locationSearch,
                        imageUrl: finalRenderedUrl,
                        shape: shape,
                        collectionId: selectedCollectionId,
                        audience: audience,
                        mood: selectedMood,
                        memoryDate: Int64(memoryDate.timeIntervalSince1970 * 1000)
                    )
                    IOSLocalPersistenceStore.shared.saveData(repository: repository)
                    onSavedSuccess()
                }) {
                    Text("Lưu Tem")
                        .font(.body.bold())
                        .foregroundColor(Color(red: 0.85, green: 0.25, blue: 0.20))
                }
            }
            .padding(.horizontal)
            .padding(.vertical, 12)

            Divider()

            ScrollView {
                VStack(spacing: 20) {
                    // Preview Stamp (Tap to flip & view real-time memory note)
                    DieCutStampView(
                        title: title.isEmpty ? "Tiêu đề tem kỷ niệm" : title,
                        imageUrl: imageUrl,
                        location: locationSearch.isEmpty ? nil : locationSearch,
                        dateStr: formattedDate,
                        note: caption,
                        shape: "classic",
                        isInteractive: true
                    )
                    .padding(.horizontal)
                    .padding(.top, 12)

                    // Inputs Section
                    VStack(alignment: .leading, spacing: 14) {
                        Text("THÔNG TIN KỶ NIỆM")
                            .font(.caption2.bold())
                            .foregroundColor(.secondary)

                        TextField("Tiêu đề tem (ví dụ: Đà Lạt Chiều Mưa)", text: $title)
                            .font(.subheadline)
                            .foregroundColor(MSColors.ink)
                            .padding(12)
                            .background(Color.white)
                            .cornerRadius(10)
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.gray.opacity(0.25), lineWidth: 1))

                        // Mood Selection Section
                        VStack(alignment: .leading, spacing: 6) {
                            Text("CẢM XÚC / MOOD")
                                .font(.caption2.bold())
                                .foregroundColor(MSColors.grey)
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 8) {
                                    ForEach(moodOptions, id: \.self) { mood in
                                        Text(mood)
                                            .font(.caption.bold())
                                            .padding(.horizontal, 12)
                                            .padding(.vertical, 8)
                                            .background(selectedMood == mood ? MSColors.stamp : Color.white)
                                            .foregroundColor(selectedMood == mood ? .white : MSColors.ink)
                                            .cornerRadius(16)
                                            .overlay(RoundedRectangle(cornerRadius: 16).stroke(selectedMood == mood ? Color.clear : MSColors.lightGrey, lineWidth: 1))
                                            .onTapGesture {
                                                selectedMood = mood
                                            }
                                    }
                                }
                            }
                        }

                        // Memory Date Picker
                        HStack {
                            Image(systemName: "calendar")
                                .foregroundColor(MSColors.stamp)
                            DatePicker("Ngày kỷ niệm", selection: $memoryDate, displayedComponents: [.date])
                                .font(.subheadline)
                                .foregroundColor(MSColors.ink)
                        }
                        .padding(10)
                        .background(Color.white)
                        .cornerRadius(10)
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.gray.opacity(0.25), lineWidth: 1))

                        TextEditor(text: $caption)
                            .font(.subheadline)
                            .foregroundColor(MSColors.ink)
                            .frame(height: 80)
                            .padding(8)
                            .background(Color.white)
                            .cornerRadius(10)
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.gray.opacity(0.25), lineWidth: 1))
                            .overlay(
                                Group {
                                    if caption.isEmpty {
                                        Text("Write your memory note or story...")
                                            .font(.subheadline)
                                            .foregroundColor(.gray.opacity(0.6))
                                            .padding(.leading, 12)
                                            .padding(.top, 12)
                                    }
                                },
                                alignment: .topLeading
                            )

                        // Album Selection Picker
                        VStack(alignment: .leading, spacing: 8) {
                            Text("LƯU VÀO ALBUM / BỘ SƯU TẬP")
                                .font(.caption2.bold())
                                .foregroundColor(MSColors.grey)

                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 10) {
                                    ForEach((repository.collections.value as? [CollectionItem]) ?? [], id: \.id) { col in
                                        HStack(spacing: 6) {
                                            Text(col.iconEmoji)
                                                .font(.subheadline)
                                            Text(col.name)
                                                .font(.caption.bold())
                                        }
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 8)
                                        .background(selectedCollectionId == col.id ? MSColors.stamp.opacity(0.18) : Color.white)
                                        .foregroundColor(selectedCollectionId == col.id ? MSColors.stamp : MSColors.ink)
                                        .cornerRadius(12)
                                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(selectedCollectionId == col.id ? MSColors.stamp : MSColors.lightGrey, lineWidth: 1.5))
                                        .onTapGesture {
                                            selectedCollectionId = col.id
                                        }
                                    }
                                }
                            }
                        }

                        // Google Maps Grounded Location Header
                        HStack {
                            Text("GOOGLE MAPS GROUNDED LOCATION")
                                .font(.caption2.bold())
                                .foregroundColor(.secondary)
                            Spacer()
                            HStack(spacing: 4) {
                                Image(systemName: "sparkles")
                                    .font(.system(size: 10))
                                    .foregroundColor(Color(red: 0.1, green: 0.45, blue: 0.9))
                                Text("Maps Grounding AI")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(Color(red: 0.1, green: 0.45, blue: 0.9))
                            }
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color(red: 0.1, green: 0.45, blue: 0.9).opacity(0.1))
                            .cornerRadius(6)
                        }

                        // Search Bar
                        HStack {
                            Image(systemName: "magnifyingglass")
                                .foregroundColor(.gray)
                            TextField("Search place, landmark, cafe...", text: $locationSearch)
                                .font(.subheadline)
                            if !locationSearch.isEmpty {
                                Button(action: { locationSearch = "" }) {
                                    Image(systemName: "xmark.circle.fill")
                                        .foregroundColor(.gray)
                                }
                            }
                        }
                        .padding(12)
                        .background(Color.white)
                        .cornerRadius(10)

                        // GPS Quick Action
                        Button(action: {
                            isGpsLocating = true
                            LocationManager.shared.fetchCurrentLocation { place in
                                DispatchQueue.main.async {
                                    isGpsLocating = false
                                    if !place.isEmpty {
                                        locationSearch = place
                                        if title.isEmpty {
                                            title = "Kỷ niệm tại \(place)"
                                        }
                                    }
                                }
                            }
                        }) {
                            HStack(spacing: 8) {
                                if isGpsLocating {
                                    ProgressView()
                                        .scaleEffect(0.8)
                                } else {
                                    Image(systemName: "location.fill")
                                        .foregroundColor(Color(red: 0.85, green: 0.25, blue: 0.20))
                                }
                                Text(locationSearch.isEmpty ? "Sử dụng vị trí GPS hiện tại" : "Vị trí GPS: \(locationSearch)")
                                    .font(.caption.bold())
                                    .foregroundColor(Color(red: 0.85, green: 0.25, blue: 0.20))
                                Spacer()
                            }
                            .padding(10)
                            .background(Color(red: 0.85, green: 0.25, blue: 0.20).opacity(0.08))
                            .cornerRadius(10)
                        }

                        // Category Filter Chips
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(locationCategories, id: \.self) { cat in
                                    Button(action: { selectedCategory = cat }) {
                                        Text(cat)
                                            .font(.caption.bold())
                                            .padding(.horizontal, 10)
                                            .padding(.vertical, 5)
                                            .background(selectedCategory == cat ? Color(red: 0.82, green: 0.65, blue: 0.35) : Color.gray.opacity(0.15))
                                            .foregroundColor(selectedCategory == cat ? .white : .primary)
                                            .cornerRadius(12)
                                    }
                                }
                            }
                        }

                        // Grounded Place Cards List
                        VStack(spacing: 8) {
                            ForEach(filteredPlaces) { place in
                                Button(action: {
                                    locationSearch = place.name
                                    if title.isEmpty || title == "Memory Stamp" {
                                        title = place.stampTitle
                                    }
                                }) {
                                    HStack(alignment: .center, spacing: 10) {
                                        VStack(alignment: .leading, spacing: 2) {
                                            HStack {
                                                Text(place.name)
                                                    .font(.subheadline.bold())
                                                    .foregroundColor(.primary)
                                                Spacer()
                                                Text(place.rating)
                                                    .font(.caption2.bold())
                                                    .foregroundColor(.orange)
                                            }
                                            Text(place.address)
                                                .font(.caption)
                                                .foregroundColor(.secondary)
                                                .lineLimit(1)
                                            HStack(spacing: 4) {
                                                Image(systemName: "tag.fill")
                                                    .font(.system(size: 10))
                                                Text("Gợi ý tem: \(place.stampTitle)")
                                                    .font(.system(size: 11, weight: .medium))
                                            }
                                            .foregroundColor(Color(red: 0.7, green: 0.5, blue: 0.1))
                                        }
                                        Image(systemName: locationSearch == place.name ? "checkmark.circle.fill" : "chevron.right")
                                            .foregroundColor(locationSearch == place.name ? Color(red: 0.85, green: 0.25, blue: 0.20) : .gray.opacity(0.5))
                                    }
                                    .padding(10)
                                    .background(Color.white)
                                    .cornerRadius(12)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12)
                                            .stroke(locationSearch == place.name ? Color(red: 0.85, green: 0.25, blue: 0.20) : Color.gray.opacity(0.15), lineWidth: 1)
                                    )
                                }
                            }
                        }

                        Text("AUDIENCE VISIBILITY")
                            .font(.caption2.bold())
                            .foregroundColor(.secondary)
                            .padding(.top, 6)

                        HStack(spacing: 10) {
                            ForEach(audienceTypes, id: \.self) { aud in
                                Button(action: { selectedAudience = aud }) {
                                    Text(aud)
                                        .font(.caption.bold())
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 8)
                                        .frame(maxWidth: .infinity)
                                        .background(selectedAudience == aud ? Color(red: 0.15, green: 0.15, blue: 0.18) : Color.white)
                                        .foregroundColor(selectedAudience == aud ? .white : .primary)
                                        .cornerRadius(12)
                                }
                            }
                        }
                    }
                    .padding(.horizontal)
                    .padding(.bottom, 40)
                }
            }
        }
        .background(Color(red: 0.98, green: 0.96, blue: 0.92).ignoresSafeArea())
    }
}
