// Operate the real macOS system-browser page opened by Flutter AppAuth.
// A private FIFO supplies a disposable member. No token or callback is injected
// into Flutter; its integration test remains the product and PKCE oracle.
import AppKit
import ApplicationServices
import Foundation

struct Fixture: Decodable {
  let email: String
  let password: String
  let issuerHost: String
  let issuerAuthority: String
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

guard CommandLine.arguments.count == 2,
      let data = try? FileHandle(forReadingFrom: URL(fileURLWithPath: CommandLine.arguments[1])).readToEnd(),
      let fixture = try? JSONDecoder().decode(Fixture.self, from: data),
      fixture.issuerHost == "auth.weave.localhost",
      fixture.issuerAuthority.hasPrefix("auth.weave.localhost:"),
      fixture.email.contains("@"), !fixture.password.isEmpty else {
  fail("fixture")
}
guard AXIsProcessTrusted() else {
  fail("accessibility-permission")
}

func expectedNativeAppIsRunning() -> Bool {
  NSWorkspace.shared.runningApplications.contains {
    $0.bundleIdentifier == "com.example.weave" &&
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

var issuerHostObserved = false
func issuerWindow() -> [AXUIElement]? {
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
        return elements
      }
    }
  }
  return nil
}

func field(in elements: [AXUIElement], stage: String) -> AXUIElement? {
  let matches = elements.filter {
    [kAXTextFieldRole as String, "AXSecureTextField"].contains(
      attribute($0, kAXRoleAttribute) as? String ?? "")
  }
  // The AppAuth Safari window has one Keycloak form field at either step.
  // A browser address/search field in another window is never selected.
  guard matches.count == 1 else { return nil }
  let text = elements.flatMap(textValues).joined(separator: " ").lowercased()
  if stage == "username" && (text.contains("username") || text.contains("email")) {
    return matches[0]
  }
  if stage == "password" && text.contains("password") {
    return matches[0]
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
  guard let elements = issuerWindow() else {
    _ = RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.5))
    continue
  }
  issuerObserved = true
  if let passwordField = field(in: elements, stage: "password") {
    guard usernameSubmitted else { fail("password-before-username") }
    guard fill(passwordField, with: fixture.password) else { fail("password-field") }
    guard let submit = signInButton(in: elements) else { fail("password-submit") }
    guard AXUIElementPerformAction(submit, kAXPressAction as CFString) == .success else {
      fail("password-press")
    }
    print("NATIVE_AUTH_STAGE phase=password-submitted")
    print("NATIVE_APP_AUTH_DRIVER_RESULT status=passed stage=form-submitted")
    exit(0)
  }
  if !usernameSubmitted, let usernameField = field(in: elements, stage: "username") {
    usernameFieldObserved = true
    guard fill(usernameField, with: fixture.email) else { fail("username-field") }
    guard (attribute(usernameField, kAXValueAttribute) as? String) == fixture.email else {
      fail("username-readback")
    }
    guard let submit = signInButton(in: elements) else { fail("username-submit") }
    guard AXUIElementPerformAction(submit, kAXPressAction as CFString) == .success else {
      fail("username-press")
    }
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
