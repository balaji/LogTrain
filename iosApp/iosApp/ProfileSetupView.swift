import SwiftUI

/// Native SwiftUI replacement for the Compose `ProfileSetupFlow`: a welcome
/// step with a "Create a Profile" link that leads to the form. Once the
/// profile is saved, `ContentView`'s `ProfileState` observer switches to the
/// main tabs, so this view doesn't navigate anywhere itself.
struct ProfileSetupView: View {
    @State private var showForm = false

    var body: some View {
        if showForm {
            VStack {
                Text("Create a Profile")
                    .font(.title2)
                    .padding(.top, 32)
                ProfileFormView(saveLabel: "Create Profile")
            }
        } else {
            VStack(spacing: 8) {
                Text("LogTrain")
                    .font(.largeTitle)
                Text("Track your workouts, all on your device.")
                    .font(.body)
                    .foregroundStyle(.secondary)
                Button("Create a Profile") {
                    showForm = true
                }
                .padding(.top, 8)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }
}
