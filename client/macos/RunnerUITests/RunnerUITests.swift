import XCTest

/// Isolated Xcode automation probe. The live Flutter journey is run by
/// integration_test; a separate native Accessibility helper drives only the
/// system browser opened by production AppAuth.
final class RunnerUITests: XCTestCase {
  func testNativeWindowLaunches() {
    let app = XCUIApplication(bundleIdentifier: "com.example.weave")
    app.launch()
    XCTAssertTrue(app.windows.firstMatch.waitForExistence(timeout: 30))
    app.terminate()
  }
}
