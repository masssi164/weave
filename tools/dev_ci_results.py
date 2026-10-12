#!/usr/bin/env python3
"""Validate the stable protected aggregate without trusting absent impact outputs."""
from __future__ import annotations

import json
import os
if __package__:
    from .dev_ci_impact import validate_results
else:
    from dev_ci_impact import validate_results

REQUIRED = ('core_docs', 'api_contract', 'api_impact', 'spec_acceptance')
BACKEND = ('core_architecture', 'postgres_persistence', 'protocol_foundation')
SELECTED = {'user': 'generated_user_api', 'admin': 'generated_admin_api',
            'client': 'client_deterministic', 'admin_console': 'admin_deterministic',
            'infra': 'infra_static', 'docs': 'docs_deterministic', 'rust': 'rust_deterministic'}


def validate(needs: dict) -> None:
    for key in REQUIRED:
        if needs.get(key, {}).get('result') != 'success':
            raise ValueError(f'required {key} did not succeed')
    selected_results = {flag: needs.get(job, {}).get('result') for flag, job in SELECTED.items()}
    backend_results = [needs.get(job, {}).get('result') for job in BACKEND]
    selected_results['backend'] = backend_results[0] if len(set(backend_results)) == 1 else 'mixed'
    validate_results(needs['api_impact'].get('outputs', {}), selected_results)


if __name__ == '__main__':
    validate(json.loads(os.environ['NEEDS_JSON']))
    print('Gradle CI: every required and impacted deterministic gate passed')
