import XCTest

// BEGIN REGISTRATION AUTHORITY VALIDATION
// Browser chrome must expose an explicit URL; a collapsed host is not HTTPS proof.
func registrationIssuerAddressMatches(_ value: String, issuerUrl: String) -> Bool {
  guard let expected = URLComponents(string: issuerUrl),
        expected.scheme == "https", let expectedHost = expected.host,
        expected.user == nil, expected.password == nil,
        expected.query == nil, expected.fragment == nil,
        let current = URLComponents(string: value), current.scheme == "https",
        current.host?.lowercased() == expectedHost.lowercased(),
        (current.port ?? 443) == (expected.port ?? 443),
        current.user == nil, current.password == nil, current.fragment == nil else { return false }
  // Prefix comparison alone must not admit traversal, encoded separators or
  // double-encoded path reinterpretation across the trusted realm boundary.
  func canonicalSegments(_ path: String) -> Bool {
    path.split(separator: "/", omittingEmptySubsequences: false).allSatisfy { segment in
      guard let decoded = String(segment).removingPercentEncoding else { return false }
      return decoded != "." && decoded != ".." &&
        !decoded.contains("/") && !decoded.contains("\\") && !decoded.contains("%") &&
        !decoded.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) })
    }
  }
  let realm = expected.percentEncodedPath.hasSuffix("/")
    ? String(expected.percentEncodedPath.dropLast()) : expected.percentEncodedPath
  return !realm.isEmpty && canonicalSegments(realm) && canonicalSegments(current.percentEncodedPath) &&
    current.percentEncodedPath.hasPrefix(realm + "/")
}

func registrationStockActionMatches(_ value: String, issuerUrl: String) -> Bool {
  guard registrationIssuerAddressMatches(value, issuerUrl: issuerUrl),
        let expected = URLComponents(string: issuerUrl), let current = URLComponents(string: value) else { return false }
  let registration = current.path == expected.path + "/protocol/openid-connect/registrations"
  let verification = current.path == expected.path + "/login-actions/action-token"
  guard registration || verification else { return false }
  let items = current.queryItems ?? []
  func exact(_ key: String, _ expected: String? = nil) -> Bool {
    let matching = items.filter { $0.name == key }
    guard matching.count == 1, let value = matching[0].value, !value.isEmpty else { return false }
    return expected == nil || value == expected
  }
  return exact("client_id", "account") &&
    (registration ? exact("response_type", "code") && exact("token") : exact("key"))
}

func registrationAddressDecision(chromeCount: Int, fullAddresses: [String], collapsedCount: Int,
                                 collapsedType: String, revealedTypes: Set<String> = [], issuerUrl: String) -> String {
  guard chromeCount <= 1, fullAddresses.count <= 1, collapsedCount <= 1 else { return "ambiguous" }
  guard chromeCount == 1 else { return "wait" }
  if collapsedCount == 1 && !["textField", "other"].contains(collapsedType) { return "ambiguous" }
  if let address = fullAddresses.first {
    return registrationIssuerAddressMatches(address, issuerUrl: issuerUrl) ? "verified" : "wrong-authority"
  }
  if collapsedCount == 1 {
    guard ["textField", "other"].contains(collapsedType) else { return "ambiguous" }
    return revealedTypes.contains(collapsedType) ? "wait" : "reveal"
  }
  return "wait"
}
// Stock registration may defer password setup until email verification.
// Counts describe only one issuer-verified leaf form, never page-wide first matches.
func registrationFormStep(registerCount: Int, usernameCount: Int, passwordCount: Int,
                          confirmationCount: Int, newPasswordCount: Int, submitCount: Int,
                          registrationSubmitted: Bool, passwordSubmitted: Bool) -> String {
  guard [registerCount, usernameCount, passwordCount, confirmationCount, newPasswordCount, submitCount]
    .allSatisfy({ $0 >= 0 && $0 <= 1 }) else { return "ambiguous" }
  if registerCount == 1 {
    guard newPasswordCount == 0 else { return "ambiguous" }
    if registrationSubmitted { return "wait" }
    if passwordCount == 0 && confirmationCount == 0 { return "profile-deferred-password" }
    if passwordCount == 1 && confirmationCount == 1 { return "profile-with-password" }
    return "ambiguous"
  }
  if newPasswordCount > 0 || confirmationCount > 0 {
    guard newPasswordCount == 1 && confirmationCount == 1 && passwordCount == 0 && submitCount == 1 else { return "ambiguous" }
    return passwordSubmitted ? "wait" : "update-password"
  }
  return "wait"
}
// The native AutoFill suggestion is not a provider form or a password-save alert.
func registrationStrongPasswordDecision(viewCount: Int, headingCount: Int, generateCount: Int, closeCount: Int) -> String {
  guard viewCount <= 1, headingCount <= 1, generateCount <= 1, closeCount <= 1 else { return "ambiguous" }
  if viewCount == 0 { return "none" }
  return headingCount == 1 && generateCount == 1 && closeCount == 1 ? "decline" : "wait"
}
// Page observation never infers acceptance from a Submit tap or vanished form.
func registrationProviderOutcome(expiredCount: Int, failureCount: Int, verificationCount: Int,
                                 completionCount: Int, activeForm: Bool) -> String {
  guard [expiredCount, failureCount, verificationCount, completionCount].allSatisfy({ $0 >= 0 && $0 <= 1 }),
        expiredCount + failureCount + verificationCount + completionCount <= 1 else { return "ambiguous" }
  if expiredCount == 1 { return "expired" }
  if failureCount == 1 { return "provider-failure" }
  if verificationCount == 1 { return "verification" }
  if completionCount == 1 && !activeForm { return "complete" }
  return "wait"
}
// END REGISTRATION AUTHORITY VALIDATION

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
    let ordinaryOrganizationOrigin: String?
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
      if (fixture.ordinaryOrganizationOrigin != nil && Self.ordinaryWorkspaceObserved(app)) || (fixture.ordinaryOrganizationOrigin == nil && Self.nativeWorkspaceObserved(path: fixture.nativeAuthCompletionPath)) {
        print("NATIVE_IOS_BROWSER_STAGE phase=auth-completion-observed")
        return true
      }
      // A completed real workspace takes priority over an optional OS sheet,
      // which can outlive AppAuth and expose stale/non-hittable controls.
      guard declinePasswordSavePrompt(
        app: app, system: system, deadline: deadline, completionPath: fixture.nativeAuthCompletionPath,
        promptSeen: &promptSeen
      ) != nil else { return false }
      if (fixture.ordinaryOrganizationOrigin != nil && Self.ordinaryWorkspaceObserved(app)) || (fixture.ordinaryOrganizationOrigin == nil && Self.nativeWorkspaceObserved(path: fixture.nativeAuthCompletionPath)) { continue }
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

  static func privateFixtureData(at url: URL) throws -> Data {
    let attributes = try FileManager.default.attributesOfItem(atPath: url.path)
    guard attributes[.type] as? FileAttributeType == .typeRegular,
          (attributes[.posixPermissions] as? NSNumber)?.intValue == 0o600,
          let size = attributes[.size] as? NSNumber,
          size.intValue > 0 && size.intValue <= 8192 else {
      throw NSError(domain: "NativeDriverFixture", code: 1)
    }
    return try Data(contentsOf: url)
  }

  func nativeFixtureData() throws -> Data {
    // Xcode forwards this private test-plan value on every invocation. Keeping
    // it outside the installed bundle avoids stale bundle-resource caching.
    let path = try XCTUnwrap(ProcessInfo.processInfo.environment["WEAVE_NATIVE_DRIVER_FIXTURE_PATH"])
    if path == "runner-documents:private-member.json" {
      // Physical XCTest has no host filesystem. The existing runner receives
      // this file in its own sandbox, never the member application's container.
      let documents = try XCTUnwrap(FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first)
      let url = documents.appendingPathComponent("private-member.json")
      let attributes = try FileManager.default.attributesOfItem(atPath: url.path)
      guard attributes[.type] as? FileAttributeType == .typeRegular else {
        throw NSError(domain: "NativeDriverFixture", code: 2)
      }
      try FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: url.path)
      defer { try? FileManager.default.removeItem(at: url) }
      return try Self.privateFixtureData(at: url)
    }
    guard path.hasPrefix("/") else {
      throw NSError(domain: "NativeDriverFixture", code: 2)
    }
    return try Self.privateFixtureData(at: URL(fileURLWithPath: path))
  }

  func testPrivateFixtureDelivery() throws {
    let fixture = try XCTUnwrap(JSONSerialization.jsonObject(with: nativeFixtureData()) as? [String: Any])
    let phase = try XCTUnwrap(fixture["phase"] as? String)
    guard ["initial", "restore", "document-picker", "lifecycle", "ordinary-registration"].contains(phase) else {
      XCTFail("Driver fixture phase is invalid")
      return
    }
    print("NATIVE_IOS_FIXTURE_STAGE phase=\(phase)")
  }

  func testPrivateFixtureRejectsUnsafeFiles() throws {
    let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: false)
    defer { try? FileManager.default.removeItem(at: directory) }
    let file = directory.appendingPathComponent("fixture.json")
    XCTAssertThrowsError(try Self.privateFixtureData(at: file))
    try Data("{}".utf8).write(to: file)
    try FileManager.default.setAttributes([.posixPermissions: 0o644], ofItemAtPath: file.path)
    XCTAssertThrowsError(try Self.privateFixtureData(at: file))
    try FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: file.path)
    XCTAssertEqual(try Self.privateFixtureData(at: file), Data("{}".utf8))
    let link = directory.appendingPathComponent("linked.json")
    try FileManager.default.createSymbolicLink(at: link, withDestinationURL: file)
    XCTAssertThrowsError(try Self.privateFixtureData(at: link))
    try Data().write(to: file)
    XCTAssertThrowsError(try Self.privateFixtureData(at: file))
    try Data(repeating: 32, count: 8193).write(to: file)
    XCTAssertThrowsError(try Self.privateFixtureData(at: file))
  }

  func testNativeDocumentPicker() throws {
    continueAfterFailure = false
    let fixture = try XCTUnwrap(JSONSerialization.jsonObject(with: nativeFixtureData()) as? [String: Any])
    guard fixture["phase"] as? String == "document-picker" else {
      throw XCTSkip("Document picker is a separate owned UI action")
    }
    let mode = try XCTUnwrap(fixture["pickerMode"] as? String)
    let fileName = try XCTUnwrap(fixture["fileName"] as? String)
    XCTAssertTrue(["import", "export", "export-cancel"].contains(mode))
    XCTAssertTrue(fileName.hasPrefix("weave-dual-"))
    XCTAssertFalse(fileName.contains("/"))
    try ordinaryDocumentPicker(mode: mode, fileName: fileName)
  }

  func ordinaryDocumentPicker(mode: String, fileName: String) throws {
    guard ["import", "export", "export-cancel"].contains(mode), fileName.hasPrefix("weave-dual-"),
          !fileName.contains("/") else { throw NSError(domain: "invalid-owned-picker-input", code: 1) }
    let stem = (fileName as NSString).deletingPathExtension
    let app = XCUIApplication(bundleIdentifier: "com.massimotter.weave")
    // Authentication has completed. Attach once to the already running app;
    // never launch a replacement process or activate during an AppAuth session.
    app.activate()
    let deadline = Date().addingTimeInterval(60)
    var enteredDevice = false
    var enteredWeave = false
    while Date() < deadline {
      let file = app.staticTexts.matching(NSPredicate(format: "label == %@", stem))
      if mode == "import" && file.count == 1 && file.element.isHittable {
        let picker = app.collectionViews.matching(identifier: "File View")
        XCTAssertEqual(picker.count, 1, "Native file view must be unique")
        let tile = picker.element.cells.containing(.staticText, identifier: stem)
        XCTAssertEqual(tile.count, 1, "Owned document tile must be unique")
        XCTAssertTrue(tile.element.isHittable, "Owned document tile must be hittable")
        let preview = tile.element.images
        XCTAssertEqual(preview.count, 1, "Owned document preview must be unique")
        let previewFrame = preview.element.frame
        let previewCenter = CGPoint(x: previewFrame.midX, y: previewFrame.midY)
        XCTAssertTrue(
          previewFrame.width > 0 && previewFrame.height > 0
            && previewFrame.origin.x.isFinite && previewFrame.origin.y.isFinite
            && previewFrame.width.isFinite && previewFrame.height.isFinite,
          "Owned document preview must have a finite nonempty frame")
        XCTAssertTrue(
          tile.element.frame.contains(previewCenter) && app.frame.contains(previewCenter),
          "Owned document preview center must be inside the visible owned tile")
        guard Date() < deadline else { break }
        preview.element.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        XCTAssertTrue(
          picker.element.waitForNonExistence(timeout: max(0, deadline.timeIntervalSinceNow)),
          "Native file picker must dismiss after selecting the owned document")
        print("NATIVE_IOS_FILES_STAGE phase=import-selected")
        return
      }
      XCTAssertLessThanOrEqual(file.count, 1, "Owned document must be unique")
      if mode == "export" || mode == "export-cancel" {
        let picker = app.otherElements.matching(identifier: "Browse View (Picker)")
        XCTAssertLessThanOrEqual(picker.count, 1, "Native export picker must be unique")
        if picker.count == 0 {
          Thread.sleep(forTimeInterval: 0.25)
          continue
        }
        if mode == "export-cancel" {
          let cancel = picker.element.buttons.matching(NSPredicate(format: "label IN %@", ["Cancel", "Abbrechen", "Close", "Schließen"]))
          XCTAssertLessThanOrEqual(cancel.count, 1, "Native cancel action must be unique")
          if cancel.count == 1 && cancel.element.isEnabled && cancel.element.isHittable {
            guard Date() < deadline else { break }
            cancel.element.tap()
            XCTAssertTrue(
              picker.element.waitForNonExistence(timeout: max(0, deadline.timeIntervalSinceNow)),
              "Native export picker must dismiss after cancellation")
            print("NATIVE_IOS_FILES_STAGE phase=export-cancelled")
            return
          }
          // iOS 26.5 shows cancellation at Browse, above the current local
          // folder. Follow only the native navigation bar's back control.
          let back = picker.element.navigationBars.buttons.matching(NSPredicate(
            format: "label IN %@", ["Back", "Zurück", "Browse", "Durchsuchen", "On My iPhone", "Auf meinem iPhone"]))
          XCTAssertLessThanOrEqual(back.count, 1, "Native picker back action must be unique")
          if back.count == 1 && back.element.isEnabled && back.element.isHittable {
            guard Date() < deadline else { break }
            back.element.tap()
          }
          Thread.sleep(forTimeInterval: 0.25)
          continue
        }
        let folder = picker.element.otherElements.matching(NSPredicate(format: "identifier BEGINSWITH %@", "DOC.browsingRoot Source: com.apple.FileProvider.LocalStorage, Title: "))
        XCTAssertLessThanOrEqual(folder.count, 1, "Owned export folder must be unique")
        if folder.count == 1 {
          let navigation = folder.element.navigationBars.matching(identifier: "FullDocumentManagerViewControllerNavigationBar")
          let filename = picker.element.textFields.matching(identifier: "DOCPicker.filenameTextField")
          XCTAssertLessThanOrEqual(navigation.count, 1, "Native export navigation must be unique")
          XCTAssertLessThanOrEqual(filename.count, 1, "Native export filename must be unique")
          if navigation.count == 1 && filename.count == 1 {
            let currentFolder = navigation.element.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Weave, "))
            XCTAssertLessThanOrEqual(currentFolder.count, 1, "Current owned export folder must be unique")
            if currentFolder.count == 0 || !currentFolder.element.isEnabled || !currentFolder.element.isHittable {
              Thread.sleep(forTimeInterval: 0.25)
              continue
            }
            let save = navigation.element.buttons.matching(NSPredicate(format: "label IN %@", ["Save", "Sichern"]))
            XCTAssertLessThanOrEqual(save.count, 1, "Native save action must be unique")
            if save.count == 1 && save.element.isEnabled && save.element.isHittable {
              guard Date() < deadline else { break }
              save.element.tap()
              XCTAssertTrue(
                picker.element.waitForNonExistence(timeout: max(0, deadline.timeIntervalSinceNow)),
                "Native export picker must dismiss after saving")
              print("NATIVE_IOS_FILES_STAGE phase=export-saved")
              return
            }
          }
          Thread.sleep(forTimeInterval: 0.25)
          continue
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
    let fixture = try XCTUnwrap(JSONSerialization.jsonObject(with: nativeFixtureData()) as? [String: Any])
    guard fixture["phase"] as? String == "lifecycle" else {
      throw XCTSkip("Lifecycle is a separate owned native UI action")
    }
    let action = try XCTUnwrap(fixture["action"] as? String)
    let app = XCUIApplication(bundleIdentifier: "com.massimotter.weave")
    if action == "background" {
      let before = app.state
      print("NATIVE_IOS_LIFECYCLE_DIAGNOSTIC action=background phase=before state=\(before.rawValue)")
      XCTAssertEqual(before, .runningForeground)
      XCUIDevice.shared.press(.home)
      let afterHome = app.state
      print("NATIVE_IOS_LIFECYCLE_DIAGNOSTIC action=background phase=after-home state=\(afterHome.rawValue)")
      let background = XCTNSPredicateExpectation(
        predicate: NSPredicate { _, _ in
          let state = app.state
          return state == .runningBackground || state == .runningBackgroundSuspended
        }, object: app)
      let waitResult = XCTWaiter.wait(for: [background], timeout: 10)
      let final = app.state
      print("NATIVE_IOS_LIFECYCLE_DIAGNOSTIC action=background phase=final state=\(final.rawValue)")
      XCTAssertEqual(waitResult, .completed)
      XCTAssertTrue(final == .runningBackground || final == .runningBackgroundSuspended)
      print("NATIVE_IOS_LIFECYCLE_STAGE phase=background-observed")
    } else {
      XCTAssertEqual(action, "foreground")
      let before = app.state
      print("NATIVE_IOS_LIFECYCLE_DIAGNOSTIC action=foreground phase=before state=\(before.rawValue)")
      XCTAssertTrue(before == .runningBackground || before == .runningBackgroundSuspended)
      app.activate()  // Post-auth lifecycle only; attach the same existing process.
      let afterActivate = app.state
      print("NATIVE_IOS_LIFECYCLE_DIAGNOSTIC action=foreground phase=after-activate state=\(afterActivate.rawValue)")
      let reachedForeground = app.wait(for: .runningForeground, timeout: 10)
      let final = app.state
      print("NATIVE_IOS_LIFECYCLE_DIAGNOSTIC action=foreground phase=final state=\(final.rawValue)")
      XCTAssertTrue(reachedForeground)
      XCTAssertEqual(final, .runningForeground)
      print("NATIVE_IOS_LIFECYCLE_STAGE phase=foreground-observed")
    }
  }

  static func validRegistrationFixture(_ value: [String: Any]) -> Bool {
    guard Set(value.keys) == Set(["schemaVersion", "phase", "planId", "nonce", "slot", "organizationOrigin", "issuerUrl", "activationUrl", "email", "displayName", "password", "appBundleIdentifier"]),
          value["schemaVersion"] as? String == "weave.dogfood-test-registration/v1",
          value["phase"] as? String == "ordinary-registration",
          value["appBundleIdentifier"] as? String == "com.massimotter.weave",
          ["A", "B"].contains(value["slot"] as? String ?? ""),
          ["planId", "nonce"].allSatisfy({ (value[$0] as? String)?.range(of: #"\A[a-f0-9]{32}\z"#, options: .regularExpression) != nil }),
          let organization = value["organizationOrigin"] as? String, let org = URLComponents(string: organization),
          org.scheme == "https", org.host != nil, org.user == nil, org.password == nil, org.path.isEmpty, org.query == nil, org.fragment == nil,
          let issuerText = value["issuerUrl"] as? String, let issuer = URLComponents(string: issuerText),
          issuer.scheme == "https", issuer.host != nil, issuer.user == nil, issuer.password == nil, issuer.query == nil, issuer.fragment == nil,
          let activationText = value["activationUrl"] as? String, let activation = URLComponents(string: activationText),
          activation.scheme == "https", activation.host == issuer.host, activation.port == issuer.port,
          activation.user == nil, activation.password == nil, activation.fragment == nil,
          activation.path.hasPrefix(issuer.path + "/"),
          registrationStockActionMatches(activationText, issuerUrl: issuerText),
          let email = value["email"] as? String, email.contains("@"), email.utf8.count <= 256,
          let name = value["displayName"] as? String, !name.isEmpty, name.count <= 160,
          let password = value["password"] as? String, !password.isEmpty, password.utf8.count <= 1024,
          [email, name, password].allSatisfy({ !$0.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) }) }) else { return false }
    return true
  }

  func testRegistrationFixtureRejectsWrongAuthorityAndOwnerSessionMaterial() {
    let base: [String: Any] = ["schemaVersion": "weave.dogfood-test-registration/v1", "phase": "ordinary-registration", "planId": String(repeating: "a", count: 32), "nonce": String(repeating: "b", count: 32), "slot": "A", "organizationOrigin": "https://organization.example.invalid", "issuerUrl": "https://identity.example.invalid/realms/weave", "activationUrl": "https://identity.example.invalid/realms/weave/protocol/openid-connect/registrations?client_id=account&response_type=code&token=nonsecret", "email": "test@example.invalid", "displayName": "Approved Test", "password": "private-fixture", "appBundleIdentifier": "com.massimotter.weave"]
    XCTAssertTrue(Self.validRegistrationFixture(base))
    for change: [String: Any] in [["activationUrl": "http://identity.example.invalid/realms/weave/action"], ["activationUrl": "https://other.example.invalid/realms/weave/action"], ["activationUrl": "https://identity.example.invalid/realms/other/action"], ["token": "forbidden"], ["password": ""], ["appBundleIdentifier": "com.example.owner"], ["phase": "initial"], ["nonce": "invalid"]] {
      XCTAssertFalse(Self.validRegistrationFixture(base.merging(change) { _, new in new }))
    }
  }

  func testOrdinaryTestRegistration() throws {
    continueAfterFailure = false
    let data = try nativeFixtureData()
    let raw = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    guard raw["phase"] as? String == "ordinary-registration" else { throw XCTSkip("Registration is a separate approved test-member action") }
    guard Self.validRegistrationFixture(raw) else { XCTFail("Approved registration input is invalid"); return }
    let email = raw["email"] as! String
    let password = raw["password"] as! String
    let name = (raw["displayName"] as! String).split(separator: " ", maxSplits: 1).map(String.init)
    #if !targetEnvironment(simulator)
    XCTFail("Approved test-member registration requires an owned simulator"); return
    #endif
    let safari = XCUIApplication(bundleIdentifier: "com.apple.mobilesafari")
    let deadline = Date().addingTimeInterval(180)
    // Open only the validated private stock action through the OS default app.
    // Unlike XCUIApplication.open, this needs no pre-existing Safari AX surface.
    XCUIDevice.shared.system.open(try XCTUnwrap(URL(string: raw["activationUrl"] as! String)))
    safari.activate()
    guard safari.wait(for: .runningForeground, timeout: min(15, max(0, deadline.timeIntervalSinceNow))),
          safari.windows.firstMatch.waitForExistence(timeout: min(15, max(0, deadline.timeIntervalSinceNow))),
          Date() < deadline else {
      XCTFail("Registration Safari attachment is unavailable"); return
    }
    guard safari.wait(for: .runningForeground, timeout: min(15, max(0, deadline.timeIntervalSinceNow))),
          safari.webViews.firstMatch.waitForExistence(timeout: min(45, max(0, deadline.timeIntervalSinceNow))),
          Date() < deadline else {
      XCTFail("Registration Safari page is unavailable"); return
    }
    var registrationSubmitted = false
    var passwordSubmitted = false
    var invitationAccepted = false
    func declineStrongPasswordSuggestion() -> Bool {
      let promptDeadline = min(deadline, Date().addingTimeInterval(5))
      var declined = false
      while Date() < promptDeadline {
        let views = safari.otherElements.matching(identifier: "SFAutoFillInputView")
        guard views.count <= 1 else { XCTFail("Registration strong-password suggestion is ambiguous"); return false }
        if views.count == 0 { return true }
        let heading = views.element.staticTexts.matching(NSPredicate(format: "label IN %@", ["Starkes Passwort verwenden?", "Use Strong Password?"]))
        let generate = views.element.buttons.matching(identifier: "GenerateStrongPasswordButton")
        let close = views.element.buttons.matching(identifier: "xmark")
        let decision = registrationStrongPasswordDecision(viewCount: views.count, headingCount: heading.count,
          generateCount: generate.count, closeCount: close.count)
        if decision == "ambiguous" { XCTFail("Registration strong-password controls are ambiguous"); return false }
        if decision == "decline" && !declined && close.element.isEnabled && close.element.isHittable {
          // Close this exact native suggestion only. Never generate/save a credential.
          close.element.tap(); declined = true
        }
        Thread.sleep(forTimeInterval: 0.1)
      }
      XCTFail("Registration strong-password suggestion could not be declined"); return false
    }
    func requireIssuerAddress() -> Bool {
      let addressDeadline = min(deadline, Date().addingTimeInterval(10))
      var revealedTypes = Set<String>()
      while Date() < addressDeadline {
        guard declineStrongPasswordSuggestion() else { return false }
        // Scope to actual browser chrome. Never accept an address-looking page field.
        let chrome = safari.otherElements.matching(NSPredicate(format: "identifier BEGINSWITH %@", "CapsuleNavigationBar?"))
        guard chrome.count <= 1 else { XCTFail("Registration browser chrome is ambiguous"); return false }
        if chrome.count == 0 { Thread.sleep(forTimeInterval: 0.1); continue }
        let full = chrome.element.textFields.matching(identifier: "URL")
        let collapsed = chrome.element.descendants(matching: .any).matching(identifier: "TabBarItemTitle")
        let values = full.allElementsBoundByIndex.map { $0.value as? String ?? "" }
        let collapsedType = collapsed.count == 1 ? (collapsed.element.elementType == .textField ? "textField" :
          collapsed.element.elementType == .other ? "other" : "unsupported") : "unavailable"
        let decision = registrationAddressDecision(chromeCount: chrome.count, fullAddresses: values,
          collapsedCount: collapsed.count, collapsedType: collapsedType, revealedTypes: revealedTypes,
          issuerUrl: raw["issuerUrl"] as! String)
        if decision == "verified" {
          let close = safari.buttons.matching(identifier: "CancelBarItemButton")
          guard close.count == 1, close.element.isEnabled, close.element.isHittable else {
            XCTFail("Registration verified address cannot be dismissed safely"); return false
          }
          // Cancel only exits address editing; it does not navigate or submit.
          close.element.tap()
          while Date() < addressDeadline {
            if chrome.count == 1 && chrome.element.textFields.matching(identifier: "URL").count == 0 &&
                chrome.element.descendants(matching: .any).matching(identifier: "TabBarItemTitle").count == 1 { return true }
            Thread.sleep(forTimeInterval: 0.1)
          }
          XCTFail("Registration address editing did not close"); return false
        }
        if decision == "wrong-authority" || decision == "ambiguous" {
          XCTFail("Registration issuer address is changed or ambiguous"); return false
        }
        if decision == "reveal" {
          guard collapsed.element.isEnabled, collapsed.element.isHittable else {
            Thread.sleep(forTimeInterval: 0.1); continue
          }
          // Keyboard pill -> collapsed field -> full URL are distinct observed
          // states. Tap each supported type once; never retry an unchanged state.
          collapsed.element.tap(); revealedTypes.insert(collapsedType)
        }
        Thread.sleep(forTimeInterval: 0.1)
      }
      XCTFail("Registration issuer address is unavailable"); return false
    }
    func fields(_ scope: XCUIElement, _ labels: [String], secure: Bool = false) -> XCUIElementQuery {
      let query = secure ? scope.secureTextFields : scope.textFields
      return query.matching(NSPredicate(format: "label IN %@ OR identifier IN %@", labels, labels))
    }
    func fill(_ scope: XCUIElement, _ labels: [String], _ text: String, secure: Bool = false) -> Bool {
      guard requireIssuerAddress() else { return false }
      let query = fields(scope, labels, secure: secure)
      guard query.count == 1, query.element.isHittable else { return false }
      if !secure && query.element.value as? String == text { return true }
      query.element.tap()
      if secure {
        // AutoFill can appear just after focus, before the first typed character.
        _ = safari.otherElements.matching(identifier: "SFAutoFillInputView").firstMatch
          .waitForExistence(timeout: min(1, max(0, deadline.timeIntervalSinceNow)))
        guard declineStrongPasswordSuggestion(), Date() < deadline else { return false }
      }
      query.element.typeText(text)
      return secure || query.element.value as? String == text
    }
    func providerOutcome(_ page: XCUIElement) -> String {
      registrationProviderOutcome(
        expiredCount: page.staticTexts.matching(NSPredicate(format: "label == %@", "Your login attempt timed out. Login will start from the beginning.")).count,
        failureCount: page.staticTexts.matching(NSPredicate(format: "label IN %@", ["Your connection is not private", "This Connection Is Not Private", "Diese Verbindung ist nicht privat", "Invalid username or password.", "Ungültiger Benutzername oder Passwort."])).count,
        verificationCount: page.staticTexts.matching(NSPredicate(format: "label IN %@", ["Verify your email", "Email verification", "E-Mail verifizieren", "E-Mail verifizieren lassen"])).count,
        completionCount: page.staticTexts.matching(NSPredicate(format: "label IN %@", ["Your account has been updated.", "Your email address has been verified.", "Ihr Benutzerkonto wurde aktualisiert.", "Ihre E-Mail-Adresse wurde verifiziert."])).count,
        activeForm: page.secureTextFields.count > 0 || page.buttons.matching(NSPredicate(format: "label IN %@ OR identifier IN %@", ["Register", "Registrieren"], ["kc-register"])).count > 0)
    }
    while Date() < deadline {
      let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
      let appPrompts = safari.alerts.matching(NSPredicate(format: "label IN %@", Self.passwordSaveTitles))
      let systemPrompts = springboard.alerts.matching(NSPredicate(format: "label IN %@", Self.passwordSaveTitles))
      if appPrompts.count + systemPrompts.count > 0 {
        let appDeclines = appPrompts.buttons.matching(NSPredicate(format: "label IN %@", Self.passwordDeclineLabels))
        let systemDeclines = systemPrompts.buttons.matching(NSPredicate(format: "label IN %@", Self.passwordDeclineLabels))
        guard let selected = Self.uniquePasswordDeclineSurface(appPromptCount: appPrompts.count, systemPromptCount: systemPrompts.count,
          appDeclineCount: appDeclines.count, systemDeclineCount: systemDeclines.count) else { XCTFail("Password-save prompt is ambiguous"); return }
        let decline = selected == 0 ? appDeclines.element : systemDeclines.element
        guard decline.isHittable else { XCTFail("Password-save decline is unavailable"); return }
        decline.tap(); continue
      }
      let web = safari.webViews
      let roots = web.allElementsBoundByIndex.filter { $0.webViews.count == 0 }
      if roots.count != 1 { Thread.sleep(forTimeInterval: 0.25); continue }
      let page = roots[0]
      // Passive observations do not repeatedly enter Safari's address editor.
      // Fresh authority is required before every mutation and terminal result.
      let outcome = providerOutcome(page)
      if outcome == "ambiguous" { XCTFail("Registration provider state is ambiguous"); return }
      if outcome != "wait" {
        guard requireIssuerAddress() else { return }
        let currentWeb = safari.webViews
        let currentRoots = currentWeb.allElementsBoundByIndex.filter { $0.webViews.count == 0 }
        guard Date() < deadline else { break }
        if currentRoots.count != 1 || providerOutcome(currentRoots[0]) != outcome { continue }
        if outcome == "expired" {
          print("NATIVE_IOS_REGISTRATION_RESULT status=incomplete stage=authentication-session-expired supportSafe=true"); return
        }
        if outcome == "provider-failure" { XCTFail("Registration trust or provider validation failed"); return }
        if outcome == "verification" {
          print("NATIVE_IOS_REGISTRATION_RESULT status=incomplete stage=email-verification-required supportSafe=true"); return
        }
        if outcome == "complete" {
          print("NATIVE_IOS_REGISTRATION_RESULT status=passed stage=registration-complete supportSafe=true"); return
        }
      }
      let register = page.buttons.matching(NSPredicate(format: "label IN %@ OR identifier IN %@", ["Register", "Registrieren"], ["kc-register"]))
      let usernames = fields(page, ["Username", "Benutzername", "username"])
      let passwords = fields(page, ["Password", "Passwort", "password"], secure: true)
      let confirmations = fields(page, ["Confirm password", "Passwort bestätigen", "password-confirm"], secure: true)
      let newPasswords = fields(page, ["New password", "New Password", "Neues Passwort", "password-new"], secure: true)
      let updateSubmit = page.buttons.matching(NSPredicate(format: "label IN %@", ["Submit", "Absenden"]))
      let step = registrationFormStep(registerCount: register.count, usernameCount: usernames.count,
        passwordCount: passwords.count, confirmationCount: confirmations.count, newPasswordCount: newPasswords.count,
        submitCount: updateSubmit.count, registrationSubmitted: registrationSubmitted, passwordSubmitted: passwordSubmitted)
      if step == "ambiguous" { XCTFail("Registration required-action form is incomplete or ambiguous"); return }
      if step == "profile-deferred-password" || step == "profile-with-password" {
        if usernames.count == 1 && !fill(page, ["Username", "Benutzername", "username"], email) {
          XCTFail("Registration username field is unavailable"); return
        }
        guard fill(page, ["First name", "Vorname", "firstName"], name[0]),
              fill(page, ["Last name", "Nachname", "lastName"], name.count > 1 ? name[1] : ""),
              fields(page, ["Email", "E-Mail", "email"]).count == 1,
              (fields(page, ["Email", "E-Mail", "email"]).element.value as? String) == email else {
          XCTFail("Registration profile form is unavailable or ambiguous"); return
        }
        if step == "profile-with-password" {
          guard fill(page, ["Password", "Passwort", "password"], password, secure: true),
                fill(page, ["Confirm password", "Passwort bestätigen", "password-confirm"], password, secure: true) else {
            XCTFail("Registration password pair is unavailable"); return
          }
        }
        guard requireIssuerAddress() else { return }
        guard register.count == 1, register.element.isEnabled, register.element.isHittable else {
          XCTFail("Registration submit is unavailable or ambiguous"); return
        }
        register.element.tap(); registrationSubmitted = true
      } else if step == "update-password" {
        guard fill(page, ["New password", "New Password", "Neues Passwort", "password-new"], password, secure: true),
              fill(page, ["Confirm password", "Passwort bestätigen", "password-confirm"], password, secure: true),
              requireIssuerAddress() else {
          XCTFail("Registration update-password action is unavailable"); return
        }
        guard updateSubmit.count == 1, updateSubmit.element.isEnabled, updateSubmit.element.isHittable else {
          XCTFail("Registration update-password submit is unavailable or ambiguous"); return
        }
        updateSubmit.element.tap(); passwordSubmitted = true
      } else if !invitationAccepted {
        let accept = page.buttons.matching(NSPredicate(format: "label IN %@", ["Accept", "Annehmen"]))
        if accept.count == 1 && accept.element.isHittable {
          guard requireIssuerAddress() else { return }
          accept.element.tap(); invitationAccepted = true
        }
      }
      Thread.sleep(forTimeInterval: 0.25)
    }
    XCTFail(passwordSubmitted ? "Registration password was submitted but completion is unverified" :
      "Approved test-member registration did not reach a verified completion state")
  }

  func testProductionBrowser() throws {
    continueAfterFailure = false
    print("NATIVE_IOS_BROWSER_STAGE phase=driver-started")
    // XCTest activities can contain typed input. The runner keeps the entire
    // bundle, transcript and xcresult private and removes them after this run.
    let fixture = try JSONDecoder().decode(Fixture.self, from: nativeFixtureData())
    if let origin = fixture.ordinaryOrganizationOrigin {
      let url = try XCTUnwrap(URLComponents(string: origin))
      guard url.scheme == "https", url.host != nil, url.user == nil, url.password == nil,
            url.query == nil, url.fragment == nil, ["", "/"].contains(url.path),
            fixture.appBundleIdentifier == "com.massimotter.weave",
            !fixture.issuerHost.isEmpty, !fixture.issuerHost.contains("/") else {
        throw NSError(domain: "ordinary-smoke-invalid-auth-origin", code: 1)
      }
    } else {
      XCTAssertEqual(fixture.issuerHost, "auth.weave.localhost")
    }
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
