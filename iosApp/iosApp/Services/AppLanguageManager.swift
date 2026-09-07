import SwiftUI
import Combine

enum AppLanguageMode: String, CaseIterable, Identifiable {
    case system = "system"
    case vietnamese = "vi"
    case english = "en"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .system: return "System default"
        case .vietnamese: return "Tiếng Việt"
        case .english: return "English"
        }
    }

    static func from(code: String?) -> AppLanguageMode {
        guard let clean = code?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() else {
            return .system
        }
        switch clean {
        case "vi": return .vietnamese
        case "en": return .english
        case "system": return .system
        default: return .system
        }
    }
}

class AppLanguageManager: ObservableObject {
    static let shared = AppLanguageManager()

    static let prefsModeKey = "app_language_mode"
    static let prefsLegacyKey = "app_language"

    @Published var currentMode: AppLanguageMode {
        didSet {
            UserDefaults.standard.set(currentMode.rawValue, forKey: AppLanguageManager.prefsModeKey)
            UserDefaults.standard.set(currentMode.rawValue, forKey: AppLanguageManager.prefsLegacyKey)
            updateActiveLocale()
        }
    }

    @Published var activeLocale: Locale = Locale.current

    private var cachedBundles: [String: Bundle] = [:]

    private init() {
        let initialMode = AppLanguageManager.migrateAndLoadMode()
        self.currentMode = initialMode
        updateActiveLocale()
    }

    private static func migrateAndLoadMode() -> AppLanguageMode {
        if let storedMode = UserDefaults.standard.string(forKey: prefsModeKey) {
            return AppLanguageMode.from(code: storedMode)
        }

        if let legacy = UserDefaults.standard.string(forKey: prefsLegacyKey) {
            let mode = AppLanguageMode.from(code: legacy)
            UserDefaults.standard.set(mode.rawValue, forKey: prefsModeKey)
            return mode
        }

        // Fresh install / no stored preference: MUST follow SYSTEM (never force Vietnamese)
        return .system
    }

    func setLanguageMode(_ mode: AppLanguageMode) {
        withAnimation {
            self.currentMode = mode
        }
    }

    func setLanguage(_ mode: AppLanguageMode) {
        setLanguageMode(mode)
    }

    var effectiveLanguageCode: String {
        switch currentMode {
        case .vietnamese:
            return "vi"
        case .english:
            return "en"
        case .system:
            let preferred = Locale.preferredLanguages.first?.lowercased() ?? Locale.current.identifier.lowercased()
            if preferred.hasPrefix("vi") {
                return "vi"
            }
            return "en"
        }
    }

    private func updateActiveLocale() {
        let code = effectiveLanguageCode
        self.activeLocale = Locale(identifier: code)
    }

    private func bundleForCurrentLanguage() -> Bundle {
        let lang = effectiveLanguageCode
        if let cached = cachedBundles[lang] {
            return cached
        }
        if let path = Bundle.main.path(forResource: lang, ofType: "lproj"),
           let bundle = Bundle(path: path) {
            cachedBundles[lang] = bundle
            return bundle
        }
        return Bundle.main
    }

    /// Primary production localization lookup
    func localized(_ key: String, _ args: CVarArg...) -> String {
        let bundle = bundleForCurrentLanguage()
        let format = bundle.localizedString(forKey: key, value: nil, table: nil)
        if args.isEmpty {
            // If value is missing in bundle, return fallback key or check main bundle
            if format == key {
                let mainFormat = Bundle.main.localizedString(forKey: key, value: nil, table: nil)
                return mainFormat
            }
            return format
        }
        return String(format: format, locale: activeLocale, arguments: args)
    }

    /// Backwards compatibility helper during migration phase
    @available(*, deprecated, message: "Use localized(key) instead")
    func string(vi: String, en: String) -> String {
        return effectiveLanguageCode == "vi" ? vi : en
    }
}
