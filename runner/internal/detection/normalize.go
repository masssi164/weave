package detection

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net/url"
	"sort"
	"strings"
	"time"
)

const (
	maximumEntities          = 1000
	maximumRelations         = 5000
	maximumAliasesPerEntity  = 32
	maximumEvidencePerObject = 32
	maximumAttributesBytes   = 32 * 1024
)

type SourceKind string

const (
	SourceDeclaration SourceKind = "DECLARATION"
	SourceContract    SourceKind = "MACHINE_CONTRACT"
	SourceRuntime     SourceKind = "RUNTIME_OBSERVATION"
	SourceDetector    SourceKind = "DETERMINISTIC_DETECTOR"
	SourceHeuristic   SourceKind = "HEURISTIC_PROPOSAL"
)

type Evidence struct {
	Kind   string `json:"kind"`
	Source string `json:"source"`
	Digest string `json:"digest,omitempty"`
}

type Entity struct {
	LocalKey   string         `json:"localKey"`
	Type       string         `json:"type"`
	DisplayName string        `json:"displayName,omitempty"`
	Aliases    []string       `json:"aliases,omitempty"`
	Attributes map[string]any `json:"attributes,omitempty"`
	Evidence   []Evidence     `json:"evidence,omitempty"`
}

type Relation struct {
	From       string         `json:"from"`
	Type       string         `json:"type"`
	To         string         `json:"to"`
	Confidence float64        `json:"confidence"`
	Attributes map[string]any `json:"attributes,omitempty"`
	Evidence   []Evidence     `json:"evidence,omitempty"`
}

type Batch struct {
	RunnerID        string     `json:"runnerId"`
	DetectorID      string     `json:"detectorId"`
	DetectorVersion string     `json:"detectorVersion"`
	SourceKind      SourceKind `json:"sourceKind"`
	Scope           string     `json:"scope"`
	ObservedAt      time.Time  `json:"observedAt"`
	TTLSeconds      int64      `json:"ttlSeconds"`
	Entities        []Entity   `json:"entities"`
	Relations       []Relation `json:"relations"`
	Digest          string     `json:"digest"`
}

// Normalize validates one detector response, strips ordering ambiguity and computes the digest
// that the Engine uses for idempotent observation reconciliation. It never infers new entities or
// relations.
func Normalize(batch Batch, now time.Time) (Batch, error) {
	if !strings.HasPrefix(batch.RunnerID, "runner_") {
		return Batch{}, errors.New("runner ID is invalid")
	}
	if !coordinate(batch.DetectorID) || !version(batch.DetectorVersion) {
		return Batch{}, errors.New("detector coordinate is invalid")
	}
	if !validSourceKind(batch.SourceKind) {
		return Batch{}, errors.New("observation source kind is unsupported")
	}
	if batch.Scope == "" || len(batch.Scope) > 256 {
		return Batch{}, errors.New("observation scope is invalid")
	}
	if batch.ObservedAt.IsZero() || batch.ObservedAt.After(now.Add(5*time.Minute)) {
		return Batch{}, errors.New("observation time is invalid")
	}
	if batch.TTLSeconds < 30 || batch.TTLSeconds > int64((30*24*time.Hour)/time.Second) {
		return Batch{}, errors.New("observation TTL is outside the accepted bound")
	}
	if len(batch.Entities) == 0 || len(batch.Entities) > maximumEntities {
		return Batch{}, errors.New("observation entity count is outside the accepted bound")
	}
	if len(batch.Relations) > maximumRelations {
		return Batch{}, errors.New("observation relation count exceeds the accepted bound")
	}

	entities := make([]Entity, 0, len(batch.Entities))
	keys := make(map[string]string, len(batch.Entities))
	for _, entity := range batch.Entities {
		normalized, err := normalizeEntity(entity)
		if err != nil {
			return Batch{}, err
		}
		if previous, exists := keys[normalized.LocalKey]; exists {
			return Batch{}, fmt.Errorf("duplicate entity key %q (types %q and %q)", normalized.LocalKey, previous, normalized.Type)
		}
		keys[normalized.LocalKey] = normalized.Type
		entities = append(entities, normalized)
	}
	sort.Slice(entities, func(left, right int) bool { return entities[left].LocalKey < entities[right].LocalKey })

	relations := make([]Relation, 0, len(batch.Relations))
	relationKeys := make(map[string]struct{}, len(batch.Relations))
	for _, relation := range batch.Relations {
		normalized, err := normalizeRelation(relation, keys)
		if err != nil {
			return Batch{}, err
		}
		key := normalized.From + "\x00" + normalized.Type + "\x00" + normalized.To
		if _, exists := relationKeys[key]; exists {
			return Batch{}, fmt.Errorf("duplicate relation %s -> %s -> %s", normalized.From, normalized.Type, normalized.To)
		}
		relationKeys[key] = struct{}{}
		relations = append(relations, normalized)
	}
	sort.Slice(relations, func(left, right int) bool {
		if relations[left].From != relations[right].From {
			return relations[left].From < relations[right].From
		}
		if relations[left].Type != relations[right].Type {
			return relations[left].Type < relations[right].Type
		}
		return relations[left].To < relations[right].To
	})

	normalized := Batch{
		RunnerID:        batch.RunnerID,
		DetectorID:      batch.DetectorID,
		DetectorVersion: batch.DetectorVersion,
		SourceKind:      batch.SourceKind,
		Scope:           strings.TrimSpace(batch.Scope),
		ObservedAt:      batch.ObservedAt.UTC(),
		TTLSeconds:      batch.TTLSeconds,
		Entities:        entities,
		Relations:       relations,
	}
	digest, err := digest(normalized)
	if err != nil {
		return Batch{}, err
	}
	normalized.Digest = digest
	return normalized, nil
}

func (batch Batch) ExpiresAt() time.Time {
	return batch.ObservedAt.Add(time.Duration(batch.TTLSeconds) * time.Second)
}

func normalizeEntity(entity Entity) (Entity, error) {
	entity.LocalKey = strings.TrimSpace(entity.LocalKey)
	entity.Type = strings.TrimSpace(entity.Type)
	entity.DisplayName = strings.TrimSpace(entity.DisplayName)
	if !localKey(entity.LocalKey) || !coordinate(entity.Type) || len(entity.DisplayName) > 512 {
		return Entity{}, errors.New("observation entity identity is invalid")
	}
	aliases := uniqueStrings(entity.Aliases, maximumAliasesPerEntity)
	if aliases == nil && len(entity.Aliases) > 0 {
		return Entity{}, errors.New("entity aliases are invalid or exceed the accepted bound")
	}
	attributes, err := safeAttributes(entity.Attributes)
	if err != nil {
		return Entity{}, fmt.Errorf("entity %s attributes: %w", entity.LocalKey, err)
	}
	evidence, err := normalizeEvidence(entity.Evidence)
	if err != nil {
		return Entity{}, fmt.Errorf("entity %s evidence: %w", entity.LocalKey, err)
	}
	return Entity{
		LocalKey: entity.LocalKey,
		Type: entity.Type,
		DisplayName: entity.DisplayName,
		Aliases: aliases,
		Attributes: attributes,
		Evidence: evidence,
	}, nil
}

func normalizeRelation(relation Relation, entities map[string]string) (Relation, error) {
	relation.From = strings.TrimSpace(relation.From)
	relation.To = strings.TrimSpace(relation.To)
	relation.Type = strings.TrimSpace(relation.Type)
	if _, exists := entities[relation.From]; !exists {
		return Relation{}, fmt.Errorf("relation source %q is absent from the same batch", relation.From)
	}
	if _, exists := entities[relation.To]; !exists {
		return Relation{}, fmt.Errorf("relation target %q is absent from the same batch", relation.To)
	}
	if !coordinate(relation.Type) || relation.Confidence < 0 || relation.Confidence > 1 {
		return Relation{}, errors.New("relation type or confidence is invalid")
	}
	attributes, err := safeAttributes(relation.Attributes)
	if err != nil {
		return Relation{}, fmt.Errorf("relation %s attributes: %w", relation.Type, err)
	}
	evidence, err := normalizeEvidence(relation.Evidence)
	if err != nil {
		return Relation{}, fmt.Errorf("relation %s evidence: %w", relation.Type, err)
	}
	return Relation{
		From: relation.From,
		Type: relation.Type,
		To: relation.To,
		Confidence: relation.Confidence,
		Attributes: attributes,
		Evidence: evidence,
	}, nil
}

func normalizeEvidence(values []Evidence) ([]Evidence, error) {
	if len(values) > maximumEvidencePerObject {
		return nil, errors.New("evidence count exceeds the accepted bound")
	}
	result := make([]Evidence, 0, len(values))
	seen := make(map[string]struct{}, len(values))
	for _, value := range values {
		value.Kind = strings.TrimSpace(value.Kind)
		value.Source = strings.TrimSpace(value.Source)
		value.Digest = strings.TrimSpace(value.Digest)
		if !coordinate(value.Kind) || len(value.Source) > 2048 {
			return nil, errors.New("evidence identity is invalid")
		}
		parsed, err := url.Parse(value.Source)
		if err != nil || parsed.Scheme == "" || parsed.Fragment != "" {
			return nil, errors.New("evidence source must be an absolute URI without a fragment")
		}
		if value.Digest != "" && !sha256Digest(value.Digest) {
			return nil, errors.New("evidence digest must be SHA-256")
		}
		key := value.Kind + "\x00" + value.Source + "\x00" + value.Digest
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
		if result[left].Source != result[right].Source {
			return result[left].Source < result[right].Source
		}
		return result[left].Digest < result[right].Digest
	})
	return result, nil
}

func safeAttributes(attributes map[string]any) (map[string]any, error) {
	if len(attributes) == 0 {
		return nil, nil
	}
	for key, value := range attributes {
		if sensitiveKey(key) {
			return nil, fmt.Errorf("sensitive attribute key %q is forbidden", key)
		}
		if err := inspectValue(value, 0); err != nil {
			return nil, fmt.Errorf("attribute %q: %w", key, err)
		}
	}
	raw, err := json.Marshal(attributes)
	if err != nil {
		return nil, errors.New("attributes are not JSON serializable")
	}
	if len(raw) > maximumAttributesBytes {
		return nil, errors.New("attributes exceed the accepted byte bound")
	}
	var copy map[string]any
	if err := json.Unmarshal(raw, &copy); err != nil {
		return nil, err
	}
	return copy, nil
}

func inspectValue(value any, depth int) error {
	if depth > 8 {
		return errors.New("nested value exceeds the accepted depth")
	}
	switch typed := value.(type) {
	case nil, bool, string, float64, float32, int, int8, int16, int32, int64, uint, uint8, uint16, uint32, uint64, json.Number:
		return nil
	case []any:
		if len(typed) > 256 {
			return errors.New("array exceeds the accepted item bound")
		}
		for _, item := range typed {
			if err := inspectValue(item, depth+1); err != nil {
				return err
			}
		}
		return nil
	case map[string]any:
		if len(typed) > 256 {
			return errors.New("object exceeds the accepted field bound")
		}
		for key, item := range typed {
			if sensitiveKey(key) {
				return fmt.Errorf("sensitive nested key %q is forbidden", key)
			}
			if err := inspectValue(item, depth+1); err != nil {
				return err
			}
		}
		return nil
	default:
		return fmt.Errorf("unsupported JSON value type %T", value)
	}
}

func digest(batch Batch) (string, error) {
	batch.Digest = ""
	raw, err := json.Marshal(batch)
	if err != nil {
		return "", err
	}
	sum := sha256.Sum256(raw)
	return "sha256:" + hex.EncodeToString(sum[:]), nil
}

func uniqueStrings(values []string, maximum int) []string {
	if len(values) > maximum {
		return nil
	}
	seen := make(map[string]struct{}, len(values))
	result := make([]string, 0, len(values))
	for _, value := range values {
		value = strings.TrimSpace(value)
		if value == "" || len(value) > 512 {
			return nil
		}
		if _, exists := seen[value]; exists {
			continue
		}
		seen[value] = struct{}{}
		result = append(result, value)
	}
	sort.Strings(result)
	return result
}

func validSourceKind(kind SourceKind) bool {
	switch kind {
	case SourceDeclaration, SourceContract, SourceRuntime, SourceDetector, SourceHeuristic:
		return true
	default:
		return false
	}
}

func coordinate(value string) bool {
	if value == "" || len(value) > 128 {
		return false
	}
	for index, character := range value {
		if character >= 'a' && character <= 'z' || character >= '0' && character <= '9' || character == '.' || character == '-' || character == '_' {
			continue
		}
		if index > 0 && character >= 'A' && character <= 'Z' {
			continue
		}
		return false
	}
	return true
}

func localKey(value string) bool {
	return value != "" && len(value) <= 512 && !strings.ContainsAny(value, "\r\n\x00")
}

func version(value string) bool {
	parts := strings.Split(value, ".")
	if len(parts) != 3 {
		return false
	}
	for _, part := range parts {
		if part == "" {
			return false
		}
		for _, character := range part {
			if character < '0' || character > '9' {
				return false
			}
		}
	}
	return true
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
		"password", "passwd", "secret", "token", "authorization", "cookie", "credential", "private_key", "api_key", "client_secret",
	} {
		if strings.Contains(normalized, marker) {
			return true
		}
	}
	return false
}
