import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

/// Native Swift Thread-Safe NSCache Image Downloader and Memory Cache Manager
public final class ImageCacheManager {
    public static let shared = ImageCacheManager()

    #if canImport(UIKit)
    private let cache = NSCache<NSString, UIImage>()
    #endif

    private init() {
        #if canImport(UIKit)
        cache.countLimit = 100 // Cache up to 100 stamp images
        cache.totalCostLimit = 1024 * 1024 * 50 // 50 MB
        #endif
    }

    #if canImport(UIKit)
    public func image(forKey key: String) -> UIImage? {
        cache.object(forKey: key as NSString)
    }

    public func setImage(_ image: UIImage, forKey key: String) {
        cache.setObject(image, forKey: key as NSString)
    }

    public func fetchImage(from urlString: String, completion: @escaping (UIImage?) -> Void) {
        if let cached = image(forKey: urlString) {
            completion(cached)
            return
        }

        guard let url = URL(string: urlString) else {
            completion(nil)
            return
        }

        URLSession.shared.dataTask(with: url) { [weak self] data, response, error in
            guard let data = data, let img = UIImage(data: data), error == nil else {
                DispatchQueue.main.async { completion(nil) }
                return
            }
            self?.setImage(img, forKey: urlString)
            DispatchQueue.main.async {
                completion(img)
            }
        }.resume()
    }
    #endif
}
