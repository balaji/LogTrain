import SwiftUI
import SharedUI

/// Native SwiftUI replacement for the Compose `ProfileScreen`: shows the
/// saved profile with an Edit link that swaps in `ProfileFormView`.
struct ProfileView: View {
    @State private var profile: Profile?
    @State private var editing = false

    var body: some View {
        Group {
            if let profile {
                if editing {
                    ProfileFormView(initial: profile, onSaved: { editing = false })
                } else {
                    List {
                        LabeledContent("Name", value: profile.name)
                        LabeledContent("Age", value: profile.age.map { "\($0)" } ?? "—")
                        LabeledContent("Gender", value: profile.gender ?? "—")
                        LabeledContent("Weight", value: profile.weight.map { "\($0) kg" } ?? "—")
                        LabeledContent("Height", value: profile.height.map { "\($0) cm" } ?? "—")
                    }
                    .toolbar {
                        ToolbarItem(placement: .primaryAction) {
                            Button("Edit") { editing = true }
                        }
                    }
                }
            } else {
                ProgressView()
            }
        }
        .task {
            for await value in ProfileRepository.shared.profile {
                profile = value
            }
        }
    }
}
