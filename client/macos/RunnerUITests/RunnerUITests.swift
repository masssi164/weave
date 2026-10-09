import Foundation
import XCTest

/// Drives the browser opened by production AppAuth while Flutter's
/// integration_test remains responsible for product assertions.
final class RunnerUITests: XCTestCase {
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
    let app = XCUIApplication(bundleIdentifier: "com.example.weave")
    XCTAssertTrue(app.windows.firstMatch.waitForExistence(timeout: 180),
                  "Flutter's native test app did not start")
    recordStage("app-window")

    // ASWebAuthenticationSession may first ask to share the browser session.
    // Only acknowledge the consent attached to the requesting Weave app.
    let continueButton = app.buttons.matching(
      NSPredicate(format: "label ==[c] 'Continue' OR label ==[c] 'Fortfahren'")
    ).firstMatch
    if continueButton.waitForExistence(timeout: 5) {
      continueButton.click()
    }
    recordStage("browser-requested")

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
    safari.activate()
    recordStage("browser-focused")

    // AppAuth's system browser can expose the expected IdP URL while
    // descendants(matching:) blocks in WebKit's accessibility bridge. The
    // disposable IdP focuses its account field on load. Drive that native
    // keyboard focus and let Flutter's callback assertion prove the result.
    safari.typeText(fixture.email)
    recordStage("account-entered")
    safari.typeKey(XCUIKeyboardKey.tab, modifierFlags: [])
    recordStage("password-focused")
    safari.typeText(fixture.password)
    recordStage("password-entered")
    safari.typeKey(XCUIKeyboardKey.return, modifierFlags: [])
    recordStage("form-submitted")
    XCTAssertTrue(app.windows.firstMatch.waitForExistence(timeout: 60),
                  "Native application did not return after authentication")
    recordStage("callback-returned")
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
