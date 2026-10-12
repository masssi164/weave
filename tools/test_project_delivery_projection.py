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
        self.run = {'id': 10, 'run_attempt': 2, 'updated_at': '2026-10-11T10:00:00Z'}
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
        self.assertEqual(self.verify()['preparationRunId'], 11)
        self.mocks[2].assert_called_once_with('masssi164/weave', SHA, TREE, SPEC, 'private-token')
        self.mocks[3].assert_called_once_with('masssi164/weave', SHA, TREE, SPEC, self.run, 'private-token')
        for target in (self.mocks[2], self.mocks[3]):
            target.side_effect = ValueError('missing/failed/stale evidence')
            with self.assertRaises(ValueError): self.verify()
            target.side_effect = None

    def test_hil_rejects_actual_old_and_single_ios_artifacts_before_preparation(self):
        # Exercise the real promotion validator with only GitHub transport stubbed.
        from tools.test_verify_main_promotion_evidence import TOOLCHAIN, dual_ios_proof
        self.patches[2].stop()
        self.latest.update(status='completed', conclusion='success')
        self.mocks[5].return_value = [
            {'name': name, 'conclusion': 'success'} for name in evidence.REQUIRED_JOBS]
        single = dual_ios_proof()
        single['actors'] = single['actors'][:1]
        native = {
            'schemaVersion': 'weave.native-qualification/v2', 'candidateCommit': SHA,
            'sourceTree': TREE, 'specificationCommit': SPEC, 'toolchain': TOOLCHAIN,
            'macos': {'status': 'passed'}, 'iosSimulator': {
                'status': 'passed', 'rustTarget': 'aarch64-apple-ios-sim', 'collaboration': single},
            'cleanupComplete': True, 'supportSafe': True, 'containsSecretValues': False,
        }
        for version in ('weave.native-qualification/v1', 'weave.native-qualification/v2'):
            with self.subTest(version=version):
                native['schemaVersion'] = version
                archive = io.BytesIO()
                with zipfile.ZipFile(archive, 'w') as bundle:
                    bundle.writestr('native-qualification.json', json.dumps(native))
                with patch.object(evidence, 'download_artifact', return_value=archive.getvalue()):
                    with self.assertRaisesRegex(ValueError, 'Mandatory native qualification'):
                        self.verify()
                self.mocks[3].assert_not_called()

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
        self.assertEqual(self.verify('Main')['promotionPr'], 1600)
        self.mocks[4].return_value = False
        with self.assertRaisesRegex(ValueError, 'human acceptance'): self.verify('Main')
        self.mocks[4].return_value = True
        self.promotion['merged'] = False
        with self.assertRaisesRegex(ValueError, 'merged same-repository'): self.verify('Main')
        self.promotion['merged'] = True
        with self.assertRaisesRegex(ValueError, 'explicit merged'): self.verify('Main', promotion=None)

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
