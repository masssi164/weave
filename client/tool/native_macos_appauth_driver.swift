// Operate the real macOS system-browser page opened by Flutter AppAuth.
// A private FIFO supplies a disposable member. No token or callback is injected
// into Flutter; its integration test remains the product and PKCE oracle.
import AppKit
import ApplicationServices
import CoreGraphics
import Foundation

struct Fixture: Decodable {
  let email: String
  let password: String
  let issuerHost: String
  let issuerAuthority: String
  let appBundleIdentifier: String
  let appExecutable: String
  let nativeAuthCompletionPath: String
}

func attribute(_ element: AXUIElement, _ key: String) -> CFTypeRef? {
  var result: CFTypeRef?
  return AXUIElementCopyAttributeValue(element, key as CFString, &result) == .success
    ? result : nil
}

func textValues(_ element: AXUIElement) -> [String] {
  [kAXTitleAttribute, kAXValueAttribute, kAXDescriptionAttribute].compactMap {
    guard let text = attribute(element, $0) as? String, !text.isEmpty else { return nil }
    return text
  }
}

func label(_ element: AXUIElement) -> String? {
  textValues(element).first
}

func descendants(_ root: AXUIElement, limit: Int = 400) -> [AXUIElement] {
  var elements = [root]
  var index = 0
  while index < elements.count && elements.count < limit {
    if let children = attribute(elements[index], kAXChildrenAttribute) as? [AXUIElement] {
      elements.append(contentsOf: children.prefix(limit - elements.count))
    }
    index += 1
  }
  return elements
}

func fail(_ stage: String) -> Never {
  print("NATIVE_APP_AUTH_DRIVER_RESULT status=failed stage=\(stage)")
  exit(1)
}

// Runs before Accessibility checks and never reads preference values.
if CommandLine.arguments.count > 1 && CommandLine.arguments[1] == "--clear-owned-preferences" {
  guard CommandLine.arguments.count == 4,
        CommandLine.arguments[2] == "com.masssi164.weave.nativeacceptance",
        CommandLine.arguments[3].range(of: #"\A[A-Za-z0-9][A-Za-z0-9._-]{0,39}\z"#,
          options: .regularExpression) != nil else { fail("owned-preferences-input") }
  func ownedAttributes(_ path: String) -> [FileAttributeKey: Any]? {
    do { return try FileManager.default.attributesOfItem(atPath: path) }
    catch let error as NSError {
      guard error.domain == NSCocoaErrorDomain &&
        [NSFileNoSuchFileError, NSFileReadNoSuchFileError].contains(error.code) else {
        fail("owned-preferences-container")
      }
      return nil
    }
  }
  let bundle = CommandLine.arguments[2]
  let runID = CommandLine.arguments[3]
  let prefix = "weave.native-acceptance.\(runID.utf8.count):\(runID)."
  var domains = [bundle]
  let home = FileManager.default.homeDirectoryForCurrentUser
  let container = home.appendingPathComponent("Library/Containers/" + bundle)
  if ownedAttributes(container.path) != nil {
    var path = home
    for component in ["Library", "Containers", bundle, "Data", "Library", "Preferences"] {
      path.appendPathComponent(component)
      guard let attributes = ownedAttributes(path.path),
            attributes[.type] as? FileAttributeType == .typeDirectory,
            (attributes[.ownerAccountID] as? NSNumber)?.uint32Value == getuid(),
            FileManager.default.isReadableFile(atPath: path.path),
            FileManager.default.isWritableFile(atPath: path.path) else {
        fail("owned-preferences-container")
      }
    }
    let domainPath = path.appendingPathComponent(bundle).path
    let file = domainPath + ".plist"
    if let attributes = ownedAttributes(file) {
      guard
            attributes[.type] as? FileAttributeType == .typeRegular,
            (attributes[.ownerAccountID] as? NSNumber)?.uint32Value == getuid() else {
        fail("owned-preferences-container")
      }
    }
    domains.append(domainPath)
  }
  for value in domains {
    let domain = value as CFString
    let keys = CFPreferencesCopyKeyList(domain, kCFPreferencesCurrentUser,
      kCFPreferencesAnyHost) as? [String] ?? []
    for key in keys where key.hasPrefix(prefix) {
      CFPreferencesSetValue(key as CFString, nil, domain,
        kCFPreferencesCurrentUser, kCFPreferencesAnyHost)
    }
    guard CFPreferencesSynchronize(domain, kCFPreferencesCurrentUser,
      kCFPreferencesAnyHost) else { fail("owned-preferences-synchronize") }
  }
  print("NATIVE_PREFERENCES_CLEANUP_RESULT status=passed scope=owned-run")
  exit(0)
}

if CommandLine.arguments.count == 2 && CommandLine.arguments[1] == "--check-accessibility" {
  let trusted = AXIsProcessTrusted()
  print("NATIVE_ACCESSIBILITY_RESULT status=\(trusted ? "passed" : "denied")")
  exit(trusted ? 0 : 1)
}

func acceptWeaveConsent(issuerHost: String) -> Bool {
  for app in NSWorkspace.shared.runningApplications
    where app.bundleIdentifier == "com.apple.UserNotificationCenter" {
    let root = AXUIElementCreateApplication(app.processIdentifier)
    for window in attribute(root, kAXWindowsAttribute) as? [AXUIElement] ?? [] {
      let elements = descendants(window, limit: 100)
      guard elements.contains(where: {
        textValues($0).contains { text in
          text.contains(issuerHost) && text.localizedCaseInsensitiveContains("weave")
        }
      }) else { continue }
      let buttons = elements.filter {
        (attribute($0, kAXRoleAttribute) as? String) == kAXButtonRole as String &&
          ["Fortfahren", "Continue"].contains(label($0) ?? "")
      }
      guard buttons.count == 1 else { fail("consent-ambiguous") }
      guard AXUIElementPerformAction(buttons[0], kAXPressAction as CFString) == .success else {
        fail("consent-press")
      }
      return true
    }
  }
  return false
}

func dismissSafariPasswordPrompt(issuerAuthority: String) -> Bool {
  for app in NSWorkspace.shared.runningApplications where app.bundleIdentifier == "com.apple.Safari" {
    let root = AXUIElementCreateApplication(app.processIdentifier)
    for window in attribute(root, kAXWindowsAttribute) as? [AXUIElement] ?? [] {
      let elements = descendants(window)
      guard elements.contains(where: {
        textValues($0).contains { $0.contains(issuerAuthority) }
      }) else { continue }
      let later = elements.filter {
        (attribute($0, kAXRoleAttribute) as? String) == kAXButtonRole as String &&
          ["Später", "Later"].contains(label($0) ?? "")
      }
      guard later.count <= 1 else { fail("password-prompt-ambiguous") }
      guard let button = later.first else { return false }
      guard AXUIElementPerformAction(button, kAXPressAction as CFString) == .success else {
        fail("password-prompt-dismiss")
      }
      return true
    }
  }
  return false
}

if CommandLine.arguments.count == 5 && CommandLine.arguments[1] == "--dismiss-password-prompt" {
  guard AXIsProcessTrusted() else { fail("accessibility-permission") }
  let bundleId = CommandLine.arguments[2]
  let issuerAuthority = CommandLine.arguments[3]
  let executable = CommandLine.arguments[4]
  guard bundleId.contains("."), issuerAuthority.hasPrefix("auth.weave.localhost:"),
        !executable.isEmpty,
        NSWorkspace.shared.runningApplications.contains(where: {
          $0.bundleIdentifier == bundleId &&
            $0.executableURL?.resolvingSymlinksInPath().path == executable
        }) else { fail("password-prompt-fixture") }
  let dismissed = dismissSafariPasswordPrompt(issuerAuthority: issuerAuthority)
  print("NATIVE_SAFARI_PASSWORD_PROMPT_RESULT status=\(dismissed ? "dismissed" : "absent")")
  exit(0)
}

if CommandLine.arguments.count == 5 && CommandLine.arguments[1] == "--accept-next-consent" {
  guard AXIsProcessTrusted() else { fail("accessibility-permission") }
  let bundleId = CommandLine.arguments[2]
  let issuerAuthority = CommandLine.arguments[3]
  let executable = CommandLine.arguments[4]
  guard bundleId.contains("."), issuerAuthority.hasPrefix("auth.weave.localhost:"),
        !executable.isEmpty else { fail("logout-fixture") }
  let registrationDeadline = Date().addingTimeInterval(10)
  var registered = false
  var waitedForRegistration = false
  while Date() < registrationDeadline {
    registered = NSWorkspace.shared.runningApplications.contains(where: {
      $0.bundleIdentifier == bundleId &&
        $0.executableURL?.resolvingSymlinksInPath().path == executable
    })
    if registered { break }
    waitedForRegistration = true
    _ = RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.1))
  }
  guard registered else { fail("logout-app-registration-timeout") }
  print("NATIVE_LOGOUT_APP_REGISTRATION_RESULT status=passed waited=\(waitedForRegistration)")
  let deadline = Date().addingTimeInterval(720)
  while Date() < deadline {
    guard NSWorkspace.shared.runningApplications.contains(where: {
      $0.bundleIdentifier == bundleId &&
        $0.executableURL?.resolvingSymlinksInPath().path == executable
    }) else { fail("app-stopped-before-logout-consent") }
    if acceptWeaveConsent(issuerHost: "auth.weave.localhost") {
      print("NATIVE_LOGOUT_CONSENT_RESULT status=accepted")
      exit(0)
    }
    _ = RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.5))
  }
  fail("logout-consent-timeout")
}

if CommandLine.arguments.count == 3 && CommandLine.arguments[1] == "--clear-stale-consent" {
  guard AXIsProcessTrusted() else { fail("accessibility-permission") }
  let bundleId = CommandLine.arguments[2]
  guard !NSWorkspace.shared.runningApplications.contains(where: {
    $0.bundleIdentifier == bundleId
  }) else { fail("app-running-during-cleanup") }
  var cleared = false
  for app in NSWorkspace.shared.runningApplications
    where app.bundleIdentifier == "com.apple.UserNotificationCenter" {
    let root = AXUIElementCreateApplication(app.processIdentifier)
    for window in attribute(root, kAXWindowsAttribute) as? [AXUIElement] ?? [] {
      let elements = descendants(window, limit: 100)
      guard elements.contains(where: {
        textValues($0).contains {
          $0.contains("auth.weave.localhost") && $0.localizedCaseInsensitiveContains("weave")
        }
      }) else { continue }
      let cancel = elements.filter {
        (attribute($0, kAXRoleAttribute) as? String) == kAXButtonRole as String &&
          ["Abbrechen", "Cancel"].contains(label($0) ?? "")
      }
      guard cancel.count == 1 else { fail("stale-consent-ambiguous") }
      guard AXUIElementPerformAction(cancel[0], kAXPressAction as CFString) == .success else {
        fail("stale-consent-cancel")
      }
      cleared = true
    }
  }
  var staleBrowserClosed = false
  for app in NSWorkspace.shared.runningApplications
    where app.bundleIdentifier == "com.apple.Safari" {
    let root = AXUIElementCreateApplication(app.processIdentifier)
    for window in attribute(root, kAXWindowsAttribute) as? [AXUIElement] ?? [] {
      guard descendants(window).contains(where: {
              textValues($0).contains { $0.contains("auth.weave.localhost:") }
            }) else { continue }
      guard let close = attribute(window, kAXCloseButtonAttribute),
            CFGetTypeID(close) == AXUIElementGetTypeID(),
            AXUIElementPerformAction(close as! AXUIElement, kAXPressAction as CFString) == .success else {
        fail("stale-browser-close")
      }
      staleBrowserClosed = true
    }
  }
  print("NATIVE_STALE_CONSENT_RESULT status=\(cleared ? "cleared" : "absent")")
  print("NATIVE_STALE_BROWSER_RESULT status=\(staleBrowserClosed ? "closed" : "absent")")
  exit(0)
}

// BEGIN ADMIN PREPARATION RECEIPT REDUCER
func adminPreparationReceiptOrigin(importedHere: Bool, receiptNonce: String, requestedNonce: String) -> String {
  if importedHere { return receiptNonce == requestedNonce ? "fresh" : "reject" }
  return receiptNonce == requestedNonce ? "reject" : "refresh"
}

func adminPreparationReceiptMayComplete(operation: String, importedHere: Bool, refreshRequested: Bool,
  proofImported: Bool, receiptNonce: String, requestedNonce: String, stage: String) -> Bool {
  let fresh = refreshRequested || adminPreparationReceiptOrigin(importedHere: importedHere,
    receiptNonce: receiptNonce, requestedNonce: requestedNonce) == "fresh"
  guard fresh, receiptNonce == requestedNonce else { return false }
  if operation == "invitations" { return ["invitations-ready", "grants-ready"].contains(stage) }
  return operation == "grants" && proofImported && stage == "grants-ready"
}
// END ADMIN PREPARATION RECEIPT REDUCER

// The explicit operator path drives only ordinary visible Admin Console controls.
// Owner credentials, browser storage and tokens are never inspected.
if CommandLine.arguments.count == 2 && CommandLine.arguments[1] == "--dogfood-admin-preparation" {
  func result(_ status: String, _ boundary: String, _ code: Int32) -> Never {
    print("DOGFOOD_ADMIN_PREPARATION_RESULT status=\(status) boundary=\(boundary) supportSafe=true")
    exit(code)
  }
  let unavailable = "preparation-state-unverified"
  let input = FileHandle.standardInput.readDataToEndOfFile()
  guard input.count <= 65536,
        let request = try? JSONSerialization.jsonObject(with: input) as? [String: Any],
        let operation = request["operation"] as? String, ["invitations", "grants"].contains(operation),
        Set(request.keys) == Set(operation == "grants"
          ? ["schemaVersion", "operation", "organizationOrigin", "issuerUrl", "plan", "proof", "receiptOutputPath", "timeoutSeconds"]
          : ["schemaVersion", "operation", "organizationOrigin", "issuerUrl", "plan", "receiptOutputPath", "timeoutSeconds"]),
        request["schemaVersion"] as? String == "weave.dogfood-admin-driver-input/v1",
        let origin = request["organizationOrigin"] as? String,
        let organization = URLComponents(string: origin), organization.scheme == "https",
        let authority = organization.host, organization.user == nil, organization.password == nil,
        organization.path.isEmpty, organization.query == nil, organization.fragment == nil,
        let issuer = request["issuerUrl"] as? String,
        let issuerURL = URLComponents(string: issuer), issuerURL.scheme == "https", issuerURL.host != nil,
        issuerURL.user == nil, issuerURL.password == nil, issuerURL.query == nil, issuerURL.fragment == nil,
        let plan = request["plan"] as? [String: Any],
        plan["organizationOrigin"] as? String == origin, plan["issuerUrl"] as? String == issuer,
        let planID = plan["planId"] as? String, planID.range(of: #"\A[a-f0-9]{32}\z"#, options: .regularExpression) != nil,
        let nonce = (operation == "grants" ? (request["proof"] as? [String: Any])?["nonce"] : plan["nonce"]) as? String,
        nonce.range(of: #"\A[a-f0-9]{32}\z"#, options: .regularExpression) != nil,
        let output = request["receiptOutputPath"] as? String, output.hasPrefix("/"), output.utf8.count <= 4096,
        let seconds = request["timeoutSeconds"] as? Int, (1...180).contains(seconds),
        let planData = try? JSONSerialization.data(withJSONObject: plan, options: [.sortedKeys]), planData.count <= 16384,
        let planText = String(data: planData, encoding: .utf8) else { result("failed", unavailable, 1) }
  guard Set(plan.keys) == Set(["schemaVersion", "planId", "nonce", "organizationOrigin", "apiOrigin", "issuerUrl", "spaceRef", "members"]),
        plan["schemaVersion"] as? String == "weave.dogfood-admin-preparation-plan/v1", plan["spaceRef"] as? String == "workspace-default",
        let plannedMembers = plan["members"] as? [[String: Any]], plannedMembers.count == 2,
        Set(plannedMembers.compactMap { $0["slot"] as? String }) == Set(["A", "B"]),
        Set(plannedMembers.compactMap { ($0["email"] as? String)?.lowercased() }).count == 2,
        plannedMembers.allSatisfy({ member in
          Set(member.keys) == Set(["slot", "email", "displayName", "role", "spaceRole", "invitationIdempotencyKey"])
            && member["role"] as? String == "member" && member["spaceRole"] as? String == "EDIT"
            && member.values.allSatisfy { value in
              guard let text = value as? String else { return false }
              return !text.isEmpty && text.utf8.count <= 1024 && !text.unicodeScalars.contains { CharacterSet.controlCharacters.contains($0) }
            }
        }) else { result("failed", unavailable, 1) }
  if operation == "grants" {
    guard let proof = request["proof"] as? [String: Any],
          Set(proof.keys) == Set(["schemaVersion", "planId", "nonce", "members"]),
          proof["schemaVersion"] as? String == "weave.dogfood-admin-preparation-proof/v1", proof["planId"] as? String == planID,
          let members = proof["members"] as? [[String: Any]], members.count == 2,
          Set(members.compactMap { $0["slot"] as? String }) == Set(["A", "B"]),
          members.allSatisfy({ member in
            Set(member.keys) == Set(["slot", "accountDigest", "organizationDigest"])
              && ["accountDigest", "organizationDigest"].allSatisfy { key in
                (member[key] as? String)?.range(of: #"\A[a-f0-9]{64}\z"#, options: .regularExpression) != nil
              }
          }) else { result("failed", unavailable, 1) }
  }
  guard AXIsProcessTrusted() else { result("incomplete", "preparation-ui-unavailable", 2) }
  let deadline = Date().addingTimeInterval(Double(seconds))
  func matching(_ elements: [AXUIElement], _ labels: [String], role: String) -> [AXUIElement] {
    elements.filter { attribute($0, kAXRoleAttribute) as? String == role && !Set(textValues($0)).isDisjoint(with: labels) }
  }
  func surface() -> [AXUIElement]? {
    var matches = [[AXUIElement]]()
    for app in NSWorkspace.shared.runningApplications where app.bundleIdentifier == "com.apple.Safari" {
      let root = AXUIElementCreateApplication(app.processIdentifier)
      for window in attribute(root, kAXWindowsAttribute) as? [AXUIElement] ?? [] {
        let elements = descendants(window, limit: 1600)
        let valid = elements.contains { element in
          guard attribute(element, kAXRoleAttribute) as? String == "AXWebArea",
                let value = attribute(element, kAXURLAttribute),
                let url = URLComponents(string: String(describing: value)) else { return false }
          return url.scheme == "https" && url.host == authority && url.port == organization.port
            && url.user == nil && url.password == nil && url.path == "/admin-console/" && url.query == nil && url.fragment == nil
        }
        if valid { matches.append(elements) }
      }
    }
    return matches.count == 1 ? matches[0] : nil
  }
  func press(_ elements: [AXUIElement], _ labels: [String]) -> Bool {
    let buttons = matching(elements, labels, role: kAXButtonRole) + matching(elements, labels, role: "AXDisclosureTriangle")
    return buttons.count == 1 && attribute(buttons[0], kAXEnabledAttribute) as? Bool == true
      && AXUIElementPerformAction(buttons[0], kAXPressAction as CFString) == .success
  }
  func setText(_ elements: [AXUIElement], _ labels: [String], _ value: String) -> Bool {
    let fields = matching(elements, labels, role: kAXTextAreaRole) + matching(elements, labels, role: kAXTextFieldRole)
    guard fields.count == 1, attribute(fields[0], kAXEnabledAttribute) as? Bool == true else { return false }
    var pid: pid_t = 0
    guard AXUIElementGetPid(fields[0], &pid) == .success,
          let safari = NSRunningApplication(processIdentifier: pid), safari.bundleIdentifier == "com.apple.Safari",
          safari.activate(options: [.activateIgnoringOtherApps]),
          AXUIElementSetAttributeValue(fields[0], kAXFocusedAttribute as CFString, kCFBooleanTrue) == .success,
          let down = CGEvent(keyboardEventSource: nil, virtualKey: 0, keyDown: true),
          let up = CGEvent(keyboardEventSource: nil, virtualKey: 0, keyDown: false) else { return false }
    down.flags = .maskCommand; up.flags = .maskCommand
    down.postToPid(pid); up.postToPid(pid)
    guard postKey(to: pid, code: 51), typeTargeted(value, to: pid) else { return false }
    let inputDeadline = min(deadline, Date().addingTimeInterval(10))
    while Date() < inputDeadline {
      if attribute(fields[0], kAXValueAttribute) as? String == value { return true }
      _ = RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.05))
    }
    return false
  }
  func writePrivate(_ data: Data) -> Bool {
    let components = output.split(separator: "/", omittingEmptySubsequences: true).map(String.init)
    guard components.count > 1, !components.contains("."), !components.contains(".."),
          "/" + components.joined(separator: "/") == output else { return false }
    var directory = open("/", O_RDONLY | O_DIRECTORY | O_NOFOLLOW)
    guard directory >= 0 else { return false }
    defer { close(directory) }
    for component in components.dropLast() {
      let child = openat(directory, component, O_RDONLY | O_DIRECTORY | O_NOFOLLOW)
      guard child >= 0 else { return false }
      close(directory); directory = child
    }
    var parentInfo = stat()
    guard fstat(directory, &parentInfo) == 0, parentInfo.st_uid == getuid(), parentInfo.st_mode & 0o777 == 0o700 else { return false }
    let file = openat(directory, components.last!, O_WRONLY | O_NOFOLLOW)
    guard file >= 0 else { return false }
    defer { close(file) }
    var info = stat()
    guard fstat(file, &info) == 0, info.st_uid == getuid(), info.st_mode & 0o777 == 0o600,
          info.st_mode & S_IFMT == S_IFREG, info.st_nlink == 1, ftruncate(file, 0) == 0 else { return false }
    do { try FileHandle(fileDescriptor: file, closeOnDealloc: false).write(contentsOf: data); return fsync(file) == 0 }
    catch { return false }
  }
  var imported = false
  var importedHere = false
  var proofImported = false
  var reviewed = false
  var refreshRequested = false
  while Date() < deadline {
    guard let elements = surface() else {
      _ = RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.25)); continue
    }
    let labels = elements.flatMap(textValues)
    if labels.contains(where: { $0.contains("Sign in with your organization account") || $0.contains("Melden Sie sich mit Ihrem Organisationskonto") }) {
      result("incomplete", "normal-administrator-authentication-required", 2)
    }
    if labels.contains(where: { $0.contains("The administrator session is no longer authorized") || $0.contains("Die Administrationssitzung ist nicht mehr berechtigt") }) {
      result("incomplete", "administrator-session-unavailable", 2)
    }
    if labels.contains(where: { $0.contains("current session does not permit") || $0.contains("could not verify current organization access")
      || $0.contains("Ihre aktuelle Sitzung erlaubt keinen Zugriff") || $0.contains("Organisationszugriff konnte nicht geprüft") }) {
      result("incomplete", "administrator-session-unavailable", 2)
    }
    let planFields = matching(elements, ["Preparation plan JSON", "Vorbereitungsplan als JSON"], role: kAXTextAreaRole)
    if planFields.count == 1, let current = attribute(planFields[0], kAXValueAttribute) as? String, !current.isEmpty,
       let parsed = try? JSONSerialization.jsonObject(with: Data(current.utf8)) as? [String: Any] {
      var oldIntent = parsed; oldIntent.removeValue(forKey: "nonce")
      var newIntent = plan; newIntent.removeValue(forKey: "nonce")
      if !(oldIntent as NSDictionary).isEqual(to: newIntent) { result("failed", unavailable, 1) }
      imported = true; reviewed = true
    }
    let receiptFields = matching(elements, ["Dogfood preparation receipt", "Dogfood-Vorbereitungsbeleg"], role: kAXTextAreaRole)
    if !refreshRequested && !importedHere && receiptFields.count == 1 {
      guard let text = attribute(receiptFields[0], kAXValueAttribute) as? String,
            let current = try? JSONSerialization.jsonObject(with: Data(text.utf8)) as? [String: Any],
            current["planId"] as? String == planID,
            let previousNonce = current["nonce"] as? String,
            adminPreparationReceiptOrigin(importedHere: importedHere, receiptNonce: previousNonce, requestedNonce: nonce) == "refresh",
            setText(elements, ["Preparation operation nonce", "Nonce des Vorbereitungsschritts"], nonce),
            press(elements, ["Refresh preparation status", "Vorbereitungsstatus aktualisieren"]) else {
        result("failed", unavailable, 1)
      }
      refreshRequested = true
      _ = RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.25)); continue
    }
    if receiptFields.count == 1, let text = attribute(receiptFields[0], kAXValueAttribute) as? String,
       text.utf8.count <= 16384, let receipt = try? JSONSerialization.jsonObject(with: Data(text.utf8)) as? [String: Any],
       Set(receipt.keys) == Set(["schemaVersion", "planId", "nonce", "stage", "organizationId", "administratorAccountRef", "intendedOwnerAccountRef", "ownerDisplayName", "members", "supportSafe", "containsSecretValues"]),
       receipt["schemaVersion"] as? String == "weave.dogfood-admin-preparation-receipt/v1",
       receipt["planId"] as? String == planID, receipt["nonce"] as? String == nonce,
       adminPreparationReceiptMayComplete(operation: operation, importedHere: importedHere, refreshRequested: refreshRequested,
         proofImported: proofImported, receiptNonce: receipt["nonce"] as? String ?? "", requestedNonce: nonce, stage: receipt["stage"] as? String ?? ""),
       receipt["supportSafe"] as? Bool == true, receipt["containsSecretValues"] as? Bool == false {
      guard let members = receipt["members"] as? [[String: Any]], members.count == 2,
            Set(members.compactMap { $0["slot"] as? String }) == Set(["A", "B"]),
            members.allSatisfy({ member in
              Set(member.keys).isSubset(of: Set(["slot", "email", "displayName", "memberHandle", "invitationHandle", "accountRef", "grantVersion"]))
                && ["slot", "email", "displayName"].allSatisfy { member[$0] is String }
                && member.values.allSatisfy { value in
                  guard let string = value as? String else { return false }
                  return !string.isEmpty && string.utf8.count <= 1024 && !string.contains("\n") && !string.contains("\r")
                }
            }) else { result("failed", unavailable, 1) }

      guard Date() < deadline, imported, writePrivate(Data(text.utf8)), Date() < deadline else { result("failed", unavailable, 1) }
      result("passed", "none", 0)
    }
    if !imported {
      guard operation == "invitations", setText(elements, ["Preparation plan JSON", "Vorbereitungsplan als JSON"], planText),
            press(elements, ["Review preparation plan", "Vorbereitungsplan prüfen"]) else { result("incomplete", "preparation-ui-unavailable", 2) }
      imported = true; importedHere = true; reviewed = true
    } else if operation == "grants" && refreshRequested && !proofImported {
      guard receiptFields.count == 1, let currentText = attribute(receiptFields[0], kAXValueAttribute) as? String,
            let current = try? JSONSerialization.jsonObject(with: Data(currentText.utf8)) as? [String: Any],
            current["planId"] as? String == planID, current["nonce"] as? String == nonce else {
        _ = RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.25)); continue
      }

      guard let proof = request["proof"] as? [String: Any], let data = try? JSONSerialization.data(withJSONObject: proof), data.count <= 16384,
            let text = String(data: data, encoding: .utf8),
            setText(elements, ["Independent member proof JSON", "Unabhängiger Mitgliedsnachweis als JSON"], text),
            press(elements, ["Verify member proof and grants", "Mitgliedsnachweis und Rechte prüfen"]) else {
        result("incomplete", "reviewed-test-member-plan-approval-required", 2)
      }
      proofImported = true
    }
    // A collapsed secondary receipt is an ordinary read-only detail, not consent.
    if receiptFields.isEmpty { _ = press(elements, ["Preparation receipt details", "Details zum Vorbereitungsbeleg"]) }
    if labels.contains(where: { $0.contains("Preparation could not verify the current state") || $0.contains("Der aktuelle Zustand konnte nicht geprüft werden") }) {
      result("failed", unavailable, 1)
    }
    if reviewed && !matching(elements, ["Approve preparation", "Vorbereitung genehmigen"], role: kAXButtonRole).isEmpty {
      result("incomplete", "reviewed-test-member-plan-approval-required", 2)
    }
    _ = RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.25))
  }
  result("incomplete", "preparation-ui-unavailable", 2)
}

guard CommandLine.arguments.count == 2,
      let data = try? FileHandle(forReadingFrom: URL(fileURLWithPath: CommandLine.arguments[1])).readToEnd(),
      let fixture = try? JSONDecoder().decode(Fixture.self, from: data),
      fixture.issuerHost == "auth.weave.localhost",
      fixture.issuerAuthority.hasPrefix("auth.weave.localhost:"),
      fixture.appBundleIdentifier.contains("."),
      fixture.nativeAuthCompletionPath.hasSuffix("/native-auth-established"),
      URL(fileURLWithPath: fixture.nativeAuthCompletionPath).deletingLastPathComponent()
        .lastPathComponent.hasPrefix("weave-native-acceptance-"),
      fixture.email.contains("@"), !fixture.password.isEmpty else {
  fail("fixture")
}
guard AXIsProcessTrusted() else {
  fail("accessibility-permission")
}

func expectedNativeAppIsRunning() -> Bool {
  NSWorkspace.shared.runningApplications.contains {
    $0.bundleIdentifier == fixture.appBundleIdentifier &&
      $0.executableURL?.resolvingSymlinksInPath().path == fixture.appExecutable
  }
}

func acceptSystemConsent() -> Bool {
  guard acceptWeaveConsent(issuerHost: fixture.issuerHost) else { return false }
  print("NATIVE_MACOS_CONSENT_RESULT status=accepted")
  return true
}

struct AuthSurface {
  let window: AXUIElement
  let elements: [AXUIElement]
  let safariPID: pid_t
}

var issuerHostObserved = false
func issuerWindow() -> AuthSurface? {
  for app in NSWorkspace.shared.runningApplications where app.bundleIdentifier == "com.apple.Safari" {
    let root = AXUIElementCreateApplication(app.processIdentifier)
    for window in attribute(root, kAXWindowsAttribute) as? [AXUIElement] ?? [] {
      let elements = descendants(window)
      issuerHostObserved = issuerHostObserved || elements.contains(where: {
        textValues($0).contains { $0.contains(fixture.issuerHost) }
      })
      if elements.contains(where: {
        textValues($0).contains { $0.contains(fixture.issuerAuthority) }
      }) {
        return AuthSurface(window: window, elements: elements, safariPID: app.processIdentifier)
      }
    }
  }
  return nil
}

func namedField(_ element: AXUIElement, stage: String) -> Bool {
  guard [kAXTextFieldRole as String, "AXSecureTextField"].contains(
    attribute(element, kAXRoleAttribute) as? String ?? "") else { return false }
  let name = [kAXTitleAttribute, kAXDescriptionAttribute].compactMap {
    attribute(element, $0) as? String
  }.joined(separator: " ").lowercased()
  if stage == "username" {
    return name.contains("username") || name.contains("email")
  }
  return name.contains("password") || name.contains("passwort")
}

func field(in elements: [AXUIElement], stage: String) -> AXUIElement? {
  let matches = elements.filter { namedField($0, stage: stage) }
  // Keycloak's password page also exposes a second, unlabeled text field.
  // Choose only a uniquely labeled form control in the exact IdP window.
  return matches.count == 1 ? matches[0] : nil
}

func postKey(to pid: pid_t, code: CGKeyCode) -> Bool {
  guard let down = CGEvent(keyboardEventSource: nil, virtualKey: code, keyDown: true),
        let up = CGEvent(keyboardEventSource: nil, virtualKey: code, keyDown: false) else {
    return false
  }
  down.postToPid(pid)
  up.postToPid(pid)
  return true
}

func clearTargetedField(_ input: AXUIElement, safariPID: pid_t) -> Bool {
  if AXUIElementSetAttributeValue(input, kAXValueAttribute as CFString, "" as CFString) == .success {
    return true
  }
  guard let down = CGEvent(keyboardEventSource: nil, virtualKey: 0, keyDown: true),
        let up = CGEvent(keyboardEventSource: nil, virtualKey: 0, keyDown: false) else {
    return false
  }
  down.flags = .maskCommand
  up.flags = .maskCommand
  down.postToPid(safariPID)
  up.postToPid(safariPID)
  return postKey(to: safariPID, code: 51) // Delete the selected field value.
}

func typeTargeted(_ text: String, to pid: pid_t) -> Bool {
  for unit in text.utf16 {
    var character = unit
    guard let down = CGEvent(keyboardEventSource: nil, virtualKey: 0, keyDown: true),
          let up = CGEvent(keyboardEventSource: nil, virtualKey: 0, keyDown: false) else {
      return false
    }
    down.keyboardSetUnicodeString(stringLength: 1, unicodeString: &character)
    up.keyboardSetUnicodeString(stringLength: 1, unicodeString: &character)
    down.postToPid(pid)
    up.postToPid(pid)
  }
  return true
}

func focusFieldViaKeyboard(_ surface: AuthSurface, stage: String) -> AXUIElement? {
  guard let webArea = surface.elements.first(where: {
    (attribute($0, kAXRoleAttribute) as? String) == "AXWebArea"
  }), AXUIElementPerformAction(surface.window, kAXRaiseAction as CFString) == .success,
        AXUIElementSetAttributeValue(
          webArea, kAXFocusedAttribute as CFString, kCFBooleanTrue
        ) == .success else { return nil }
  let safari = AXUIElementCreateApplication(surface.safariPID)
  for _ in 0..<8 {
    let focusedWindow = attribute(safari, kAXFocusedWindowAttribute)
    if let focusedWindow, CFEqual(focusedWindow, surface.window),
       let focused = attribute(safari, kAXFocusedUIElementAttribute),
       CFGetTypeID(focused) == AXUIElementGetTypeID(),
       namedField(focused as! AXUIElement, stage: stage) {
      return (focused as! AXUIElement)
    }
    guard postKey(to: surface.safariPID, code: 48) else { return nil } // Tab
    _ = RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.05))
  }
  return nil
}

func signInButton(in elements: [AXUIElement]) -> AXUIElement? {
  let matches = elements.filter {
    (attribute($0, kAXRoleAttribute) as? String) == kAXButtonRole as String &&
      ["Sign In", "Log in", "Anmelden"].contains(label($0) ?? "")
  }
  return matches.count == 1 ? matches[0] : nil
}

func fill(_ input: AXUIElement, with text: String) -> Bool {
  AXUIElementSetAttributeValue(input, kAXValueAttribute as CFString, text as CFString) == .success
}

func waitForUsernameReadback(_ input: AXUIElement, expected: String) -> Bool {
  for _ in 0..<20 {
    if (attribute(input, kAXValueAttribute) as? String) == expected { return true }
    _ = RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.1))
  }
  return false
}

func enterUsername(_ input: AXUIElement, in surface: AuthSurface, expected: String) -> Bool {
  // WebKit can report a successful AXValue write to an unfocused field while
  // leaving the HTML input unchanged. Focus it and verify the value before
  // submitting; use targeted keyboard events if that AX write does not stick.
  _ = AXUIElementPerformAction(surface.window, kAXRaiseAction as CFString)
  if AXUIElementSetAttributeValue(input, kAXFocusedAttribute as CFString, kCFBooleanTrue)
      == .success,
     fill(input, with: expected),
     waitForUsernameReadback(input, expected: expected) {
    return true
  }
  guard let targeted = focusFieldViaKeyboard(surface, stage: "username") else { return false }
  for _ in 0..<3 {
    guard clearTargetedField(targeted, safariPID: surface.safariPID),
          typeTargeted(expected, to: surface.safariPID) else { return false }
    if waitForUsernameReadback(targeted, expected: expected) { return true }
  }
  return false
}

func waitForNativeSession(timeout: TimeInterval) -> Bool {
  let deadline = Date().addingTimeInterval(timeout)
  while Date() < deadline {
    // The owned runner writes this private signal only after Flutter's real
    // workspace assertion. Safari may retain its window after native callback;
    // neither that window nor a missing AX field is the completion oracle.
    if let data = try? Data(contentsOf: URL(fileURLWithPath: fixture.nativeAuthCompletionPath)),
       data == Data("workspace-ready\n".utf8) { return true }
    _ = RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.1))
  }
  return false
}

func finishPasswordSubmission(method: String, promptDismissed: Bool = false) {
  // The native test may finish immediately after workspace bootstrap. Handle
  // this browser-owned prompt in the same driver rather than requiring the
  // app to remain registered for a second helper process after callback.
  let dismissed = promptDismissed || dismissSafariPasswordPrompt(issuerAuthority: fixture.issuerAuthority)
  print("NATIVE_SAFARI_PASSWORD_PROMPT_RESULT status=\(dismissed ? "dismissed" : "absent")")
  print("NATIVE_PASSWORD_SUBMISSION_RESULT status=passed method=\(method)")
  print("NATIVE_AUTH_CALLBACK_RESULT status=observed")
  print("NATIVE_AUTH_STAGE phase=password-submitted")
  print("NATIVE_APP_AUTH_DRIVER_RESULT status=passed stage=form-submitted")
}

func submitPassword(_ input: AXUIElement, in surface: AuthSurface) {
  guard fill(input, with: fixture.password) else { fail("password-field") }
  _ = RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.5))
  guard let submit = signInButton(in: surface.elements) else { fail("password-submit") }
  guard AXUIElementPerformAction(submit, kAXPressAction as CFString) == .success else {
    fail("password-press")
  }
  var method = "accessibility"
  var passwordPromptDismissed = false
  if !waitForNativeSession(timeout: 5) {
    // Safari can acknowledge AXPress without submitting an unfocused form.
    // Re-resolve the exact disposable IdP window, focus its password field,
    // and send Return only to that Safari process. Never treat AX success as
    // proof of navigation or of the native AppAuth callback.
    passwordPromptDismissed = dismissSafariPasswordPrompt(issuerAuthority: fixture.issuerAuthority)
    if passwordPromptDismissed {
      // The password sheet can retain the old page in AX after submission.
      // Dismiss it without saving credentials and await the real callback;
      // do not send Return to the stale form behind that sheet.
      print("NATIVE_PASSWORD_PROMPT_BEFORE_CALLBACK status=dismissed")
    } else if let current = issuerWindow(),
       let password = field(in: current.elements, stage: "password") {
      guard let safari = NSRunningApplication(processIdentifier: current.safariPID),
          safari.bundleIdentifier == "com.apple.Safari",
          safari.activate(options: [.activateIgnoringOtherApps]),
          AXUIElementPerformAction(current.window, kAXRaiseAction as CFString) == .success,
          AXUIElementSetAttributeValue(password, kAXFocusedAttribute as CFString, kCFBooleanTrue)
            == .success else { fail("password-focus") }
      let root = AXUIElementCreateApplication(current.safariPID)
      let focusDeadline = Date().addingTimeInterval(2)
      var focusedPassword = false
      while Date() < focusDeadline {
        if let focusedWindow = attribute(root, kAXFocusedWindowAttribute),
           CFEqual(focusedWindow, current.window),
           let focused = attribute(root, kAXFocusedUIElementAttribute),
           CFEqual(focused, password) { focusedPassword = true; break }
        _ = RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.1))
      }
      guard focusedPassword, postKey(to: current.safariPID, code: 36) else {
        fail("password-targeted-submit")
      }
      method = "targeted-keyboard"
    }
    // Flutter allows five minutes for the real callback and workspace bootstrap.
    guard waitForNativeSession(timeout: 300) else { fail("native-callback") }
  }
  finishPasswordSubmission(method: method, promptDismissed: passwordPromptDismissed)
}

let deadline = Date().addingTimeInterval(180)
var consentHandled = false
var usernameSubmitted = false
var usernameSubmittedAt: Date?
var usernameRetryCount = 0
var appObserved = false
var issuerObserved = false
var usernameFieldObserved = false
while Date() < deadline {
  guard expectedNativeAppIsRunning() else {
    _ = RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.5))
    continue
  }
  appObserved = true
  if !consentHandled {
    consentHandled = acceptSystemConsent()
  }
  guard let surface = issuerWindow() else {
    _ = RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.5))
    continue
  }
  issuerObserved = true
  if let passwordField = field(in: surface.elements, stage: "password") {
    guard usernameSubmitted else { fail("password-before-username") }
    submitPassword(passwordField, in: surface)
    exit(0)
  }
  if usernameSubmitted,
     focusFieldViaKeyboard(surface, stage: "password") != nil {
    guard typeTargeted(fixture.password, to: surface.safariPID),
          postKey(to: surface.safariPID, code: 36) else { fail("password-targeted-input") }
    guard waitForNativeSession(timeout: 300) else { fail("native-callback") }
    finishPasswordSubmission(method: "targeted-keyboard")
    exit(0)
  }
  if usernameSubmitted,
     let submittedAt = usernameSubmittedAt,
     Date().timeIntervalSince(submittedAt) > 3,
     usernameRetryCount < 3,
     let usernameField = field(in: surface.elements, stage: "username"),
     waitForUsernameReadback(usernameField, expected: fixture.email),
     let submit = signInButton(in: surface.elements) {
    guard AXUIElementPerformAction(submit, kAXPressAction as CFString) == .success else {
      fail("username-retry-press")
    }
    usernameRetryCount += 1
    usernameSubmittedAt = Date()
    print("NATIVE_AUTH_STAGE phase=username-resubmitted attempt=\(usernameRetryCount)")
  }
  if usernameSubmitted,
     let submittedAt = usernameSubmittedAt,
     Date().timeIntervalSince(submittedAt) > 3,
     usernameRetryCount < 3,
     let usernameField = focusFieldViaKeyboard(surface, stage: "username"),
     waitForUsernameReadback(usernameField, expected: fixture.email) {
    guard postKey(to: surface.safariPID, code: 36) else {
      fail("username-retry-targeted")
    }
    usernameRetryCount += 1
    usernameSubmittedAt = Date()
    print("NATIVE_AUTH_STAGE phase=username-resubmitted attempt=\(usernameRetryCount)")
  }
  if !usernameSubmitted,
     let usernameField = field(in: surface.elements, stage: "username") {
    usernameFieldObserved = true
    guard enterUsername(usernameField, in: surface, expected: fixture.email) else {
      fail("username-readback")
    }
    guard let submit = signInButton(in: surface.elements) else { fail("username-submit") }
    guard AXUIElementPerformAction(submit, kAXPressAction as CFString) == .success else {
      fail("username-press")
    }
    usernameSubmitted = true
    usernameSubmittedAt = Date()
    print("NATIVE_AUTH_STAGE phase=username-submitted")
  }
  if !usernameSubmitted,
     let usernameField = focusFieldViaKeyboard(surface, stage: "username") {
    usernameFieldObserved = true
    guard enterUsername(usernameField, in: surface, expected: fixture.email),
          postKey(to: surface.safariPID, code: 36) else {
      fail("username-targeted-input")
    }
    usernameSubmitted = true
    usernameSubmittedAt = Date()
    print("NATIVE_AUTH_STAGE phase=username-submitted")
  }
  _ = RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.5))
}
if !appObserved { fail("app-not-observed") }
if !issuerHostObserved { fail("issuer-host-not-observed") }
if !issuerObserved { fail("issuer-not-observed") }
if !usernameFieldObserved { fail("username-not-observed") }
fail("password-not-observed")
