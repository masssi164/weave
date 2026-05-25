.PHONY: ci client-ci server-ci infra-static admin-ci acceptance-contract docs-build docs-check docs-serve docs-structure-check release-notes-check release-notes-label-check release-evidence-check live-stack-help doctor

GRADLE ?= ./gradlew

ci:
	$(GRADLE) ci

doctor:
	$(GRADLE) doctor

client-ci:
	$(GRADLE) clientCi

server-ci:
	$(GRADLE) serverCi

infra-static:
	$(GRADLE) infraStatic

admin-ci:
	$(GRADLE) adminCi

acceptance-contract:
	$(GRADLE) acceptanceContract

# Install docs tooling with: python3 -m pip install -r docs/requirements.txt
docs-build:
	$(GRADLE) docsBuild

docs-check:
	$(GRADLE) docsCheck

docs-serve:
	$(GRADLE) docsServe

docs-structure-check:
	$(GRADLE) docsStructureCheck

release-notes-check:
	$(GRADLE) releaseEvidenceCheck

release-evidence-check:
	$(GRADLE) releaseEvidenceCheck

release-notes-label-check:
	$(GRADLE) releaseNotesLabelCheck

live-stack-help:
	$(GRADLE) liveStackHelp
