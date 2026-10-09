import AppKit
import ApplicationServices
import CoreGraphics
import Foundation
import XCTest

/// Drives the browser opened by production AppAuth while Flutter's
/// integration_test remains responsible for product assertions.
final class RunnerUITests: XCTestCase {
  private enum NativeAuthError: Error {
    case unexpectedForeground
    case keyboardEventUnavailable
    case idpUnavailable
  }

  private struct LoginFixture: Decodable {
    let email: String
    let password: String
    let issuerHost: String
    let issuerAuthority: String
    let appExecutable: String
  }

  func testAutomationStartupProbe() {
    recordStage("startup")
    XCTAssertTrue(true)
  }

  func testPrivateFixtureProbe() throws {
    let fixture = try readPrivateFixture()
    XCTAssertEqual(fixture.issuerHost, "auth.weave.localhost")
    recordStage("fixture-read")
  }

  func testNativeAppAuthCallback() throws {
    let fixture = try readPrivateFixture()
    recordStage("fixture-read")
    let permittedOwners = Set([
      "com.example.weave", "com.apple.Safari", "com.apple.SafariViewService",
      "com.apple.AuthenticationServicesUIAgent",
    ])
    let deadline = Date().addingTimeInterval(180)
    var authOwner: String?
    var observedHost = false
    var observedConsent = false
    var observedFields = false
    var lastOwner = "none"
    var activationAttempted = false
    var consentHandled = false
    while Date() < deadline {
      let owner = NSWorkspace.shared.frontmostApplication?.bundleIdentifier ?? "none"
      lastOwner = owner
      if !consentHandled {
        consentHandled = try acceptExpectedSystemBrowserConsent(fixture)
        if consentHandled {
          continue
        }
      }
      if !activationAttempted && !permittedOwners.contains(owner),
         let nativeApp = NSWorkspace.shared.runningApplications.first(where: {
           $0.executableURL?.resolvingSymlinksInPath().path == fixture.appExecutable
         }) {
        activationAttempted = true
        let activated = nativeApp.activate(options: [])
        print("NATIVE_XCTEST_APP_ACTIVATION status=\(activated ? "passed" : "failed")")
      }
      if permittedOwners.contains(owner) {
        let description = XCUIApplication(bundleIdentifier: owner).debugDescription
        observedHost = observedHost || description.contains(fixture.issuerHost)
        observedConsent = observedConsent ||
          description.contains("Continue") || description.contains("Fortfahren")
        observedFields = observedFields || description.contains("SecureTextField")
        if description.contains(fixture.issuerAuthority) {
          authOwner = owner
          break
        }
      }
      Thread.sleep(forTimeInterval: 1)
    }
    print("NATIVE_XCTEST_AUTH_DISCOVERY owner=\(lastOwner) host=\(observedHost) consent=\(observedConsent) fields=\(observedFields)")
    guard let authOwner else {
      XCTFail("The expected disposable IdP did not appear in the foreground app")
      throw NativeAuthError.idpUnavailable
    }
    recordStage("issuer-visible")
    // WebKit's accessibility bridge can block even a bounded element query or
    // XCUIApplication.typeText on this AppAuth window. Never post a global
    // key until the expected disposable authority is visible in the foreground
    // application. ASWebAuthenticationSession can be attached to Weave's window.
    try requireAuthSurfaceForeground(authOwner)
    try typeOnFocusedAuthPage(fixture.email)
    recordStage("account-entered")
    try requireAuthSurfaceForeground(authOwner)
    try postKey(48) // Tab to the password field.
    recordStage("password-focused")
    try typeOnFocusedAuthPage(fixture.password)
    recordStage("password-entered")
    try requireAuthSurfaceForeground(authOwner)
    try postKey(36) // Return submits the IdP form.
    recordStage("form-submitted")
    // Flutter's product test observes the callback and authorized workspace.
  }

  private func acceptExpectedSystemBrowserConsent(_ fixture: LoginFixture) throws -> Bool {
    // macOS asks once whether this exact app may use the requested IdP. The
    // prompt is hosted by UserNotificationCenter, outside XCTest's app tree.
    // Inspect the native Accessibility window and press only a button in the
    // window that names both this app and the disposable IdP authority.
    guard AXIsProcessTrusted() else {
      return false
    }
    for application in NSWorkspace.shared.runningApplications
      where application.bundleIdentifier == "com.apple.UserNotificationCenter" {
      let root = AXUIElementCreateApplication(application.processIdentifier)
      guard let windows = axValue(root, kAXWindowsAttribute) as? [AXUIElement] else {
        continue
      }
      for window in windows {
        let elements = axDescendants(window, limit: 100)
        let labels = elements.compactMap { axLabel($0) }
        guard labels.contains(where: { $0.contains(fixture.issuerHost) &&
          $0.localizedCaseInsensitiveContains("weave") }) else {
          continue
        }
        let buttons = elements.filter { element in
          (axValue(element, kAXRoleAttribute) as? String) == kAXButtonRole as String &&
            ["Fortfahren", "Continue"].contains(axLabel(element) ?? "")
        }
        guard buttons.count == 1 else {
          XCTFail("Expected one Continue action in the verified macOS IdP consent")
          throw NativeAuthError.idpUnavailable
        }
        print("NATIVE_XCTEST_OS_CONSENT status=verified owner=com.apple.UserNotificationCenter")
        guard AXUIElementPerformAction(buttons[0], kAXPressAction as CFString) == .success else {
          XCTFail("Could not accept the verified macOS IdP consent")
          throw NativeAuthError.idpUnavailable
        }
        print("NATIVE_XCTEST_OS_CONSENT status=accepted owner=com.apple.UserNotificationCenter")
        return true
      }
    }
    return false
  }

  private func axValue(_ element: AXUIElement, _ attribute: String) -> CFTypeRef? {
    var result: CFTypeRef?
    return AXUIElementCopyAttributeValue(element, attribute as CFString, &result) == .success
      ? result : nil
  }

  private func axLabel(_ element: AXUIElement) -> String? {
    for attribute in [kAXTitleAttribute, kAXValueAttribute, kAXDescriptionAttribute] {
      if let text = axValue(element, attribute) as? String, !text.isEmpty {
        return text
      }
    }
    return nil
  }

  private func axDescendants(_ root: AXUIElement, limit: Int) -> [AXUIElement] {
    var elements = [root]
    var index = 0
    while index < elements.count && elements.count < limit {
      if let children = axValue(elements[index], kAXChildrenAttribute) as? [AXUIElement] {
        elements.append(contentsOf: children.prefix(limit - elements.count))
      }
      index += 1
    }
    return elements
  }

  private func requireAuthSurfaceForeground(_ expectedOwner: String) throws {
    let identifier = NSWorkspace.shared.frontmostApplication?.bundleIdentifier ?? "none"
    print("NATIVE_XCTEST_AUTH_FOREGROUND bundle=\(identifier)")
    guard identifier == expectedOwner else {
      XCTFail("Unexpected foreground during IdP entry")
      throw NativeAuthError.unexpectedForeground
    }
  }

  private func typeOnFocusedAuthPage(_ text: String) throws {
    for unit in text.utf16 {
      var character = unit
      guard let press = CGEvent(keyboardEventSource: nil, virtualKey: 0, keyDown: true),
            let release = CGEvent(keyboardEventSource: nil, virtualKey: 0, keyDown: false) else {
        throw NativeAuthError.keyboardEventUnavailable
      }
      press.keyboardSetUnicodeString(stringLength: 1, unicodeString: &character)
      release.keyboardSetUnicodeString(stringLength: 1, unicodeString: &character)
      press.post(tap: .cghidEventTap)
      release.post(tap: .cghidEventTap)
    }
  }

  private func postKey(_ code: CGKeyCode) throws {
    guard let press = CGEvent(keyboardEventSource: nil, virtualKey: code, keyDown: true),
          let release = CGEvent(keyboardEventSource: nil, virtualKey: code, keyDown: false) else {
      throw NativeAuthError.keyboardEventUnavailable
    }
    press.post(tap: .cghidEventTap)
    release.post(tap: .cghidEventTap)
  }

  private func recordStage(_ stage: String) {
    print("NATIVE_XCTEST_STAGE phase=\(stage)")
  }

  private func readPrivateFixture() throws -> LoginFixture {
    let bundle = Bundle(for: type(of: self))
    let path = try XCTUnwrap(
      bundle.object(forInfoDictionaryKey: "WEAVE_NATIVE_FIXTURE_PATH") as? String
    )
    let input = try FileHandle(forReadingFrom: URL(fileURLWithPath: path))
    defer { try? input.close() }
    let data = try XCTUnwrap(input.readToEnd())
    return try JSONDecoder().decode(LoginFixture.self, from: data)
  }
}
