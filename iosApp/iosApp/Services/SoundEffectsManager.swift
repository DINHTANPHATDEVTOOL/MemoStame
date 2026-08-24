import Foundation
import AudioToolbox

/// Native Swift Sound Effects Engine playing retro shutter click and stamp press audio feedback.
final class SoundEffectsManager {
    static let shared = SoundEffectsManager()

    private init() {}

    func playShutterSound() {
        // System Sound ID 1108 is the classic iOS Camera Shutter Click
        AudioServicesPlaySystemSound(1108)
    }

    func playStampPressSound() {
        // System Sound ID 1109 or 1057 (Tink / Lock)
        AudioServicesPlaySystemSound(1057)
    }

    func playEnvelopeSealSound() {
        // System Sound ID 1001 (Mail Sent)
        AudioServicesPlaySystemSound(1001)
    }
}
