import SwiftUI
import UIKit

/// Reports the OS-level light/dark setting, independent of any
/// `preferredColorScheme` override already applied above it in the view
/// hierarchy. Needed because `preferredColorScheme(nil)` doesn't reliably
/// clear a previously-set override on a *presented* sheet (a known
/// SwiftUI/UIKit bug) — call sites resolve "system" to this concrete value
/// themselves instead of passing `nil`.
private struct SystemColorSchemeReader: UIViewControllerRepresentable {
    let onChange: (ColorScheme) -> Void

    func makeUIViewController(context: Context) -> Controller {
        let controller = Controller()
        controller.onChange = onChange
        return controller
    }

    func updateUIViewController(_ uiViewController: Controller, context: Context) {
        uiViewController.onChange = onChange
    }

    final class Controller: UIViewController {
        var onChange: ((ColorScheme) -> Void)?

        override func viewDidLoad() {
            super.viewDidLoad()
            report()
        }

        override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
            super.traitCollectionDidChange(previousTraitCollection)
            report()
        }

        // UIScreen's trait collection tracks the physical device setting and
        // isn't affected by any app-level overrideUserInterfaceStyle, unlike
        // `self.traitCollection`.
        private func report() {
            onChange?(UIScreen.main.traitCollection.userInterfaceStyle == .dark ? .dark : .light)
        }
    }
}

extension View {
    /// Keeps `binding` in sync with the OS light/dark setting.
    func observeSystemColorScheme(_ binding: Binding<ColorScheme>) -> some View {
        background(SystemColorSchemeReader(onChange: { binding.wrappedValue = $0 }))
    }
}
