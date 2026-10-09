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

guard CommandLine.arguments.count == 2,
      let data = try? FileHandle(forReadingFrom: URL(fileURLWithPath: CommandLine.arguments[1])).readToEnd(),
      let fixture = try? JSONDecoder().decode(Fixture.self, from: data),
      fixture.issuerHost == "auth.weave.localhost",
      fixture.issuerAuthority.hasPrefix("auth.weave.localhost:"),
      fixture.appBundleIdentifier.contains("."),
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
  for app in NSWorkspace.shared.runningApplications
    where app.bundleIdentifier == "com.apple.UserNotificationCenter" {
    let root = AXUIElementCreateApplication(app.processIdentifier)
    for window in attribute(root, kAXWindowsAttribute) as? [AXUIElement] ?? [] {
      let elements = descendants(window, limit: 100)
      guard elements.contains(where: {
        textValues($0).contains { text in
          text.contains(fixture.issuerHost) && text.localizedCaseInsensitiveContains("weave")
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
      print("NATIVE_MACOS_CONSENT_RESULT status=accepted")
      return true
    }
  }
  return false
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

let deadline = Date().addingTimeInterval(180)
var consentHandled = false
var usernameSubmitted = false
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
    guard fill(passwordField, with: fixture.password) else { fail("password-field") }
    _ = RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.5))
    guard let submit = signInButton(in: surface.elements) else { fail("password-submit") }
    guard AXUIElementPerformAction(submit, kAXPressAction as CFString) == .success else {
      fail("password-press")
    }
    print("NATIVE_AUTH_STAGE phase=password-submitted")
    print("NATIVE_APP_AUTH_DRIVER_RESULT status=passed stage=form-submitted")
    exit(0)
  }
  if usernameSubmitted,
     focusFieldViaKeyboard(surface, stage: "password") != nil {
    guard typeTargeted(fixture.password, to: surface.safariPID),
          postKey(to: surface.safariPID, code: 36) else { fail("password-targeted-input") }
    print("NATIVE_AUTH_STAGE phase=password-submitted")
    print("NATIVE_APP_AUTH_DRIVER_RESULT status=passed stage=form-submitted")
    exit(0)
  }
  if !usernameSubmitted,
     let usernameField = field(in: surface.elements, stage: "username") {
    usernameFieldObserved = true
    guard fill(usernameField, with: fixture.email) else { fail("username-field") }
    guard waitForUsernameReadback(usernameField, expected: fixture.email) else {
      fail("username-readback")
    }
    guard let submit = signInButton(in: surface.elements) else { fail("username-submit") }
    guard AXUIElementPerformAction(submit, kAXPressAction as CFString) == .success else {
      fail("username-press")
    }
    usernameSubmitted = true
    print("NATIVE_AUTH_STAGE phase=username-submitted")
  }
  if !usernameSubmitted,
     let usernameField = focusFieldViaKeyboard(surface, stage: "username") {
    usernameFieldObserved = true
    guard typeTargeted(fixture.email, to: surface.safariPID),
          waitForUsernameReadback(usernameField, expected: fixture.email),
          postKey(to: surface.safariPID, code: 36) else { fail("username-targeted-input") }
    usernameSubmitted = true
    print("NATIVE_AUTH_STAGE phase=username-submitted")
  }
  _ = RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.5))
}
if !appObserved { fail("app-not-observed") }
if !issuerHostObserved { fail("issuer-host-not-observed") }
if !issuerObserved { fail("issuer-not-observed") }
if !usernameFieldObserved { fail("username-not-observed") }
fail("password-not-observed")
