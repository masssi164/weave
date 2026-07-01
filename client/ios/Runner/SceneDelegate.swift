import Flutter
import UIKit

class SceneDelegate: FlutterSceneDelegate {
  private let pendingDeepLinkKey = "pending_deep_link_url"

  override func scene(
    _ scene: UIScene,
    willConnectTo session: UISceneSession,
    options connectionOptions: UIScene.ConnectionOptions
  ) {
    persistPendingDeepLink(from: connectionOptions.urlContexts)
    super.scene(scene, willConnectTo: session, options: connectionOptions)
  }

  override func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
    persistPendingDeepLink(from: URLContexts)
    super.scene(scene, openURLContexts: URLContexts)
  }

  private func persistPendingDeepLink(from urlContexts: Set<UIOpenURLContext>) {
    guard let url = urlContexts.first?.url else {
      return
    }
    UserDefaults.standard.set(url.absoluteString, forKey: pendingDeepLinkKey)
  }
}
