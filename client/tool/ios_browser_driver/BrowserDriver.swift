import XCTest

final class BrowserDriver: XCTestCase {
  // Exact supported product labels; the owned simulator can inherit either locale.
  static let signInLabels = ["Sign In", "Anmelden"]
  static let consentLabels = ["Continue", "Fortfahren"]
  // Pinned Keycloak 26.7.1 doLogIn labels, independent of product/OS labels.
  static let providerSubmitLabels = ["Sign In", "Anmelden"]
  // Exact failure messages from pinned Keycloak 26.7.1 en/de resources.
  static let providerErrorCategories = [
    "invalid-credentials": ["Invalid username or password.", "Ungültiger Benutzername oder Passwort."],
    "invalid-username": ["Invalid username.", "Ungültiger Benutzername."],
    "invalid-password": ["Invalid password.", "Ungültiges Passwort."],
    "account-disabled": ["Account is disabled, contact your administrator.",
                         "Ihr Benutzerkonto ist gesperrt, bitte kontaktieren Sie den Admin."]
  ]

  static var providerErrorLabels: [String] { providerErrorCategories.values.flatMap { $0 } }

  // Observed iOS 26.5 password-save alert controls; never save test credentials.
  static let passwordSaveTitles = ["Passwort sichern?", "Save Password?"]
  static let passwordDeclineLabels = ["Später", "Not Now"]

  static func uniquePasswordDeclineSurface(
    appPromptCount: Int, systemPromptCount: Int, appDeclineCount: Int, systemDeclineCount: Int
  ) -> Int? {
    guard let prompt = uniqueConsentSurface(appCount: appPromptCount, springboardCount: systemPromptCount),
          let decline = uniqueConsentSurface(appCount: appDeclineCount, springboardCount: systemDeclineCount),
          prompt == decline else { return nil }
    return prompt
  }

  func testPasswordDeclineRejectsMissingAndAmbiguousAlerts() {
    XCTAssertEqual(Self.uniquePasswordDeclineSurface(appPromptCount: 1, systemPromptCount: 0, appDeclineCount: 1, systemDeclineCount: 0), 0)
    XCTAssertEqual(Self.uniquePasswordDeclineSurface(appPromptCount: 0, systemPromptCount: 1, appDeclineCount: 0, systemDeclineCount: 1), 1)
    for counts in [(0, 0, 0, 0), (1, 1, 1, 1), (2, 0, 1, 0), (1, 0, 2, 0), (1, 0, 0, 0), (1, 0, 0, 1)] {
      XCTAssertNil(Self.uniquePasswordDeclineSurface(appPromptCount: counts.0, systemPromptCount: counts.1, appDeclineCount: counts.2, systemDeclineCount: counts.3))
    }
  }

  static func nativeWorkspaceObserved(path: String) -> Bool {
    guard let data = try? Data(contentsOf: URL(fileURLWithPath: path)) else { return false }
    return data == Data("workspace-ready\n".utf8)
  }

  func testWorkspaceSignalRejectsMissingAndMalformedContent() throws {
    let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: false)
    defer { try? FileManager.default.removeItem(at: directory) }
    let signal = directory.appendingPathComponent("workspace.ready")
    XCTAssertFalse(Self.nativeWorkspaceObserved(path: signal.path))
    for content in ["", "submitted", "workspace-ready", "workspace-ready\nextra"] {
      try Data(content.utf8).write(to: signal)
      XCTAssertFalse(Self.nativeWorkspaceObserved(path: signal.path))
    }
    try Data("workspace-ready\n".utf8).write(to: signal)
    XCTAssertTrue(Self.nativeWorkspaceObserved(path: signal.path))
  }
  static func providerLeafIndices(childWebViews: [Int], formFields: [Int]) -> [Int] {
    guard childWebViews.count == formFields.count else { return [] }
    return childWebViews.indices.filter { childWebViews[$0] == 0 && formFields[$0] > 0 }
  }

  func testProviderScopeRejectsMissingAndAmbiguousForms() {
    // Three nested wrappers describe one provider form, not three providers.
    XCTAssertEqual(Self.providerLeafIndices(childWebViews: [2, 1, 0], formFields: [1, 1, 1]), [2])
    XCTAssertTrue(Self.providerLeafIndices(childWebViews: [], formFields: []).isEmpty)
    XCTAssertTrue(Self.providerLeafIndices(childWebViews: [0], formFields: [0]).isEmpty)
    XCTAssertEqual(Self.providerLeafIndices(childWebViews: [0, 0], formFields: [1, 1]).count, 2)
    XCTAssertTrue(Self.providerLeafIndices(childWebViews: [0], formFields: []).isEmpty)
  }
  static func uniqueConsentSurface(appCount: Int, springboardCount: Int) -> Int? {
    if appCount == 1 && springboardCount == 0 { return 0 }
    if appCount == 0 && springboardCount == 1 { return 1 }
    return nil
  }

  func testConsentSelectionRejectsAmbiguousSurfaces() {
    XCTAssertNil(Self.uniqueConsentSurface(appCount: 0, springboardCount: 0))
    XCTAssertEqual(Self.uniqueConsentSurface(appCount: 1, springboardCount: 0), 0)
    XCTAssertEqual(Self.uniqueConsentSurface(appCount: 0, springboardCount: 1), 1)
    for counts in [(1, 1), (2, 0), (0, 2), (2, 2)] {
      XCTAssertNil(Self.uniqueConsentSurface(appCount: counts.0, springboardCount: counts.1))
    }
  }

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
    let nativeAuthCompletionPath: String
  }

  // nil means an observed prompt was ambiguous/inaccessible; false means absent.
  func declinePasswordSavePrompt(app: XCUIApplication, system: XCUIApplication) -> Bool? {
    let appRunning = app.state == .runningForeground || app.state == .runningBackground
    let systemRunning = system.state == .runningForeground || system.state == .runningBackground
    let appPrompts = app.alerts.matching(NSPredicate(format: "label IN %@", Self.passwordSaveTitles))
    let systemPrompts = system.alerts.matching(NSPredicate(format: "label IN %@", Self.passwordSaveTitles))
    let appCount = appRunning ? appPrompts.count : 0
    let systemCount = systemRunning ? systemPrompts.count : 0
    guard appCount + systemCount > 0 else { return false }
    let appDeclines = appPrompts.buttons.matching(NSPredicate(format: "label IN %@", Self.passwordDeclineLabels))
    let systemDeclines = systemPrompts.buttons.matching(NSPredicate(format: "label IN %@", Self.passwordDeclineLabels))
    guard let surface = Self.uniquePasswordDeclineSurface(
      appPromptCount: appCount, systemPromptCount: systemCount,
      appDeclineCount: appRunning ? appDeclines.count : 0, systemDeclineCount: systemRunning ? systemDeclines.count : 0
    ) else {
      XCTFail("Password-save alert must have one exact decline control on one surface")
      return nil
    }
    let decline = surface == 0 ? appDeclines.element : systemDeclines.element
    guard decline.isHittable else {
      XCTFail("Password-save decline control must be accessible")
      return nil
    }
    decline.tap()
    let prompt = surface == 0 ? appPrompts.element : systemPrompts.element
    guard prompt.waitForNonExistence(timeout: 10) else {
      XCTFail("Password-save alert did not dismiss after declining")
      return nil
    }
    print("NATIVE_IOS_BROWSER_STAGE phase=password-save-declined")
    return true
  }

  func waitForNativeWorkspace(
    app: XCUIApplication, browser: XCUIApplication, fixture: Fixture, deadline: Date
  ) -> Bool {
    var nextDiagnostic = Date.distantPast
    let system = XCUIApplication(bundleIdentifier: "com.apple.springboard")
    while Date() < deadline {
      guard declinePasswordSavePrompt(app: app, system: system) != nil else { return false }
      // Only the runner's observation of Flutter's real workspace assertion
      // releases this wait; a submit tap or disappearing browser never does.
      if Self.nativeWorkspaceObserved(path: fixture.nativeAuthCompletionPath) {
        print("NATIVE_IOS_BROWSER_STAGE phase=auth-completion-observed")
        return true
      }
      if Date() >= nextDiagnostic {
        var issuerSurfaces = 0
        var providerErrors = 0
        for surface in [app, browser] {
          let state = surface.state
          guard state == .runningForeground || state == .runningBackground else { continue }
          let host = surface.descendants(matching: .any).matching(NSPredicate(
            format: "label CONTAINS %@ OR value CONTAINS %@", fixture.issuerHost, fixture.issuerHost
          ))
          guard host.count > 0 else { continue }
          issuerSurfaces += 1
          let webViews = surface.webViews
          let leaves = Self.providerLeafIndices(
            childWebViews: (0..<webViews.count).map { webViews.element(boundBy: $0).webViews.count },
            formFields: (0..<webViews.count).map {
              let web = webViews.element(boundBy: $0)
              return web.textFields.count + web.secureTextFields.count
            }
          )
          if leaves.count == 1 {
            let provider = webViews.element(boundBy: leaves[0])
            let submits = provider.buttons.matching(NSPredicate(format: "label IN %@", Self.providerSubmitLabels))
            print("NATIVE_IOS_PROVIDER_CONTROLS phase=post-submit leafWebViews=1 usernameFields=\(provider.textFields.count) passwordFields=\(provider.secureTextFields.count) submitButtons=\(submits.count)")
          } else {
            print("NATIVE_IOS_PROVIDER_CONTROLS phase=post-submit leafWebViews=\(leaves.count) usernameFields=0 passwordFields=0 submitButtons=0")
          }
          // Count exact public provider errors without exporting UI text.
          let rendered = surface.webViews.descendants(matching: .any)
          providerErrors += rendered.matching(NSPredicate(
            format: "label IN %@ OR value IN %@", Self.providerErrorLabels, Self.providerErrorLabels
          )).count
          // Browser error text can be rendered as a non-static accessibility role.
          // Exact public labels only; nested wrappers can duplicate these diagnostic counts.
          for category in Self.providerErrorCategories.keys.sorted() {
            let labels = Self.providerErrorCategories[category]!
            let count = rendered.matching(NSPredicate(format: "label IN %@ OR value IN %@", labels, labels)).count
            print("NATIVE_IOS_PROVIDER_CONTROLS phase=provider-error category=\(category) matchingElements=\(min(count, 999))")
          }
        }
        let appRunning = app.state == .runningForeground || app.state == .runningBackground
        let systemRunning = system.state == .runningForeground || system.state == .runningBackground
        let appAlerts = appRunning ? app.alerts.count : 0
        let systemAlerts = systemRunning ? system.alerts.count : 0
        print("NATIVE_IOS_POST_SUBMIT_STATE phase=pending issuerSurfaces=\(issuerSurfaces) providerErrors=\(providerErrors) appAlertCount=\(appAlerts) systemAlertCount=\(systemAlerts)")
        nextDiagnostic = Date().addingTimeInterval(15)
      }
      Thread.sleep(forTimeInterval: 0.25)
    }
    return false
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
      let appConsent = app.alerts.buttons.matching(NSPredicate(format: "label IN %@", Self.consentLabels))
      let systemConsent = springboard.alerts.buttons.matching(NSPredicate(format: "label IN %@", Self.consentLabels))
      let appCount = appConsent.count
      let systemCount = systemConsent.count
      if appCount != 0 || systemCount != 0 {
        guard let surface = Self.uniqueConsentSurface(appCount: appCount, springboardCount: systemCount) else {
          XCTFail("Expected one native authentication consent control across both surfaces")
          return
        }
        let consent = surface == 0 ? appConsent.element : systemConsent.element
        if consent.isHittable { consent.tap() }
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
          // AppAuth exposes nested web-view wrappers plus the underlying Weave
          // controls on the app surface. Scope both fields and submit to one leaf
          // form in this already issuer-verified surface; never choose a first match.
          let webViews = surface.webViews
          let leaves = Self.providerLeafIndices(
            childWebViews: (0..<webViews.count).map { webViews.element(boundBy: $0).webViews.count },
            formFields: (0..<webViews.count).map {
              let web = webViews.element(boundBy: $0)
              return web.textFields.count + web.secureTextFields.count
            }
          )
          if leaves.isEmpty {
            print("NATIVE_IOS_PROVIDER_CONTROLS phase=content leafWebViews=0 usernameFields=0 passwordFields=0 submitButtons=0")
            continue
          }
          guard leaves.count == 1 else {
            print("NATIVE_IOS_PROVIDER_CONTROLS phase=content leafWebViews=\(leaves.count) usernameFields=0 passwordFields=0 submitButtons=0")
            XCTFail("Expected one identity-provider leaf form in the verified issuer surface")
            return
          }
          let provider = webViews.element(boundBy: leaves[0])
          let fields = provider.textFields.matching(NSPredicate(format: "label CONTAINS[c] 'email' OR label CONTAINS[c] 'username' OR identifier == 'username'"))
          if !usernameSubmitted {
            let hittable = fields.count == 1 && fields.element.isHittable
            print("NATIVE_IOS_PROVIDER_CONTROLS phase=username-before usernameFields=\(min(fields.count, 999)) passwordFields=\(min(provider.secureTextFields.count, 999)) selectedFieldHittable=\(hittable)")
          }
          if !usernameSubmitted && fields.count == 1 && fields.element.isHittable {
            fields.element.tap()
            fields.element.typeText(fixture.email)
            let readbackEqual = fields.element.value as? String == fixture.email
            print("NATIVE_IOS_PROVIDER_CONTROLS phase=username-after readbackEqual=\(readbackEqual)")
            let submit = provider.buttons.matching(NSPredicate(format: "label IN %@", Self.providerSubmitLabels))
            print("NATIVE_IOS_PROVIDER_CONTROLS phase=username leafWebViews=\(leaves.count) usernameFields=\(fields.count) passwordFields=\(provider.secureTextFields.count) submitButtons=\(submit.count)")
            XCTAssertEqual(submit.count, 1, "Expected one identity-provider submit control")
            XCTAssertTrue(submit.element.isHittable, "Identity-provider submit control must be accessible")
            submit.element.tap()
            usernameSubmitted = true
            print("NATIVE_IOS_BROWSER_STAGE phase=username-submitted")
            // Keycloak can replace the form after its identity-first step.
            // Re-verify the issuer and derive a fresh leaf before password input.
            continue
          }
          let passwords = provider.secureTextFields
          if usernameSubmitted {
            let hittable = passwords.count == 1 && passwords.element.isHittable
            print("NATIVE_IOS_PROVIDER_CONTROLS phase=password-before usernameFields=\(min(fields.count, 999)) passwordFields=\(min(passwords.count, 999)) selectedFieldHittable=\(hittable)")
          }
          if usernameSubmitted && passwords.count == 1 && passwords.element.isHittable {
            passwords.element.tap()
            passwords.element.typeText(fixture.password)
            // Secure accessibility values can be masked or absent. Observe only;
            // no representation-dependent assertion or raw length/value is exported.
            let lengthEqual = (passwords.element.value as? String)?.count == fixture.password.count
            print("NATIVE_IOS_PROVIDER_CONTROLS phase=password-after secureValueLengthEqual=\(lengthEqual)")
            let submit = provider.buttons.matching(NSPredicate(format: "label IN %@", Self.providerSubmitLabels))
            print("NATIVE_IOS_PROVIDER_CONTROLS phase=password leafWebViews=\(leaves.count) usernameFields=\(fields.count) passwordFields=\(passwords.count) submitButtons=\(submit.count)")
            XCTAssertEqual(submit.count, 1, "Expected one identity-provider submit control")
            XCTAssertTrue(submit.element.isHittable, "Identity-provider submit control must be accessible")
            submit.element.tap()
            print("NATIVE_IOS_BROWSER_STAGE phase=password-submitted")
            guard waitForNativeWorkspace(app: app, browser: browser, fixture: fixture, deadline: deadline) else {
              XCTFail("Native workspace did not follow identity-provider submission")
              return
            }
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
