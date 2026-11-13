import SwiftUI

@main
struct NuaSovereignApp: App {
    @StateObject private var model = CompanionViewModel()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(model)
        }
    }
}
