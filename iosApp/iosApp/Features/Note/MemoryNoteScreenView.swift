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
    let repository: SharedMemoStampRepository
    var onSavedSuccess: () -> Void
    var onCancel: () -> Void

    @State private var title: String = ""
    @State private var caption: String = ""
    @State private var locationSearch: String = ""
    @State private var selectedCategory: String = "Tất cả"
    @State private var selectedAudience: String = "Friends 👥"
    @State private var isGpsLocating: Bool = false

    let locationCategories = ["Tất cả", "Biểu tượng 🏛️", "Cà phê ☕", "Thiên nhiên 🌲", "Di tích 📮"]
    
    let groundedPlaces: [GroundedPlaceItem] = [
        GroundedPlaceItem(
            name: "Quảng trường Lâm Viên",
            address: "Trần Quốc Toản, P.10, Đà Lạt",
            category: "Biểu tượng 🏛️",
            stampTitle: "Nụ Hoa Atisô Đà Lạt",
            rating: "4.7★"
        ),
        GroundedPlaceItem(
            name: "Hồ Xuân Hương",
            address: "Trung tâm TP. Đà Lạt, Lâm Đồng",
            category: "Thiên nhiên 🌲",
            stampTitle: "Sương Mù Hồ Xuân Hương",
            rating: "4.8★"
        ),
        GroundedPlaceItem(
            name: "Tiệm Cà Phê Túi Mơ To",
            address: "Hẻm 31 Sào Nam, P.11, Đà Lạt",
            category: "Cà phê ☕",
            stampTitle: "Cúc Họa Mi Mơ Màng",
            rating: "4.6★"
        ),
        GroundedPlaceItem(
            name: "Bưu Điện Trung Tâm Sài Gòn",
            address: "2 Công xã Paris, Q.1, TP.HCM",
            category: "Di tích 📮",
            stampTitle: "Bưu Chính Sài Gòn 1891",
            rating: "4.8★"
        ),
        GroundedPlaceItem(
            name: "Chợ Bến Thành",
            address: "Lê Lợi, P. Bến Thành, Q.1, TP.HCM",
            category: "Di tích 📮",
            stampTitle: "Tháp Đồng Hồ Bến Thành",
            rating: "4.6★"
        ),
        GroundedPlaceItem(
            name: "Hồ Hoàn Kiếm (Hồ Gươm)",
            address: "Hoàn Kiếm, Hà Nội",
            category: "Biểu tượng 🏛️",
            stampTitle: "Mùa Thu Hà Nội",
            rating: "4.9★"
        ),
        GroundedPlaceItem(
            name: "Phố Cổ Hội An",
            address: "TP. Hội An, Quảng Nam",
            category: "Di tích 📮",
            stampTitle: "Đèn Lồng Phố Cổ",
            rating: "4.9★"
        ),
        GroundedPlaceItem(
            name: "Cầu Vàng Bà Nà Hills",
            address: "Hòa Vang, Đà Nẵng",
            category: "Biểu tượng 🏛️",
            stampTitle: "Dải Lụa Mây Ngàn",
            rating: "4.7★"
        )
    ]

    var filteredPlaces: [GroundedPlaceItem] {
        groundedPlaces.filter { place in
            let matchCat = (selectedCategory == "Tất cả" || place.category == selectedCategory)
            let matchQuery = locationSearch.isEmpty || place.name.localizedCaseInsensitiveContains(locationSearch) || place.address.localizedCaseInsensitiveContains(locationSearch)
            return matchCat && matchQuery
        }
    }

    let audienceTypes = ["Public 🌍", "Friends 👥", "Only Me 🔒"]

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Button(action: onCancel) {
                    Text("Cancel")
                        .foregroundColor(.gray)
                }

                Spacer()

                Text("New Memory Stamp")
                    .font(.headline.bold())

                Spacer()

                Button(action: {
                    var audience = AudienceType.friends
                    if selectedAudience.contains("Public") {
                        audience = AudienceType.circle
                    } else if selectedAudience.contains("Only Me") {
                        audience = AudienceType.onlyMe
                    }
                    _ = repository.addStamp(
                        title: title.isEmpty ? "Memory Stamp" : title,
                        note: caption,
                        location: locationSearch.isEmpty ? "Đà Lạt, Lâm Đồng" : locationSearch,
                        imageUrl: imageUrl,
                        shape: "classic",
                        collectionId: nil,
                        audience: audience
                    )
                    onSavedSuccess()
                }) {
                    Text("Save & Post")
                        .font(.body.bold())
                        .foregroundColor(Color(red: 0.85, green: 0.25, blue: 0.20))
                }
            }
            .padding(.horizontal)
            .padding(.vertical, 12)

            Divider()

            ScrollView {
                VStack(spacing: 20) {
                    // Preview Stamp
                    DieCutStampView(
                        title: title.isEmpty ? "Memory Stamp Title" : title,
                        imageUrl: imageUrl,
                        location: locationSearch.isEmpty ? "Location Tag" : locationSearch,
                        dateStr: "2026.08.18",
                        shape: "classic",
                        isInteractive: false
                    )
                    .padding(.horizontal)
                    .padding(.top, 12)

                    // Inputs Section
                    VStack(alignment: .leading, spacing: 14) {
                        Text("MEMORY DETAILS")
                            .font(.caption2.bold())
                            .foregroundColor(.secondary)

                        TextField("Stamp Title (e.g. Đà Lạt Chiều Mưa)", text: $title)
                            .font(.subheadline)
                            .padding(12)
                            .background(Color.white)
                            .cornerRadius(10)

                        TextEditor(text: $caption)
                            .font(.subheadline)
                            .frame(height: 80)
                            .padding(8)
                            .background(Color.white)
                            .cornerRadius(10)
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
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
                                isGpsLocating = false
                                locationSearch = "Quảng trường Lâm Viên, Đà Lạt"
                                if title.isEmpty {
                                    title = "Nụ Hoa Atisô Đà Lạt"
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
                                Text("Sử dụng vị trí GPS hiện tại (Đà Lạt)")
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
                                            Text("🏷️ Gợi ý tem: \(place.stampTitle)")
                                                .font(.system(size: 11, weight: .medium))
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
