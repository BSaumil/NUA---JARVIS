import Foundation

@MainActor
final class CompanionViewModel: ObservableObject {
    @Published var health: LocalApiHealth?
    @Published var presence: PresenceState?
    @Published var error: String?
    @Published var loading = false

    private let base = URL(string: "http://127.0.0.1:8765")!

    func refresh() async {
        loading = true
        defer { loading = false }
        do {
            health = try await get("health", as: LocalApiHealth.self)
            presence = try await get("presence", as: PresenceState.self)
            error = nil
        } catch {
            health = nil
            presence = nil
            self.error = "NUA Companion is unavailable. Pairing and privileged jobs remain disabled."
        }
    }

    private func get<T: Decodable>(_ path: String, as type: T.Type) async throws -> T {
        let allowed = Set(["health", "presence"])
        guard allowed.contains(path) else { throw URLError(.unsupportedURL) }
        var url = base
        url.append(path: path)
        let (data, response) = try await URLSession.shared.data(from: url)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            throw URLError(.badServerResponse)
        }
        return try JSONDecoder().decode(T.self, from: data)
    }
}
