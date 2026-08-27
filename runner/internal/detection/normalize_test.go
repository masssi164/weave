package detection

import (
	"reflect"
	"testing"
	"time"

	"github.com/masssi164/weave/runner/internal/protocol"
)

func TestNormalizeProducesAStableDigestIndependentOfInputOrder(t *testing.T) {
	now := time.Date(2026, 8, 27, 12, 0, 0, 0, time.UTC)
	repository := protocol.ObservationEntity{
		LocalKey:    "repo:home-core",
		Kind:        "repository",
		DisplayName: "home-core",
		Aliases:     []string{"git:home-core", "git:home-core"},
		Attributes:  map[string]any{"language": "java"},
		Evidence: []protocol.ObservationEvidence{{
			Kind:      "DECLARATION",
			Reference: "capability://internal.topology",
		}},
	}
	service := protocol.ObservationEntity{
		LocalKey:    "service:nextcloud",
		Kind:        "service",
		DisplayName: "Nextcloud",
	}
	relation := protocol.ObservationRelation{
		FromLocalKey: repository.LocalKey,
		Predicate:    "deploys",
		ToLocalKey:   service.LocalKey,
		Confidence:   1,
		Evidence: []protocol.ObservationEvidence{{
			Kind:      "DECLARATION",
			Reference: "urn:weave:test:compose",
		}},
	}
	first, err := Normalize(protocol.ObservationBatch{
		SchemaVersion:   "weave.runner.observation/v1",
		RunnerID:        "runner_example_01",
		DetectorID:      "internal.topology",
		DetectorVersion: "1.0.0",
		SourceKind:      SourceDeclaration,
		Scope:           "home-core",
		ObservedAt:      now,
		TTLSeconds:      300,
		Entities:        []protocol.ObservationEntity{service, repository},
		Relations:       []protocol.ObservationRelation{relation},
	}, now)
	if err != nil {
		t.Fatalf("normalize first batch: %v", err)
	}
	second, err := Normalize(protocol.ObservationBatch{
		SchemaVersion:   "weave.runner.observation/v1",
		RunnerID:        "runner_example_01",
		DetectorID:      "internal.topology",
		DetectorVersion: "1.0.0",
		SourceKind:      SourceDeclaration,
		Scope:           "home-core",
		ObservedAt:      now,
		TTLSeconds:      300,
		Entities:        []protocol.ObservationEntity{repository, service},
		Relations:       []protocol.ObservationRelation{relation},
	}, now)
	if err != nil {
		t.Fatalf("normalize second batch: %v", err)
	}
	if first.BatchDigest != second.BatchDigest {
		t.Fatalf("digest changed with input order: %s != %s", first.BatchDigest, second.BatchDigest)
	}
	if !reflect.DeepEqual(first.Entities, second.Entities) {
		t.Fatal("normalized entities differ")
	}
	if got := first.Entities[0].Aliases; len(got) != 1 || got[0] != "git:home-core" {
		t.Fatalf("aliases were not de-duplicated: %#v", got)
	}
}

func TestNormalizeRejectsSensitiveAttributes(t *testing.T) {
	now := time.Now().UTC()
	_, err := Normalize(protocol.ObservationBatch{
		SchemaVersion:   "weave.runner.observation/v1",
		RunnerID:        "runner_example_01",
		DetectorID:      "internal.topology",
		DetectorVersion: "1.0.0",
		SourceKind:      SourceCustom,
		Scope:           "internal",
		ObservedAt:      now,
		TTLSeconds:      300,
		Entities: []protocol.ObservationEntity{{
			LocalKey:  "service:internal",
			Kind:      "service",
			Attributes: map[string]any{"clientSecret": "must-not-leave-runner"},
		}},
	}, now)
	if err == nil {
		t.Fatal("sensitive observation attribute was accepted")
	}
}

func TestNormalizeRejectsRelationsOutsideTheSameEvidenceBatch(t *testing.T) {
	now := time.Now().UTC()
	_, err := Normalize(protocol.ObservationBatch{
		SchemaVersion:   "weave.runner.observation/v1",
		RunnerID:        "runner_example_01",
		DetectorID:      "internal.topology",
		DetectorVersion: "1.0.0",
		SourceKind:      SourceCustom,
		Scope:           "internal",
		ObservedAt:      now,
		TTLSeconds:      300,
		Entities: []protocol.ObservationEntity{{
			LocalKey: "service:a",
			Kind:     "service",
		}},
		Relations: []protocol.ObservationRelation{{
			FromLocalKey: "service:a",
			Predicate:    "calls",
			ToLocalKey:   "service:b",
			Confidence:   0.8,
		}},
	}, now)
	if err == nil {
		t.Fatal("dangling relation was accepted")
	}
}

func TestNormalizeRejectsEvidenceOutsideThePublicEnum(t *testing.T) {
	now := time.Now().UTC()
	_, err := Normalize(protocol.ObservationBatch{
		SchemaVersion:   "weave.runner.observation/v1",
		RunnerID:        "runner_example_01",
		DetectorID:      "internal.topology",
		DetectorVersion: "1.0.0",
		SourceKind:      SourceDeclaration,
		ObservedAt:      now,
		TTLSeconds:      300,
		Entities: []protocol.ObservationEntity{{
			LocalKey: "service:a",
			Kind:     "service",
			Evidence: []protocol.ObservationEvidence{{
				Kind:      "COMPOSE_DECLARATION",
				Reference: "urn:weave:test:compose",
			}},
		}},
	}, now)
	if err == nil {
		t.Fatal("unknown evidence kind was accepted")
	}
}

func TestNormalizeRejectsNestedAttributes(t *testing.T) {
	now := time.Now().UTC()
	_, err := Normalize(protocol.ObservationBatch{
		SchemaVersion:   "weave.runner.observation/v1",
		RunnerID:        "runner_example_01",
		DetectorID:      "internal.topology",
		DetectorVersion: "1.0.0",
		SourceKind:      SourceRuntime,
		ObservedAt:      now,
		TTLSeconds:      300,
		Entities: []protocol.ObservationEntity{{
			LocalKey:  "service:a",
			Kind:      "service",
			Attributes: map[string]any{"nested": map[string]any{"value": "forbidden"}},
		}},
	}, now)
	if err == nil {
		t.Fatal("nested attribute was accepted")
	}
}

func TestExpirationIsDerivedFromObservedTimeAndTTL(t *testing.T) {
	now := time.Date(2026, 8, 27, 12, 0, 0, 0, time.UTC)
	batch, err := Normalize(protocol.ObservationBatch{
		SchemaVersion:   "weave.runner.observation/v1",
		RunnerID:        "runner_example_01",
		DetectorID:      "internal.topology",
		DetectorVersion: "1.0.0",
		SourceKind:      SourceOpenAPI,
		Scope:           "home-core",
		ObservedAt:      now,
		TTLSeconds:      600,
		Entities: []protocol.ObservationEntity{{
			LocalKey: "repo:home-core",
			Kind:     "repository",
		}},
	}, now)
	if err != nil {
		t.Fatalf("normalize: %v", err)
	}
	if want := now.Add(10 * time.Minute); !ExpiresAt(batch).Equal(want) {
		t.Fatalf("unexpected expiry: got %s want %s", ExpiresAt(batch), want)
	}
}
