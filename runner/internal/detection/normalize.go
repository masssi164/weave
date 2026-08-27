package detection

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"regexp"
	"sort"
	"strings"
	"time"

	"github.com/masssi164/weave/runner/internal/protocol"
)

const (
	maximumEntities          = 4096
	maximumRelations         = 8192
	maximumAliasesPerEntity  = 32
	maximumEvidencePerObject = 32
	maximumAttributesBytes   = 32 * 1024

	SourceDeclaration = "DECLARATION"
	SourceOpenAPI     = "OPENAPI"
	SourceAsyncAPI    = "ASYNCAPI"
	SourceSBOM        = "SBOM"
	SourceOTel        = "OTEL"
	SourceRuntime     = "RUNTIME"
	SourceCustom      = "CUSTOM"
)

var (
	identifierPattern      = regexp.MustCompile(`^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$`)
	semanticVersionPattern = regexp.MustCompile(`^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$`)
	runnerIDPattern        = regexp.MustCompile(`^runner_[A-Za-z0-9_-]{8,128}$`)
	traceparentPattern     = regexp.MustCompile(`^[0-9a-f]{2}-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$`)
)

var acceptedSourceKinds = map[string]struct{}{
	SourceDeclaration: {},
	SourceOpenAPI:     {},
	SourceAsyncAPI:    {},
	SourceSBOM:        {},
	SourceOTel:        {},
	SourceRuntime:     {},
	SourceCustom:      {},
}

var acceptedEvidenceKinds = map[string]struct{}{
	"DECLARATION": {},
	"DOCUMENT":    {},
	"SCHEMA":      {},
	"SBOM":        {},
	"TRACE":       {},
	"RUNTIME":     {},
	"HASH":        {},
	"CUSTOM":      {},
}

// Normalize validates one detector response against the public observation contract, removes
// ordering ambiguity and computes the digest used by the Engine for idempotent reconciliation. It
// never infers entities or relations and never widens the submitted evidence.
func Normalize(batch protocol.ObservationBatch, now time.Time) (protocol.ObservationBatch, error) {
	if batch.SchemaVersion != "weave.runner.observation/v1" {
		return protocol.ObservationBatch{}, errors.New("observation schema version is unsupported")
	}
	if !runnerIDPattern.MatchString(batch.RunnerID) {
		return protocol.ObservationBatch{}, errors.New("runner ID is invalid")
	}
	batch.DetectorID = strings.TrimSpace(batch.DetectorID)
	batch.DetectorVersion = strings.TrimSpace(batch.DetectorVersion)
	if !identifier(batch.DetectorID) || !semanticVersion(batch.DetectorVersion) {
		return protocol.ObservationBatch{}, errors.New("detector coordinate is invalid")
	}
	if _, accepted := acceptedSourceKinds[batch.SourceKind]; !accepted {
		return protocol.ObservationBatch{}, errors.New("observation source kind is unsupported")
	}
	batch.Scope = strings.TrimSpace(batch.Scope)
	if len(batch.Scope) > 256 {
		return protocol.ObservationBatch{}, errors.New("observation scope exceeds the accepted bound")
	}
	if batch.ObservedAt.IsZero() || batch.ObservedAt.After(now.Add(5*time.Minute)) {
		return protocol.ObservationBatch{}, errors.New("observation time is invalid")
	}
	if batch.TTLSeconds < 30 || batch.TTLSeconds > int((30*24*time.Hour)/time.Second) {
		return protocol.ObservationBatch{}, errors.New("observation TTL is outside the accepted bound")
	}
	if len(batch.Entities) > maximumEntities {
		return protocol.ObservationBatch{}, errors.New("observation entity count exceeds the accepted bound")
	}
	if len(batch.Relations) > maximumRelations {
		return protocol.ObservationBatch{}, errors.New("observation relation count exceeds the accepted bound")
	}
	if batch.Traceparent != "" && !traceparentPattern.MatchString(batch.Traceparent) {
		return protocol.ObservationBatch{}, errors.New("traceparent is invalid")
	}

	entities := make([]protocol.ObservationEntity, 0, len(batch.Entities))
	keys := make(map[string]string, len(batch.Entities))
	for _, entity := range batch.Entities {
		normalized, err := normalizeEntity(entity)
		if err != nil {
			return protocol.ObservationBatch{}, err
		}
		if previous, exists := keys[normalized.LocalKey]; exists {
			return protocol.ObservationBatch{}, fmt.Errorf(
				"duplicate entity key %q (kinds %q and %q)",
				normalized.LocalKey,
				previous,
				normalized.Kind,
			)
		}
		keys[normalized.LocalKey] = normalized.Kind
		entities = append(entities, normalized)
	}
	sort.Slice(entities, func(left, right int) bool {
		return entities[left].LocalKey < entities[right].LocalKey
	})

	relations := make([]protocol.ObservationRelation, 0, len(batch.Relations))
	relationKeys := make(map[string]struct{}, len(batch.Relations))
	for _, relation := range batch.Relations {
		normalized, err := normalizeRelation(relation, keys)
		if err != nil {
			return protocol.ObservationBatch{}, err
		}
		key := normalized.FromLocalKey + "\x00" + normalized.Predicate + "\x00" + normalized.ToLocalKey
		if _, exists := relationKeys[key]; exists {
			return protocol.ObservationBatch{}, fmt.Errorf(
				"duplicate relation %s -> %s -> %s",
				normalized.FromLocalKey,
				normalized.Predicate,
				normalized.ToLocalKey,
			)
		}
		relationKeys[key] = struct{}{}
		relations = append(relations, normalized)
	}
	sort.Slice(relations, func(left, right int) bool {
		if relations[left].FromLocalKey != relations[right].FromLocalKey {
			return relations[left].FromLocalKey < relations[right].FromLocalKey
		}
		if relations[left].Predicate != relations[right].Predicate {
			return relations[left].Predicate < relations[right].Predicate
		}
		return relations[left].ToLocalKey < relations[right].ToLocalKey
	})

	normalized := protocol.ObservationBatch{
		SchemaVersion:   batch.SchemaVersion,
		RunnerID:        batch.RunnerID,
		DetectorID:      batch.DetectorID,
		DetectorVersion: batch.DetectorVersion,
		SourceKind:      batch.SourceKind,
		Scope:           batch.Scope,
		ObservedAt:      batch.ObservedAt.UTC(),
		TTLSeconds:      batch.TTLSeconds,
		Entities:        entities,
		Relations:       relations,
		Traceparent:     batch.Traceparent,
	}
	digest, err := digest(normalized)
	if err != nil {
		return protocol.ObservationBatch{}, err
	}
	normalized.BatchDigest = digest
	return normalized, nil
}

func ExpiresAt(batch protocol.ObservationBatch) time.Time {
	return batch.ObservedAt.Add(time.Duration(batch.TTLSeconds) * time.Second)
}

func normalizeEntity(entity protocol.ObservationEntity) (protocol.ObservationEntity, error) {
	entity.LocalKey = strings.TrimSpace(entity.LocalKey)
	entity.Kind = strings.TrimSpace(entity.Kind)
	entity.DisplayName = strings.TrimSpace(entity.DisplayName)
	if !localKey(entity.LocalKey) || !identifier(entity.Kind) {
		return protocol.ObservationEntity{}, errors.New("observation entity identity is invalid")
	}
	if len(entity.DisplayName) > 256 {
		return protocol.ObservationEntity{}, errors.New("entity display name exceeds the accepted bound")
	}
	aliases, err := uniqueStrings(entity.Aliases, maximumAliasesPerEntity, 512)
	if err != nil {
		return protocol.ObservationEntity{}, fmt.Errorf("entity %s aliases: %w", entity.LocalKey, err)
	}
	attributes, err := safeAttributes(entity.Attributes, 64)
	if err != nil {
		return protocol.ObservationEntity{}, fmt.Errorf("entity %s attributes: %w", entity.LocalKey, err)
	}
	evidence, err := normalizeEvidence(entity.Evidence)
	if err != nil {
		return protocol.ObservationEntity{}, fmt.Errorf("entity %s evidence: %w", entity.LocalKey, err)
	}
	return protocol.ObservationEntity{
		LocalKey:    entity.LocalKey,
		Kind:        entity.Kind,
		DisplayName: entity.DisplayName,
		Aliases:     aliases,
		Attributes:  attributes,
		Evidence:    evidence,
	}, nil
}

func normalizeRelation(
	relation protocol.ObservationRelation,
	entities map[string]string,
) (protocol.ObservationRelation, error) {
	relation.FromLocalKey = strings.TrimSpace(relation.FromLocalKey)
	relation.ToLocalKey = strings.TrimSpace(relation.ToLocalKey)
	relation.Predicate = strings.TrimSpace(relation.Predicate)
	if _, exists := entities[relation.FromLocalKey]; !exists {
		return protocol.ObservationRelation{}, fmt.Errorf(
			"relation source %q is absent from the same batch",
			relation.FromLocalKey,
		)
	}
	if _, exists := entities[relation.ToLocalKey]; !exists {
		return protocol.ObservationRelation{}, fmt.Errorf(
			"relation target %q is absent from the same batch",
			relation.ToLocalKey,
		)
	}
	if !identifier(relation.Predicate) || relation.Confidence < 0 || relation.Confidence > 1 {
		return protocol.ObservationRelation{}, errors.New("relation predicate or confidence is invalid")
	}
	attributes, err := safeAttributes(relation.Attributes, 32)
	if err != nil {
		return protocol.ObservationRelation{}, fmt.Errorf(
			"relation %s attributes: %w",
			relation.Predicate,
			err,
		)
	}
	evidence, err := normalizeEvidence(relation.Evidence)
	if err != nil {
		return protocol.ObservationRelation{}, fmt.Errorf(
			"relation %s evidence: %w",
			relation.Predicate,
			err,
		)
	}
	return protocol.ObservationRelation{
		FromLocalKey: relation.FromLocalKey,
		Predicate:    relation.Predicate,
		ToLocalKey:   relation.ToLocalKey,
		Confidence:   relation.Confidence,
		Attributes:   attributes,
		Evidence:     evidence,
	}, nil
}

func normalizeEvidence(values []protocol.ObservationEvidence) ([]protocol.ObservationEvidence, error) {
	if len(values) > maximumEvidencePerObject {
		return nil, errors.New("evidence count exceeds the accepted bound")
	}
	result := make([]protocol.ObservationEvidence, 0, len(values))
	seen := make(map[string]struct{}, len(values))
	for _, value := range values {
		value.Kind = strings.TrimSpace(value.Kind)
		value.Reference = strings.TrimSpace(value.Reference)
		value.Digest = strings.TrimSpace(value.Digest)
		if _, accepted := acceptedEvidenceKinds[value.Kind]; !accepted {
			return nil, errors.New("evidence kind is unsupported")
		}
		if value.Reference == "" || len(value.Reference) > 1024 {
			return nil, errors.New("evidence reference is invalid")
		}
		if value.Digest != "" && !sha256Digest(value.Digest) {
			return nil, errors.New("evidence digest must be SHA-256")
		}
		key := value.Kind + "\x00" + value.Reference + "\x00" + value.Digest
		if _, exists := seen[key]; exists {
			continue
		}
		seen[key] = struct{}{}
		result = append(result, value)
	}
	sort.Slice(result, func(left, right int) bool {
		if result[left].Kind != result[right].Kind {
			return result[left].Kind < result[right].Kind
		}
		if result[left].Reference != result[right].Reference {
			return result[left].Reference < result[right].Reference
		}
		return result[left].Digest < result[right].Digest
	})
	return result, nil
}

func safeAttributes(attributes map[string]any, maximumProperties int) (map[string]any, error) {
	if len(attributes) == 0 {
		return nil, nil
	}
	if len(attributes) > maximumProperties {
		return nil, errors.New("attribute count exceeds the accepted bound")
	}
	for key, value := range attributes {
		if key == "" || len(key) > 128 {
			return nil, fmt.Errorf("attribute key %q is invalid", key)
		}
		if sensitiveKey(key) {
			return nil, fmt.Errorf("sensitive attribute key %q is forbidden", key)
		}
		if !scalarJSONValue(value) {
			return nil, fmt.Errorf("attribute %q is not a scalar JSON value", key)
		}
	}
	raw, err := json.Marshal(attributes)
	if err != nil {
		return nil, errors.New("attributes are not JSON serializable")
	}
	if len(raw) > maximumAttributesBytes {
		return nil, errors.New("attributes exceed the accepted byte bound")
	}
	var copied map[string]any
	if err := json.Unmarshal(raw, &copied); err != nil {
		return nil, err
	}
	return copied, nil
}

func scalarJSONValue(value any) bool {
	switch value.(type) {
	case nil,
		bool,
		string,
		json.Number,
		float32,
		float64,
		int,
		int8,
		int16,
		int32,
		int64,
		uint,
		uint8,
		uint16,
		uint32,
		uint64:
		return true
	default:
		return false
	}
}

func digest(batch protocol.ObservationBatch) (string, error) {
	batch.BatchDigest = ""
	raw, err := json.Marshal(batch)
	if err != nil {
		return "", err
	}
	sum := sha256.Sum256(raw)
	return "sha256:" + hex.EncodeToString(sum[:]), nil
}

func uniqueStrings(values []string, maximumItems, maximumLength int) ([]string, error) {
	if len(values) > maximumItems {
		return nil, errors.New("item count exceeds the accepted bound")
	}
	seen := make(map[string]struct{}, len(values))
	result := make([]string, 0, len(values))
	for _, value := range values {
		value = strings.TrimSpace(value)
		if value == "" || len(value) > maximumLength {
			return nil, errors.New("item is invalid")
		}
		if _, exists := seen[value]; exists {
			continue
		}
		seen[value] = struct{}{}
		result = append(result, value)
	}
	sort.Strings(result)
	return result, nil
}

func identifier(value string) bool {
	return len(value) <= 128 && identifierPattern.MatchString(value)
}

func localKey(value string) bool {
	return value != "" && len(value) <= 512 && !strings.ContainsAny(value, "\r\n\x00")
}

func semanticVersion(value string) bool {
	return len(value) <= 96 && semanticVersionPattern.MatchString(value)
}

func sha256Digest(value string) bool {
	if !strings.HasPrefix(value, "sha256:") || len(value) != len("sha256:")+64 {
		return false
	}
	_, err := hex.DecodeString(strings.TrimPrefix(value, "sha256:"))
	return err == nil
}

func sensitiveKey(value string) bool {
	normalized := strings.ToLower(strings.NewReplacer("-", "_", ".", "_").Replace(value))
	for _, marker := range []string{
		"password",
		"passwd",
		"secret",
		"token",
		"authorization",
		"cookie",
		"credential",
		"private_key",
		"api_key",
		"client_secret",
	} {
		if strings.Contains(normalized, marker) {
			return true
		}
	}
	return false
}
