import SwiftUI

struct ContentView: View {
    @EnvironmentObject var model: CompanionViewModel

    var body: some View {
        NavigationStack {
            List {
                Section("NUA Sovereign") {
                    Text("Your Intelligence. Your Control.")
                        .foregroundStyle(.secondary)
                }

                Section("Presence Mesh") {
                    if let presence = model.presence {
                        LabeledContent("Companion", value: presence.identity.ownerLabel)
                        LabeledContent("Platform", value: presence.identity.platform)
                        LabeledContent("Trust", value: presence.trustState)
                        LabeledContent("Models", value: String(presence.models.count))
                    } else {
                        Text(model.error ?? "No local companion status yet.")
                            .foregroundStyle(.secondary)
                    }
                    Button("Refresh") {
                        Task { await model.refresh() }
                    }
                    .disabled(model.loading)
                }

                Section("Platform boundary") {
                    Text("iOS is a NUA control and intelligence surface. Device-level actions remain limited by iOS permissions; privileged companion actions require paired-device authorization.")
                        .font(.footnote)
                }
            }
            .navigationTitle("NUA")
            .task { await model.refresh() }
        }
    }
}
