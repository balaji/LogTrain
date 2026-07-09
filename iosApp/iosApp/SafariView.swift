import SwiftUI
import SafariServices

/// SwiftUI wrapper for `SFSafariViewController`, used to open external links
/// in an in-app browser sheet instead of bouncing out to Safari.
struct SafariView: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        SFSafariViewController(url: url)
    }

    func updateUIViewController(_ controller: SFSafariViewController, context: Context) {}
}
