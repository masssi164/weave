import XCTest

final class BrowserDriver: XCTestCase {
  // Exact supported product labels; the owned simulator can inherit either locale.
  static let signInLabels = ["Sign In", "Anmelden"]
  static let consentLabels = ["Continue", "Fortfahren"]
  static func waitForRunningApplication(
    state: () -> XCUIApplication.State,
    deadline: Date,
    now: () -> Date = { Date() },
    pause: () -> Void = { Thread.sleep(forTimeInterval: 0.25) }
  ) -> Bool {
    while now() < deadline {
      let current = state()
      if current == .runningForeground || current == .runningBackground { return true }
      pause()
    }
    return false
  }

  func testStartupWaitAllowsDelayedFlutterLaunch() {
    var states: [XCUIApplication.State] = [.notRunning, .unknown, .runningForeground]
    var clock = Date(timeIntervalSince1970: 0)
    let ready = Self.waitForRunningApplication(
      state: { states.removeFirst() }, deadline: clock.addingTimeInterval(3),
      now: { clock }, pause: { clock.addTimeInterval(1) }
    )
    XCTAssertTrue(ready)
    XCTAssertTrue(states.isEmpty)
  }

  func testUnavailableTargetHasBoundedStartupWait() {
    var clock = Date(timeIntervalSince1970: 0)
    var reads = 0
    let ready = Self.waitForRunningApplication(
      state: { reads += 1; return .notRunning }, deadline: clock.addingTimeInterval(3),
      now: { clock }, pause: { clock.addTimeInterval(1) }
    )
    XCTAssertFalse(ready)
    XCTAssertEqual(reads, 3)
  }

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
    // The runner has observed Flutter shell readiness. Standalone XCTest must
    // attach once to that existing process before any AppAuth UI interaction.
    // Never launch a replacement app or activate it again during authentication.
    print("NATIVE_IOS_BROWSER_STAGE phase=app-wait-started")
    app.activate()
    guard Self.waitForRunningApplication(state: { app.state }, deadline: deadline) else {
      XCTFail("Target application did not start within its bounded deadline")
      return
    }
    print("NATIVE_IOS_BROWSER_STAGE phase=app-observed")
    if fixture.phase == "initial" {
      let signInControls = app.buttons.matching(NSPredicate(format: "label IN %@", Self.signInLabels))
      let signIn = signInControls.element
      XCTAssertTrue(signIn.waitForExistence(timeout: 30), "Expected native Sign in control")
      XCTAssertEqual(signInControls.count, 1)
      XCTAssertTrue(signIn.isHittable, "Native Sign in control must be accessible")
      signIn.tap()
      print("NATIVE_IOS_BROWSER_STAGE phase=sign-in-tapped")
    }
    var usernameSubmitted = false
    while Date() < deadline {
      // Do not steal foreground focus from the production authentication
      // browser. Also tolerate Flutter replacing the app during test startup.
      let state = app.state
      guard state == .runningForeground || state == .runningBackground else {
        Thread.sleep(forTimeInterval: 0.25)
        continue
      }
      for surface in [app, springboard] {
        let consent = surface.alerts.buttons.matching(NSPredicate(format: "label IN %@", Self.consentLabels))
        XCTAssertLessThanOrEqual(consent.count, 1, "Expected at most one native authentication consent control")
        if consent.count == 1 && consent.element.isHittable { consent.element.tap() }
      }
      if fixture.phase == "restore" {
        // Logout must return through the production session, without a token
        // or callback injected by this driver.
        if app.buttons.matching(NSPredicate(format: "label IN %@", Self.signInLabels)).count == 1 {
          print("NATIVE_IOS_BROWSER_RESULT status=passed phase=logout")
          return
        }
      } else {
        for surface in [app, browser] {
          let surfaceState = surface.state
          guard surfaceState == .runningForeground || surfaceState == .runningBackground else { continue }
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
