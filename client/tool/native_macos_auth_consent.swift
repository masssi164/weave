// Handle only the first-use macOS ASWebAuthenticationSession consent for the
// disposable Weave IdP. This helper runs beside XCTest because the Xcode UI
// test runner can automate its target app but is not an Accessibility client.
import AppKit
import ApplicationServices
import Foundation

func attribute(_ element: AXUIElement, _ key: String) -> CFTypeRef? {
  var result: CFTypeRef?
  return AXUIElementCopyAttributeValue(element, key as CFString, &result) == .success
    ? result : nil
}

func label(_ element: AXUIElement) -> String? {
  for key in [kAXTitleAttribute, kAXValueAttribute, kAXDescriptionAttribute] {
    if let text = attribute(element, key) as? String, !text.isEmpty {
      return text
    }
  }
  return nil
}

func descendants(_ root: AXUIElement, limit: Int) -> [AXUIElement] {
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

guard CommandLine.arguments.count == 2,
      CommandLine.arguments[1] == "auth.weave.localhost" else {
  print("NATIVE_MACOS_CONSENT_RESULT status=invalid-host")
  exit(2)
}
guard AXIsProcessTrusted() else {
  print("NATIVE_MACOS_CONSENT_RESULT status=accessibility-unavailable")
  exit(0) // A previously approved app can still complete native sign-in.
}

let issuerHost = CommandLine.arguments[1]
let deadline = Date().addingTimeInterval(75)
while Date() < deadline {
  for application in NSWorkspace.shared.runningApplications
    where application.bundleIdentifier == "com.apple.UserNotificationCenter" {
    let root = AXUIElementCreateApplication(application.processIdentifier)
    let windows = attribute(root, kAXWindowsAttribute) as? [AXUIElement] ?? []
    for window in windows {
      let elements = descendants(window, limit: 100)
      guard elements.contains(where: {
        let text = label($0) ?? ""
        return text.contains(issuerHost) && text.localizedCaseInsensitiveContains("weave")
      }) else {
        continue
      }
      let buttons = elements.filter { element in
        (attribute(element, kAXRoleAttribute) as? String) == kAXButtonRole as String &&
          ["Fortfahren", "Continue"].contains(label(element) ?? "")
      }
      guard buttons.count == 1 else {
        print("NATIVE_MACOS_CONSENT_RESULT status=ambiguous-action")
        exit(1)
      }
      guard AXUIElementPerformAction(buttons[0], kAXPressAction as CFString) == .success else {
        print("NATIVE_MACOS_CONSENT_RESULT status=press-failed")
        exit(1)
      }
      print("NATIVE_MACOS_CONSENT_RESULT status=accepted")
      exit(0)
    }
  }
  Thread.sleep(forTimeInterval: 0.5)
}
print("NATIVE_MACOS_CONSENT_RESULT status=not-observed")
