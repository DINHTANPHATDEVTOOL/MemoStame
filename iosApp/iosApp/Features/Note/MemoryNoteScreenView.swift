import SwiftUI
#if canImport(UIKit)
import UIKit
#endif
import shared

struct MemoryNoteScreenView: View {
    let imageUrl: String
    var shape: String
    var stampColorHex: String
    var replyToPostId: String?
    var initialLocation: String
    let repository: SharedMemoStampRepository
    var onSavedSuccess: () -> Void
    var onCancel: () -> Void

    init(
        imageUrl: String,
        shape: String = "classic",
        stampColorHex: String = "#D32F2F",
        replyToPostId: String? = nil,
        initialLocation: String = "",
        repository: SharedMemoStampRepository,
        onSavedSuccess: @escaping () -> Void,
        onCancel: @escaping () -> Void
    ) {
        self.imageUrl = imageUrl
        self.shape = shape
        self.stampColorHex = stampColorHex
        self.replyToPostId = replyToPostId
        self.initialLocation = initialLocation
        self.repository = repository
        self.onSavedSuccess = onSavedSuccess
        self.onCancel = onCancel
    }

    init(
        imageUrl: String,
        shape: String = "classic",
        stampColorHex: String = "#D32F2F",
        replyToPostId: String? = nil,
        repository: SharedMemoStampRepository,
        onSavedSuccess: @escaping () -> Void,
        onCancel: @escaping () -> Void
    ) {
        self.imageUrl = imageUrl
        self.shape = shape
        self.stampColorHex = stampColorHex
        self.replyToPostId = replyToPostId
        self.initialLocation = ""
        self.repository = repository
        self.onSavedSuccess = onSavedSuccess
        self.onCancel = onCancel
    }

    @State private var title: String = ""
    @State private var caption: String = ""
    @State private var locationSearch: String = ""
    @State private var showLocationPickerSheet: Bool = false
    @State private var selectedAudience: String = "Friends"
    @State private var selectedCollectionId: String? = nil
    @State private var selectedMood: String = "happy"
    @State private var memoryDate: Date = Date()
    @State private var isGpsLocating: Bool = false
    @State private var isSaving: Bool = false
    @State private var localStampAlreadySaved: StampItem? = nil
    @State private var alertMessage: String? = nil
    @State private var showAlert: Bool = false

    struct MoodOption: Hashable {
        let key: String
        let label: String
    }
    let moodOptions: [MoodOption] = [
        MoodOption(key: "happy", label: "Happy"),
        MoodOption(key: "love", label: "Love"),
        MoodOption(key: "travel", label: "Travel"),
        MoodOption(key: "chill", label: "Chill"),
        MoodOption(key: "excited", label: "Excited"),
        MoodOption(key: "nostalgic", label: "Nostalgic"),
        MoodOption(key: "peaceful", label: "Peaceful"),
        MoodOption(key: "special", label: "Special")
    ]
    let audienceTypes = ["Friends", "Only Me"]

    private var currentUid: String {
        SupabaseAuthService.shared.currentUserId ?? ""
    }

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
                    guard !isSaving else { return }
                    let authUid = SupabaseAuthService.shared.currentUserId ?? ""
                    guard IOSLocalPersistenceStore.shared.isValidAuthenticatedUserId(authUid) else {
                        alertMessage = "Vui lòng đăng nhập để lưu tem."
                        showAlert = true
                        return
                    }

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
                    guard let renderedFileUrl = StampRenderEngine.shared.saveStampPng(
                        photo: inputImage,
                        title: finalTitle,
                        location: locationSearch.isEmpty ? nil : locationSearch,
                        dateStr: formattedDate,
                        stampColorHex: stampColorHex,
                        shape: shape
                    ) else {
                        alertMessage = "Không thể xuất ảnh tem PNG. Vui lòng thử lại."
                        showAlert = true
                        return
                    }
                    let finalRenderedUrl = renderedFileUrl.absoluteString
                    #else
                    let finalRenderedUrl = imageUrl
                    #endif

                    let stampItemTitle = finalTitle.isEmpty ? "Memory Stamp" : finalTitle

                    // 1. Save local stamp exactly once
                    let currentStamp: StampItem
                    if let existing = localStampAlreadySaved {
                        currentStamp = existing
                    } else {
                        let saved = repository.addStamp(
                            title: stampItemTitle,
                            note: caption,
                            location: locationSearch.isEmpty ? nil : locationSearch,
                            imageUrl: finalRenderedUrl,
                            originalImageUrl: imageUrl,
                            shape: shape,
                            collectionId: selectedCollectionId,
                            audience: audience,
                            mood: selectedMood,
                            memoryDate: Int64(memoryDate.timeIntervalSince1970 * 1000)
                        )
                        localStampAlreadySaved = saved
                        currentStamp = saved
                        IOSLocalPersistenceStore.shared.saveData(repository: repository, userId: authUid)
                    }

                    // 2. Normal Save vs Cross-Device Reply Flow
                    if let replyId = replyToPostId, !replyId.isEmpty {
                        isSaving = true
                        SupabaseMediaUploader.shared.ensureRemoteRenderedStamp(ownerUid: authUid, localOrRemotePath: finalRenderedUrl) { uploadResult in
                            switch uploadResult {
                            case .success(let remoteUrl):
                                IOSFeedRepository.shared.createStampReply(
                                    postId: replyId,
                                    stampId: currentStamp.id,
                                    stampUrl: remoteUrl,
                                    shape: shape,
                                    note: caption
                                ) { replyResult in
                                    DispatchQueue.main.async {
                                        isSaving = false
                                        switch replyResult {
                                        case .success:
                                            onSavedSuccess()
                                        case .failure(let err):
                                            alertMessage = "Lưu tem thành công nhưng gửi phản hồi thất bại: \(err.localizedDescription)"
                                            showAlert = true
                                        }
                                    }
                                }
                            case .failure(let err):
                                DispatchQueue.main.async {
                                    isSaving = false
                                    alertMessage = "Tải ảnh tem lên máy chủ thất bại: \(err.localizedDescription)"
                                    showAlert = true
                                }
                            }
                        }
                    } else {
                        onSavedSuccess()
                    }
                }) {
                    if isSaving {
                        ProgressView().scaleEffect(0.8)
                    } else {
                        Text("Lưu Tem")
                            .font(.body.bold())
                            .foregroundColor(Color(red: 0.85, green: 0.25, blue: 0.20))
                    }
                }
                .disabled(isSaving)
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
                        shape: shape,
                        isInteractive: true,
                        stampColorHex: stampColorHex
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
                                    ForEach(moodOptions, id: \.key) { mood in
                                        HStack(spacing: 6) {
                                            MemoStampIcon(key: mood.key, contentDescription: mood.label)
                                                .frame(width: 14, height: 14)
                                            Text(mood.label)
                                                .font(.caption.bold())
                                        }
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 8)
                                        .background(selectedMood == mood.key ? MSColors.stamp : Color.white)
                                        .foregroundColor(selectedMood == mood.key ? .white : MSColors.ink)
                                        .cornerRadius(16)
                                        .overlay(RoundedRectangle(cornerRadius: 16).stroke(selectedMood == mood.key ? Color.clear : MSColors.lightGrey, lineWidth: 1))
                                        .onTapGesture {
                                            selectedMood = mood.key
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
                                            MemoStampIcon(key: col.iconKey, contentDescription: col.name)
                                                .frame(width: 16, height: 16)
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

                        // Location Section Header
                        HStack {
                            Text("ĐỊA ĐIỂM KỶ NIỆM")
                                .font(.caption2.bold())
                                .foregroundColor(.secondary)
                            Spacer()
                            Button(action: { showLocationPickerSheet = true }) {
                                HStack(spacing: 4) {
                                    Image(systemName: "map.fill")
                                        .font(.caption2)
                                    Text("Bản đồ AI")
                                        .font(.caption2.bold())
                                }
                                .foregroundColor(Color(red: 0.85, green: 0.25, blue: 0.20))
                            }
                        }

                        // Search & Picker Field
                        HStack(spacing: 10) {
                            Image(systemName: "mappin.and.ellipse")
                                .foregroundColor(locationSearch.isEmpty ? .gray : Color(red: 0.85, green: 0.25, blue: 0.20))

                            TextField("Nhập hoặc chọn địa điểm kỷ niệm...", text: $locationSearch)
                                .font(.subheadline)
                                .foregroundColor(MSColors.ink)

                            if !locationSearch.isEmpty {
                                Button(action: { locationSearch = "" }) {
                                    Image(systemName: "xmark.circle.fill")
                                        .foregroundColor(.gray.opacity(0.6))
                                }
                            }

                            Button(action: { showLocationPickerSheet = true }) {
                                Image(systemName: "sparkles")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(Color(red: 0.82, green: 0.65, blue: 0.35))
                                    .padding(6)
                                    .background(Color(red: 0.82, green: 0.65, blue: 0.35).opacity(0.15))
                                    .clipShape(Circle())
                            }
                        }
                        .padding(12)
                        .background(Color.white)
                        .cornerRadius(10)
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.gray.opacity(0.25), lineWidth: 1))

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
        .onAppear {
            if locationSearch.isEmpty && !initialLocation.isEmpty {
                locationSearch = initialLocation
            }
        }
        .sheet(isPresented: $showLocationPickerSheet) {
            LocationPickerSheetView(
                initialLocation: locationSearch,
                onDismiss: { showLocationPickerSheet = false },
                onLocationSelected: { locationName, suggestedTitle, story in
                    showLocationPickerSheet = false
                    locationSearch = locationName

                    // Non-destructive title autofill: only populate if user title is currently blank
                    if title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                       let sug = suggestedTitle?.trimmingCharacters(in: .whitespacesAndNewlines), !sug.isEmpty {
                        title = sug
                    }

                    // Non-destructive caption autofill: only populate if user caption is currently blank
                    if caption.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                       let storyNote = story?.poeticNote.trimmingCharacters(in: .whitespacesAndNewlines), !storyNote.isEmpty {
                        caption = storyNote
                    }
                }
            )
        }
        .alert(isPresented: $showAlert) {
            Alert(
                title: Text("Thông Báo"),
                message: Text(alertMessage ?? "Có lỗi xảy ra."),
                dismissButton: .default(Text("Đóng"))
            )
        }
    }
}
