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
                Text(L10n.createAProfile)
                    .font(.title2)
                    .padding(.top, 32)
                ProfileFormView(saveLabel: L10n.createProfile)
            }
        } else {
            VStack(spacing: 8) {
                Text(L10n.appName)
                    .font(.largeTitle)
                Text(L10n.appTagline)
                    .font(.body)
                    .foregroundStyle(.secondary)
                Button(L10n.createAProfile) {
                    showForm = true
                }
                .padding(.top, 8)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }
}
