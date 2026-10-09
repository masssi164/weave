import AppKit
import CoreGraphics
import Foundation
import XCTest

/// Drives the browser opened by production AppAuth while Flutter's
/// integration_test remains responsible for product assertions.
final class RunnerUITests: XCTestCase {
  private enum NativeAuthError: Error {
    case unexpectedForeground
    case keyboardEventUnavailable
  }

  private struct LoginFixture: Decodable {
    let email: String
    let password: String
    let issuerHost: String
    let issuerAuthority: String
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
    let safari = XCUIApplication(bundleIdentifier: "com.apple.Safari")
    let deadline = Date().addingTimeInterval(180)
    var issuerVisible = false
    while Date() < deadline {
      if safari.exists && safari.debugDescription.contains(fixture.issuerAuthority) {
        issuerVisible = true
        break
      }
      Thread.sleep(forTimeInterval: 1)
    }
    XCTAssertTrue(issuerVisible, "The expected disposable IdP did not appear")
    recordStage("issuer-visible")
    // WebKit's accessibility bridge can block even a bounded element query or
    // XCUIApplication.typeText on this AppAuth window. Never post a global
    // key until the expected disposable authority is visible and the system
    // browser, rather than another desktop app, owns the foreground.
    try requireAuthBrowserForeground()
    try typeOnFocusedAuthPage(fixture.email)
    recordStage("account-entered")
    try requireAuthBrowserForeground()
    try postKey(48) // Tab to the password field.
    recordStage("password-focused")
    try typeOnFocusedAuthPage(fixture.password)
    recordStage("password-entered")
    try requireAuthBrowserForeground()
    try postKey(36) // Return submits the IdP form.
    recordStage("form-submitted")
    // Flutter's product test observes the callback and authorized workspace.
  }

  private func requireAuthBrowserForeground() throws {
    let identifier = NSWorkspace.shared.frontmostApplication?.bundleIdentifier ?? "none"
    let permitted = Set([
      "com.apple.Safari", "com.apple.SafariViewService",
      "com.apple.AuthenticationServicesUIAgent",
    ])
    print("NATIVE_XCTEST_AUTH_FOREGROUND bundle=\(identifier)")
    guard permitted.contains(identifier) else {
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
