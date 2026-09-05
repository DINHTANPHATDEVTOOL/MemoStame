import SwiftUI
#if canImport(UIKit)
import UIKit
#endif
import shared

enum IOSAuthGateState {
    case checking
    case authenticated
    case unauthenticated
}

enum ActiveTab {
    case home
    case vault
    case camera
    case friends
    case profile
}

struct ContentView: View {
    @StateObject private var homeViewModel: HomeObservableViewModel
    private let repository: SharedMemoStampRepository
    
    @State private var authGateState: IOSAuthGateState = .checking
    @State private var startupBootstrapStarted: Bool = false
    @State private var selectedTab: ActiveTab = .home
    @State private var showCameraModal: Bool = false
    @State private var replyToPostId: String? = nil
    @State private var showOfflineToast: Bool = false

    let platform = Platform_iosKt.getPlatform()

    init() {
        let repo = SharedMemoStampRepository()
        self.repository = repo
        _homeViewModel = StateObject(wrappedValue: HomeObservableViewModel(repository: repo))
    }

    var body: some View {
        Group {
            switch authGateState {
            case .checking:
                ZStack {
                    MSColors.paper.ignoresSafeArea()
                    VStack(spacing: 16) {
                        Image(systemName: "envelope.badge.fill")
                            .font(.system(size: 48))
                            .foregroundColor(MSColors.stamp)
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: MSColors.stamp))
                        Text("MemoStamp")
                            .font(.headline.bold())
                            .foregroundColor(MSColors.ink)
                    }
                }

            case .unauthenticated:
                AuthLoginScreenView(
                    repository: repository,
                    onLoginSuccess: {
                        withAnimation {
                            authGateState = .authenticated
                        }
                    }
                )

            case .authenticated:
                NavigationView {
                    ZStack(alignment: .bottom) {
                        // Top Offline Toast Alert
                        if showOfflineToast {
                            VStack {
                                HStack(spacing: 8) {
                                    Image(systemName: "wifi.slash")
                                        .foregroundColor(.white)
                                    Text("📡 Ngoại Tuyến: Kết nối Wi-Fi/4G để đồng bộ đám mây Supabase")
                                        .font(.caption.bold())
                                        .foregroundColor(.white)
                                }
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                                .background(Color(red: 0.85, green: 0.25, blue: 0.20))
                                .cornerRadius(20)
                                .shadow(radius: 4)
                                .padding(.top, 40)
                                Spacer()
                            }
                            .zIndex(10)
                        }

                        // Screen Switcher Content
                        Group {
                            switch selectedTab {
                            case .home:
                                HomeScreenView(
                                    viewModel: homeViewModel,
                                    onNavigateToCamera: { targetReplyId in
                                        self.replyToPostId = targetReplyId
                                        self.showCameraModal = true
                                    }
                                )
                            case .vault:
                                StampVaultScreenView(
                                    repository: repository,
                                    onNavigateToCamera: {
                                        self.replyToPostId = nil
                                        self.showCameraModal = true
                                    }
                                )
                            case .friends:
                                FriendsAndTradeScreenView(repository: repository)
                            case .profile:
                                PassportScreenView(
                                    repository: repository,
                                    onLogout: {
                                        performLogout()
                                    }
                                )
                            default:
                                HomeScreenView(
                                    viewModel: homeViewModel,
                                    onNavigateToCamera: { targetReplyId in
                                        self.replyToPostId = targetReplyId
                                        self.showCameraModal = true
                                    }
                                )
                            }
                        }

                        // Custom Floating Bottom Navigation Bar
                        HStack {
                            // Home Tab Button
                            BottomNavItem(
                                iconName: "house.fill",
                                label: "Home",
                                isSelected: selectedTab == .home
                            ) {
                                selectedTab = .home
                            }

                            Spacer()

                            // Vault Tab Button
                            BottomNavItem(
                                iconName: "square.grid.2x2.fill",
                                label: "Vault",
                                isSelected: selectedTab == .vault
                            ) {
                                selectedTab = .vault
                            }

                            Spacer()

                            // Center Floating Camera Button
                            Button(action: {
                                self.replyToPostId = nil
                                self.showCameraModal = true
                            }) {
                                ZStack {
                                    Circle()
                                        .fill(MSColors.stamp)
                                        .frame(width: 58, height: 58)
                                        .shadow(color: MSColors.stamp.opacity(0.4), radius: 8, x: 0, y: 4)

                                    Image(systemName: "camera.fill")
                                        .font(.system(size: 22, weight: .bold))
                                        .foregroundColor(.white)
                                }
                            }
                            .offset(y: -16)

                            Spacer()

                            // Friends & Trade Tab Button
                            BottomNavItem(
                                iconName: "person.2.fill",
                                label: "Trade",
                                isSelected: selectedTab == .friends
                            ) {
                                selectedTab = .friends
                            }

                            Spacer()

                            // Passport Profile Tab Button
                            BottomNavItem(
                                iconName: "person.crop.square.fill",
                                label: "Profile",
                                isSelected: selectedTab == .profile
                            ) {
                                selectedTab = .profile
                            }
                        }
                        .padding(.horizontal, 24)
                        .padding(.vertical, 12)
                        .background(
                            RoundedRectangle(cornerRadius: 30)
                                .fill(MSColors.white.opacity(0.98))
                                .shadow(color: Color.black.opacity(0.10), radius: 10, x: 0, y: 4)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 30)
                                        .stroke(MSColors.lightGrey, lineWidth: 1)
                                )
                        )
                        .padding(.horizontal, 16)
                        .padding(.bottom, 8)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                    }
                    .navigationBarHidden(true)
                }
                .navigationViewStyle(StackNavigationViewStyle())
                .fullScreenCover(isPresented: $showCameraModal) {
                    CameraFlowContainerView(
                        replyToPostId: replyToPostId,
                        repository: repository,
                        onComplete: {
                            showCameraModal = false
                            selectedTab = .home
                            homeViewModel.refreshFeed()
                        }
                    )
                }
            }
        }
        .onAppear {
            LocationManager.shared.requestLocationPermission()
            bootstrapSessionIfNeeded()
        }
    }

    private func bootstrapSessionIfNeeded() {
        guard !startupBootstrapStarted else { return }
        startupBootstrapStarted = true
        authGateState = .checking

        SupabaseAuthService.shared.loadOrRefreshSession { session in
            DispatchQueue.main.async {
                guard let session = session,
                      IOSLocalPersistenceStore.shared.isValidAuthenticatedUserId(session.userId) else {
                    self.repository.resetUserScopedState()
                    withAnimation {
                        self.authGateState = .unauthenticated
                    }
                    return
                }

                IOSAuthenticatedSessionCoordinator.shared.hydrate(
                    session: session,
                    repository: self.repository
                ) { result in
                    DispatchQueue.main.async {
                        switch result {
                        case .success:
                            withAnimation {
                                self.authGateState = .authenticated
                            }
                        case .failure:
                            self.repository.resetUserScopedState()
                            withAnimation {
                                self.authGateState = .unauthenticated
                            }
                        }
                    }
                }
            }
        }
    }

    private func performLogout() {
        let currentUid = (repository.currentUser.value as? UserProfile)?.uid ?? ""
        if IOSLocalPersistenceStore.shared.isValidAuthenticatedUserId(currentUid) {
            IOSLocalPersistenceStore.shared.saveData(repository: repository, userId: currentUid)
        }
        SupabaseAuthService.shared.signOut { _ in }
        repository.resetUserScopedState()
        withAnimation {
            selectedTab = .home
            showCameraModal = false
            replyToPostId = nil
            authGateState = .unauthenticated
        }
    }
}

// Subview: Bottom Navigation Bar Item
struct BottomNavItem: View {
    let iconName: String
    let label: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Image(systemName: iconName)
                    .font(.system(size: 20))
                    .foregroundColor(isSelected ? MSColors.stamp : MSColors.grey)
                Text(label)
                    .font(.caption2.bold())
                    .foregroundColor(isSelected ? MSColors.stamp : MSColors.grey)
            }
        }
    }
}

// Full Camera & Memory Note Flow Modal Container (3-Step Navigation Flow: Camera -> Editor -> Memory Note)
struct CameraFlowContainerView: View {
    let replyToPostId: String?
    let repository: SharedMemoStampRepository
    let onComplete: () -> Void

    @State private var rawCapturedUrl: String? = nil
    @State private var editedStampUrl: String? = nil
    @State private var selectedMoldId: String = "classic_perforated"
    @State private var selectedColorHex: String = "#D32F2F"

    var body: some View {
        Group {
            if let editedUrl = editedStampUrl {
                MemoryNoteScreenView(
                    imageUrl: editedUrl,
                    shape: selectedMoldId,
                    stampColorHex: selectedColorHex,
                    replyToPostId: replyToPostId,
                    repository: repository,
                    onSavedSuccess: onComplete,
                    onCancel: { editedStampUrl = nil }
                )
            } else if let rawUrl = rawCapturedUrl {
                StampEditorScreenView(
                    initialImageUrl: rawUrl,
                    onContinue: { photoUrl, moldId, colorHex in
                        self.selectedMoldId = moldId
                        self.selectedColorHex = colorHex
                        self.editedStampUrl = photoUrl
                    },
                    onCancel: {
                        self.rawCapturedUrl = nil
                    }
                )
            } else {
                CameraScreenView(
                    replyToPostId: replyToPostId,
                    onNavigateToNote: { url in
                        rawCapturedUrl = url
                    },
                    onCancel: onComplete
                )
            }
        }
    }
}
