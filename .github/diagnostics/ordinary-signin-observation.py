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
    let labels: [String: [String]] = [
PAIRS
    ]
    var counts = [String: Int]()
    for (key, values) in labels {
      counts[key] = min(app.staticTexts.matching(NSPredicate(format: "label IN %@", values)).count, 999)
    }
    let controls = app.buttons.matching(NSPredicate(format: "label IN %@", Self.signInLabels))
    let controlCount = controls.count
    let frame = controlCount == 1 ? controls.element.frame : .zero
    let report: [String: Any] = ["counts": counts, "appState": app.state.rawValue,
      "browserState": browser.state.rawValue, "systemState": system.state.rawValue,
      "controlCount": min(controlCount, 999), "controlEnabled": controlCount == 1 && controls.element.isEnabled,
      "controlHittable": controlCount == 1 && controls.element.isHittable,
      "appAlerts": min(app.alerts.count, 999), "systemAlerts": min(system.alerts.count, 999),
      "appWebViews": min(app.webViews.count, 999), "browserWebViews": min(browser.webViews.count, 999),
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
