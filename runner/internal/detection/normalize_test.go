package detection

import (
	"reflect"
	"testing"
	"time"
)

func TestNormalizeProducesAStableDigestIndependentOfInputOrder(t *testing.T) {
	now := time.Date(2026, 8, 27, 12, 0, 0, 0, time.UTC)
	repository := Entity{
		LocalKey: "repo:home-core",
		Type: "repository",
		DisplayName: "home-core",
		Aliases: []string{"git:home-core", "git:home-core"},
		Attributes: map[string]any{"language": "java"},
		Evidence: []Evidence{{Kind: "DECLARATION", Source: "capability://internal.topology"}},
	}
	service := Entity{LocalKey: "service:nextcloud", Type: "service", DisplayName: "Nextcloud"}
	relation := Relation{
		From: repository.LocalKey,
		Type: "deploys",
		To: service.LocalKey,
		Confidence: 1,
		Evidence: []Evidence{{Kind: "COMPOSE_DECLARATION", Source: "urn:weave:test:compose"}},
	}
	first, err := Normalize(Batch{
		RunnerID: "runner_example_01",
		DetectorID: "internal.topology",
		DetectorVersion: "1.0.0",
		SourceKind: SourceDeclaration,
		Scope: "home-core",
		ObservedAt: now,
		TTLSeconds: 300,
		Entities: []Entity{service, repository},
		Relations: []Relation{relation},
	}, now)
	if err != nil {
		t.Fatalf("normalize first batch: %v", err)
	}
	second, err := Normalize(Batch{
		RunnerID: "runner_example_01",
		DetectorID: "internal.topology",
		DetectorVersion: "1.0.0",
		SourceKind: SourceDeclaration,
		Scope: "home-core",
		ObservedAt: now,
		TTLSeconds: 300,
		Entities: []Entity{repository, service},
		Relations: []Relation{relation},
	}, now)
	if err != nil {
		t.Fatalf("normalize second batch: %v", err)
	}
	if first.Digest != second.Digest {
		t.Fatalf("digest changed with input order: %s != %s", first.Digest, second.Digest)
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
	_, err := Normalize(Batch{
		RunnerID: "runner_example_01",
		DetectorID: "internal.topology",
		DetectorVersion: "1.0.0",
		SourceKind: SourceDetector,
		Scope: "internal",
		ObservedAt: now,
		TTLSeconds: 300,
		Entities: []Entity{{
			LocalKey: "service:internal",
			Type: "service",
			Attributes: map[string]any{"clientSecret": "must-not-leave-runner"},
		}},
	}, now)
	if err == nil {
		t.Fatal("sensitive observation attribute was accepted")
	}
}

func TestNormalizeRejectsRelationsOutsideTheSameEvidenceBatch(t *testing.T) {
	now := time.Now().UTC()
	_, err := Normalize(Batch{
		RunnerID: "runner_example_01",
		DetectorID: "internal.topology",
		DetectorVersion: "1.0.0",
		SourceKind: SourceDetector,
		Scope: "internal",
		ObservedAt: now,
		TTLSeconds: 300,
		Entities: []Entity{{LocalKey: "service:a", Type: "service"}},
		Relations: []Relation{{From: "service:a", Type: "calls", To: "service:b", Confidence: 0.8}},
	}, now)
	if err == nil {
		t.Fatal("dangling relation was accepted")
	}
}

func TestExpirationIsDerivedFromObservedTimeAndTTL(t *testing.T) {
	now := time.Date(2026, 8, 27, 12, 0, 0, 0, time.UTC)
	batch, err := Normalize(Batch{
		RunnerID: "runner_example_01",
		DetectorID: "internal.topology",
		DetectorVersion: "1.0.0",
		SourceKind: SourceContract,
		Scope: "home-core",
		ObservedAt: now,
		TTLSeconds: 600,
		Entities: []Entity{{LocalKey: "repo:home-core", Type: "repository"}},
	}, now)
	if err != nil {
		t.Fatalf("normalize: %v", err)
	}
	if want := now.Add(10 * time.Minute); !batch.ExpiresAt().Equal(want) {
		t.Fatalf("unexpected expiry: got %s want %s", batch.ExpiresAt(), want)
	}
}
