import json, os, re, subprocess, sys, tempfile, time
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path.cwd()
sys.path.insert(0, str(ROOT / 'tools'))
import dogfood_functional_smoke as smoke
from artifact_consumers import native_bundle, copy_driver_products, verify_runtime_container, candidate_composition
from run_ios_simulator_product_acceptance import emit_phase_stages

CANDIDATE = '190799c721343a2f21e1b68181f39cb8c87bea6a'
report = {'status': 'incomplete', 'stage': 'preflight', 'diagnosticOnly': True,
          'supportSafe': True, 'containsSecretValues': False, 'actors': []}
devices = []

class ObservedRunner(smoke.Runner):
    def run(self, arguments, **options):
        try:
            return super().run(arguments, **options)
        finally:
            if arguments[0] == 'xcodebuild':
                transcript = self.directory / f'command-{self.sequence}.log'
                if transcript.is_file() and not transcript.is_symlink() and transcript.stat().st_size <= 8 * 1024 * 1024:
                    text = transcript.read_text(errors='replace')
                    emit_phase_stages('initial', '', text)
                    for category in ('sign-in-unacknowledged', 'sign-in-unavailable'):
                        if 'NATIVE_IOS_BROWSER_FAILURE category=' + category + ' supportSafe=true' in text:
                            print('ORDINARY_AUTH_DIAGNOSTIC category=' + category, flush=True)
                    for filename in ('BrowserDriver.swift', 'OrdinaryProductSmoke.swift'):
                        locations = {int(match.group(1)) for line in text.splitlines() if 'error:' in line
                                     for match in re.finditer(re.escape(filename) + r':([1-9][0-9]{0,4})', line)}
                        for number in sorted(locations)[:5]:
                            print(json.dumps({'stage': 'xctest-failure-location', 'file': filename, 'line': number}), flush=True)

try:
    assert os.environ['RUNNER_NAME'] == 'weave-live-mac-mini'
    assert os.environ['GITHUB_REPOSITORY'] == 'masssi164/weave'
    assert os.environ['GITHUB_REF'] == 'refs/heads/diagnostic/iphone-handoff-190799-20261028'
    assert subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip() == CANDIDATE
    assert not subprocess.check_output(['git','status','--porcelain'],text=True).strip()
    context = smoke.load_context('dogfood', ROOT/'infra/weave-workspace', os.environ['WEAVE_ENV_FILE'])
    assert context.environment == 'dogfood' and context.isolated_namespace is None
    assert context.env['WEAVE_COMPOSE_PROJECT'] == 'weave-dogfood'
    origin = smoke.canonical_origin(context.env['WEAVE_PUBLIC_URL'])
    fixture_path = Path.home()/'.weave/dogfood/functional-smoke-identities.json'
    with smoke._dogfood_session_lock(context), tempfile.TemporaryDirectory(prefix='weave-ordinary-auth-diagnostic-') as temporary:
        directory = Path(temporary); directory.chmod(0o700)
        runner = ObservedRunner(directory)
        runner.configure_native_toolchain()
        smoke.verify_dogfood_identity(context)
        before = smoke._service_container(context, 'backend', deadline=time.monotonic()+30)
        verify_runtime_container(before, CANDIDATE)
        candidate_composition(CANDIDATE)
        approved = smoke.identities(fixture_path, origin)
        smoke.approved_identity_binding(context, fixture_path, approved)
        runtime, device_type = smoke.select_runtime()
        manifest, app, products = native_bundle(candidate=CANDIDATE)
        report['simulatorBuildNumber'] = manifest['buildNumber']
        for index, role in enumerate(('A','B')):
            report['stage'] = 'actor-' + role + '-simulator-start'
            udid = runner.run(['xcrun','simctl','create','weave-auth-diag-'+os.environ['GITHUB_RUN_ID']+'-'+role,device_type,runtime]).strip()
            assert re.fullmatch(r'[0-9A-Fa-f-]{36}', udid)
            devices.append(udid)
            try:
                runner.run(['xcrun','simctl','boot',udid])
                runner.run(['xcrun','simctl','bootstatus',udid,'-b'], timeout=180)
                runner.run(['xcrun','simctl','keychain',udid,'add-root-cert',str(context.tls_root/'ca.pem')])
                runner.run(['xcrun','simctl','install',udid,str(app)])
                destination = directory/('driver-'+role)/'Build/Products'
                copy_driver_products(products, destination)
                actor={'id':udid,'physical':False,'destination':'platform=iOS Simulator,id='+udid,'products':destination,'app':app}
                smoke.restart(runner,actor)
                report['stage']='actor-'+role+'-organization-entry'
                runner.ui(actor,{'organizationOrigin':origin},'testOrdinaryOrganizationEntry')
                report['stage']='actor-'+role+'-browser-authentication'
                member=approved['members'][index]
                runner.ui(actor,{**member,'issuerHost':urlsplit(context.env['WEAVE_AUTH_URL']).hostname,
                    'appBundleIdentifier':smoke.BUNDLE,'phase':'initial','nativeAuthCompletionPath':'',
                    'ordinaryOrganizationOrigin':origin},'testProductionBrowser')
                report['actors'].append({'slot':role,'ordinaryBrowserAuthentication':'passed'})
            finally:
                runner.run(['xcrun','simctl','delete',udid],timeout=60)
                devices.remove(udid)
        report.update(status='passed',stage='complete')
except Exception as error:
    report['errorType']=type(error).__name__
finally:
    for udid in devices[:]:
        result=subprocess.run(['xcrun','simctl','delete',udid],capture_output=True,timeout=60)
        if result.returncode == 0:
            devices.remove(udid)
    report['ownedSimulatorsRemoved']=not devices
    if devices: report['status']='incomplete'
    print(json.dumps(report,sort_keys=True),flush=True)
raise SystemExit(0 if report['status']=='passed' else 1)
