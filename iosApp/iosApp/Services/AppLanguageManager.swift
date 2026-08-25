import SwiftUI
import Combine

enum AppLanguage: String, CaseIterable, Identifiable {
    case vietnamese = "vi"
    case english = "en"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .vietnamese: return "Tiếng Việt 🇻🇳"
        case .english: return "English 🇬🇧"
        }
    }
}

class AppLanguageManager: ObservableObject {
    static let shared = AppLanguageManager()

    @Published var currentLanguage: AppLanguage {
        didSet {
            UserDefaults.standard.set(currentLanguage.rawValue, forKey: "app_language")
        }
    }

    private init() {
        let saved = UserDefaults.standard.string(forKey: "app_language") ?? "vi"
        self.currentLanguage = AppLanguage(rawValue: saved) ?? .vietnamese
    }

    func setLanguage(_ lang: AppLanguage) {
        withAnimation {
            self.currentLanguage = lang
        }
    }

    // Helper localization lookup
    func string(vi: String, en: String) -> String {
        return currentLanguage == .vietnamese ? vi : en
    }
}
