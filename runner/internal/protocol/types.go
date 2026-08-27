package protocol

import (
	"encoding/json"
	"time"
)

type CapabilityRef struct {
	ID      string `json:"id"`
	Version string `json:"version"`
}

type PublicCapability struct {
	ID                 string          `json:"id"`
	Version            string          `json:"version"`
	Title              string          `json:"title"`
	Description        string          `json:"description"`
	Effect             string          `json:"effect"`
	InputSchema        json.RawMessage `json:"inputSchema"`
	InputSchemaDigest  string          `json:"inputSchemaDigest"`
	OutputSchema       json.RawMessage `json:"outputSchema"`
	OutputSchemaDigest string          `json:"outputSchemaDigest"`
	TimeoutSeconds     int             `json:"timeoutSeconds"`
	MaxOutputBytes     int64           `json:"maxOutputBytes"`
	ArtifactTypes      []string        `json:"artifactTypes"`
}

type PublicCapabilityBundle struct {
	SchemaVersion string             `json:"schemaVersion"`
	BundleID      string             `json:"bundleId"`
	BundleVersion string             `json:"bundleVersion"`
	BundleDigest  string             `json:"bundleDigest"`
	Capabilities  []PublicCapability `json:"capabilities"`
}

type EnrollmentExchangeRequest struct {
	CSRPEM       string            `json:"csrPem"`
	BundleDigest string            `json:"bundleDigest"`
	RunnerVersion string           `json:"runnerVersion"`
	Labels       map[string]string `json:"labels,omitempty"`
}

type EnrollmentExchangeResponse struct {
	RunnerID             string    `json:"runnerId"`
	ClientCertificatePEM string    `json:"clientCertificatePem"`
	CACertificatePEM     string    `json:"caCertificatePem"`
	CertificateExpiresAt time.Time `json:"certificateExpiresAt"`
	ControlBaseURL       string    `json:"controlBaseUrl"`
}

type CertificateRotationRequest struct {
	CSRPEM string `json:"csrPem"`
}

type CertificateRotationResponse struct {
	ClientCertificatePEM string    `json:"clientCertificatePem"`
	CACertificatePEM     string    `json:"caCertificatePem"`
	CertificateExpiresAt time.Time `json:"certificateExpiresAt"`
}

type RunnerHeartbeat struct {
	RunnerID       string    `json:"runnerId"`
	RunnerVersion  string    `json:"runnerVersion"`
	BundleDigest   string    `json:"bundleDigest"`
	RunningTasks   int       `json:"runningTasks"`
	Capacity       int       `json:"capacity"`
	ObservedAt     time.Time `json:"observedAt"`
}

type TaskClaimRequest struct {
	RunnerID       string          `json:"runnerId"`
	BundleDigest   string          `json:"bundleDigest"`
	Capabilities   []CapabilityRef `json:"capabilities"`
	AvailableSlots int             `json:"availableSlots"`
}

type ResourceGrant struct {
	ResourceID      string   `json:"resourceId"`
	Operations      []string `json:"operations"`
	ExpectedVersion string   `json:"expectedVersion,omitempty"`
}

type TaskLease struct {
	SchemaVersion  string          `json:"schemaVersion"`
	TaskID         string          `json:"taskId"`
	LeaseID        string          `json:"leaseId"`
	FencingToken   int64           `json:"fencingToken"`
	RunnerID       string          `json:"runnerId"`
	Capability     CapabilityRef   `json:"capability"`
	BundleDigest   string          `json:"bundleDigest"`
	Attempt        int             `json:"attempt"`
	IdempotencyKey string          `json:"idempotencyKey"`
	Payload        json.RawMessage `json:"payload"`
	ContextRefs    []string        `json:"contextRefs,omitempty"`
	ResourceGrants []ResourceGrant `json:"resourceGrants"`
	IssuedAt       time.Time       `json:"issuedAt"`
	ExpiresAt      time.Time       `json:"expiresAt"`
	Deadline       *time.Time      `json:"deadline,omitempty"`
	Traceparent    string          `json:"traceparent,omitempty"`
}

type TaskLeaseReference struct {
	LeaseID      string `json:"leaseId"`
	FencingToken int64  `json:"fencingToken"`
}

type TaskLeaseRenewal struct {
	LeaseID        string    `json:"leaseId"`
	FencingToken   int64     `json:"fencingToken"`
	ExpiresAt      time.Time `json:"expiresAt"`
	CancelRequested bool     `json:"cancelRequested"`
}

type ArtifactDescriptor struct {
	ArtifactID   string `json:"artifactId"`
	Name         string `json:"name,omitempty"`
	ArtifactType string `json:"artifactType,omitempty"`
	MediaType    string `json:"mediaType"`
	Size         int64  `json:"size"`
	Digest       string `json:"digest"`
}

type ArtifactReceipt struct {
	ArtifactID string `json:"artifactId"`
	Digest     string `json:"digest"`
	Size       int64  `json:"size"`
	URI        string `json:"uri"`
}

type TaskError struct {
	Code      string `json:"code"`
	Message   string `json:"message"`
	Retryable bool   `json:"retryable"`
}

type TaskResult struct {
	SchemaVersion string               `json:"schemaVersion"`
	TaskID        string               `json:"taskId"`
	LeaseID       string               `json:"leaseId"`
	FencingToken  int64                `json:"fencingToken"`
	Capability    CapabilityRef        `json:"capability"`
	Status        string               `json:"status"`
	Result        json.RawMessage      `json:"result"`
	Artifacts     []ArtifactDescriptor `json:"artifacts"`
	Error         *TaskError           `json:"error,omitempty"`
	CompletedAt   time.Time            `json:"completedAt"`
	Traceparent   string               `json:"traceparent,omitempty"`
}

type TaskReceipt struct {
	TaskID       string   `json:"taskId"`
	State        string   `json:"state"`
	StateRevision int64   `json:"stateRevision"`
	ArtifactURIs []string `json:"artifactUris,omitempty"`
}

type ObservationEvidence struct {
	Kind      string `json:"kind"`
	Reference string `json:"reference"`
	Digest    string `json:"digest,omitempty"`
}

type ObservationEntity struct {
	LocalKey   string                 `json:"localKey"`
	Kind       string                 `json:"kind"`
	DisplayName string                `json:"displayName,omitempty"`
	Aliases    []string               `json:"aliases,omitempty"`
	Attributes map[string]any         `json:"attributes,omitempty"`
	Evidence   []ObservationEvidence  `json:"evidence,omitempty"`
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
	SchemaVersion  string                `json:"schemaVersion"`
	RunnerID       string                `json:"runnerId"`
	DetectorID     string                `json:"detectorId"`
	DetectorVersion string               `json:"detectorVersion"`
	SourceKind     string                `json:"sourceKind"`
	Scope          string                `json:"scope,omitempty"`
	ObservedAt     time.Time             `json:"observedAt"`
	TTLSeconds     int                   `json:"ttlSeconds"`
	Entities       []ObservationEntity   `json:"entities"`
	Relations      []ObservationRelation `json:"relations"`
	BatchDigest    string                `json:"batchDigest,omitempty"`
	Traceparent    string                `json:"traceparent,omitempty"`
}

type ObservationReceipt struct {
	BatchID          string `json:"batchId"`
	AcceptedEntities int    `json:"acceptedEntities"`
	AcceptedRelations int   `json:"acceptedRelations"`
}

type Problem struct {
	Type     string `json:"type"`
	Title    string `json:"title"`
	Status   int    `json:"status"`
	Detail   string `json:"detail,omitempty"`
	Instance string `json:"instance,omitempty"`
}
