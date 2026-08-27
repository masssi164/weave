package bundle

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"

	"github.com/masssi164/weave/runner/internal/protocol"
	"github.com/santhosh-tekuri/jsonschema/v5"
)

const (
	maxBundleBytes = 4 * 1024 * 1024
	maxSchemaBytes = 256 * 1024
)

var (
	identifierPattern = regexp.MustCompile(`^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$`)
	versionPattern = regexp.MustCompile(`^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$`)
	environmentPattern = regexp.MustCompile(`^[A-Z_][A-Z0-9_]*$`)
)

type LocalBundle struct {
	SchemaVersion string            `json:"schemaVersion"`
	BundleID      string            `json:"bundleId"`
	BundleVersion string            `json:"bundleVersion"`
	DisplayName   string            `json:"displayName,omitempty"`
	Capabilities  []LocalCapability `json:"capabilities"`
	Detectors     []LocalDetector   `json:"detectors,omitempty"`
	Labels        map[string]string `json:"labels,omitempty"`
}

type LocalCapability struct {
	ID            string         `json:"id"`
	Version       string         `json:"version"`
	Title         string         `json:"title"`
	Description   string         `json:"description,omitempty"`
	InputSchema   string         `json:"inputSchema"`
	OutputSchema  string         `json:"outputSchema"`
	Effect        string         `json:"effect"`
	ArtifactTypes []string       `json:"artifactTypes,omitempty"`
	Execution     LocalExecution `json:"execution"`
}

type LocalDetector struct {
	ID              string         `json:"id"`
	Version         string         `json:"version"`
	SourceKind      string         `json:"sourceKind"`
	IntervalSeconds int            `json:"intervalSeconds,omitempty"`
	TTLSeconds      int            `json:"ttlSeconds,omitempty"`
	Execution       LocalExecution `json:"execution"`
}

type LocalExecution struct {
	Handler              string   `json:"handler"`
	Arguments            []string `json:"arguments,omitempty"`
	WorkingDirectory     string   `json:"workingDirectory,omitempty"`
	EnvironmentAllowlist []string `json:"environmentAllowlist,omitempty"`
	NetworkProfile       string   `json:"networkProfile,omitempty"`
	TimeoutSeconds       int      `json:"timeoutSeconds,omitempty"`
	MaxOutputBytes       int64    `json:"maxOutputBytes,omitempty"`
}

type Capability struct {
	Local        LocalCapability
	Reference    protocol.CapabilityRef
	InputRaw     json.RawMessage
	OutputRaw    json.RawMessage
	InputSchema  *jsonschema.Schema
	OutputSchema *jsonschema.Schema
}

type Loaded struct {
	Local        LocalBundle
	Public       protocol.PublicCapabilityBundle
	Raw          []byte
	Digest       string
	capabilities map[string]*Capability
}

func Load(path string) (*Loaded, error) {
	raw, err := readBoundedFile(path, maxBundleBytes)
	if err != nil {
		return nil, fmt.Errorf("read capability bundle: %w", err)
	}

	var local LocalBundle
	if err := decodeStrict(raw, &local); err != nil {
		return nil, fmt.Errorf("decode capability bundle: %w", err)
	}
	if err := validateBundle(local); err != nil {
		return nil, err
	}

	digest := digestBytes(raw)
	loaded := &Loaded{
		Local: local,
		Raw: append([]byte(nil), raw...),
		Digest: digest,
		capabilities: make(map[string]*Capability, len(local.Capabilities)),
	}
	loaded.Public = protocol.PublicCapabilityBundle{
		SchemaVersion: "weave.runner.public-capability-bundle/v1",
		BundleID: local.BundleID,
		BundleVersion: local.BundleVersion,
		BundleDigest: digest,
		Capabilities: make([]protocol.PublicCapability, 0, len(local.Capabilities)),
	}

	for _, declaration := range local.Capabilities {
		capability, public, err := loadCapability(declaration)
		if err != nil {
			return nil, fmt.Errorf("capability %s@%s: %w", declaration.ID, declaration.Version, err)
		}
		coordinate := capability.Reference.ID + "@" + capability.Reference.Version
		loaded.capabilities[coordinate] = capability
		loaded.Public.Capabilities = append(loaded.Public.Capabilities, public)
	}
	sort.Slice(loaded.Public.Capabilities, func(i, j int) bool {
		left := loaded.Public.Capabilities[i]
		right := loaded.Public.Capabilities[j]
		if left.ID == right.ID {
			return left.Version < right.Version
		}
		return left.ID < right.ID
	})
	return loaded, nil
}

func (loaded *Loaded) Find(reference protocol.CapabilityRef) (*Capability, bool) {
	if loaded == nil {
		return nil, false
	}
	value, ok := loaded.capabilities[reference.ID+"@"+reference.Version]
	return value, ok
}

func (loaded *Loaded) CapabilityReferences() []protocol.CapabilityRef {
	if loaded == nil {
		return nil
	}
	result := make([]protocol.CapabilityRef, 0, len(loaded.capabilities))
	for _, capability := range loaded.capabilities {
		result = append(result, capability.Reference)
	}
	sort.Slice(result, func(i, j int) bool {
		if result[i].ID == result[j].ID {
			return result[i].Version < result[j].Version
		}
		return result[i].ID < result[j].ID
	})
	return result
}

func loadCapability(declaration LocalCapability) (*Capability, protocol.PublicCapability, error) {
	inputRaw, err := readSchema(declaration.InputSchema)
	if err != nil {
		return nil, protocol.PublicCapability{}, fmt.Errorf("input schema: %w", err)
	}
	outputRaw, err := readSchema(declaration.OutputSchema)
	if err != nil {
		return nil, protocol.PublicCapability{}, fmt.Errorf("output schema: %w", err)
	}
	inputSchema, err := compileSchema(declaration.ID+"-input", inputRaw)
	if err != nil {
		return nil, protocol.PublicCapability{}, fmt.Errorf("compile input schema: %w", err)
	}
	outputSchema, err := compileSchema(declaration.ID+"-output", outputRaw)
	if err != nil {
		return nil, protocol.PublicCapability{}, fmt.Errorf("compile output schema: %w", err)
	}
	inputCompact := compactJSON(inputRaw)
	outputCompact := compactJSON(outputRaw)
	ref := protocol.CapabilityRef{ID: declaration.ID, Version: declaration.Version}
	capability := &Capability{
		Local: declaration,
		Reference: ref,
		InputRaw: inputCompact,
		OutputRaw: outputCompact,
		InputSchema: inputSchema,
		OutputSchema: outputSchema,
	}
	public := protocol.PublicCapability{
		ID: declaration.ID,
		Version: declaration.Version,
		Title: declaration.Title,
		Description: declaration.Description,
		Effect: declaration.Effect,
		InputSchema: inputCompact,
		InputSchemaDigest: digestBytes(inputCompact),
		OutputSchema: outputCompact,
		OutputSchemaDigest: digestBytes(outputCompact),
		TimeoutSeconds: declaration.Execution.TimeoutSeconds,
		MaxOutputBytes: declaration.Execution.MaxOutputBytes,
		ArtifactTypes: append([]string(nil), declaration.ArtifactTypes...),
	}
	return capability, public, nil
}

func validateBundle(bundle LocalBundle) error {
	if bundle.SchemaVersion != "weave.runner.capability-bundle/v1" {
		return errors.New("unsupported capability bundle schemaVersion")
	}
	if !identifierPattern.MatchString(bundle.BundleID) {
		return errors.New("bundleId has an invalid format")
	}
	if !versionPattern.MatchString(bundle.BundleVersion) {
		return errors.New("bundleVersion is not semantic versioning")
	}
	if len(bundle.Capabilities) == 0 || len(bundle.Capabilities) > 128 {
		return errors.New("capabilities must contain between one and 128 entries")
	}
	if len(bundle.Detectors) > 32 {
		return errors.New("detectors exceed the supported bound")
	}
	if len(bundle.Labels) > 32 {
		return errors.New("labels exceed the supported bound")
	}
	coordinates := make(map[string]struct{}, len(bundle.Capabilities))
	for index := range bundle.Capabilities {
		capability := &bundle.Capabilities[index]
		if err := validateCapability(capability); err != nil {
			return fmt.Errorf("capabilities[%d]: %w", index, err)
		}
		coordinate := capability.ID + "@" + capability.Version
		if _, exists := coordinates[coordinate]; exists {
			return fmt.Errorf("duplicate capability coordinate %s", coordinate)
		}
		coordinates[coordinate] = struct{}{}
	}
	detectors := make(map[string]struct{}, len(bundle.Detectors))
	for index := range bundle.Detectors {
		detector := &bundle.Detectors[index]
		if err := validateDetector(detector); err != nil {
			return fmt.Errorf("detectors[%d]: %w", index, err)
		}
		coordinate := detector.ID + "@" + detector.Version
		if _, exists := detectors[coordinate]; exists {
			return fmt.Errorf("duplicate detector coordinate %s", coordinate)
		}
		detectors[coordinate] = struct{}{}
	}
	return nil
}

func validateCapability(capability *LocalCapability) error {
	if !identifierPattern.MatchString(capability.ID) {
		return errors.New("id has an invalid format")
	}
	if !versionPattern.MatchString(capability.Version) {
		return errors.New("version is not semantic versioning")
	}
	if strings.TrimSpace(capability.Title) == "" || len(capability.Title) > 160 {
		return errors.New("title is required and must not exceed 160 characters")
	}
	if len(capability.Description) > 1000 {
		return errors.New("description exceeds the supported bound")
	}
	switch capability.Effect {
	case "READ_ONLY", "IDEMPOTENT_WRITE", "NON_IDEMPOTENT_WRITE":
	default:
		return errors.New("effect is unsupported")
	}
	if len(capability.ArtifactTypes) > 32 {
		return errors.New("artifactTypes exceed the supported bound")
	}
	if err := validateSchemaPath(capability.InputSchema); err != nil {
		return fmt.Errorf("inputSchema: %w", err)
	}
	if err := validateSchemaPath(capability.OutputSchema); err != nil {
		return fmt.Errorf("outputSchema: %w", err)
	}
	return validateExecution(&capability.Execution)
}

func validateDetector(detector *LocalDetector) error {
	if !identifierPattern.MatchString(detector.ID) {
		return errors.New("id has an invalid format")
	}
	if !versionPattern.MatchString(detector.Version) {
		return errors.New("version is not semantic versioning")
	}
	switch detector.SourceKind {
	case "DECLARATION", "OPENAPI", "ASYNCAPI", "SBOM", "OTEL", "RUNTIME", "CUSTOM":
	default:
		return errors.New("sourceKind is unsupported")
	}
	if detector.IntervalSeconds == 0 {
		detector.IntervalSeconds = 300
	}
	if detector.TTLSeconds == 0 {
		detector.TTLSeconds = 900
	}
	if detector.IntervalSeconds < 30 || detector.IntervalSeconds > 86400 {
		return errors.New("intervalSeconds must be between 30 and 86400")
	}
	if detector.TTLSeconds < 30 || detector.TTLSeconds > 2592000 {
		return errors.New("ttlSeconds must be between 30 and 2592000")
	}
	return validateExecution(&detector.Execution)
}

func validateExecution(execution *LocalExecution) error {
	if execution.TimeoutSeconds == 0 {
		execution.TimeoutSeconds = 300
	}
	if execution.MaxOutputBytes == 0 {
		execution.MaxOutputBytes = 1024 * 1024
	}
	if execution.TimeoutSeconds < 1 || execution.TimeoutSeconds > 3600 {
		return errors.New("timeoutSeconds must be between one and 3600")
	}
	if execution.MaxOutputBytes < 1024 || execution.MaxOutputBytes > 16*1024*1024 {
		return errors.New("maxOutputBytes is outside the supported bound")
	}
	if len(execution.Arguments) > 32 || len(execution.EnvironmentAllowlist) > 64 {
		return errors.New("arguments or environmentAllowlist exceed the supported bound")
	}
	if err := validateExecutable(execution.Handler); err != nil {
		return fmt.Errorf("handler: %w", err)
	}
	if execution.WorkingDirectory != "" {
		clean, err := safeAbsolutePath(execution.WorkingDirectory)
		if err != nil {
			return fmt.Errorf("workingDirectory: %w", err)
		}
		info, err := os.Stat(clean)
		if err != nil || !info.IsDir() {
			return errors.New("workingDirectory must be an existing directory")
		}
		execution.WorkingDirectory = clean
	}
	seenEnv := make(map[string]struct{}, len(execution.EnvironmentAllowlist))
	for _, name := range execution.EnvironmentAllowlist {
		if !environmentPattern.MatchString(name) {
			return fmt.Errorf("environment variable %q has an invalid name", name)
		}
		if _, exists := seenEnv[name]; exists {
			return fmt.Errorf("environment variable %q is duplicated", name)
		}
		seenEnv[name] = struct{}{}
	}
	for _, argument := range execution.Arguments {
		if len(argument) > 1024 || strings.ContainsAny(argument, "\x00\r\n") {
			return errors.New("argument exceeds the supported syntax bound")
		}
	}
	return nil
}

func validateExecutable(path string) error {
	clean, err := safeAbsolutePath(path)
	if err != nil {
		return err
	}
	info, err := os.Stat(clean)
	if err != nil {
		return err
	}
	if !info.Mode().IsRegular() || info.Mode().Perm()&0o111 == 0 {
		return errors.New("path must reference an executable regular file")
	}
	return nil
}

func validateSchemaPath(path string) error {
	clean, err := safeAbsolutePath(path)
	if err != nil {
		return err
	}
	info, err := os.Stat(clean)
	if err != nil {
		return err
	}
	if !info.Mode().IsRegular() {
		return errors.New("path must reference a regular file")
	}
	return nil
}

func safeAbsolutePath(path string) (string, error) {
	if path == "" || !filepath.IsAbs(path) || strings.ContainsAny(path, "\x00\r\n") {
		return "", errors.New("path must be absolute and contain no control characters")
	}
	clean := filepath.Clean(path)
	if clean != path {
		return "", errors.New("path must already be normalized")
	}
	return clean, nil
}

func readSchema(path string) ([]byte, error) {
	raw, err := readBoundedFile(path, maxSchemaBytes)
	if err != nil {
		return nil, err
	}
	if !json.Valid(raw) {
		return nil, errors.New("schema is not valid JSON")
	}
	return raw, nil
}

func compileSchema(name string, raw []byte) (*jsonschema.Schema, error) {
	compiler := jsonschema.NewCompiler()
	compiler.Draft = jsonschema.Draft2020
	uri := "urn:weave:runner:schema:" + name
	if err := compiler.AddResource(uri, bytes.NewReader(raw)); err != nil {
		return nil, err
	}
	return compiler.Compile(uri)
}

func readBoundedFile(path string, maximum int64) ([]byte, error) {
	file, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer file.Close()
	limited := io.LimitReader(file, maximum+1)
	raw, err := io.ReadAll(limited)
	if err != nil {
		return nil, err
	}
	if int64(len(raw)) > maximum {
		return nil, fmt.Errorf("file exceeds %d bytes", maximum)
	}
	return raw, nil
}

func decodeStrict(raw []byte, target any) error {
	decoder := json.NewDecoder(bytes.NewReader(raw))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(target); err != nil {
		return err
	}
	if decoder.More() {
		return errors.New("unexpected trailing JSON value")
	}
	var trailing any
	if err := decoder.Decode(&trailing); err != io.EOF {
		if err == nil {
			return errors.New("unexpected trailing JSON value")
		}
		return err
	}
	return nil
}

func compactJSON(raw []byte) []byte {
	var target bytes.Buffer
	if err := json.Compact(&target, raw); err != nil {
		panic("compactJSON called with invalid JSON")
	}
	return target.Bytes()
}

func digestBytes(raw []byte) string {
	digest := sha256.Sum256(raw)
	return "sha256:" + hex.EncodeToString(digest[:])
}
