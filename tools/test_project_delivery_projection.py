"""Project evidence projection fails closed without inventing human acceptance."""
import argparse
import io
import json
import unittest
import zipfile
from unittest.mock import patch
from tools import sprint_bootstrap as board
from tools import verify_main_promotion_evidence as evidence

SHA = 'a' * 40
TREE = 'b' * 40
SPEC = 'c' * 40
URL = 'https://github.com/masssi164/weave/issues/1565'
PR_URL = 'https://github.com/masssi164/weave/pull/1563'


class ProjectionTest(unittest.TestCase):
    def setUp(self):
        self.pr = {'html_url': PR_URL, 'head': {'sha': SHA, 'repo': {'full_name': 'masssi164/weave'}},
                   'state': 'open', 'draft': False, 'merged': False, 'body': 'Implements #1565'}
        self.run = {'id': 10, 'run_attempt': 2, 'qualification_attempt': 1,
                    'updated_at': '2026-10-11T10:00:00Z'}
        self.prepared = {'id': 11, 'updated_at': '2026-10-11T11:00:00Z'}
        self.promotion = {**self.pr, 'merged': True, 'merge_commit_sha': SHA,
                          'base': {'ref': 'main', 'repo': {'full_name': 'masssi164/weave'}}}
        self.latest = {'id': 10, 'run_attempt': 2, 'head_sha': SHA, 'head_branch': 'dogfood',
                       'event': 'push', 'path': '.github/workflows/live-stack-e2e.yml'}
        self.patches = [
            patch.object(evidence, 'api', side_effect=self.api),
            patch.object(evidence, 'candidate_identity', return_value=(TREE, SPEC)),
            patch.object(evidence, 'qualified_run', return_value=self.run),
            patch.object(evidence, 'prepared_run', return_value=self.prepared),
            patch.object(evidence, 'owner_human_pass', return_value=True),
            patch.object(evidence, 'paged', return_value=[]),
            patch.object(board, 'gh_json', return_value={'closingIssuesReferences': [{'url': URL}]}),
        ]
        self.mocks = [p.start() for p in self.patches]
        self.addCleanup(lambda: [p.stop() for p in self.patches])

    def api(self, path, token):
        if path.endswith('/pulls/1563'): return self.pr
        if path.endswith('/pulls/1600'): return self.promotion
        if '/compare/' in path: return {'status': 'identical'}
        if '/branches/' in path: return {'protected': True, 'commit': {'sha': SHA}}
        if '/runs?' in path: return {'workflow_runs': [self.latest]}
        raise AssertionError(path)

    def verify(self, stage='HIL', url=URL, promotion=1600):
        return board.verify_delivery_stage('masssi164/weave', 'masssi164', stage,
                                           SHA, url, 1563, promotion, 'private-token')

    def test_hil_requires_existing_qualification_and_exact_physical_preparation(self):
        report = self.verify()
        self.assertEqual(report['preparationRunId'], 11)
        self.assertEqual(report['qualificationRunAttempt'], 1)
        self.assertEqual(report['ownerHIL'], 'pending')
        self.mocks[2].assert_called_once_with('masssi164/weave', SHA, TREE, SPEC, 'private-token')
        self.mocks[3].assert_called_once_with('masssi164/weave', SHA, TREE, SPEC, self.run, 'private-token')
        for target in (self.mocks[2], self.mocks[3]):
            target.side_effect = ValueError('missing/failed/stale evidence')
            with self.assertRaises(ValueError): self.verify()
            target.side_effect = None

    def test_hil_rejects_actual_old_and_single_ios_artifacts_before_preparation(self):
        # Exercise the real promotion validator with only GitHub transport stubbed.
        from tools.test_verify_main_promotion_evidence import executed_job, native_proof
        self.patches[2].stop()
        self.latest.update(status='completed', conclusion='success')
        original_jobs = [executed_job(name, 1) for name in evidence.REQUIRED_JOBS]
        latest_jobs = [
            {**job, 'run_attempt': 2} if job['name'] == 'Full Compose E2E' else
            executed_job(job['name'], 2, '2026-10-07T11:00:00Z', '2026-10-07T11:05:00Z')
            for job in original_jobs]
        self.mocks[5].side_effect = lambda path, *_: (
            [*original_jobs, *latest_jobs] if 'filter=all' in path else latest_jobs)
        for variant in ('v1', 'v2', 'single-actor', 'owner-actor', 'scenario-drift'):
            with self.subTest(variant=variant):
                native = native_proof()
                actors = native['iosSimulator']['collaboration']['actors']
                if variant in {'v1', 'v2'}:
                    native['schemaVersion'] = 'weave.native-qualification/' + variant
                elif variant == 'single-actor':
                    native['iosSimulator']['collaboration']['actors'] = actors[:1]
                elif variant == 'owner-actor':
                    actors[0]['organizationRole'] = 'owner'
                else:
                    actors[0]['memberScenarioSha256'] = '0' * 64
                archive = io.BytesIO()
                with zipfile.ZipFile(archive, 'w') as bundle:
                    bundle.writestr('native-qualification.json', json.dumps(native))
                with patch.object(evidence, 'download_artifact', return_value=archive.getvalue()) as download:
                    with self.assertRaisesRegex(ValueError, 'Mandatory native qualification'):
                        self.verify()
                download.assert_called_once_with('masssi164/weave', 10,
                    f'full-compose-e2e-{SHA}-attempt-1', 'private-token')
                self.mocks[3].assert_not_called()

    def test_hil_accepts_observed_normal_sign_in_and_keeps_human_acceptance_pending(self):
        from tools.test_verify_main_promotion_evidence import preparation_proof
        receipt = preparation_proof()
        receipt.update(qualificationRunId=10, qualificationRunAttempt=1)
        def prepared(*args):
            if not evidence.preparation_passed(receipt, SHA, TREE, SPEC, 10, 1):
                raise ValueError('Incomplete physical startup evidence')
            return self.prepared
        self.mocks[3].side_effect = prepared
        self.assertEqual(self.verify()['ownerHIL'], 'pending')
        self.mocks[4].assert_not_called()
        for field, value in [('ownerHIL', 'passed'), ('schemaVersion', 'weave.hil-preparation/v3')]:
            previous = receipt[field]; receipt[field] = value
            with self.assertRaisesRegex(ValueError, 'Incomplete physical startup'): self.verify()
            receipt[field] = previous
        receipt['ordinaryStartup']['ownerSessionReplaced'] = True
        with self.assertRaisesRegex(ValueError, 'Incomplete physical startup'): self.verify()

    def test_missing_explicit_item_association_fails(self):
        self.mocks[6].return_value = {'closingIssuesReferences': []}
        with self.assertRaisesRegex(ValueError, 'explicitly associated'): self.verify()
        self.verify(url=PR_URL)

    def test_nonclosing_verified_graph_link_keeps_story_open(self):
        self.mocks[6].return_value = {'closingIssuesReferences': []}
        self.mocks[5].return_value = [{'event': 'cross-referenced', 'source': {'issue': {
            'html_url': PR_URL, 'pull_request': {'url': 'same-pr'}}}}]
        self.verify()
        self.pr['body'] = 'Mentions #15650 rather than the selected item'
        with self.assertRaisesRegex(ValueError, 'explicitly associated'): self.verify()

    def test_url_prefix_cannot_reuse_historical_cross_reference(self):
        self.mocks[6].return_value = {'closingIssuesReferences': []}
        self.mocks[5].return_value = [{'event': 'cross-referenced', 'source': {'issue': {
            'html_url': PR_URL, 'pull_request': {'url': 'same-pr'}}}}]
        self.pr['body'] = URL + '0'
        with self.assertRaisesRegex(ValueError, 'explicitly associated'): self.verify()
        self.pr['body'] = '(' + URL + ')'
        self.verify()

    def test_foreign_closing_reference_never_bypasses_same_repository(self):
        foreign = 'https://github.com/other/weave/issues/1565'
        self.mocks[6].return_value = {'closingIssuesReferences': [{'url': foreign}]}
        with self.assertRaisesRegex(ValueError, 'canonical same-repository'): self.verify(url=foreign)
        for suffix in ('/extra', '?query=value', '#comment'):
            with self.assertRaisesRegex(ValueError, 'canonical same-repository'): self.verify(url=URL + suffix)

    def test_stale_source_foreign_pr_and_changed_protected_head_fail(self):
        self.mocks[0].side_effect = lambda path, token: {'status': 'behind'} if '/compare/' in path else self.api(path, token)
        with self.assertRaisesRegex(ValueError, 'does not contain'): self.verify()
        self.mocks[0].side_effect = self.api
        self.pr['head']['repo']['full_name'] = 'foreign/weave'
        with self.assertRaisesRegex(ValueError, 'same repository'): self.verify()
        self.pr['head']['repo']['full_name'] = 'masssi164/weave'
        self.mocks[0].side_effect = lambda path, token: {'protected': True, 'commit': {'sha': 'd'*40}} if '/branches/dogfood' in path else self.api(path, token)
        with self.assertRaisesRegex(ValueError, 'protected Dogfood head'): self.verify()

    def test_main_requires_explicit_human_and_actual_merged_main(self):
        report = self.verify('Main')
        self.assertEqual(report['promotionPr'], 1600)
        self.assertEqual(report['ownerHIL'], 'passed')
        self.mocks[4].return_value = False
        with self.assertRaisesRegex(ValueError, 'human acceptance'): self.verify('Main')
        self.mocks[4].return_value = True
        self.promotion['merged'] = False
        with self.assertRaisesRegex(ValueError, 'merged same-repository'): self.verify('Main')
        self.promotion['merged'] = True
        with self.assertRaisesRegex(ValueError, 'explicit merged'): self.verify('Main', promotion=None)

    def test_main_requires_the_reported_actual_human_actor_role(self):
        from tools.test_verify_main_promotion_evidence import HUMAN
        self.patches[4].stop()
        comment = {'id': 100, 'user': {'login': 'masssi164'}, 'author_association': 'OWNER',
                   'created_at': '2026-10-11T12:00:00Z', 'updated_at': '2026-10-11T12:00:00Z'}
        for role in ('owner', 'admin', 'member'):
            self.mocks[5].return_value = [{**comment, 'body': HUMAN.replace('actor-role: owner', 'actor-role: ' + role)}]
            self.assertEqual(self.verify('Main')['ownerHIL'], 'passed')
        for body in (HUMAN.replace('actor-role: owner\n', ''), HUMAN.replace('actor-role: owner', 'actor-role: guest'),
                     HUMAN.replace('Weave human test v2', 'Weave human test v1')):
            self.mocks[5].return_value = [{**comment, 'body': body}]
            with self.assertRaisesRegex(ValueError, 'human acceptance'): self.verify('Main')

    def test_started_e2e_never_infers_hil_and_review_rejects_draft(self):
        self.verify('E2E Testing')
        self.mocks[2].assert_not_called()
        self.latest['head_sha'] = 'e'*40
        with self.assertRaisesRegex(ValueError, 'has not started'): self.verify('E2E Testing')
        self.pr['draft'] = True
        with self.assertRaisesRegex(ValueError, 'Draft'): self.verify('Review')


class MutationTest(unittest.TestCase):
    def args(self, apply=False):
        return argparse.Namespace(repo='masssi164/weave', project_owner='masssi164', project_number=2,
                                  delivery_stage='HIL', item_url=URL, candidate_sha=SHA,
                                  association_pr=1563, promotion_pr=None, apply=apply)

    def context(self, command):
        if command[:2] == ['project', 'list']: return {'projects': [{'id': 'project', 'number': 2}]}
        if command[:2] == ['project', 'field-list']:
            return {'fields': [{'id': 'field', 'name': board.DELIVERY_FIELD, 'type': 'ProjectV2SingleSelectField',
                                'options': [{'name': name, 'id': name} for name in board.DELIVERY_STAGES]}]}
        if command[:2] == ['project', 'item-list']:
            return {'items': [{'id': 'item', 'content': {'url': URL}, 'delivery stage': self.current}], 'totalCount': 1}
        if command[:2] == ['api', 'graphql']: return {'data': {'node': {'project': {'id': 'project'}, 'fieldValueByName': {'name': self.current} if self.current else None}}}
        raise AssertionError(command)

    @patch.object(board, 'verify_delivery_stage', return_value={'candidateCommit': SHA})
    @patch.object(board, 'run', return_value='private-token')
    def test_dry_run_rerun_and_idempotent_write(self, run, verify):
        self.current = ''
        with patch.object(board, 'gh_json', side_effect=self.context), patch('builtins.print'):
            board.project_delivery_transition(self.args())
            self.assertEqual(run.call_count, 1)  # token read only
            board.project_delivery_transition(self.args(True))
            self.assertTrue(any(call.args[0][:3] == ['gh', 'project', 'item-edit'] for call in run.call_args_list))
            writes = len(run.call_args_list)
            self.current = 'HIL'
            board.project_delivery_transition(self.args(True))
            self.assertEqual(len(run.call_args_list), writes + 1)  # no second write

    @patch.object(board, 'run', return_value='private-token')
    def test_permissions_or_invalid_inventory_cannot_write(self, run):
        with patch.object(board, 'gh_json', side_effect=SystemExit('permission denied')):
            with self.assertRaises(SystemExit): board.project_delivery_transition(self.args(True))
        self.assertEqual(run.call_count, 1)

    @patch.object(board, 'run', return_value='private-token')
    @patch.object(board, 'verify_delivery_stage', side_effect=ValueError('failed qualification'))
    def test_failed_evidence_never_mutates(self, verify, run):
        self.current = ''
        with patch.object(board, 'gh_json', side_effect=self.context):
            with self.assertRaises(ValueError): board.project_delivery_transition(self.args(True))
        self.assertEqual(run.call_count, 1)


if __name__ == '__main__': unittest.main()
