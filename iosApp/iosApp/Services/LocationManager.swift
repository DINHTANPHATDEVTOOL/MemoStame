import Foundation
import CoreLocation
import Combine

/// Native Swift CoreLocation Manager providing GPS location tracking and Dalat fallback tag.
final class LocationManager: NSObject, ObservableObject, CLLocationManagerDelegate {
    static let shared = LocationManager()

    @Published var currentCityName: String = ""
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
        if CLLocationManager.authorizationStatus() == .notDetermined {
            clManager.requestWhenInUseAuthorization()
        }
    }

    func fetchCurrentLocation(completion: @escaping (String) -> Void) {
        isLocating = true
        requestLocationPermission()
        clManager.requestLocation()
        
        if let location = clManager.location {
            self.currentCoordinate = location.coordinate
            geocoder.reverseGeocodeLocation(location) { [weak self] placemarks, error in
                DispatchQueue.main.async {
                    self?.isLocating = false
                    if let mark = placemarks?.first, error == nil {
                        let parts = [mark.name, mark.subLocality, mark.locality].compactMap { $0 }
                        let result = parts.isEmpty ? (mark.locality ?? "") : parts.joined(separator: ", ")
                        self?.currentCityName = result
                        completion(result)
                    } else {
                        completion(self?.currentCityName ?? "")
                    }
                }
            }
        } else {
            clManager.startUpdatingLocation()
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) { [weak self] in
                guard let self = self else { return }
                self.isLocating = false
                completion(self.currentCityName)
            }
        }
    }

    // MARK: - CLLocationManagerDelegate
    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        self.authorizationStatus = status
        if status == .authorizedWhenInUse || status == .authorizedAlways {
            clManager.startUpdatingLocation()
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        self.isLocating = false
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        self.currentCoordinate = location.coordinate
        clManager.stopUpdatingLocation()

        geocoder.reverseGeocodeLocation(location) { [weak self] placemarks, error in
            guard let self = self, let mark = placemarks?.first, error == nil else { return }
            let city = mark.locality ?? mark.administrativeArea ?? ""
            let country = mark.country ?? ""
            let res = [city, country].filter { !$0.isEmpty }.joined(separator: ", ")
            DispatchQueue.main.async {
                self.currentCityName = res
            }
        }
    }
}
