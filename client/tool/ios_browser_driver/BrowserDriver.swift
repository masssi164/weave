import XCTest

final class BrowserDriver: XCTestCase {
  struct Fixture: Decodable {
    let email: String
    let password: String
    let issuerHost: String
    let appBundleIdentifier: String
    let phase: String
  }

  func testProductionBrowser() throws {
    continueAfterFailure = false
    print("NATIVE_IOS_BROWSER_STAGE phase=driver-started")
    // XCTest activities can contain typed input. The runner keeps the entire
    // bundle, transcript and xcresult private and removes them after this run.
    let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "private-member", withExtension: "json"))
    let fixture = try JSONDecoder().decode(Fixture.self, from: Data(contentsOf: url))
    XCTAssertEqual(fixture.issuerHost, "auth.weave.localhost")
    let app = XCUIApplication(bundleIdentifier: fixture.appBundleIdentifier)
    let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
    let browser = XCUIApplication(bundleIdentifier: "com.apple.SafariViewService")
    let deadline = Date().addingTimeInterval(360)
    var usernameSubmitted = false
    while Date() < deadline {
      if app.state == .runningBackground { app.activate() }
      for surface in [app, springboard] {
        let consent = surface.alerts.buttons["Continue"]
        if consent.exists && consent.isHittable { consent.tap() }
      }
      if fixture.phase == "restore" {
        // Logout must return through the production session, without a token
        // or callback injected by this driver.
        if app.staticTexts["Sign in"].exists || app.buttons["Sign in"].exists {
          print("NATIVE_IOS_BROWSER_RESULT status=passed phase=logout")
          return
        }
      } else {
        for surface in [app, browser] {
          let host = surface.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@ OR value CONTAINS %@", fixture.issuerHost, fixture.issuerHost))
          guard host.count > 0 else { continue }
          print("NATIVE_IOS_BROWSER_STAGE phase=issuer-observed")
          let fields = surface.textFields.matching(NSPredicate(format: "label CONTAINS[c] 'email' OR label CONTAINS[c] 'username' OR identifier == 'username'"))
          if !usernameSubmitted && fields.count == 1 && fields.element.isHittable {
            fields.element.tap()
            fields.element.typeText(fixture.email)
            let submit = surface.buttons.matching(NSPredicate(format: "label IN {'Sign In', 'Log in', 'Anmelden'}"))
            XCTAssertEqual(submit.count, 1, "Expected one identity-provider submit control")
            submit.element.tap()
            usernameSubmitted = true
            print("NATIVE_IOS_BROWSER_STAGE phase=username-submitted")
          }
          let passwords = surface.secureTextFields
          if usernameSubmitted && passwords.count == 1 && passwords.element.isHittable {
            passwords.element.tap()
            passwords.element.typeText(fixture.password)
            let submit = surface.buttons.matching(NSPredicate(format: "label IN {'Sign In', 'Log in', 'Anmelden'}"))
            XCTAssertEqual(submit.count, 1, "Expected one identity-provider submit control")
            submit.element.tap()
            print("NATIVE_IOS_BROWSER_STAGE phase=password-submitted")
            print("NATIVE_IOS_BROWSER_RESULT status=passed phase=form-submitted")
            return
          }
        }
      }
      Thread.sleep(forTimeInterval: 0.25)
    }
    XCTFail("Production browser did not complete within its bounded deadline")
  }
}
