package protocol

import "time"

type ArtifactReceipt struct {
	ArtifactID string `json:"artifactId"`
	Digest     string `json:"digest"`
	Size       int64  `json:"size"`
	URI        string `json:"uri"`
}
type TaskReceipt struct {
	TaskID        string   `json:"taskId"`
	State         string   `json:"state"`
	StateRevision int64    `json:"stateRevision"`
	ArtifactURIs  []string `json:"artifactUris,omitempty"`
}
type ObservationEvidence struct {
	Kind      string `json:"kind"`
	Reference string `json:"reference"`
	Digest    string `json:"digest,omitempty"`
}
type ObservationEntity struct {
	LocalKey    string                `json:"localKey"`
	Kind        string                `json:"kind"`
	DisplayName string                `json:"displayName,omitempty"`
	Aliases     []string              `json:"aliases,omitempty"`
	Attributes  map[string]any        `json:"attributes,omitempty"`
	Evidence    []ObservationEvidence `json:"evidence,omitempty"`
}
type ObservationRelation struct {
	FromLocalKey string                `json:"fromLocalKey"`
	Predicate    string                `json:"predicate"`
	ToLocalKey   string                `json:"toLocalKey"`
	Confidence   float64               `json:"confidence"`
	Attributes   map[string]any        `json:"attributes,omitempty"`
	Evidence     []ObservationEvidence `json:"evidence,omitempty"`
}
type ObservationBatch struct {
	SchemaVersion   string                `json:"schemaVersion"`
	RunnerID        string                `json:"runnerId"`
	DetectorID      string                `json:"detectorId"`
	DetectorVersion string                `json:"detectorVersion"`
	SourceKind      string                `json:"sourceKind"`
	Scope           string                `json:"scope,omitempty"`
	ObservedAt      time.Time             `json:"observedAt"`
	TTLSeconds      int                   `json:"ttlSeconds"`
	Entities        []ObservationEntity   `json:"entities"`
	Relations       []ObservationRelation `json:"relations"`
	BatchDigest     string                `json:"batchDigest,omitempty"`
	Traceparent     string                `json:"traceparent,omitempty"`
}
type ObservationReceipt struct {
	BatchID           string `json:"batchId"`
	AcceptedEntities  int    `json:"acceptedEntities"`
	AcceptedRelations int    `json:"acceptedRelations"`
}
