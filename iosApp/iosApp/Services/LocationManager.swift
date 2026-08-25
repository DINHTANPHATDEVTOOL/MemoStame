import Foundation
import CoreLocation
import Combine

/// Native Swift CoreLocation Manager providing GPS location tracking and Dalat fallback tag.
final class LocationManager: NSObject, ObservableObject, CLLocationManagerDelegate {
    static let shared = LocationManager()

    @Published var currentCityName: String = "Đà Lạt, Lâm Đồng"
    @Published var currentCoordinate: CLLocationCoordinate2D? = nil
    @Published var isLocating: Bool = false
    @Published var authorizationStatus: CLAuthorizationStatus = .notDetermined

    private let clManager = CLLocationManager()
    private let geocoder = CLGeocoder()

    private override init() {
        super.init()
        clManager.delegate = self
        clManager.desiredAccuracy = kCLLocationAccuracyBest
    }

    func requestLocationPermission() {
        if clManager.authorizationStatus == .notDetermined {
            clManager.requestWhenInUseAuthorization()
        }
    }

    func fetchCurrentLocation(completion: @escaping (String) -> Void) {
        isLocating = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) { [weak self] in
            guard let self = self else { return }
            self.isLocating = false
            let place = "Quảng trường Lâm Viên, Đà Lạt"
            self.currentCityName = place
            completion(place)
        }
    }

    // MARK: - CLLocationManagerDelegate
    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        self.authorizationStatus = status
        if status == .authorizedWhenInUse || status == .authorizedAlways {
            clManager.startUpdatingLocation()
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        self.currentCoordinate = location.coordinate
        clManager.stopUpdatingLocation()

        geocoder.reverseGeocodeLocation(location) { [weak self] placemarks, error in
            guard let self = self, let mark = placemarks?.first, error == nil else { return }
            let city = mark.locality ?? mark.administrativeArea ?? "Đà Lạt"
            let country = mark.country ?? "Việt Nam"
            DispatchQueue.main.async {
                self.currentCityName = "\(city), \(country)"
            }
        }
    }
}
