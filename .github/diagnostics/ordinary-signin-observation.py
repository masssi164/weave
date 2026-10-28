"""Diagnostic-only observation; no production source or acceptance result is changed."""
import json
from pathlib import Path
import sys

FIELDS = (
    'signInCancelled', 'signInConfigurationFailure', 'signInProtocolFailure',
    'signInStorageFailure', 'signInUnsupportedPlatform', 'signInUnknownFailure',
    'signInOfflineSessionNotAllowed', 'signInInProgress',
)

if sys.argv[1] == 'instrument':
    root = Path.cwd()
    catalogs = [json.loads((root / ('client/lib/l10n/app_' + language + '.arb')).read_text())
                for language in ('en', 'de')]
    pairs = ',\n'.join('      ' + json.dumps(key) + ': ' + json.dumps([catalog[key] for catalog in catalogs], ensure_ascii=False)
                      for key in FIELDS)
    helper = '''  func reportOrdinarySignInFailureState(_ app: XCUIApplication, _ browser: XCUIApplication, _ system: XCUIApplication) {
    print("ORDINARY_SIGNIN_OBSERVER stage=started")
    let labels: [String: [String]] = [
PAIRS
    ]
    let appState = app.state
    let browserState = browser.state
    let systemState = system.state
    let appRunning = appState == .runningForeground || appState == .runningBackground
    let browserRunning = browserState == .runningForeground || browserState == .runningBackground
    let systemRunning = systemState == .runningForeground || systemState == .runningBackground
    var counts = [String: Int]()
    for (key, values) in labels {
      counts[key] = appRunning ? min(app.staticTexts.matching(NSPredicate(format: "label IN %@", values)).count, 999) : 0
    }
    let controls = app.buttons.matching(NSPredicate(format: "label IN %@", Self.signInLabels))
    let controlCount = appRunning ? controls.count : 0
    let frame = controlCount == 1 ? controls.element.frame : .zero
    let report: [String: Any] = ["counts": counts, "appState": appState.rawValue,
      "browserState": browserState.rawValue, "systemState": systemState.rawValue,
      "controlCount": min(controlCount, 999), "controlEnabled": controlCount == 1 && controls.element.isEnabled,
      "controlHittable": controlCount == 1 && controls.element.isHittable,
      "appAlerts": appRunning ? min(app.alerts.count, 999) : 0,
      "systemAlerts": systemRunning ? min(system.alerts.count, 999) : 0,
      "appWebViews": appRunning ? min(app.webViews.count, 999) : 0,
      "browserWebViews": browserRunning ? min(browser.webViews.count, 999) : 0,
      "frame": [frame.minX, frame.minY, frame.width, frame.height], "diagnosticOnly": true]
    if let data = try? JSONSerialization.data(withJSONObject: report, options: [.sortedKeys]),
       let encoded = String(data: data, encoding: .utf8) {
      print("ORDINARY_SIGNIN_STATE " + encoded)
    }
  }

'''.replace('PAIRS', pairs)
    path = root / 'client/tool/ios_browser_driver/BrowserDriver.swift'
    source = path.read_text()
    entry = '  func testProductionBrowser() throws {'
    timeout = '          if outcome == "timeout" {\n'
    assert source.count(entry) == 1 and source.count(timeout) == 1
    source = source.replace(entry, helper + entry).replace(timeout, timeout + '            reportOrdinarySignInFailureState(app, browser, springboard)\n')
    path.write_text(source)
    print('ORDINARY_SIGNIN_INSTRUMENTATION readOnly=true qualificationEvidence=false')
elif sys.argv[1] == 'observe':
    source = (Path(__file__).parent / 'ordinary-browser-auth.py').read_text()
    original = '                copy_driver_products(products, destination)'
    assert source.count(original) == 1
    source = source.replace(original, "                copy_driver_products(Path(os.environ['WEAVE_DIAGNOSTIC_BROWSER_PRODUCTS']), destination)")
    marker = "                    emit_phase_stages('initial', '', text)"
    assert source.count(marker) == 1
    source = source.replace(marker, marker + '''
                    if 'ORDINARY_SIGNIN_OBSERVER stage=started' in text:
                        print('ORDINARY_SIGNIN_OBSERVER stage=started', flush=True)
                    for line in text.splitlines():
                        if 'ORDINARY_SIGNIN_STATE ' in line:
                            value = json.loads(line.split('ORDINARY_SIGNIN_STATE ', 1)[1])
                            assert set(value) == {'counts', 'appState', 'browserState', 'systemState',
                                'controlCount', 'controlEnabled', 'controlHittable', 'appAlerts',
                                'systemAlerts', 'appWebViews', 'browserWebViews', 'frame', 'diagnosticOnly'}
                            assert set(value['counts']) == set(FIELDS)
                            assert all(type(count) is int and 0 <= count <= 999 for count in value['counts'].values())
                            assert all(type(value[key]) in (int, bool) for key in set(value)-{'counts','frame'})
                            assert len(value['frame']) == 4 and all(type(n) in (int,float) and abs(n)<100000 for n in value['frame'])
                            print('ORDINARY_SIGNIN_STATE ' + json.dumps(value, sort_keys=True), flush=True)
''')
    source = source.replace("'diagnosticOnly': True,", "'diagnosticOnly': True, 'diagnosticDriverUsed': True, 'acceptanceEvidence': False,")
    exec(compile(source, '<ordinary-signin-diagnostic>', 'exec'))
else:
    raise SystemExit('unsupported-diagnostic-operation')
