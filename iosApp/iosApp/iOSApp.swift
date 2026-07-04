import SwiftUI
import SharedUI

@main
struct iOSApp: App {
    init() {
        #if DEBUG
        MoonroseConfig.shared.baseUrl = "http://localhost:3000"
        #else
        MoonroseConfig.shared.baseUrl = "https://moonrose.balaji.dev"
        #endif
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