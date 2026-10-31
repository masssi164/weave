#!/usr/bin/env python3
"""The personal secret cannot enter automation, process arguments, environment or diagnostics."""
import contextlib
import io
import os
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import provision_personal_ldap as operation


class PersonalLdapProvisionTest(unittest.TestCase):
    def setUp(self):
        self.candidate = 'a' * 40
        self.credential = 'private test input never echoed'
        self.env = {'GITHUB_ACTIONS': 'true', 'GITHUB_REF': 'refs/heads/dogfood',
                    'RUNNER_NAME': 'weave-live-mac-mini', 'DOGFOOD_USER_SECRET': self.credential}

    def test_nonphysical_context_and_missing_secret_stop_before_git_or_docker(self):
        for changes in ({'GITHUB_REF': 'refs/heads/dev'}, {'DOGFOOD_USER_SECRET': ''},
                        {'RUNNER_NAME': 'hosted-automation'}):
            with self.subTest(changes=changes), patch.dict(os.environ, {**self.env, **changes}, clear=True), \
                    patch.object(operation.subprocess, 'check_output') as git, \
                    patch.object(operation.subprocess, 'run') as docker, self.assertRaises(operation.ContractError):
                operation.provision(self.candidate)
            git.assert_not_called()
            docker.assert_not_called()

    def execute(self, *, wrong_image=False):
        image = 'sha256:' + 'b' * 64
        context = SimpleNamespace(env={'WEAVE_RUNTIME_UID': '501', 'WEAVE_TENANT_DOMAIN': 'weave.test',
                                      'WEAVE_LDAP_IMAGE': image})
        directory = {'Id': 'c' * 64, 'Image': 'sha256:' + 'd' * 64 if wrong_image else image}
        def consume(*args):
            self.assertNotIn('DOGFOOD_USER_SECRET', os.environ)
            return {'WEAVE_LDAP_IMAGE': image}
        with patch.dict(os.environ, self.env, clear=True), \
                patch.object(operation.subprocess, 'check_output', side_effect=[self.candidate, self.candidate, b'']), \
                patch.object(operation, 'candidate_composition'), \
                patch.object(operation, 'load_runtime_images', side_effect=consume), \
                patch.object(operation, 'load_context', return_value=context), \
                patch.object(operation, '_dogfood_session_lock', return_value=contextlib.nullcontext()), \
                patch.object(operation, 'require_personal_resources') as ownership, \
                patch.object(operation, 'verify_runtime_container') as candidate, \
                patch.object(operation, '_service_container', side_effect=[{}, directory, directory]), \
                patch.object(operation.subprocess, 'run', return_value=SimpleNamespace(returncode=0,
                    stdout=b'LDAP_PROVISION_RESULT status=passed phase=personal credentials=preserved\n')) as docker:
            if wrong_image:
                with self.assertRaises(operation.ContractError):
                    operation.provision(self.candidate)
                docker.assert_not_called()
                return
            operation.provision(self.candidate)
            ownership.assert_called_once_with(context)
            candidate.assert_called_once()
            arguments, options = docker.call_args
            self.assertEqual(options['input'], self.credential.encode())
            self.assertNotIn(self.credential, repr(arguments))
            self.assertNotIn(self.credential, repr(options['env']))
            self.assertNotIn('DOGFOOD_USER_SECRET', options['env'])
            command = arguments[0]
            self.assertEqual(command[:3], ['docker', 'exec', '--interactive'])
            self.assertIn('/opt/weave/ldap/provision.py', command)
            self.assertIn('personal', command)
            self.assertNotIn('ldapwhoami', command)
            self.assertNotIn('token', command)

    def test_exact_credential_uses_only_stdin_after_ownership_and_candidate_checks(self):
        self.execute()

    def test_foreign_directory_image_never_receives_the_secret(self):
        self.execute(wrong_image=True)

    def test_error_diagnostics_never_echo_a_provider_payload(self):
        output = io.StringIO()
        with patch('sys.argv', ['provision_personal_ldap', '--candidate', self.candidate]), \
                patch.object(operation, 'provision', side_effect=operation.ContractError(self.credential)), \
                contextlib.redirect_stderr(output):
            self.assertEqual(1, operation.main())
        self.assertNotIn(self.credential, output.getvalue())


if __name__ == '__main__':
    unittest.main()
