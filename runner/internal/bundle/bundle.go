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

const maxBundleBytes = 4 * 1024 * 1024
const maxSchemaBytes = 256 * 1024

var identifierPattern = regexp.MustCompile(`^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$`)
var versionPattern = regexp.MustCompile(`^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$`)
var environmentPattern = regexp.MustCompile(`^[A-Z_][A-Z0-9_]*$`)

type LocalBundle struct { SchemaVersion string `json:"schemaVersion"`; BundleID string `json:"bundleId"`; BundleVersion string `json:"bundleVersion"`; DisplayName string `json:"displayName,omitempty"`; Capabilities []LocalCapability `json:"capabilities"`; Detectors []LocalDetector `json:"detectors,omitempty"`; Labels map[string]string `json:"labels,omitempty"` }
type LocalCapability struct { ID string `json:"id"`; Version string `json:"version"`; Title string `json:"title"`; Description string `json:"description,omitempty"`; InputSchema string `json:"inputSchema"`; OutputSchema string `json:"outputSchema"`; Effect string `json:"effect"`; ArtifactTypes []string `json:"artifactTypes,omitempty"`; Execution LocalExecution `json:"execution"` }
type LocalDetector struct { ID string `json:"id"`; Version string `json:"version"`; SourceKind string `json:"sourceKind"`; IntervalSeconds int `json:"intervalSeconds,omitempty"`; TTLSeconds int `json:"ttlSeconds,omitempty"`; Execution LocalExecution `json:"execution"` }
type LocalExecution struct { Handler string `json:"handler"`; Arguments []string `json:"arguments,omitempty"`; WorkingDirectory string `json:"workingDirectory,omitempty"`; EnvironmentAllowlist []string `json:"environmentAllowlist,omitempty"`; NetworkProfile string `json:"networkProfile,omitempty"`; TimeoutSeconds int `json:"timeoutSeconds,omitempty"`; MaxOutputBytes int64 `json:"maxOutputBytes,omitempty"` }

type Capability struct { Local LocalCapability; Reference protocol.CapabilityRef; InputRaw json.RawMessage; OutputRaw json.RawMessage; InputSchema *jsonschema.Schema; OutputSchema *jsonschema.Schema }
type Loaded struct { Local LocalBundle; Public protocol.PublicCapabilityBundle; Raw []byte; Digest string; capabilities map[string]*Capability }

func Load(path string) (*Loaded, error) {
	raw, err := readBounded(path, maxBundleBytes)
	if err != nil { return nil, fmt.Errorf("read capability bundle: %w", err) }
	var local LocalBundle
	if err := decodeStrict(raw, &local); err != nil { return nil, fmt.Errorf("decode capability bundle: %w", err) }
	if err := validateBundle(&local); err != nil { return nil, err }
	loaded := &Loaded{Local: local, Raw: append([]byte(nil), raw...), Digest: digest(raw), capabilities: map[string]*Capability{}}
	loaded.Public = protocol.PublicCapabilityBundle{SchemaVersion: "weave.runner.public-capability-bundle/v1", BundleID: local.BundleID, BundleVersion: local.BundleVersion, BundleDigest: loaded.Digest}
	for _, declaration := range local.Capabilities {
		capability, public, err := loadCapability(declaration)
		if err != nil { return nil, fmt.Errorf("capability %s@%s: %w", declaration.ID, declaration.Version, err) }
		loaded.capabilities[capability.Reference.ID+"@"+capability.Reference.Version] = capability
		loaded.Public.Capabilities = append(loaded.Public.Capabilities, public)
	}
	sort.Slice(loaded.Public.Capabilities, func(i, j int) bool { if loaded.Public.Capabilities[i].ID == loaded.Public.Capabilities[j].ID { return loaded.Public.Capabilities[i].Version < loaded.Public.Capabilities[j].Version }; return loaded.Public.Capabilities[i].ID < loaded.Public.Capabilities[j].ID })
	return loaded, nil
}

func (loaded *Loaded) Find(reference protocol.CapabilityRef) (*Capability, bool) { value, ok := loaded.capabilities[reference.ID+"@"+reference.Version]; return value, ok }
func (loaded *Loaded) CapabilityReferences() []protocol.CapabilityRef { result := make([]protocol.CapabilityRef, 0, len(loaded.capabilities)); for _, capability := range loaded.capabilities { result = append(result, capability.Reference) }; sort.Slice(result, func(i, j int) bool { if result[i].ID == result[j].ID { return result[i].Version < result[j].Version }; return result[i].ID < result[j].ID }); return result }

func loadCapability(declaration LocalCapability) (*Capability, protocol.PublicCapability, error) {
	inputRaw, err := readSchema(declaration.InputSchema); if err != nil { return nil, protocol.PublicCapability{}, fmt.Errorf("input schema: %w", err) }
	outputRaw, err := readSchema(declaration.OutputSchema); if err != nil { return nil, protocol.PublicCapability{}, fmt.Errorf("output schema: %w", err) }
	inputCompiled, err := compileSchema(declaration.ID+"-input", inputRaw); if err != nil { return nil, protocol.PublicCapability{}, err }
	outputCompiled, err := compileSchema(declaration.ID+"-output", outputRaw); if err != nil { return nil, protocol.PublicCapability{}, err }
	inputRaw = compact(inputRaw); outputRaw = compact(outputRaw)
	ref := protocol.CapabilityRef{ID: declaration.ID, Version: declaration.Version}
	capability := &Capability{Local: declaration, Reference: ref, InputRaw: inputRaw, OutputRaw: outputRaw, InputSchema: inputCompiled, OutputSchema: outputCompiled}
	public := protocol.PublicCapability{ID: declaration.ID, Version: declaration.Version, Title: declaration.Title, Description: declaration.Description, Effect: declaration.Effect, InputSchema: inputRaw, InputSchemaDigest: digest(inputRaw), OutputSchema: outputRaw, OutputSchemaDigest: digest(outputRaw), TimeoutSeconds: declaration.Execution.TimeoutSeconds, MaxOutputBytes: declaration.Execution.MaxOutputBytes, ArtifactTypes: append([]string(nil), declaration.ArtifactTypes...)}
	return capability, public, nil
}

func validateBundle(bundle *LocalBundle) error {
	if bundle.SchemaVersion != "weave.runner.capability-bundle/v1" { return errors.New("unsupported capability bundle schemaVersion") }
	if !identifierPattern.MatchString(bundle.BundleID) || !versionPattern.MatchString(bundle.BundleVersion) { return errors.New("bundle identity or version is invalid") }
	if len(bundle.Capabilities) == 0 || len(bundle.Capabilities) > 128 || len(bundle.Detectors) > 32 || len(bundle.Labels) > 32 { return errors.New("bundle exceeds supported bounds") }
	seen := map[string]struct{}{}
	for index := range bundle.Capabilities { capability := &bundle.Capabilities[index]; if err := validateCapability(capability); err != nil { return fmt.Errorf("capabilities[%d]: %w", index, err) }; key := capability.ID+"@"+capability.Version; if _, exists := seen[key]; exists { return fmt.Errorf("duplicate capability %s", key) }; seen[key] = struct{}{} }
	for index := range bundle.Detectors { detector := &bundle.Detectors[index]; if err := validateDetector(detector); err != nil { return fmt.Errorf("detectors[%d]: %w", index, err) } }
	return nil
}

func validateCapability(value *LocalCapability) error {
	if !identifierPattern.MatchString(value.ID) || !versionPattern.MatchString(value.Version) { return errors.New("id or version is invalid") }
	if strings.TrimSpace(value.Title) == "" || len(value.Title) > 160 || len(value.Description) > 1000 { return errors.New("title or description is invalid") }
	switch value.Effect { case "READ_ONLY", "IDEMPOTENT_WRITE", "NON_IDEMPOTENT_WRITE": default: return errors.New("effect is unsupported") }
	if len(value.ArtifactTypes) > 32 { return errors.New("artifactTypes exceed supported bounds") }
	if _, err := readSchema(value.InputSchema); err != nil { return fmt.Errorf("inputSchema: %w", err) }
	if _, err := readSchema(value.OutputSchema); err != nil { return fmt.Errorf("outputSchema: %w", err) }
	return validateExecution(&value.Execution)
}

func validateDetector(value *LocalDetector) error {
	if !identifierPattern.MatchString(value.ID) || !versionPattern.MatchString(value.Version) { return errors.New("id or version is invalid") }
	switch value.SourceKind { case "DECLARATION", "OPENAPI", "ASYNCAPI", "SBOM", "OTEL", "RUNTIME", "CUSTOM": default: return errors.New("sourceKind is unsupported") }
	if value.IntervalSeconds == 0 { value.IntervalSeconds = 300 }; if value.TTLSeconds == 0 { value.TTLSeconds = 900 }
	if value.IntervalSeconds < 30 || value.IntervalSeconds > 86400 || value.TTLSeconds < 30 || value.TTLSeconds > 2592000 { return errors.New("detector interval or TTL is outside supported bounds") }
	return validateExecution(&value.Execution)
}

func validateExecution(value *LocalExecution) error {
	if value.TimeoutSeconds == 0 { value.TimeoutSeconds = 300 }; if value.MaxOutputBytes == 0 { value.MaxOutputBytes = 1024*1024 }
	if value.TimeoutSeconds < 1 || value.TimeoutSeconds > 3600 || value.MaxOutputBytes < 1024 || value.MaxOutputBytes > 16*1024*1024 { return errors.New("execution limits are invalid") }
	if len(value.Arguments) > 32 || len(value.EnvironmentAllowlist) > 64 { return errors.New("arguments or environment allowlist exceed supported bounds") }
	clean, err := safeAbsolute(value.Handler); if err != nil { return fmt.Errorf("handler: %w", err) }; info, err := os.Stat(clean); if err != nil { return err }; if !info.Mode().IsRegular() || info.Mode().Perm()&0o111 == 0 { return errors.New("handler must be an executable regular file") }; value.Handler = clean
	if value.WorkingDirectory != "" { clean, err := safeAbsolute(value.WorkingDirectory); if err != nil { return err }; info, err := os.Stat(clean); if err != nil || !info.IsDir() { return errors.New("workingDirectory must exist") }; value.WorkingDirectory = clean }
	seen := map[string]struct{}{}; for _, name := range value.EnvironmentAllowlist { if !environmentPattern.MatchString(name) { return fmt.Errorf("invalid environment name %q", name) }; if _, exists := seen[name]; exists { return fmt.Errorf("duplicate environment name %q", name) }; seen[name] = struct{}{} }
	for _, argument := range value.Arguments { if len(argument)>1024 || strings.ContainsAny(argument, "\x00\r\n") { return errors.New("argument exceeds syntax bounds") } }
	return nil
}

func safeAbsolute(path string) (string, error) { if path=="" || !filepath.IsAbs(path) || strings.ContainsAny(path, "\x00\r\n") { return "", errors.New("path must be an absolute normalized path") }; clean:=filepath.Clean(path); if clean!=path { return "", errors.New("path must already be normalized") }; return clean,nil }
func readSchema(path string) ([]byte,error) { clean,err:=safeAbsolute(path); if err!=nil{return nil,err}; raw,err:=readBounded(clean,maxSchemaBytes); if err!=nil{return nil,err}; if !json.Valid(raw){return nil,errors.New("schema is not valid JSON")}; return raw,nil }
func compileSchema(name string, raw []byte)(*jsonschema.Schema,error){ compiler:=jsonschema.NewCompiler(); compiler.Draft=jsonschema.Draft2020; uri:="urn:weave:runner:schema:"+name; if err:=compiler.AddResource(uri,bytes.NewReader(raw));err!=nil{return nil,err}; return compiler.Compile(uri) }
func readBounded(path string, maximum int64)([]byte,error){ file,err:=os.Open(path);if err!=nil{return nil,err};defer file.Close();raw,err:=io.ReadAll(io.LimitReader(file,maximum+1));if err!=nil{return nil,err};if int64(len(raw))>maximum{return nil,fmt.Errorf("file exceeds %d bytes",maximum)};return raw,nil }
func decodeStrict(raw []byte,target any)error{ decoder:=json.NewDecoder(bytes.NewReader(raw));decoder.DisallowUnknownFields();if err:=decoder.Decode(target);err!=nil{return err};var trailing any;if err:=decoder.Decode(&trailing);err!=io.EOF{return errors.New("unexpected trailing JSON")};return nil }
func compact(raw []byte)[]byte{ var target bytes.Buffer;if err:=json.Compact(&target,raw);err!=nil{panic(err)};return target.Bytes() }
func digest(raw []byte)string{ sum:=sha256.Sum256(raw);return "sha256:"+hex.EncodeToString(sum[:]) }
