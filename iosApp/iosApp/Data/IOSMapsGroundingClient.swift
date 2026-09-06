import Foundation
import CoreLocation
import MapKit

// MARK: - Typed Models

public struct GroundedPlace: Identifiable, Equatable {
    public let id: String
    public let name: String
    public let address: String
    public let category: String
    public let description: String
    public let stampTitleSuggestion: String
    public let rating: String?
    public let approximateDistanceMeters: Double?
    public let distanceFormatted: String?
    public let isGroundedWithMaps: Bool

    public init(
        id: String = UUID().uuidString,
        name: String,
        address: String,
        category: String = "LANDMARK",
        description: String = "",
        stampTitleSuggestion: String = "",
        rating: String? = nil,
        approximateDistanceMeters: Double? = nil,
        distanceFormatted: String? = nil,
        isGroundedWithMaps: Bool = true
    ) {
        self.id = id
        self.name = name
        self.address = address
        self.category = category
        self.description = description
        self.stampTitleSuggestion = stampTitleSuggestion.isEmpty ? name : stampTitleSuggestion
        self.rating = rating
        self.approximateDistanceMeters = approximateDistanceMeters
        self.distanceFormatted = distanceFormatted ?? IOSMapsGroundingClient.formatDistance(approximateDistanceMeters)
        self.isGroundedWithMaps = isGroundedWithMaps
    }
}

public struct GroundedPostmarkStory: Equatable {
    public let poeticNote: String
    public let historicalFact: String
    public let suggestedPostmarkCode: String

    public init(
        poeticNote: String,
        historicalFact: String,
        suggestedPostmarkCode: String
    ) {
        self.poeticNote = poeticNote
        self.historicalFact = historicalFact
        self.suggestedPostmarkCode = suggestedPostmarkCode
    }
}

public enum GroundingState: Equatable {
    case idle
    case searchingNative
    case augmentingServer
    case success([GroundedPlace])
    case rateLimited(retryAfterSeconds: Int)
    case serverFallback([GroundedPlace], reason: String)
}

// MARK: - IOSMapsGroundingClient

public final class IOSMapsGroundingClient {
    public static let shared = IOSMapsGroundingClient()

    public static let allowedCategories: Set<String> = [
        "ALL",
        "LANDMARK",
        "CAFE",
        "HERITAGE",
        "NATURE",
        "STREET",
        "RESTAURANT"
    ]

    private let urlSession: URLSession
    private var currentNativeSearch: MKLocalSearch?
    private var activeSearchGeneration: Int = 0
    private let queue = DispatchQueue(label: "com.mipastudio.memostamp.mapsgrounding", qos: .userInitiated)

    public init(session: URLSession = .shared) {
        self.urlSession = session
    }

    // MARK: - Distance Formatting Helper

    public static func formatDistance(_ meters: Double?) -> String? {
        guard let meters = meters, !meters.isNaN, !meters.isInfinite, meters >= 0 else {
            return nil
        }
        if meters < 1000 {
            return "\(Int(meters.rounded())) m"
        } else {
            return String(format: "%.1f km", meters / 1000.0)
        }
    }

    // MARK: - Search Places

    /// Executes place search combining native iOS MapKit search with server-side AI grounding.
    /// Strictly protects against account-switch and search-generation race conditions.
    public func searchPlacesWithMaps(
        query: String,
        currentCity: String? = nil,
        coordinate: CLLocationCoordinate2D? = nil,
        categoryFilter: String = "ALL",
        onStateChange: @escaping (GroundingState) -> Void
    ) {
        let trimmedQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)
        let effectiveCategory = sanitizeCategory(categoryFilter)

        // Increment generation counter to track newer queries
        var currentGen = 0
        queue.sync {
            self.activeSearchGeneration += 1
            currentGen = self.activeSearchGeneration
        }

        // 1. First obtain native candidates
        DispatchQueue.main.async {
            onStateChange(.searchingNative)
        }

        searchNativePlaces(query: trimmedQuery, coordinate: coordinate) { [weak self] nativeCandidates in
            guard let self = self else { return }

            // Validate generation
            var isStale = false
            self.queue.sync {
                if self.activeSearchGeneration != currentGen {
                    isStale = true
                }
            }
            if isStale { return }

            // If user is unauthenticated or session cannot be loaded/refreshed, safely return native results
            SupabaseAuthService.shared.loadOrRefreshSession { session in
                guard let session = session, !session.accessToken.isEmpty else {
                    DispatchQueue.main.async {
                        onStateChange(.serverFallback(nativeCandidates, reason: "Chưa đăng nhập — hiển thị kết quả ngoại tuyến."))
                    }
                    return
                }

                let capturedAuthUid = session.userId

                // Validate generation again
                var generationStillValid = false
                self.queue.sync {
                    generationStillValid = (self.activeSearchGeneration == currentGen)
                }
                guard generationStillValid else { return }

                DispatchQueue.main.async {
                    onStateChange(.augmentingServer)
                }

                // Build strictly bounded request payload adhering to #60 contract
                var requestBody: [String: Any] = [
                    "action": "SEARCH_PLACES",
                    "query": String(trimmedQuery.prefix(100)),
                    "categoryFilter": effectiveCategory
                ]

                if let city = currentCity?.trimmingCharacters(in: .whitespacesAndNewlines), !city.isEmpty {
                    requestBody["currentCity"] = String(city.prefix(100))
                }

                if let coord = coordinate,
                   !coord.latitude.isNaN && !coord.latitude.isInfinite && coord.latitude >= -90.0 && coord.latitude <= 90.0,
                   !coord.longitude.isNaN && !coord.longitude.isInfinite && coord.longitude >= -180.0 && coord.longitude <= 180.0 {
                    requestBody["latitude"] = coord.latitude
                    requestBody["longitude"] = coord.longitude
                }

                // Construct server URL
                let baseUrl = SupabaseAuthService.shared.supabaseUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
                guard let functionUrl = URL(string: "\(baseUrl)/functions/v1/maps-grounding") else {
                    DispatchQueue.main.async {
                        onStateChange(.serverFallback(nativeCandidates, reason: "URL máy chủ không hợp lệ."))
                    }
                    return
                }

                var request = URLRequest(url: functionUrl)
                request.httpMethod = "POST"
                request.setValue("Bearer \(session.accessToken)", forHTTPHeaderField: "Authorization")
                request.setValue(SupabaseAuthService.shared.anonKey, forHTTPHeaderField: "apikey")
                request.setValue("application/json", forHTTPHeaderField: "Content-Type")
                request.timeoutInterval = 15.0

                do {
                    request.httpBody = try JSONSerialization.data(withJSONObject: requestBody)
                } catch {
                    DispatchQueue.main.async {
                        onStateChange(.serverFallback(nativeCandidates, reason: "Lỗi mã hóa yêu cầu."))
                    }
                    return
                }

                let task = self.urlSession.dataTask(with: request) { data, response, error in
                    // Guard 1: Account-switch isolation check
                    guard let currentUid = SupabaseAuthService.shared.currentUserId, currentUid == capturedAuthUid else {
                        // User switched accounts or logged out while request was in flight. Discard cleanly!
                        return
                    }

                    // Guard 2: Generation race check
                    var isGenerationValid = false
                    self.queue.sync {
                        isGenerationValid = (self.activeSearchGeneration == currentGen)
                    }
                    guard isGenerationValid else {
                        // Superseded by newer search query. Discard cleanly!
                        return
                    }

                    if let httpResp = response as? HTTPURLResponse {
                        if httpResp.statusCode == 429 {
                            let retrySec = self.parseRetryAfter(data: data)
                            DispatchQueue.main.async {
                                onStateChange(.rateLimited(retryAfterSeconds: retrySec))
                            }
                            return
                        }

                        if httpResp.statusCode == 200, let responseData = data {
                            let parsedServerPlaces = self.parseServerPlacesResponse(responseData)
                            if !parsedServerPlaces.isEmpty {
                                let merged = self.mergeAndDeduplicate(
                                    serverPlaces: parsedServerPlaces,
                                    nativePlaces: nativeCandidates
                                )
                                DispatchQueue.main.async {
                                    onStateChange(.success(merged))
                                }
                                return
                            }
                        }
                    }

                    // Network / 5xx / timeout fallback: safely deliver native candidates
                    DispatchQueue.main.async {
                        onStateChange(.serverFallback(nativeCandidates, reason: "Không thể kết nối máy chủ AI — đang dùng kết quả bản đồ cục bộ."))
                    }
                }
                task.resume()
            }
        }
    }

    // MARK: - Postmark Story Generation

    /// Generates poetic story and postmark details via the authenticated Edge Function.
    public func generateGroundedPostmarkNote(
        placeName: String,
        locationAddress: String,
        completion: @escaping (GroundedPostmarkStory) -> Void
    ) {
        let trimmedName = String(placeName.trimmingCharacters(in: .whitespacesAndNewlines).prefix(150))
        let trimmedAddress = String(locationAddress.trimmingCharacters(in: .whitespacesAndNewlines).prefix(250))

        let fallbackStory = GroundedPostmarkStory(
            poeticNote: "Những khoảnh khắc đẹp đẽ nhất luôn nằm lại nơi góc quán quen và con đường ngập nắng \(trimmedName).",
            historicalFact: "\(trimmedName) là một trong những điểm dừng chân ghi dấu kỷ niệm khó quên tại \(trimmedAddress.isEmpty ? "Việt Nam" : trimmedAddress).",
            suggestedPostmarkCode: "MEMO-\(String(trimmedName.prefix(4)).uppercased())"
        )

        SupabaseAuthService.shared.loadOrRefreshSession { [weak self] session in
            guard let self = self, let session = session, !session.accessToken.isEmpty else {
                completion(fallbackStory)
                return
            }

            let capturedAuthUid = session.userId

            let requestBody: [String: Any] = [
                "action": "GENERATE_POSTMARK_STORY",
                "placeName": trimmedName,
                "locationAddress": trimmedAddress
            ]

            let baseUrl = SupabaseAuthService.shared.supabaseUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            guard let functionUrl = URL(string: "\(baseUrl)/functions/v1/maps-grounding") else {
                completion(fallbackStory)
                return
            }

            var request = URLRequest(url: functionUrl)
            request.httpMethod = "POST"
            request.setValue("Bearer \(session.accessToken)", forHTTPHeaderField: "Authorization")
            request.setValue(SupabaseAuthService.shared.anonKey, forHTTPHeaderField: "apikey")
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.timeoutInterval = 15.0

            guard let bodyData = try? JSONSerialization.data(withJSONObject: requestBody) else {
                completion(fallbackStory)
                return
            }
            request.httpBody = bodyData

            self.urlSession.dataTask(with: request) { data, response, error in
                // Account switch guard
                guard let currentUid = SupabaseAuthService.shared.currentUserId, currentUid == capturedAuthUid else {
                    return
                }

                if let httpResp = response as? HTTPURLResponse, httpResp.statusCode == 200, let responseData = data {
                    if let story = self.parseServerStoryResponse(responseData, placeName: trimmedName, locationAddress: trimmedAddress) {
                        DispatchQueue.main.async {
                            completion(story)
                        }
                        return
                    }
                }

                DispatchQueue.main.async {
                    completion(fallbackStory)
                }
            }.resume()
        }
    }

    // MARK: - Native MapKit Search

    public func searchNativePlaces(
        query: String,
        coordinate: CLLocationCoordinate2D? = nil,
        completion: @escaping ([GroundedPlace]) -> Void
    ) {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !trimmed.isEmpty else {
            completion(getCuratedFallbackPlaces(query: "", currentCity: nil))
            return
        }

        let request = MKLocalSearch.Request()
        request.naturalLanguageQuery = String(trimmed.prefix(100))

        if let coord = coordinate,
           !coord.latitude.isNaN && !coord.latitude.isInfinite && coord.latitude >= -90.0 && coord.latitude <= 90.0,
           !coord.longitude.isNaN && !coord.longitude.isInfinite && coord.longitude >= -180.0 && coord.longitude <= 180.0 {
            request.region = MKCoordinateRegion(
                center: coord,
                latitudinalMeters: 5000,
                longitudinalMeters: 5000
            )
        }

        currentNativeSearch?.cancel()
        let search = MKLocalSearch(request: request)
        currentNativeSearch = search

        search.start { [weak self] response, error in
            guard let self = self else { return }

            guard let response = response, error == nil else {
                completion(self.getCuratedFallbackPlaces(query: trimmed, currentCity: nil))
                return
            }

            var results: [GroundedPlace] = []
            for item in response.mapItems.prefix(10) {
                guard let name = item.name?.trimmingCharacters(in: .whitespacesAndNewlines), !name.isEmpty else {
                    continue
                }

                let placemark = item.placemark
                let addressParts = [
                    placemark.subThoroughfare,
                    placemark.thoroughfare,
                    placemark.subLocality,
                    placemark.locality,
                    placemark.administrativeArea
                ].compactMap { $0 }

                let formattedAddress = addressParts.isEmpty ? (placemark.title ?? "") : addressParts.joined(separator: ", ")

                var distMeters: Double? = nil
                if let userCoord = coordinate {
                    let loc1 = CLLocation(latitude: userCoord.latitude, longitude: userCoord.longitude)
                    let loc2 = CLLocation(latitude: placemark.coordinate.latitude, longitude: placemark.coordinate.longitude)
                    let d = loc1.distance(from: loc2)
                    if !d.isNaN && !d.isInfinite && d >= 0 {
                        distMeters = d
                    }
                }

                results.append(
                    GroundedPlace(
                        name: String(name.prefix(150)),
                        address: String(formattedAddress.prefix(250)),
                        category: "LANDMARK",
                        description: "Địa điểm tìm thấy từ bản đồ Apple Maps.",
                        stampTitleSuggestion: String(name.prefix(100)),
                        rating: nil,
                        approximateDistanceMeters: distMeters,
                        distanceFormatted: IOSMapsGroundingClient.formatDistance(distMeters),
                        isGroundedWithMaps: false
                    )
                )
            }

            if results.isEmpty {
                completion(self.getCuratedFallbackPlaces(query: trimmed, currentCity: nil))
            } else {
                completion(results)
            }
        }
    }

    // MARK: - Deduplication & Merging

    public func mergeAndDeduplicate(
        serverPlaces: [GroundedPlace],
        nativePlaces: [GroundedPlace]
    ) -> [GroundedPlace] {
        var merged: [GroundedPlace] = []
        var seenNames = Set<String>()

        for p in serverPlaces {
            let norm = normalizeName(p.name)
            if !norm.isEmpty && !seenNames.contains(norm) {
                seenNames.insert(norm)
                merged.append(p)
            }
        }

        for p in nativePlaces {
            let norm = normalizeName(p.name)
            if !norm.isEmpty && !seenNames.contains(norm) {
                seenNames.insert(norm)
                merged.append(p)
            }
        }

        return Array(merged.prefix(15))
    }

    private func normalizeName(_ name: String) -> String {
        return name
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .folding(options: [.caseInsensitive, .diacriticInsensitive], locale: Locale(identifier: "vi_VN"))
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
    }

    // MARK: - Defensive Parsing

    public func parseServerPlacesResponse(_ data: Data) -> [GroundedPlace] {
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let placesArray = root["places"] as? [[String: Any]] else {
            return []
        }

        var results: [GroundedPlace] = []
        for dict in placesArray.prefix(10) {
            guard let rawName = dict["name"] as? String,
                  !rawName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                continue
            }

            let name = String(rawName.trimmingCharacters(in: .whitespacesAndNewlines).prefix(150))
            let address = (dict["address"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines).prefix(250) ?? ""
            let rawCat = (dict["category"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() ?? "LANDMARK"
            let category = IOSMapsGroundingClient.allowedCategories.contains(rawCat) && rawCat != "ALL" ? rawCat : "LANDMARK"
            let description = (dict["description"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines).prefix(500) ?? ""
            let rawTitle = (dict["stampTitleSuggestion"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let stampTitleSuggestion = String((rawTitle.isEmpty ? name : rawTitle).prefix(100))
            let rating = (dict["rating"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines).prefix(10)

            var approxDistance: Double? = nil
            if let distNum = dict["approximateDistanceMeters"] as? Double, !distNum.isNaN && !distNum.isInfinite && distNum >= 0 {
                approxDistance = distNum
            } else if let distInt = dict["approximateDistanceMeters"] as? Int, distInt >= 0 {
                approxDistance = Double(distInt)
            }

            results.append(
                GroundedPlace(
                    name: name,
                    address: String(address),
                    category: category,
                    description: String(description),
                    stampTitleSuggestion: stampTitleSuggestion,
                    rating: rating != nil ? String(rating!) : nil,
                    approximateDistanceMeters: approxDistance,
                    distanceFormatted: IOSMapsGroundingClient.formatDistance(approxDistance),
                    isGroundedWithMaps: true
                )
            )
        }

        return results
    }

    public func parseServerStoryResponse(_ data: Data, placeName: String, locationAddress: String) -> GroundedPostmarkStory? {
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return nil
        }

        let poetic = (root["poeticNote"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
            ?? "Lưu giữ một thoáng mộng mơ cùng ánh nắng dịu dàng tại \(placeName)."
        let historical = (root["historicalFact"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
            ?? "Điểm ghi dấu hành trình bưu chính tại \(locationAddress.isEmpty ? "Việt Nam" : locationAddress)."
        let code = (root["suggestedPostmarkCode"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
            ?? "POST-\(String(placeName.prefix(3)).uppercased())"

        return GroundedPostmarkStory(
            poeticNote: String(poetic.prefix(400)),
            historicalFact: String(historical.prefix(400)),
            suggestedPostmarkCode: String(code.prefix(20))
        )
    }

    private func parseRetryAfter(data: Data?) -> Int {
        guard let data = data,
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let retrySec = root["retry_after_seconds"] as? Int, retrySec > 0 else {
            return 60
        }
        return retrySec
    }

    private func sanitizeCategory(_ cat: String) -> String {
        let upper = cat.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if IOSMapsGroundingClient.allowedCategories.contains(upper) {
            return upper
        }
        return "ALL"
    }

    // MARK: - Curated Fallback Landmarks

    public func getCuratedFallbackPlaces(query: String, currentCity: String?) -> [GroundedPlace] {
        let all: [GroundedPlace] = [
            GroundedPlace(
                name: "Quảng trường Lâm Viên",
                address: "Đường Trần Quốc Toản, Phường 10, TP. Đà Lạt",
                category: "LANDMARK",
                description: "Biểu tượng nụ hoa Atisô và hoa dã quỳ khổng lồ bên hồ.",
                stampTitleSuggestion: "Nụ Hoa Atisô Đà Lạt",
                rating: "4.7★",
                isGroundedWithMaps: false
            ),
            GroundedPlace(
                name: "Hồ Xuân Hương",
                address: "Trung tâm TP. Đà Lạt, Lâm Đồng",
                category: "NATURE",
                description: "Trái tim lãng mạn của thành phố sương mù, mặt nước phẳng lặng soi bóng thông reo.",
                stampTitleSuggestion: "Sương Mù Hồ Xuân Hương",
                rating: "4.8★",
                isGroundedWithMaps: false
            ),
            GroundedPlace(
                name: "Chợ Đêm Đà Lạt (Chợ Âm Phủ)",
                address: "Đường Nguyễn Thị Minh Khai, Phường 1, TP. Đà Lạt",
                category: "STREET",
                description: "Không gian ẩm thực phố núi ấm nồng với sữa đậu nành, bánh tráng nướng.",
                stampTitleSuggestion: "Đêm Lạnh Phố Chợ",
                rating: "4.5★",
                isGroundedWithMaps: false
            ),
            GroundedPlace(
                name: "Tiệm Cà Phê Túi Mơ To",
                address: "Hẻm 31 Sào Nam, Phường 11, TP. Đà Lạt",
                category: "CAFE",
                description: "Vườn cúc hoạ mi trắng tinh khôi và view ngắm thung lũng lồng kính rực rỡ về đêm.",
                stampTitleSuggestion: "Cúc Họa Mi Mơ Màng",
                rating: "4.6★",
                isGroundedWithMaps: false
            ),
            GroundedPlace(
                name: "Bưu Điện Trung Tâm Sài Gòn",
                address: "Số 2 Công xã Paris, Bến Nghé, Quận 1, TP.HCM",
                category: "HERITAGE",
                description: "Kiến trúc Pháp cổ kính biểu tượng bưu chính lâu đời nhất Việt Nam.",
                stampTitleSuggestion: "Bưu Chính Sài Gòn 1891",
                rating: "4.8★",
                isGroundedWithMaps: false
            ),
            GroundedPlace(
                name: "Chợ Bến Thành",
                address: "Đường Lê Lợi, Phường Bến Thành, Quận 1, TP.HCM",
                category: "HERITAGE",
                description: "Biểu tượng văn hóa giao thương trăm năm của Sài Gòn.",
                stampTitleSuggestion: "Tháp Đồng Hồ Bến Thành",
                rating: "4.6★",
                isGroundedWithMaps: false
            ),
            GroundedPlace(
                name: "Hồ Hoàn Kiếm (Hồ Gươm)",
                address: "Quận Hoàn Kiếm, Hà Nội",
                category: "HERITAGE",
                description: "Tháp Rùa trầm mặc giữa lòng thủ đô ngàn năm văn hiến.",
                stampTitleSuggestion: "Mùa Thu Hà Nội",
                rating: "4.9★",
                isGroundedWithMaps: false
            ),
            GroundedPlace(
                name: "Phố Cổ Hội An",
                address: "Thành phố Hội An, Tỉnh Quảng Nam",
                category: "HERITAGE",
                description: "Dãy nhà cổ tường vàng hoa giấy và lung linh ánh đèn lồng bên sông Hoài.",
                stampTitleSuggestion: "Đèn Lồng Phố Cổ",
                rating: "4.9★",
                isGroundedWithMaps: false
            ),
            GroundedPlace(
                name: "Cầu Vàng Bà Nà Hills",
                address: "Hòa Vang, Đà Nẵng",
                category: "LANDMARK",
                description: "Bàn tay khổng lồ nâng dải lụa vàng giữa biển mây bồng bềnh.",
                stampTitleSuggestion: "Dải Lụa Mây Ngàn",
                rating: "4.7★",
                isGroundedWithMaps: false
            )
        ]

        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            return all
        }

        let filtered = all.filter {
            $0.name.localizedCaseInsensitiveContains(trimmed) ||
            $0.address.localizedCaseInsensitiveContains(trimmed) ||
            $0.description.localizedCaseInsensitiveContains(trimmed)
        }

        if !filtered.isEmpty {
            return filtered
        }

        return [
            GroundedPlace(
                name: trimmed,
                address: currentCity ?? "Việt Nam",
                category: "LANDMARK",
                description: "Địa điểm ghi dấu hành trình kỷ niệm của bạn.",
                stampTitleSuggestion: trimmed,
                isGroundedWithMaps: false
            )
        ]
    }
}
