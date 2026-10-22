import XCTest
import CryptoKit

// The existing XCTest runner observes the ordinary installed application. This
// fixture contains normal test credentials and run-owned content, never sessions.
extension BrowserDriver {
  struct OrdinarySmokeFixture: Decodable {
    let schemaVersion: String
    let runId: String
    let nonce: String
    let operation: String
    let organizationOrigin: String
    let conversation: String
    let participant: String
    let folder: String
    let filename: String
    let filePayload: String
    let event: String
    let expected: [String: String]
  }

  static func ordinaryWorkspaceObserved(_ app: XCUIApplication) -> Bool {
    ["nav-chat", "nav-files", "nav-calendar", "nav-settings"].allSatisfy {
      app.buttons.matching(identifier: $0).count == 1
    }
  }

  // The budget is independent of UI timing and never grows after a gesture.
  struct OrdinaryScrollBudget {
    var remaining = 8
    var nextAllowedAt = Date.distantPast

    mutating func request(now: Date) -> Bool {
      guard remaining > 0 && now >= nextAllowedAt else { return false }
      remaining -= 1
      nextAllowedAt = now.addingTimeInterval(1)
      return true
    }
  }
  // End bounded scroll budget.

  func smokeScroll(_ budget: inout OrdinaryScrollBudget, toward target: XCUIElement? = nil) throws {
    guard budget.request(now: Date()) else { return }
    let app = XCUIApplication(bundleIdentifier: "com.massimotter.weave")
    let frame = app.frame
    guard app.state == .runningForeground, frame.width.isFinite, frame.height.isFinite,
          frame.width > 0, frame.height > 0 else { throw NSError(domain: "ordinary-smoke-scroll-surface-unavailable", code: 1) }
    let keyboards = app.keyboards
    guard keyboards.count <= 1 else { throw NSError(domain: "ordinary-smoke-scroll-keyboard-ambiguous", code: 1) }
    let upper = frame.height * 0.25
    let lower = min(frame.height * 0.75, keyboards.count == 1 ? keyboards.element.frame.minY - frame.minY - 16 : frame.height * 0.75)
    guard lower.isFinite && lower - upper >= 48 else { throw NSError(domain: "ordinary-smoke-scroll-obstructed", code: 1) }
    let moveTowardTop = target.map { $0.frame.midY < frame.minY + upper } ?? false
    let origin = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0))
    let start = origin.withOffset(CGVector(dx: 0, dy: moveTowardTop ? upper : lower))
    let end = origin.withOffset(CGVector(dx: 0, dy: moveTowardTop ? lower : upper))
    start.press(forDuration: 0.05, thenDragTo: end)
  }

  func smokeElement(_ app: XCUIElement, identifier: String, labels: [String] = [], timeout: TimeInterval = 40) throws -> XCUIElement {
    let deadline = Date().addingTimeInterval(timeout)
    var scrolling = OrdinaryScrollBudget(nextAllowedAt: Date().addingTimeInterval(0.75))
    while Date() < deadline {
      let identified = app.descendants(matching: .any).matching(identifier: identifier)
      if identified.count > 1 { throw NSError(domain: "ordinary-smoke-ambiguous-control", code: 1) }
      var target: XCUIElement? = identified.count == 1 ? identified.element : nil
      if target == nil && !labels.isEmpty {
        let labelled = app.descendants(matching: .any).matching(NSPredicate(format: "label IN %@", labels))
        if labelled.count > 1 { throw NSError(domain: "ordinary-smoke-ambiguous-control", code: 1) }
        target = labelled.count == 1 ? labelled.element : nil
      }
      if let target = target, target.exists && target.isHittable { return target }
      try smokeScroll(&scrolling, toward: target)
      Thread.sleep(forTimeInterval: 0.25)
    }
    throw NSError(domain: "ordinary-smoke-control-unavailable", code: 1)
  }

  func smokeTap(_ app: XCUIElement, _ identifier: String, _ labels: [String] = []) throws {
    let element = try smokeElement(app, identifier: identifier, labels: labels)
    guard element.isEnabled && element.isHittable else { throw NSError(domain: "ordinary-smoke-control-disabled", code: 1) }
    element.tap()
  }

  func smokeText(_ app: XCUIElement, _ identifier: String, _ value: String) throws {
    let field = try smokeElement(app, identifier: identifier)
    guard field.isEnabled && field.isHittable else { throw NSError(domain: "ordinary-smoke-field-disabled", code: 1) }
    field.tap()
    if let old = field.value as? String, !old.isEmpty {
      field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: old.count))
    }
    field.typeText(value)
  }

  func smokeResourceElement(_ app: XCUIApplication, prefix: String, name: String, expected: String?) throws -> XCUIElement {
    let deadline = Date().addingTimeInterval(60)
    var scrolling = OrdinaryScrollBudget(nextAllowedAt: Date().addingTimeInterval(0.75))
    while Date() < deadline {
      let rows = app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@", prefix))
      let matches = rows.allElementsBoundByIndex.filter { row in
        if let expected = expected {
          return SHA256.hash(data: Data(row.identifier.utf8)).map { String(format: "%02x", $0) }.joined() == expected
            && (name.isEmpty || row.label.contains(name))
        }
        return row.label.contains(name)
      }
      if matches.count > 1 { throw NSError(domain: "ordinary-smoke-resource-ambiguous", code: 1) }
      if matches.count == 1 && matches[0].isHittable { return matches[0] }
      try smokeScroll(&scrolling, toward: matches.first)
      Thread.sleep(forTimeInterval: 0.25)
    }
    throw NSError(domain: "ordinary-smoke-resource-not-observed", code: 1)
  }

  func smokeResource(_ app: XCUIApplication, prefix: String, name: String, expected: String?) throws -> String {
    let row = try smokeResourceElement(app, prefix: prefix, name: name, expected: expected)
    return SHA256.hash(data: Data(row.identifier.utf8)).map { String(format: "%02x", $0) }.joined()
  }

  func smokeObserveMessage(_ app: XCUIApplication, _ text: String, conversationDigest: String) throws {
    let deadline = Date().addingTimeInterval(60)
    var scrolling = OrdinaryScrollBudget(nextAllowedAt: Date().addingTimeInterval(0.75))
    while Date() < deadline {
      let room = app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@", "chat-active-conversation-"))
      guard room.count == 1 else { throw NSError(domain: "ordinary-smoke-active-conversation-ambiguous", code: 1) }
      let rowIdentifier = "chat-conversation-" + room.element.identifier.dropFirst("chat-active-conversation-".count)
      let observed = SHA256.hash(data: Data(rowIdentifier.utf8)).map { String(format: "%02x", $0) }.joined()
      guard observed == conversationDigest else { throw NSError(domain: "ordinary-smoke-message-context-mismatch", code: 1) }
      // The ordinary accessible bubble groups sender, body, time and status.
      // Match the complete nonce-bound body segment inside that actual room;
      // neither composer drafts nor list previews have this message identifier.
      let messages = room.element.descendants(matching: .any).matching(NSPredicate(
        format: "identifier BEGINSWITH %@ AND label CONTAINS %@", "chat-message-", ". " + text + ". "))
      guard messages.count <= 1 else { throw NSError(domain: "ordinary-smoke-independent-readback-ambiguous", code: 1) }
      if messages.count == 1 && messages.element.isHittable { return }
      try smokeScroll(&scrolling, toward: messages.count == 1 ? messages.element : nil)
      Thread.sleep(forTimeInterval: 0.25)
    }
    throw NSError(domain: "ordinary-smoke-independent-readback-missing", code: 1)
  }

  func smokeObserveFilePreview(_ app: XCUIApplication, expected: String) throws {
    let preview = try smokeElement(app, identifier: "files-preview-content")
    guard preview.label == expected || (preview.value as? String) == expected else {
      throw NSError(domain: "ordinary-smoke-file-preview-byte-text-mismatch", code: 1)
    }
  }

  func smokeSharedFolder(_ app: XCUIApplication, _ fixture: OrdinarySmokeFixture, create: Bool) throws -> String {
    try smokeTap(app, "nav-files")
    try smokeTap(app, "files-space-selector")
    try smokeTap(app, "files-space-option-workspace-default")
    try smokeTap(app, "files-refresh")
    if create {
      try smokeTap(app, "files-create-folder")
      try smokeText(app, "files-folder-name", fixture.folder)
      try smokeTap(app, "files-confirm-create-folder")
    }
    let digest = try smokeResource(app, prefix: "files-entry-", name: fixture.folder, expected: fixture.expected["folder"])
    let row = app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@ AND label CONTAINS %@", "files-entry-", fixture.folder))
    guard row.count == 1, row.element.isHittable else { throw NSError(domain: "ordinary-smoke-folder-unavailable", code: 1) }
    row.element.tap()
    return digest
  }

  func smokeConversation(_ app: XCUIApplication, _ fixture: OrdinarySmokeFixture) throws -> String {
    try smokeTap(app, "nav-chat")
    let digest = try smokeResource(app, prefix: "chat-conversation-", name: fixture.expected["conversation"] == nil ? fixture.participant : "", expected: fixture.expected["conversation"])
    let row = try smokeResourceElement(app, prefix: "chat-conversation-", name: fixture.expected["conversation"] == nil ? fixture.participant : "", expected: fixture.expected["conversation"])
    guard row.isHittable else { throw NSError(domain: "ordinary-smoke-conversation-unavailable", code: 1) }
    row.tap()
    return digest
  }

  func testOrdinaryOrganizationEntry() throws {
    continueAfterFailure = false
    let fixture = try XCTUnwrap(JSONSerialization.jsonObject(with: nativeFixtureData()) as? [String: Any])
    let origin = try XCTUnwrap(fixture["organizationOrigin"] as? String)
    let url = try XCTUnwrap(URLComponents(string: origin))
    guard url.scheme == "https", url.host != nil, url.user == nil, url.password == nil,
          url.query == nil, url.fragment == nil, ["", "/"].contains(url.path) else { throw NSError(domain: "ordinary-smoke-invalid-origin", code: 1) }
    let app = XCUIApplication(bundleIdentifier: "com.massimotter.weave")
    app.activate()
    if Self.ordinaryWorkspaceObserved(app) { return }
    if app.buttons.matching(NSPredicate(format: "label IN %@", Self.signInLabels)).count == 1 { return }
    guard app.textFields.element.waitForExistence(timeout: 60), app.textFields.count == 1 else {
      throw NSError(domain: "ordinary-smoke-entry-unavailable", code: 1)
    }
    app.textFields.element.tap()
    app.textFields.element.typeText(origin)
    try smokeTap(app, "organization-continue", ["Continue to organization", "Weiter zur Organisation"])
    let signIn = app.buttons.matching(NSPredicate(format: "label IN %@", Self.signInLabels))
    XCTAssertTrue(signIn.element.waitForExistence(timeout: 60))
  }

  func testOrdinaryProductSmoke() throws {
    continueAfterFailure = false
    let fixture = try JSONDecoder().decode(OrdinarySmokeFixture.self, from: nativeFixtureData())
    guard fixture.schemaVersion == "weave.ordinary-product-ui/v1", fixture.runId.count >= 6,
          fixture.nonce.count == 32, fixture.folder.hasPrefix("weave-smoke-"),
          fixture.filename.hasPrefix("weave-dual-smoke-") else { throw NSError(domain: "ordinary-smoke-invalid-fixture", code: 1) }
    let app = XCUIApplication(bundleIdentifier: "com.massimotter.weave")
    app.activate()
    guard Self.ordinaryWorkspaceObserved(app) else {
      print("WEAVE_FUNCTIONAL_UI_FAILURE category=authentication-required")
      throw NSError(domain: "ordinary-smoke-authentication-required", code: 1)
    }
    var resources = [String: String]()
    switch fixture.operation {
    case "discover":
      // Entry-only diagnostic. The host excludes this from functional acceptance.
      for tab in ["nav-chat", "nav-files", "nav-calendar"] { try smokeTap(app, tab) }
    case "identity":
      try smokeTap(app, "nav-settings")
      try smokeTap(app, "settings-help")
      for (key, identifier) in [("account", "settings-current-account-reference"), ("organization", "settings-current-organization-reference")] {
        let reference = try smokeElement(app, identifier: identifier)
        let regex = try NSRegularExpression(pattern: "sha256:([0-9a-f]{64})")
        let text = reference.label
        let matches = regex.matches(in: text, range: NSRange(text.startIndex..., in: text))
        guard matches.count == 1, let range = Range(matches[0].range(at: 1), in: text) else {
          throw NSError(domain: "ordinary-smoke-current-identity-reference-unavailable", code: 1)
        }
        resources[key] = String(text[range])
      }
    case "chat-create":
      try smokeTap(app, "nav-chat")
      try smokeTap(app, "chat-create-conversation")
      try smokeText(app, "chat-conversation-name", fixture.conversation)
      try smokeText(app, "chat-participants", fixture.participant)
      try smokeTap(app, "chat-selected-participant", [fixture.participant])
      try smokeTap(app, "chat-create-submit")
      let active = app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@", "chat-active-conversation-"))
      guard active.element.waitForExistence(timeout: 60), active.count == 1 else { throw NSError(domain: "ordinary-smoke-active-conversation-missing", code: 1) }
      let rowIdentifier = "chat-conversation-" + active.element.identifier.dropFirst("chat-active-conversation-".count)
      resources["conversation"] = SHA256.hash(data: Data(rowIdentifier.utf8)).map { String(format: "%02x", $0) }.joined()
      try smokeText(app, "chat-message-input", fixture.conversation + "-a")
      try smokeTap(app, "chat-send-message")
      try smokeObserveMessage(app, fixture.conversation + "-a", conversationDigest: try XCTUnwrap(resources["conversation"]))
    case "chat-peer":
      resources["conversation"] = try smokeConversation(app, fixture)
      try smokeObserveMessage(app, fixture.conversation + "-a", conversationDigest: try XCTUnwrap(resources["conversation"]))
      try smokeText(app, "chat-message-input", fixture.conversation + "-b")
      try smokeTap(app, "chat-send-message")
      try smokeObserveMessage(app, fixture.conversation + "-b", conversationDigest: try XCTUnwrap(resources["conversation"]))
    case "chat-readback":
      resources["conversation"] = try smokeConversation(app, fixture)
      try smokeObserveMessage(app, fixture.conversation + "-a", conversationDigest: try XCTUnwrap(resources["conversation"]))
      try smokeObserveMessage(app, fixture.conversation + "-b", conversationDigest: try XCTUnwrap(resources["conversation"]))
    case "folder-create":
      resources["folder"] = try smokeSharedFolder(app, fixture, create: true)
    case "file-upload":
      resources["folder"] = try smokeSharedFolder(app, fixture, create: false)
      try smokeTap(app, "files-upload")
      try ordinaryDocumentPicker(mode: "import", fileName: fixture.filename)
      resources["file"] = try smokeResource(app, prefix: "files-entry-", name: fixture.filename, expected: nil)
    case "file-readback":
      resources["folder"] = try smokeSharedFolder(app, fixture, create: false)
      resources["file"] = try smokeResource(app, prefix: "files-entry-", name: fixture.filename, expected: fixture.expected["file"])
      let fileRow = app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@ AND label CONTAINS %@", "files-entry-", fixture.filename))
      guard fileRow.count == 1 else { throw NSError(domain: "ordinary-smoke-file-ambiguous", code: 1) }
      let rawID = String(fileRow.element.identifier.dropFirst("files-entry-".count))
      try smokeTap(app, "files-open-" + rawID)
      try smokeObserveFilePreview(app, expected: fixture.filePayload)
      try smokeTap(app, "files-preview-close")
    case "file-export":
      resources["folder"] = try smokeSharedFolder(app, fixture, create: false)
      resources["file"] = try smokeResource(app, prefix: "files-entry-", name: fixture.filename, expected: fixture.expected["file"])
      let fileRow = app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@ AND label CONTAINS %@", "files-entry-", fixture.filename))
      guard fileRow.count == 1 else { throw NSError(domain: "ordinary-smoke-file-ambiguous", code: 1) }
      let rawID = String(fileRow.element.identifier.dropFirst("files-entry-".count))
      try smokeTap(app, "files-export-" + rawID)
      try ordinaryDocumentPicker(mode: "export", fileName: fixture.filename)
    case "calendar-create", "calendar-update", "calendar-read", "calendar-delete", "calendar-absent":
      try smokeTap(app, "nav-calendar")
      let before = app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@", "calendar-loaded-"))
      guard before.count <= 1 else { throw NSError(domain: "ordinary-smoke-calendar-context-ambiguous", code: 1) }
      let previousGeneration = before.count == 1 ? before.element.identifier : ""
      try smokeTap(app, "calendar-refresh")
      let refreshDeadline = Date().addingTimeInterval(60)
      var refreshed = false
      while Date() < refreshDeadline {
        let loaded = app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@", "calendar-loaded-"))
        if loaded.count == 1 && loaded.element.identifier != previousGeneration {
          refreshed = true
          break
        }
        Thread.sleep(forTimeInterval: 0.25)
      }
      guard refreshed else { throw NSError(domain: "ordinary-smoke-calendar-refresh-not-completed", code: 1) }
      if fixture.operation == "calendar-create" {
        try smokeTap(app, "calendar-create-event")
        try smokeText(app, "calendar-event-title", fixture.event)
        try smokeTap(app, "calendar-save-event")
      }
      let title = fixture.expected["updated"] == "true" ? fixture.event + "-updated" : fixture.event
      if fixture.operation == "calendar-absent" {
        let old = app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@", "calendar-event-")).allElementsBoundByIndex.filter {
          SHA256.hash(data: Data($0.identifier.utf8)).map { String(format: "%02x", $0) }.joined() == fixture.expected["event"]
        }
        guard old.isEmpty else { throw NSError(domain: "ordinary-smoke-calendar-delete-readback-present", code: 1) }
        guard app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@", "calendar-loaded-")).count == 1 else {
          throw NSError(domain: "ordinary-smoke-calendar-refresh-not-current", code: 1)
        }
        resources["deletedEvent"] = try XCTUnwrap(fixture.expected["event"])
      } else {
        resources["event"] = try smokeResource(app, prefix: "calendar-event-", name: title, expected: fixture.expected["event"])
        let row = app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@ AND label CONTAINS %@", "calendar-event-", title))
        if fixture.operation == "calendar-update" {
          try smokeTap(row.element, "calendar-edit-event")
          try smokeText(app, "calendar-event-title", fixture.event + "-updated")
          try smokeTap(app, "calendar-save-event")
          resources["event"] = try smokeResource(app, prefix: "calendar-event-", name: fixture.event + "-updated", expected: fixture.expected["event"])
        } else if fixture.operation == "calendar-delete" {
          try smokeTap(row.element, "calendar-delete-event")
          try smokeTap(app, "calendar-confirm-delete", ["Delete", "Löschen"])
        }
      }
    default: throw NSError(domain: "ordinary-smoke-unsupported-operation", code: 1)
    }
    let record: [String: Any] = ["schemaVersion": "weave.ordinary-product-ui-result/v1", "runId": fixture.runId,
      "nonce": fixture.nonce, "operation": fixture.operation, "status": "passed", "resources": resources]
    let data = try JSONSerialization.data(withJSONObject: record, options: [.sortedKeys])
    print("WEAVE_FUNCTIONAL_UI_RESULT " + String(decoding: data, as: UTF8.self))
  }
}
