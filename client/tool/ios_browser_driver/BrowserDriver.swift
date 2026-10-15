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
    // Private simulator harness initiator; absent preserves native tap consumers.
    let flutterInitiatesSignIn: Bool?
  }

  func testPrivateSignInInitiatorDefaultsToNativeAndRejectsInvalidType() throws {
    let base = "\"email\":\"fixture@example.invalid\",\"password\":\"nonsecret\",\"issuerHost\":\"auth.weave.localhost\",\"appBundleIdentifier\":\"com.massimotter.weave\",\"phase\":\"initial\",\"nativeAuthCompletionPath\":\"/private/owned/workspace.ready\""
    func decode(_ additional: String) throws -> Fixture {
      try JSONDecoder().decode(Fixture.self, from: Data(("{" + base + additional + "}").utf8))
    }
    XCTAssertNotEqual(try decode("").flutterInitiatesSignIn, true)
    XCTAssertNotEqual(try decode(",\"flutterInitiatesSignIn\":false").flutterInitiatesSignIn, true)
    XCTAssertEqual(try decode(",\"flutterInitiatesSignIn\":true").flutterInitiatesSignIn, true)
    XCTAssertThrowsError(try decode(",\"flutterInitiatesSignIn\":\"true\""))
  }

  enum PasswordPromptReadiness { case absent, ambiguous, waiting, ready }

  static func waitForPasswordPrompt(
    state: () -> PasswordPromptReadiness, deadline: Date, promptSeen: inout Bool,
    now: () -> Date = Date.init, pause: () -> Void = { Thread.sleep(forTimeInterval: 0.25) }
  ) -> PasswordPromptReadiness {
    var latest = PasswordPromptReadiness.waiting
    while now() < deadline {
      latest = state()
      if latest != .absent { promptSeen = true }
      if latest == .absent { return latest }
      // Even a ready optional sheet may disappear during genuine auth completion.
      // Keep observing within the existing window; never select a transient control early.
      pause()
    }
    latest = state()
    if latest != .absent { promptSeen = true }
    return latest
  }

  func testPasswordPromptWaitUsesFreshStateAndBoundedDeadline() {
    var clock = Date(timeIntervalSince1970: 0)
    var states: [PasswordPromptReadiness] = [.waiting, .waiting, .ready]
    var promptSeen = false
    XCTAssertEqual(Self.waitForPasswordPrompt(
      state: { states.isEmpty ? .ready : states.removeFirst() }, deadline: clock.addingTimeInterval(5), promptSeen: &promptSeen,
      now: { clock }, pause: { clock.addTimeInterval(1) }
    ), .ready)
    XCTAssertTrue(states.isEmpty)
    XCTAssertEqual(Self.waitForPasswordPrompt(state: { .absent }, deadline: clock.addingTimeInterval(5), promptSeen: &promptSeen, now: { clock }), .absent)
    var reads = 0
    XCTAssertEqual(Self.waitForPasswordPrompt(
      state: { reads += 1; return .waiting }, deadline: clock.addingTimeInterval(5), promptSeen: &promptSeen,
      now: { clock }, pause: { clock.addTimeInterval(1) }
    ), .waiting)
    XCTAssertEqual(reads, 6)
  }

  func testAmbiguousPasswordPromptSettlesWithoutSelectingEarly() {
    var clock = Date(timeIntervalSince1970: 0)
    var states: [PasswordPromptReadiness] = [.waiting, .ambiguous, .ready]
    var promptSeen = false
    XCTAssertEqual(Self.waitForPasswordPrompt(
      state: { states.isEmpty ? .ready : states.removeFirst() }, deadline: clock.addingTimeInterval(5), promptSeen: &promptSeen,
      now: { clock }, pause: { clock.addTimeInterval(1) }
    ), .ready)
    XCTAssertTrue(states.isEmpty)
    XCTAssertTrue(promptSeen)
    XCTAssertEqual(clock.timeIntervalSince1970, 5)
    var reads = 0
    XCTAssertEqual(Self.waitForPasswordPrompt(
      state: { reads += 1; return .ambiguous }, deadline: clock.addingTimeInterval(5), promptSeen: &promptSeen,
      now: { clock }, pause: { clock.addTimeInterval(1) }
    ), .ambiguous)
    XCTAssertEqual(reads, 6)
    XCTAssertTrue(promptSeen)
  }

  func testReadyPromptIsNotSelectedBeforeCompletionSettles() {
    var clock = Date(timeIntervalSince1970: 0)
    var reads = 0
    var promptSeen = false
    let result = Self.waitForPasswordPrompt(
      state: { reads += 1; return clock.timeIntervalSince1970 < 2 ? .ready : .absent },
      deadline: clock.addingTimeInterval(5), promptSeen: &promptSeen,
      now: { clock }, pause: { clock.addTimeInterval(1) }
    )
    XCTAssertEqual(result, .absent)
    XCTAssertEqual(reads, 3)
    XCTAssertEqual(clock.timeIntervalSince1970, 2)
    XCTAssertTrue(promptSeen)

    clock = Date(timeIntervalSince1970: 0)
    reads = 0
    let stable = Self.waitForPasswordPrompt(
      state: { reads += 1; return .ready },
      deadline: clock.addingTimeInterval(5), promptSeen: &promptSeen,
      now: { clock }, pause: { clock.addTimeInterval(1) }
    )
    XCTAssertEqual(stable, .ready)
    XCTAssertEqual(reads, 6)
    XCTAssertEqual(clock.timeIntervalSince1970, 5)
  }

  func testReadyPromptYieldsToGenuineCompletionDuringSettling() {
    var clock = Date(timeIntervalSince1970: 0)
    var promptSeen = false
    var completionObserved = false
    let result = Self.waitForPasswordPrompt(
      state: { completionObserved ? .absent : .ready },
      deadline: clock.addingTimeInterval(5), promptSeen: &promptSeen,
      now: { clock }, pause: {
        clock.addTimeInterval(1)
        if clock.timeIntervalSince1970 == 2 { completionObserved = true }
      }
    )
    XCTAssertEqual(result, .absent)
    XCTAssertEqual(clock.timeIntervalSince1970, 2)
    XCTAssertTrue(promptSeen)
    XCTAssertTrue(completionObserved)
  }

  func testDisappearingObservedPromptSuppressesReturnRecovery() {
    var clock = Date(timeIntervalSince1970: 0)
    var states: [PasswordPromptReadiness] = [.waiting, .absent]
    var promptSeen = false
    XCTAssertEqual(Self.waitForPasswordPrompt(
      state: { states.removeFirst() }, deadline: clock.addingTimeInterval(5), promptSeen: &promptSeen,
      now: { clock }, pause: { clock.addTimeInterval(1) }
    ), .absent)
    XCTAssertTrue(promptSeen)
    XCTAssertFalse(Self.returnRecoveryEligible(
      settled: true, promptSeen: promptSeen, issuerObserved: true, leafCount: 1,
      passwordCount: 1, submitCount: 1, passwordHittable: true, submitHittable: true,
      usernameCount: 0, usernameMatches: true, secureLengthMatches: true,
      providerErrorCount: 0, blockingAlertCount: 0
    ))
  }

  // nil means an observed prompt was ambiguous/inaccessible; false means absent.
  func declinePasswordSavePrompt(
    app: XCUIApplication, system: XCUIApplication, deadline: Date, completionPath: String,
    promptSeen: inout Bool
  ) -> Bool? {
    var selectedDecline: XCUIElement?
    var selectedPrompt: XCUIElement?
    let readiness = Self.waitForPasswordPrompt(state: {
      if Self.nativeWorkspaceObserved(path: completionPath) { return .absent }
      let appRunning = app.state == .runningForeground || app.state == .runningBackground
      let systemRunning = system.state == .runningForeground || system.state == .runningBackground
      let appPrompts = app.alerts.matching(NSPredicate(format: "label IN %@", Self.passwordSaveTitles))
      let systemPrompts = system.alerts.matching(NSPredicate(format: "label IN %@", Self.passwordSaveTitles))
      let appCount = appRunning ? appPrompts.count : 0
      let systemCount = systemRunning ? systemPrompts.count : 0
      guard appCount + systemCount > 0 else { return .absent }
      let appDeclines = appPrompts.buttons.matching(NSPredicate(format: "label IN %@", Self.passwordDeclineLabels))
      let systemDeclines = systemPrompts.buttons.matching(NSPredicate(format: "label IN %@", Self.passwordDeclineLabels))
      let appDeclineCount = appRunning ? appDeclines.count : 0
      let systemDeclineCount = systemRunning ? systemDeclines.count : 0
      func diagnose(_ readiness: String) {
        print("NATIVE_IOS_PASSWORD_PROMPT_STATE appPrompts=\(min(appCount, 999)) systemPrompts=\(min(systemCount, 999)) appDeclines=\(min(appDeclineCount, 999)) systemDeclines=\(min(systemDeclineCount, 999)) readiness=\(readiness)")
      }
      guard let surface = Self.uniquePasswordDeclineSurface(
        appPromptCount: appCount, systemPromptCount: systemCount,
        appDeclineCount: appDeclineCount, systemDeclineCount: systemDeclineCount
      ) else { diagnose("ambiguous"); return .ambiguous }
      let decline = surface == 0 ? appDeclines.element : systemDeclines.element
      guard decline.isHittable else { diagnose("waiting"); return .waiting }
      diagnose("ready")
      selectedDecline = decline
      selectedPrompt = surface == 0 ? appPrompts.element : systemPrompts.element
      return .ready
    }, deadline: min(deadline, Date().addingTimeInterval(5)), promptSeen: &promptSeen)
    guard readiness != .absent else { return false }
    if Self.nativeWorkspaceObserved(path: completionPath) { return false }
    guard Date() < deadline, readiness == .ready, let decline = selectedDecline, let prompt = selectedPrompt else {
      XCTFail("Password-save alert must settle to one accessible exact decline control")
      return nil
    }
    decline.tap()
    guard prompt.waitForNonExistence(timeout: min(10, max(0, deadline.timeIntervalSinceNow))) else {
      XCTFail("Password-save alert did not dismiss after declining")
      return nil
    }
    print("NATIVE_IOS_BROWSER_STAGE phase=password-save-declined")
    return true
  }

  static func returnRecoveryEligible(
    settled: Bool, promptSeen: Bool, issuerObserved: Bool, leafCount: Int,
    passwordCount: Int, submitCount: Int, passwordHittable: Bool, submitHittable: Bool,
    usernameCount: Int, usernameMatches: Bool, secureLengthMatches: Bool,
    providerErrorCount: Int, blockingAlertCount: Int
  ) -> Bool {
    settled && !promptSeen && issuerObserved && leafCount == 1 && passwordCount == 1 &&
      submitCount == 1 && passwordHittable && submitHittable && usernameCount <= 1 &&
      usernameCount >= 0 && (usernameCount == 0 || usernameMatches) && secureLengthMatches &&
      providerErrorCount == 0 && blockingAlertCount == 0
  }

  func testReturnRecoveryRejectsChangedOrAmbiguousProviderState() {
    func eligible(_ overrides: [String: Int] = [:]) -> Bool {
      func count(_ key: String, _ fallback: Int) -> Int { overrides[key] ?? fallback }
      return Self.returnRecoveryEligible(
        settled: count("settled", 1) == 1, promptSeen: count("prompt", 0) == 1,
        issuerObserved: count("issuer", 1) == 1, leafCount: count("leaf", 1),
        passwordCount: count("password", 1), submitCount: count("submit", 1),
        passwordHittable: count("passwordHittable", 1) == 1,
        submitHittable: count("submitHittable", 1) == 1,
        usernameCount: count("username", 0), usernameMatches: count("usernameMatches", 1) == 1,
        secureLengthMatches: count("secureLength", 1) == 1,
        providerErrorCount: count("error", 0), blockingAlertCount: count("alert", 0)
      )
    }
    XCTAssertTrue(eligible())
    XCTAssertTrue(eligible(["username": 1]))
    for changed in [["settled": 0], ["prompt": 1], ["issuer": 0], ["leaf": 0], ["leaf": 2],
                    ["password": 0], ["password": 2], ["submit": 0], ["submit": 2],
                    ["passwordHittable": 0], ["submitHittable": 0], ["username": 2],
                    ["username": 1, "usernameMatches": 0], ["secureLength": 0],
                    ["error": 1], ["alert": 1]] {
      XCTAssertFalse(eligible(changed))
    }
  }

  func waitForNativeWorkspace(
    app: XCUIApplication, browser: XCUIApplication, issuerSurface: XCUIApplication,
    fixture: Fixture, deadline: Date
  ) -> Bool {
    var nextDiagnostic = Date.distantPast
    let recoveryAfter = Date().addingTimeInterval(5)
    var recoveryConsidered = false
    var promptSeen = false
    let system = XCUIApplication(bundleIdentifier: "com.apple.springboard")
    while Date() < deadline {
      // Only the runner's observation of Flutter's real workspace assertion
      // releases this wait; a submit tap or disappearing browser never does.
      if Self.nativeWorkspaceObserved(path: fixture.nativeAuthCompletionPath) {
        print("NATIVE_IOS_BROWSER_STAGE phase=auth-completion-observed")
        return true
      }
      // A completed real workspace takes priority over an optional OS sheet,
      // which can outlive AppAuth and expose stale/non-hittable controls.
      guard declinePasswordSavePrompt(
        app: app, system: system, deadline: deadline, completionPath: fixture.nativeAuthCompletionPath,
        promptSeen: &promptSeen
      ) != nil else { return false }
      if Self.nativeWorkspaceObserved(path: fixture.nativeAuthCompletionPath) { continue }
      if !recoveryConsidered && Date() >= recoveryAfter {
        // One optional native Return on the freshly resolved original issuer form.
        // No refill, further Return attempts, app activation, or representation-dependent pass.
        recoveryConsidered = true
        let state = issuerSurface.state
        if !promptSeen && (state == .runningForeground || state == .runningBackground) {
          let host = issuerSurface.descendants(matching: .any).matching(NSPredicate(
            format: "label CONTAINS %@ OR value CONTAINS %@", fixture.issuerHost, fixture.issuerHost
          ))
          let webViews = issuerSurface.webViews
          let leaves = Self.providerLeafIndices(
            childWebViews: (0..<webViews.count).map { webViews.element(boundBy: $0).webViews.count },
            formFields: (0..<webViews.count).map {
              let web = webViews.element(boundBy: $0)
              return web.textFields.count + web.secureTextFields.count
            }
          )
          if host.count > 0 && leaves.count == 1 {
            let provider = webViews.element(boundBy: leaves[0])
            let passwords = provider.secureTextFields
            let usernames = provider.textFields
            let submits = provider.buttons.matching(NSPredicate(format: "label IN %@", Self.providerSubmitLabels))
            let errors = webViews.descendants(matching: .any).matching(NSPredicate(
              format: "label IN %@ OR value IN %@", Self.providerErrorLabels, Self.providerErrorLabels
            )).count
            let appRunning = app.state == .runningForeground || app.state == .runningBackground
            let systemRunning = system.state == .runningForeground || system.state == .runningBackground
            let alerts = (appRunning ? app.alerts.count : 0) + (systemRunning ? system.alerts.count : 0) +
              issuerSurface.alerts.count
            let usernameMatches = usernames.count == 1 && usernames.element.value as? String == fixture.email
            let secureMatches = passwords.count == 1 &&
              (passwords.element.value as? String)?.count == fixture.password.count
            if Self.returnRecoveryEligible(
              settled: true, promptSeen: promptSeen, issuerObserved: true, leafCount: leaves.count,
              passwordCount: passwords.count, submitCount: submits.count,
              passwordHittable: passwords.count == 1 && passwords.element.isHittable,
              submitHittable: submits.count == 1 && submits.element.isHittable,
              usernameCount: usernames.count, usernameMatches: usernameMatches,
              secureLengthMatches: secureMatches, providerErrorCount: errors, blockingAlertCount: alerts
            ) {
              passwords.element.tap()
              passwords.element.typeText(XCUIKeyboardKey.return.rawValue)
              print("NATIVE_IOS_BROWSER_STAGE phase=password-return-submitted")
            }
          }
        }
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

  func testNativeDocumentPicker() throws {
    continueAfterFailure = false
    let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "private-member", withExtension: "json"))
    let fixture = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
    guard fixture["phase"] as? String == "document-picker" else {
      throw XCTSkip("Document picker is a separate owned UI action")
    }
    let mode = try XCTUnwrap(fixture["pickerMode"] as? String)
    let fileName = try XCTUnwrap(fixture["fileName"] as? String)
    XCTAssertTrue(["import", "export"].contains(mode))
    XCTAssertTrue(fileName.hasPrefix("weave-dual-"))
    XCTAssertFalse(fileName.contains("/"))
    let app = XCUIApplication(bundleIdentifier: "com.massimotter.weave")
    // Authentication has completed. Attach once to the already running app;
    // never launch a replacement process or activate during an AppAuth session.
    app.activate()
    let deadline = Date().addingTimeInterval(60)
    var enteredDevice = false
    var enteredWeave = false
    while Date() < deadline {
      let file = app.staticTexts.matching(NSPredicate(format: "label == %@", fileName))
      if mode == "import" && file.count == 1 && file.element.isHittable {
        file.element.tap()
        print("NATIVE_IOS_FILES_STAGE phase=import-selected")
        return
      }
      XCTAssertLessThanOrEqual(file.count, 1, "Owned document must be unique")
      if mode == "export" && enteredWeave {
        let save = app.buttons.matching(NSPredicate(format: "label IN %@", ["Save", "Sichern"]))
        XCTAssertLessThanOrEqual(save.count, 1, "Native save action must be unique")
        if save.count == 1 && save.element.isHittable {
          save.element.tap()
          print("NATIVE_IOS_FILES_STAGE phase=export-saved")
          return
        }
      }
      let weave = app.staticTexts.matching(NSPredicate(format: "label == %@", "Weave"))
      if !enteredWeave && weave.count == 1 && weave.element.isHittable {
        weave.element.tap()
        enteredWeave = true
        Thread.sleep(forTimeInterval: 0.25)
        continue
      }
      let device = app.staticTexts.matching(NSPredicate(format: "label IN %@", ["On My iPhone", "Auf meinem iPhone"]))
      if !enteredDevice && device.count == 1 && device.element.isHittable {
        device.element.tap()
        enteredDevice = true
        Thread.sleep(forTimeInterval: 0.25)
        continue
      }
      let browse = app.buttons.matching(NSPredicate(format: "label IN %@", ["Browse", "Durchsuchen"]))
      if !enteredDevice && browse.count == 1 && browse.element.isHittable { browse.element.tap() }
      Thread.sleep(forTimeInterval: 0.25)
    }
    // Failure-only observations never change the selector or grant more selection time.
    let priorContinuation = continueAfterFailure
    continueAfterFailure = true
    defer { continueAfterFailure = priorContinuation }
    XCTFail("Owned native document picker action did not complete")
    print("NATIVE_IOS_FILES_DIAGNOSTIC phase=timeout enteredDevice=\(enteredDevice) enteredWeave=\(enteredWeave) supportSafe=true")
    guard app.state == .runningForeground || app.state == .runningBackground else {
      print("NATIVE_IOS_FILES_DIAGNOSTIC phase=unavailable reason=app-not-running supportSafe=true")
      return
    }
    let observationDeadline = Date().addingTimeInterval(5)
    let stem = (fileName as NSString).deletingPathExtension
    let types: [(String, XCUIElement.ElementType)] = [
      ("staticText", .staticText), ("cell", .cell), ("button", .button), ("other", .other)
    ]
    let probes = [
      ("fullLabel", "label == %@", fileName), ("stemLabel", "label == %@", stem),
      ("fullIdentifier", "identifier == %@", fileName), ("stemIdentifier", "identifier == %@", stem)
    ]
    var observed = 0
    for (typeName, type) in types {
      for (matchName, predicate, value) in probes {
        guard Date() < observationDeadline else {
          print("NATIVE_IOS_FILES_DIAGNOSTIC phase=bounded records=\(observed) complete=false supportSafe=true")
          return
        }
        let matches = app.descendants(matching: type).matching(NSPredicate(format: predicate, value))
        let count = matches.count
        // Inspect at most one element per exact query; ambiguous matches stay unselected.
        let uniqueHittable = count == 1 && matches.element.isHittable
        print("NATIVE_IOS_FILES_DIAGNOSTIC phase=match elementType=\(typeName) match=\(matchName) count=\(min(count, 999)) uniqueHittable=\(uniqueHittable) supportSafe=true")
        observed += 1
      }
    }
    print("NATIVE_IOS_FILES_DIAGNOSTIC phase=bounded records=\(observed) complete=true supportSafe=true")
  }

  func testNativeLifecycle() throws {
    continueAfterFailure = false
    let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "private-member", withExtension: "json"))
    let fixture = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
    guard fixture["phase"] as? String == "lifecycle" else {
      throw XCTSkip("Lifecycle is a separate owned native UI action")
    }
    let action = try XCTUnwrap(fixture["action"] as? String)
    let app = XCUIApplication(bundleIdentifier: "com.massimotter.weave")
    if action == "background" {
      XCTAssertEqual(app.state, .runningForeground)
      XCUIDevice.shared.press(.home)
      let deadline = Date().addingTimeInterval(10)
      while Date() < deadline && app.state == .runningForeground { Thread.sleep(forTimeInterval: 0.25) }
      XCTAssertTrue(app.state == .runningBackground || app.state == .runningBackgroundSuspended)
      print("NATIVE_IOS_LIFECYCLE_STAGE phase=background-observed")
    } else {
      XCTAssertEqual(action, "foreground")
      XCTAssertTrue(app.state == .runningBackground || app.state == .runningBackgroundSuspended)
      app.activate()  // Post-auth lifecycle only; attach the same existing process.
      let deadline = Date().addingTimeInterval(10)
      while Date() < deadline && app.state != .runningForeground { Thread.sleep(forTimeInterval: 0.25) }
      XCTAssertEqual(app.state, .runningForeground)
      print("NATIVE_IOS_LIFECYCLE_STAGE phase=foreground-observed")
    }
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
      print("NATIVE_IOS_BROWSER_STAGE phase=sign-in-control-observed")
      if fixture.flutterInitiatesSignIn != true {
        signIn.tap()
        print("NATIVE_IOS_BROWSER_STAGE phase=sign-in-tapped")
      }
    }
    var usernameEntered = false
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
            if !usernameEntered {
              fields.element.tap()
              fields.element.typeKey("a", modifierFlags: .command)
              fields.element.typeText(XCUIKeyboardKey.delete.rawValue)
              let editableEmpty = fields.element.value as? String == ""
              print("NATIVE_IOS_PROVIDER_CONTROLS phase=username-cleared editableEmpty=\(editableEmpty)")
              guard editableEmpty else {
                XCTFail("Expected an empty editable username before assigned-identity entry")
                return
              }
              fields.element.typeText(fixture.email)
              usernameEntered = true
              // Re-observe the issuer and its unique leaf before admitting a submit.
              continue
            }
            let readbackEqual = fields.element.value as? String == fixture.email
            print("NATIVE_IOS_PROVIDER_CONTROLS phase=username-after readbackEqual=\(readbackEqual)")
            guard readbackEqual, Date() < deadline else { continue }
            let submit = provider.buttons.matching(NSPredicate(format: "label IN %@", Self.providerSubmitLabels))
            print("NATIVE_IOS_PROVIDER_CONTROLS phase=username leafWebViews=\(leaves.count) usernameFields=\(fields.count) passwordFields=\(provider.secureTextFields.count) submitButtons=\(submit.count)")
            XCTAssertEqual(submit.count, 1, "Expected one identity-provider submit control")
            XCTAssertTrue(submit.element.isHittable, "Identity-provider submit control must be accessible")
            guard fields.count == 1, fields.element.isHittable,
                  fields.element.value as? String == fixture.email,
                  submit.count == 1, submit.element.isHittable,
                  Date() < deadline else { continue }
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
            guard waitForNativeWorkspace(app: app, browser: browser, issuerSurface: surface, fixture: fixture, deadline: deadline) else {
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
