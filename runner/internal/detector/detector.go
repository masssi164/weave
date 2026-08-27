package detector

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"time"

	"github.com/masssi164/weave/runner/internal/bundle"
	"github.com/masssi164/weave/runner/internal/detection"
	"github.com/masssi164/weave/runner/internal/protocol"
)

type payload struct {
	Scope     string                         `json:"scope,omitempty"`
	Entities  []protocol.ObservationEntity   `json:"entities"`
	Relations []protocol.ObservationRelation `json:"relations"`
}

func Run(
	ctx context.Context,
	runnerID string,
	declaration bundle.LocalDetector,
) (protocol.ObservationBatch, error) {
	timeout := time.Duration(declaration.Execution.TimeoutSeconds) * time.Second
	runContext, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	command := exec.CommandContext(
		runContext,
		declaration.Execution.Handler,
		declaration.Execution.Arguments...,
	)
	command.WaitDelay = 5 * time.Second
	if declaration.Execution.WorkingDirectory != "" {
		command.Dir = declaration.Execution.WorkingDirectory
	}
	stdout := newCappedBuffer(declaration.Execution.MaxOutputBytes)
	stderr := newCappedBuffer(64 * 1024)
	command.Stdout = stdout
	command.Stderr = stderr
	command.Env = detectorEnvironment(declaration)

	if err := command.Run(); err != nil {
		if runContext.Err() != nil {
			return protocol.ObservationBatch{}, fmt.Errorf("detector timed out: %w", runContext.Err())
		}
		return protocol.ObservationBatch{}, errors.New("detector exited unsuccessfully")
	}
	if stdout.exceeded {
		return protocol.ObservationBatch{}, errors.New("detector output exceeded configured limit")
	}

	raw := bytes.TrimSpace(stdout.Bytes())
	if len(raw) == 0 {
		return protocol.ObservationBatch{}, errors.New("detector returned no JSON")
	}
	var local payload
	decoder := json.NewDecoder(bytes.NewReader(raw))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&local); err != nil {
		return protocol.ObservationBatch{}, fmt.Errorf("decode detector output: %w", err)
	}
	if err := requireJSONEOF(decoder); err != nil {
		return protocol.ObservationBatch{}, err
	}

	observedAt := time.Now().UTC()
	return detection.Normalize(protocol.ObservationBatch{
		SchemaVersion:   "weave.runner.observation/v1",
		RunnerID:        runnerID,
		DetectorID:      declaration.ID,
		DetectorVersion: declaration.Version,
		SourceKind:      declaration.SourceKind,
		Scope:           local.Scope,
		ObservedAt:      observedAt,
		TTLSeconds:      declaration.TTLSeconds,
		Entities:        local.Entities,
		Relations:       local.Relations,
	}, observedAt)
}

func detectorEnvironment(declaration bundle.LocalDetector) []string {
	values := []string{
		"PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
		"HOME=/tmp",
		"LANG=C.UTF-8",
		"WEAVE_DETECTOR_ID=" + declaration.ID,
		"WEAVE_DETECTOR_VERSION=" + declaration.Version,
	}
	for _, name := range declaration.Execution.EnvironmentAllowlist {
		if value, ok := os.LookupEnv(name); ok {
			values = append(values, name+"="+value)
		}
	}
	return values
}

func requireJSONEOF(decoder *json.Decoder) error {
	var trailing any
	if err := decoder.Decode(&trailing); errors.Is(err, io.EOF) {
		return nil
	} else if err != nil {
		return fmt.Errorf("decode trailing detector output: %w", err)
	}
	return errors.New("detector returned more than one JSON value")
}

type cappedBuffer struct {
	buffer   bytes.Buffer
	maximum  int64
	written  int64
	exceeded bool
}

func newCappedBuffer(maximum int64) *cappedBuffer {
	return &cappedBuffer{maximum: maximum}
}

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

func (buffer *cappedBuffer) Bytes() []byte {
	return buffer.buffer.Bytes()
}
