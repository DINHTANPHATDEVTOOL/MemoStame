import SwiftUI
import CoreLocation

public struct LocationPickerSheetView: View {
    public let initialLocation: String
    public let onDismiss: () -> Void
    public let onLocationSelected: (_ locationName: String, _ suggestedStampTitle: String?, _ story: GroundedPostmarkStory?) -> Void

    @State private var searchQuery: String = ""
    @State private var selectedCityChip: String = "Đà Lạt"
    @State private var selectedCategoryFilter: String = "ALL"
    @State private var placesList: [GroundedPlace] = []
    @State private var groundingState: GroundingState = .idle
    @State private var currentCoordinate: CLLocationCoordinate2D? = nil
    @State private var currentGpsName: String? = nil
    @State private var isLocatingGps: Bool = false
    @State private var selectingPlaceId: String? = nil
    @State private var debounceWorkItem: DispatchWorkItem? = nil

    private let cities: [String] = [
        "Đà Lạt", "Sài Gòn", "Hà Nội", "Hội An", "Đà Nẵng", "Phú Quốc", "Sapa", "Huế"
    ]

    private let categories: [(id: String, title: String, iconKey: MemoStampIconKey)] = [
        ("ALL", "Tất cả", .filter),
        ("LANDMARK", "Biểu tượng", .map),
        ("CAFE", "Cà phê hoài niệm", .cafe),
        ("NATURE", "Thiên nhiên", .nature),
        ("HERITAGE", "Di tích bưu chính", .stamp),
        ("RESTAURANT", "Ẩm thực phố", .food)
    ]

    public init(
        initialLocation: String = "",
        onDismiss: @escaping () -> Void,
        onLocationSelected: @escaping (String, String?, GroundedPostmarkStory?) -> Void
    ) {
        self.initialLocation = initialLocation
        self.onDismiss = onDismiss
        self.onLocationSelected = onLocationSelected
    }

    public var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // Search Input Header
                VStack(spacing: 12) {
                    HStack(spacing: 10) {
                        Image(systemName: "magnifyingglass")
                            .foregroundColor(MSTheme.Colors.primaryRed)
                            .font(.system(size: 16, weight: .semibold))

                        TextField("Tìm quán cà phê, danh lam thắng cảnh...", text: $searchQuery)
                            .font(.subheadline)
                            .foregroundColor(MSTheme.Colors.textPrimary)
                            .onChange(of: searchQuery) { newValue in
                                scheduleDebouncedSearch(newValue)
                            }

                        if !searchQuery.isEmpty {
                            Button(action: {
                                searchQuery = ""
                                performSearch(query: "", city: cityOnlyName(selectedCityChip))
                            }) {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(.gray.opacity(0.6))
                                    .font(.system(size: 16))
                            }
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(Color.white)
                    .cornerRadius(12)
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.gray.opacity(0.2), lineWidth: 1))
                    .padding(.horizontal, 16)
                    .padding(.top, 12)

                    // City Quick Filter Chips
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(cities, id: \.self) { city in
                                Button(action: {
                                    selectedCityChip = city
                                    performSearch(query: searchQuery, city: cityOnlyName(city))
                                }) {
                                    Text(city)
                                        .font(.caption.bold())
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 6)
                                        .background(selectedCityChip == city ? MSTheme.Colors.primaryRed : Color.white)
                                        .foregroundColor(selectedCityChip == city ? .white : MSTheme.Colors.textPrimary)
                                        .cornerRadius(16)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 16)
                                                .stroke(selectedCityChip == city ? Color.clear : Color.gray.opacity(0.2), lineWidth: 1)
                                        )
                                }
                            }
                        }
                        .padding(.horizontal, 16)
                    }

                    // Category Filter Chips
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(categories, id: \.id) { cat in
                                Button(action: {
                                    selectedCategoryFilter = cat.id
                                    performSearch(query: searchQuery, city: cityOnlyName(selectedCityChip))
                                }) {
                                    HStack(spacing: 4) {
                                        MemoStampIcon(key: cat.iconKey, size: 12, color: selectedCategoryFilter == cat.id ? .white : MSTheme.Colors.textSecondary)
                                        Text(cat.title)
                                    }
                                    .font(.caption2.bold())
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 5)
                                    .background(selectedCategoryFilter == cat.id ? Color(red: 0.20, green: 0.20, blue: 0.25) : Color.white)
                                    .foregroundColor(selectedCategoryFilter == cat.id ? .white : MSTheme.Colors.textSecondary)
                                    .cornerRadius(12)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12)
                                            .stroke(selectedCategoryFilter == cat.id ? Color.clear : Color.gray.opacity(0.2), lineWidth: 1)
                                    )
                                }
                            }
                        }
                        .padding(.horizontal, 16)
                    }

                    // GPS Quick Action
                    Button(action: fetchCurrentGpsAndSearch) {
                        HStack(spacing: 8) {
                            if isLocatingGps {
                                ProgressView()
                                    .scaleEffect(0.7)
                            } else {
                                Image(systemName: "location.fill")
                                    .font(.caption.bold())
                                    .foregroundColor(MSTheme.Colors.primaryRed)
                            }

                            Text(currentGpsName != nil ? "Vị trí GPS: \(currentGpsName!)" : "Sử dụng vị trí GPS hiện tại")
                                .font(.caption.bold())
                                .foregroundColor(MSTheme.Colors.primaryRed)

                            Spacer()

                            Text("Tự động định vị")
                                .font(.system(size: 10, weight: .medium))
                                .foregroundColor(Color.gray.opacity(0.8))
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(MSTheme.Colors.primaryRed.opacity(0.08))
                        .cornerRadius(10)
                        .padding(.horizontal, 16)
                    }
                }
                .padding(.bottom, 8)
                .background(MSTheme.Colors.paperBackground)

                Divider()

                // Status Banner (Rate Limit / Augmenting / Fallback)
                renderStatusBanner()

                // Results List
                ScrollView {
                    LazyVStack(spacing: 12) {
                        // Custom manual entry row if query is typed
                        if !searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                            customEntryRow
                        }

                        ForEach(placesList) { place in
                            placeCardRow(place: place)
                        }

                        if placesList.isEmpty && groundingState != .searchingNative && groundingState != .augmentingServer {
                            emptyPlacesPlaceholder
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                }
                .background(MSTheme.Colors.paperBackground)
            }
            .navigationBarTitle("Chọn Địa Điểm", displayMode: .inline)
            .navigationBarItems(
                leading: Button(action: onDismiss) {
                    Text("Đóng")
                        .foregroundColor(MSTheme.Colors.textSecondary)
                }
            )
            .onAppear {
                searchQuery = initialLocation
                if let coord = LocationManager.shared.currentCoordinate {
                    currentCoordinate = coord
                }
                performSearch(query: initialLocation, city: cityOnlyName(selectedCityChip))
            }
        }
    }

    // MARK: - Subviews

    @ViewBuilder
    private func renderStatusBanner() -> some View {
        switch groundingState {
        case .searchingNative:
            HStack(spacing: 6) {
                ProgressView().scaleEffect(0.6)
                Text("Đang tìm kiếm trên bản đồ...")
                    .font(.caption2)
                    .foregroundColor(MSTheme.Colors.textSecondary)
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 6)
            .background(Color.white)

        case .augmentingServer:
            HStack(spacing: 6) {
                Image(systemName: "sparkles")
                    .foregroundColor(MSTheme.Colors.vintageGold)
                    .font(.caption2)
                Text("Đang tối ưu danh sách với Google Maps AI...")
                    .font(.caption2.bold())
                    .foregroundColor(MSTheme.Colors.textPrimary)
                Spacer()
                ProgressView().scaleEffect(0.6)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 6)
            .background(Color.white)

        case .rateLimited(let retrySec):
            HStack(spacing: 6) {
                Image(systemName: "clock.badge.exclamationmark")
                    .foregroundColor(MSTheme.Colors.primaryRed)
                    .font(.caption2)
                Text("Bạn thao tác quá nhanh. Thử lại sau \(retrySec) giây.")
                    .font(.caption2.bold())
                    .foregroundColor(MSTheme.Colors.primaryRed)
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 6)
            .background(MSTheme.Colors.primaryRed.opacity(0.1))

        case .serverFallback(_, let reason):
            HStack(spacing: 6) {
                Image(systemName: "info.circle")
                    .foregroundColor(.secondary)
                    .font(.caption2)
                Text(reason)
                    .font(.caption2)
                    .foregroundColor(.secondary)
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 6)
            .background(Color.white)

        case .idle, .success:
            EmptyView()
        }
    }

    private var customEntryRow: some View {
        Button(action: {
            let trimmed = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
            onLocationSelected(trimmed, trimmed, nil)
        }) {
            HStack(spacing: 12) {
                Image(systemName: "mappin.and.ellipse")
                    .foregroundColor(MSTheme.Colors.primaryRed)
                    .font(.title3)

                VStack(alignment: .leading, spacing: 2) {
                    Text("Sử dụng: \"\(searchQuery)\"")
                        .font(.subheadline.bold())
                        .foregroundColor(MSTheme.Colors.primaryRed)
                    Text("Lưu địa điểm thủ công chính xác theo tên bạn nhập")
                        .font(.caption2)
                        .foregroundColor(MSTheme.Colors.textSecondary)
                }

                Spacer()

                Image(systemName: "arrow.right.circle.fill")
                    .foregroundColor(MSTheme.Colors.primaryRed)
            }
            .padding(12)
            .background(Color.white)
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(MSTheme.Colors.primaryRed.opacity(0.3), lineWidth: 1)
            )
        }
    }

    private func placeCardRow(place: GroundedPlace) -> some View {
        Button(action: {
            selectPlace(place)
        }) {
            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(place.name)
                            .font(.subheadline.bold())
                            .foregroundColor(MSTheme.Colors.textPrimary)
                            .multilineTextAlignment(.leading)

                        Text(place.address)
                            .font(.caption)
                            .foregroundColor(MSTheme.Colors.textSecondary)
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)
                    }

                    Spacer()

                    if selectingPlaceId == place.id {
                        ProgressView()
                            .scaleEffect(0.8)
                    } else if place.isGroundedWithMaps {
                        HStack(spacing: 3) {
                            Image(systemName: "sparkles")
                                .font(.system(size: 10))
                            Text("Bản đồ AI")
                                .font(.system(size: 10, weight: .bold))
                        }
                        .padding(.horizontal, 6)
                        .padding(.vertical, 3)
                        .background(MSTheme.Colors.primaryRed.opacity(0.12))
                        .foregroundColor(MSTheme.Colors.primaryRed)
                        .cornerRadius(6)
                    }
                }

                if !place.description.isEmpty {
                    Text(place.description)
                        .font(.caption)
                        .italic()
                        .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.38))
                        .multilineTextAlignment(.leading)
                }

                HStack(spacing: 8) {
                    // Category Badge
                    HStack(spacing: 4) {
                        MemoStampIcon(key: categoryIconKey(place.category), size: 10, color: MSTheme.Colors.textSecondary)
                        Text(displayCategoryName(place.category))
                    }
                    .font(.system(size: 10, weight: .bold))
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color.gray.opacity(0.12))
                    .foregroundColor(MSTheme.Colors.textSecondary)
                    .cornerRadius(4)

                    if let rating = place.rating {
                        HStack(spacing: 2) {
                            Image(systemName: "star.fill")
                                .font(.system(size: 9))
                                .foregroundColor(MSTheme.Colors.vintageGold)
                            Text(rating)
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(MSTheme.Colors.textPrimary)
                        }
                    }

                    if let distance = place.distanceFormatted {
                        HStack(spacing: 2) {
                            Image(systemName: "arrow.triangle.turn.up.right.diamond.fill")
                                .font(.system(size: 9))
                                .foregroundColor(.gray)
                            Text(distance)
                                .font(.system(size: 10))
                                .foregroundColor(.secondary)
                        }
                    }

                    Spacer()

                    Text("Chọn ›")
                        .font(.caption.bold())
                        .foregroundColor(MSTheme.Colors.primaryRed)
                }
            }
            .padding(12)
            .background(Color.white)
            .cornerRadius(12)
            .shadow(color: Color.black.opacity(0.04), radius: 3, x: 0, y: 1)
        }
        .disabled(selectingPlaceId != nil)
    }

    private var emptyPlacesPlaceholder: some View {
        VStack(spacing: 12) {
            Image(systemName: "mappin.slash")
                .font(.system(size: 36))
                .foregroundColor(Color.gray.opacity(0.4))
                .padding(.top, 24)

            Text("Không tìm thấy địa điểm phù hợp")
                .font(.subheadline.bold())
                .foregroundColor(MSTheme.Colors.textSecondary)

            Text("Bạn có thể gõ trực tiếp tên địa điểm ở ô tìm kiếm phía trên để lưu thủ công.")
                .font(.caption)
                .foregroundColor(Color.gray)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
        }
    }

    // MARK: - Actions & Search Logic

    private func scheduleDebouncedSearch(_ query: String) {
        debounceWorkItem?.cancel()
        let workItem = DispatchWorkItem {
            performSearch(query: query, city: cityOnlyName(selectedCityChip))
        }
        debounceWorkItem = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15, execute: workItem)
    }

    private func performSearch(query: String, city: String?) {
        IOSMapsGroundingClient.shared.searchPlacesWithMaps(
            query: query,
            currentCity: city,
            coordinate: currentCoordinate,
            categoryFilter: selectedCategoryFilter
        ) { state in
            DispatchQueue.main.async {
                self.groundingState = state
                switch state {
                case .success(let places):
                    self.placesList = places
                case .serverFallback(let places, _):
                    self.placesList = places
                default:
                    break
                }
            }
        }
    }

    private func fetchCurrentGpsAndSearch() {
        isLocatingGps = true
        LocationManager.shared.fetchCurrentLocation { placeStr in
            DispatchQueue.main.async {
                self.isLocatingGps = false
                self.currentGpsName = placeStr.isEmpty ? nil : placeStr
                self.currentCoordinate = LocationManager.shared.currentCoordinate
                self.performSearch(query: self.searchQuery, city: self.cityOnlyName(self.selectedCityChip))
            }
        }
    }

    private func selectPlace(_ place: GroundedPlace) {
        selectingPlaceId = place.id

        // Attempt story generation via authenticated Edge Function
        IOSMapsGroundingClient.shared.generateGroundedPostmarkNote(
            placeName: place.name,
            locationAddress: place.address
        ) { story in
            DispatchQueue.main.async {
                self.selectingPlaceId = nil
                self.onLocationSelected(place.name, place.stampTitleSuggestion, story)
            }
        }
    }

    private func cityOnlyName(_ chip: String) -> String {
        return chip.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func categoryIconKey(_ cat: String) -> MemoStampIconKey {
        switch cat.uppercased() {
        case "CAFE": return .cafe
        case "HERITAGE": return .stamp
        case "NATURE": return .nature
        case "STREET", "RESTAURANT": return .food
        default: return .map
        }
    }

    private func displayCategoryName(_ cat: String) -> String {
        switch cat.uppercased() {
        case "CAFE": return "Cà phê"
        case "HERITAGE": return "Bưu chính"
        case "NATURE": return "Thiên nhiên"
        case "STREET": return "Phố phường"
        case "RESTAURANT": return "Ẩm thực"
        default: return "Biểu tượng"
        }
    }
}
