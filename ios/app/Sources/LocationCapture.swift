import CoreLocation
import Foundation

/// One location fix on request, reverse geocoded to a place name, the way the Android app's
/// LocationProvider does it. Nothing runs in the background and nothing is kept here: the
/// fix goes onto the memo, on the user's own server, and that is the only place it goes.
@MainActor
final class LocationCapture: NSObject, CLLocationManagerDelegate {
    static let shared = LocationCapture()

    struct Fix {
        let latitude: Double
        let longitude: Double
        let placeName: String
    }

    enum Failure: LocalizedError {
        case denied, noFix

        var errorDescription: String? {
            switch self {
            case .denied: return "location access is off for MyMemos in Settings."
            case .noFix: return "the phone could not work out where it is."
            }
        }
    }

    private let manager = CLLocationManager()
    private var waiting: CheckedContinuation<CLLocation, Error>?
    private var authorisation: CheckedContinuation<Void, Never>?

    override private init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    func current() async throws -> Fix {
        if manager.authorizationStatus == .notDetermined {
            await withCheckedContinuation { continuation in
                authorisation = continuation
                manager.requestWhenInUseAuthorization()
            }
        }
        guard manager.authorizationStatus == .authorizedWhenInUse || manager.authorizationStatus == .authorizedAlways else {
            throw Failure.denied
        }
        let location = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<CLLocation, Error>) in
            waiting = continuation
            manager.requestLocation()
        }
        let name = await placeName(for: location)
        return Fix(latitude: location.coordinate.latitude, longitude: location.coordinate.longitude, placeName: name)
    }

    /// The nearest thing to an address, or empty when the geocoder has nothing to say.
    private func placeName(for location: CLLocation) async -> String {
        let marks = (try? await CLGeocoder().reverseGeocodeLocation(location)) ?? []
        guard let mark = marks.first else { return "" }
        return [mark.name, mark.locality].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: ", ")
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        Task { @MainActor in
            authorisation?.resume()
            authorisation = nil
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        Task { @MainActor in
            if let fix = locations.last { waiting?.resume(returning: fix) } else { waiting?.resume(throwing: Failure.noFix) }
            waiting = nil
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in
            waiting?.resume(throwing: error)
            waiting = nil
        }
    }
}
