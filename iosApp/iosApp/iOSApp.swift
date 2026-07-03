import SwiftUI
import SharedUI

@main
struct iOSApp: App {
    init() {
        MoonroseConfig.shared.baseUrl = "http://localhost:3000"
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    AppAuth.shared.loginController.completeLogin(callbackUrl: url.absoluteString)
                }
        }
    }
}