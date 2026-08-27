package executor

import (
	"bytes"
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"

	"github.com/masssi164/weave/runner/internal/bundle"
	"github.com/masssi164/weave/runner/internal/protocol"
)

const maximumArtifactBytes = int64(1024 * 1024 * 1024)
const maximumArtifacts = 128
const maximumStderrBytes = 64 * 1024

type Executor struct{ WorkRoot string }
type LocalArtifact struct {
	Descriptor protocol.Artifact
	Path       string
}
type Result struct {
	Output        json.RawMessage
	Artifacts     []LocalArtifact
	WorkDirectory string
}
type Failure struct {
	Code      string
	Message   string
	Retryable bool
	cause     error
}

func (failure *Failure) Error() string { return failure.Message }
func (failure *Failure) Unwrap() error { return failure.cause }

type handlerInput struct {
	TaskID         string                   `json:"taskId"`
	LeaseID        string                   `json:"leaseId"`
	Capability     protocol.CapabilityRef   `json:"capability"`
	Attempt        int                      `json:"attempt"`
	Payload        json.RawMessage          `json:"payload"`
	ContextRefs    []string                 `json:"contextRefs"`
	ResourceGrants []protocol.ResourceGrant `json:"resourceGrants"`
}
type localArtifactManifest struct {
	Artifacts []localArtifact `json:"artifacts"`
}
type localArtifact struct {
	Path         string `json:"path"`
	Name         string `json:"name,omitempty"`
	ArtifactType string `json:"artifactType,omitempty"`
	MediaType    string `json:"mediaType"`
}

func (executor Executor) Execute(ctx context.Context, lease protocol.TaskLease, capability *bundle.Capability) (*Result, error) {
	if capability == nil {
		return nil, &Failure{Code: "CAPABILITY_UNAVAILABLE", Message: "The leased capability is not loaded by this Runner."}
	}
	var payload any
	decoder := json.NewDecoder(bytes.NewReader(lease.Payload))
	decoder.UseNumber()
	if err := decoder.Decode(&payload); err != nil {
		return nil, &Failure{Code: "INVALID_TASK_INPUT", Message: "The task input is not valid JSON.", cause: err}
	}
	if err := capability.InputSchema.Validate(payload); err != nil {
		return nil, &Failure{Code: "INVALID_TASK_INPUT", Message: "The task input does not satisfy the capability schema.", cause: err}
	}
	root := executor.WorkRoot
	if root == "" {
		root = "/var/lib/weave-runner/work"
	}
	if !filepath.IsAbs(root) {
		return nil, errors.New("Runner work root must be absolute")
	}
	if err := os.MkdirAll(root, 0o700); err != nil {
		return nil, err
	}
	workDirectory, err := os.MkdirTemp(root, "task-"+lease.TaskID+"-")
	if err != nil {
		return nil, err
	}
	if err := os.Chmod(workDirectory, 0o700); err != nil {
		os.RemoveAll(workDirectory)
		return nil, err
	}
	inputDirectory := filepath.Join(workDirectory, "input")
	outputDirectory := filepath.Join(workDirectory, "output")
	if err := os.Mkdir(inputDirectory, 0o700); err != nil {
		os.RemoveAll(workDirectory)
		return nil, err
	}
	if err := os.Mkdir(outputDirectory, 0o700); err != nil {
		os.RemoveAll(workDirectory)
		return nil, err
	}
	envelope := handlerInput{TaskID: lease.TaskID, LeaseID: lease.LeaseID, Capability: lease.Capability, Attempt: lease.Attempt, Payload: lease.Payload, ContextRefs: lease.ContextRefs, ResourceGrants: lease.ResourceGrants}
	stdin, err := json.Marshal(envelope)
	if err != nil {
		os.RemoveAll(workDirectory)
		return nil, err
	}
	if err := os.WriteFile(filepath.Join(inputDirectory, "task.json"), stdin, 0o600); err != nil {
		os.RemoveAll(workDirectory)
		return nil, err
	}
	deadline := time.Duration(capability.Local.Execution.TimeoutSeconds) * time.Second
	if lease.Deadline != nil {
		remaining := time.Until(*lease.Deadline)
		if remaining <= 0 {
			os.RemoveAll(workDirectory)
			return nil, &Failure{Code: "TASK_DEADLINE_EXCEEDED", Message: "The task deadline has already elapsed."}
		}
		if remaining < deadline {
			deadline = remaining
		}
	}
	taskContext, cancel := context.WithTimeout(ctx, deadline)
	defer cancel()
	command := exec.CommandContext(taskContext, capability.Local.Execution.Handler, capability.Local.Execution.Arguments...)
	command.WaitDelay = 5 * time.Second
	if capability.Local.Execution.WorkingDirectory != "" {
		command.Dir = capability.Local.Execution.WorkingDirectory
	} else {
		command.Dir = workDirectory
	}
	command.Stdin = bytes.NewReader(stdin)
	stdout := newCappedBuffer(capability.Local.Execution.MaxOutputBytes)
	stderr := newCappedBuffer(maximumStderrBytes)
	command.Stdout = stdout
	command.Stderr = stderr
	command.Env = handlerEnvironment(capability, lease, inputDirectory, outputDirectory)
	runErr := command.Run()
	if taskContext.Err() != nil {
		os.RemoveAll(workDirectory)
		return nil, &Failure{Code: "HANDLER_TIMEOUT", Message: "The capability handler exceeded its deadline.", Retryable: true, cause: taskContext.Err()}
	}
	if stdout.exceeded {
		os.RemoveAll(workDirectory)
		return nil, &Failure{Code: "OUTPUT_LIMIT_EXCEEDED", Message: "The capability result exceeded the configured output limit."}
	}
	if runErr != nil {
		os.RemoveAll(workDirectory)
		return nil, &Failure{Code: "HANDLER_FAILED", Message: "The capability handler exited unsuccessfully.", cause: runErr}
	}
	outputRaw := bytes.TrimSpace(stdout.Bytes())
	if len(outputRaw) == 0 {
		os.RemoveAll(workDirectory)
		return nil, &Failure{Code: "INVALID_HANDLER_RESULT", Message: "The capability handler returned no JSON result."}
	}
	var output any
	outDecoder := json.NewDecoder(bytes.NewReader(outputRaw))
	outDecoder.UseNumber()
	if err := outDecoder.Decode(&output); err != nil {
		os.RemoveAll(workDirectory)
		return nil, &Failure{Code: "INVALID_HANDLER_RESULT", Message: "The capability handler returned invalid JSON.", cause: err}
	}
	if err := capability.OutputSchema.Validate(output); err != nil {
		os.RemoveAll(workDirectory)
		return nil, &Failure{Code: "INVALID_HANDLER_RESULT", Message: "The capability result does not satisfy the declared output schema.", cause: err}
	}
	artifacts, err := collectArtifacts(outputDirectory)
	if err != nil {
		os.RemoveAll(workDirectory)
		return nil, &Failure{Code: "INVALID_ARTIFACT", Message: "The capability produced an invalid Artifact manifest.", cause: err}
	}
	return &Result{Output: append(json.RawMessage(nil), outputRaw...), Artifacts: artifacts, WorkDirectory: workDirectory}, nil
}

func (result *Result) Cleanup() {
	if result != nil && result.WorkDirectory != "" {
		_ = os.RemoveAll(result.WorkDirectory)
	}
}

func handlerEnvironment(capability *bundle.Capability, lease protocol.TaskLease, inputDirectory, outputDirectory string) []string {
	values := []string{"PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin", "HOME=/tmp", "LANG=C.UTF-8", "WEAVE_INPUT_DIR=" + inputDirectory, "WEAVE_OUTPUT_DIR=" + outputDirectory, "WEAVE_TASK_ID=" + lease.TaskID, "WEAVE_LEASE_ID=" + lease.LeaseID, "WEAVE_CAPABILITY_ID=" + capability.Reference.ID, "WEAVE_CAPABILITY_VERSION=" + capability.Reference.Version}
	for _, name := range capability.Local.Execution.EnvironmentAllowlist {
		if value, ok := os.LookupEnv(name); ok {
			values = append(values, name+"="+value)
		}
	}
	return values
}

func collectArtifacts(outputDirectory string) ([]LocalArtifact, error) {
	manifestPath := filepath.Join(outputDirectory, "artifact-manifest.json")
	raw, err := os.ReadFile(manifestPath)
	if errors.Is(err, os.ErrNotExist) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	if len(raw) > 256*1024 {
		return nil, errors.New("Artifact manifest is too large")
	}
	var manifest localArtifactManifest
	decoder := json.NewDecoder(bytes.NewReader(raw))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&manifest); err != nil {
		return nil, err
	}
	if len(manifest.Artifacts) > maximumArtifacts {
		return nil, errors.New("too many Artifacts")
	}
	result := make([]LocalArtifact, 0, len(manifest.Artifacts))
	for _, entry := range manifest.Artifacts {
		resolved, err := resolveArtifactPath(outputDirectory, entry.Path)
		if err != nil {
			return nil, err
		}
		info, err := os.Lstat(resolved)
		if err != nil {
			return nil, err
		}
		if info.Mode()&os.ModeSymlink != 0 || !info.Mode().IsRegular() {
			return nil, errors.New("Artifact must be a regular non-symlink file")
		}
		if info.Size() > maximumArtifactBytes {
			return nil, errors.New("Artifact exceeds maximum size")
		}
		digest, err := fileDigest(resolved)
		if err != nil {
			return nil, err
		}
		artifactID, err := newUUID()
		if err != nil {
			return nil, err
		}
		result = append(result, LocalArtifact{Descriptor: protocol.Artifact{ArtifactID: artifactID, Name: entry.Name, ArtifactType: entry.ArtifactType, MediaType: entry.MediaType, Size: info.Size(), Digest: digest}, Path: resolved})
	}
	return result, nil
}
func resolveArtifactPath(root, relative string) (string, error) {
	if relative == "" || filepath.IsAbs(relative) || strings.ContainsAny(relative, "\x00\r\n") {
		return "", errors.New("Artifact path must be relative")
	}
	clean := filepath.Clean(relative)
	if clean != relative || clean == "." || clean == ".." || strings.HasPrefix(clean, ".."+string(filepath.Separator)) {
		return "", errors.New("Artifact path is unsafe")
	}
	resolved := filepath.Join(root, clean)
	inside, err := filepath.Rel(root, resolved)
	if err != nil || inside == ".." || strings.HasPrefix(inside, ".."+string(filepath.Separator)) {
		return "", errors.New("Artifact escaped output directory")
	}
	return resolved, nil
}
func fileDigest(path string) (string, error) {
	file, err := os.Open(path)
	if err != nil {
		return "", err
	}
	defer file.Close()
	hash := sha256.New()
	if _, err := io.Copy(hash, file); err != nil {
		return "", err
	}
	return "sha256:" + hex.EncodeToString(hash.Sum(nil)), nil
}
func newUUID() (string, error) {
	raw := make([]byte, 16)
	if _, err := rand.Read(raw); err != nil {
		return "", err
	}
	raw[6] = (raw[6] & 0x0f) | 0x40
	raw[8] = (raw[8] & 0x3f) | 0x80
	return fmt.Sprintf("%08x-%04x-%04x-%04x-%012x", raw[0:4], raw[4:6], raw[6:8], raw[8:10], raw[10:16]), nil
}

type cappedBuffer struct {
	buffer   bytes.Buffer
	maximum  int64
	written  int64
	exceeded bool
}

func newCappedBuffer(maximum int64) *cappedBuffer { return &cappedBuffer{maximum: maximum} }
func (buffer *cappedBuffer) Write(value []byte) (int, error) {
	buffer.written += int64(len(value))
	remaining := buffer.maximum - int64(buffer.buffer.Len())
	if remaining > 0 {
		part := value
		if int64(len(part)) > remaining {
			part = part[:remaining]
		}
		_, _ = buffer.buffer.Write(part)
	}
	if buffer.written > buffer.maximum {
		buffer.exceeded = true
	}
	return len(value), nil
}
func (buffer *cappedBuffer) Bytes() []byte { return buffer.buffer.Bytes() }
