import Foundation

struct DeviceIdentity: Codable {
    let deviceId: String
    let publicKey: String
    let ownerLabel: String
    let platform: String
    let appVersion: String
    let protocolVersion: Int
}

struct DeviceCapability: Codable, Identifiable {
    var id: String
    let version: Int
    let readOnly: Bool
    let requiresOwnerApproval: Bool
}

struct ModelAdvertisement: Codable, Identifiable {
    let id: String
    let provider: String
    let localToNode: Bool
    let supportsVision: Bool
    let supportsStructuredOutput: Bool
    let maxContextTokens: Int?
}

struct PresenceState: Codable {
    let identity: DeviceIdentity
    let trustState: String
    let capabilities: [DeviceCapability]
    let models: [ModelAdvertisement]
    let lastVerifiedAtMillis: Int64
}

struct LocalApiHealth: Codable {
    let protocolVersion: Int
    let appVersion: String
    let deviceId: String
    let ready: Bool
}
