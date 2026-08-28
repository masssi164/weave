package control

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"path"
	"strconv"
	"strings"
	"time"

	"github.com/masssi164/weave/runner/internal/protocol"
)

const maxJSONResponse = 4 * 1024 * 1024

type Client struct {
	base *url.URL
	http *http.Client
}

func New(base *url.URL, httpClient *http.Client) (*Client, error) {
	if base == nil || base.Scheme != "https" || base.Host == "" {
		return nil, errors.New("control base URL must be HTTPS")
	}
	if httpClient == nil {
		return nil, errors.New("HTTP client is required")
	}
	copyURL := *base
	copyURL.Path = strings.TrimSuffix(copyURL.Path, "/")
	return &Client{base: &copyURL, http: httpClient}, nil
}

func (client *Client) PublishBundle(ctx context.Context, bundle protocol.PublicCapabilityBundle) error {
	return client.doJSON(ctx, http.MethodPut, "/runner/v1/capability-bundle", nil, bundle, map[string]string{"Idempotency-Key": "bundle-" + bundle.BundleDigest}, []int{http.StatusNoContent}, nil)
}
func (client *Client) Heartbeat(ctx context.Context, heartbeat protocol.Heartbeat) error {
	return client.doJSON(ctx, http.MethodPost, "/runner/v1/heartbeat", nil, heartbeat, nil, []int{http.StatusNoContent}, nil)
}

func (client *Client) Claim(ctx context.Context, waitSeconds int, request protocol.ClaimRequest) (*protocol.TaskLease, error) {
	if waitSeconds < 0 || waitSeconds > 30 {
		return nil, errors.New("waitSeconds is outside supported bounds")
	}
	var lease protocol.TaskLease
	status, err := client.doJSONStatus(
		ctx,
		http.MethodPost,
		"/runner/v1/tasks:claim",
		nil,
		request,
		map[string]string{"Prefer": "wait=" + strconv.Itoa(waitSeconds)},
		[]int{http.StatusOK, http.StatusNoContent},
		&lease,
	)
	if err != nil {
		return nil, err
	}
	if status == http.StatusNoContent {
		return nil, nil
	}
	return &lease, nil
}

func (client *Client) HeartbeatTask(ctx context.Context, lease protocol.TaskLease) (*protocol.LeaseRenewal, error) {
	var renewal protocol.LeaseRenewal
	err := client.doJSON(ctx, http.MethodPost, "/runner/v1/tasks/"+url.PathEscape(lease.TaskID)+":heartbeat", nil, protocol.LeaseRef{LeaseID: lease.LeaseID, FencingToken: lease.FencingToken}, nil, []int{http.StatusOK}, &renewal)
	return &renewal, err
}

func (client *Client) UploadArtifact(ctx context.Context, lease protocol.TaskLease, artifact protocol.Artifact, filePath string) (*protocol.ArtifactReceipt, error) {
	file, err := os.Open(filePath)
	if err != nil {
		return nil, err
	}
	defer file.Close()
	info, err := file.Stat()
	if err != nil {
		return nil, err
	}
	if !info.Mode().IsRegular() || info.Size() != artifact.Size {
		return nil, errors.New("artifact changed after validation")
	}
	contentDigest, err := contentDigestHeader(artifact.Digest)
	if err != nil {
		return nil, err
	}
	endpoint := client.endpoint("/runner/v1/tasks/"+url.PathEscape(lease.TaskID)+"/artifacts/"+url.PathEscape(artifact.ArtifactID), nil)
	request, err := http.NewRequestWithContext(ctx, http.MethodPut, endpoint.String(), file)
	if err != nil {
		return nil, err
	}
	request.ContentLength = artifact.Size
	request.Header.Set("Content-Type", "application/octet-stream")
	request.Header.Set("Content-Digest", contentDigest)
	request.Header.Set("X-Weave-Lease-Id", lease.LeaseID)
	request.Header.Set("X-Weave-Fencing-Token", strconv.FormatInt(lease.FencingToken, 10))
	request.Header.Set("Idempotency-Key", lease.IdempotencyKey+"-artifact-"+artifact.ArtifactID)
	response, err := client.http.Do(request)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	raw, err := readBounded(response.Body, maxJSONResponse)
	if err != nil {
		return nil, err
	}
	if response.StatusCode != http.StatusCreated && response.StatusCode != http.StatusOK {
		return nil, decodeProblem(response.StatusCode, raw)
	}
	var receipt protocol.ArtifactReceipt
	if err := json.Unmarshal(raw, &receipt); err != nil {
		return nil, err
	}
	return &receipt, nil
}

func (client *Client) Complete(ctx context.Context, result protocol.TaskResult) (*protocol.TaskReceipt, error) {
	return client.finish(ctx, "complete", result)
}
func (client *Client) Fail(ctx context.Context, result protocol.TaskResult) (*protocol.TaskReceipt, error) {
	return client.finish(ctx, "fail", result)
}
func (client *Client) finish(ctx context.Context, action string, result protocol.TaskResult) (*protocol.TaskReceipt, error) {
	var receipt protocol.TaskReceipt
	err := client.doJSON(ctx, http.MethodPost, "/runner/v1/tasks/"+url.PathEscape(result.TaskID)+":"+action, nil, result, map[string]string{"Idempotency-Key": "task-result-" + result.TaskID + "-" + result.LeaseID}, []int{http.StatusOK}, &receipt)
	return &receipt, err
}
func (client *Client) PublishObservations(ctx context.Context, batch protocol.ObservationBatch) (*protocol.ObservationReceipt, error) {
	var receipt protocol.ObservationReceipt
	key := "observation-" + batch.BatchDigest
	if batch.BatchDigest == "" {
		key = "observation-" + batch.RunnerID + "-" + batch.DetectorID + "-" + batch.ObservedAt.UTC().Format(time.RFC3339Nano)
	}
	err := client.doJSON(ctx, http.MethodPost, "/runner/v1/observations", nil, batch, map[string]string{"Idempotency-Key": key}, []int{http.StatusAccepted}, &receipt)
	return &receipt, err
}

func (client *Client) doJSON(ctx context.Context, method, requestPath string, query url.Values, input any, headers map[string]string, accepted []int, output any) error {
	_, err := client.doJSONStatus(ctx, method, requestPath, query, input, headers, accepted, output)
	return err
}
func (client *Client) doJSONStatus(ctx context.Context, method, requestPath string, query url.Values, input any, headers map[string]string, accepted []int, output any) (int, error) {
	var body io.Reader
	if input != nil {
		raw, err := json.Marshal(input)
		if err != nil {
			return 0, err
		}
		body = bytes.NewReader(raw)
	}
	request, err := http.NewRequestWithContext(ctx, method, client.endpoint(requestPath, query).String(), body)
	if err != nil {
		return 0, err
	}
	request.Header.Set("Accept", "application/json, application/problem+json")
	if input != nil {
		request.Header.Set("Content-Type", "application/json")
	}
	for key, value := range headers {
		request.Header.Set(key, value)
	}
	response, err := client.http.Do(request)
	if err != nil {
		return 0, err
	}
	defer response.Body.Close()
	raw, err := readBounded(response.Body, maxJSONResponse)
	if err != nil {
		return 0, err
	}
	if !containsStatus(accepted, response.StatusCode) {
		return response.StatusCode, decodeProblem(response.StatusCode, raw)
	}
	if output != nil && response.StatusCode != http.StatusNoContent {
		if err := json.Unmarshal(raw, output); err != nil {
			return response.StatusCode, fmt.Errorf("decode control response: %w", err)
		}
	}
	return response.StatusCode, nil
}
func (client *Client) endpoint(requestPath string, query url.Values) *url.URL {
	result := *client.base
	result.Path = path.Join(strings.TrimSuffix(client.base.Path, "/"), requestPath)
	if strings.HasSuffix(requestPath, ":claim") {
		result.Path = strings.TrimSuffix(result.Path, "/tasks:claim") + "/tasks:claim"
	}
	result.RawQuery = query.Encode()
	return &result
}
func readBounded(reader io.Reader, maximum int64) ([]byte, error) {
	raw, err := io.ReadAll(io.LimitReader(reader, maximum+1))
	if err != nil {
		return nil, err
	}
	if int64(len(raw)) > maximum {
		return nil, errors.New("control response exceeds supported bound")
	}
	return raw, nil
}
func containsStatus(values []int, status int) bool {
	for _, value := range values {
		if value == status {
			return true
		}
	}
	return false
}
func decodeProblem(status int, raw []byte) error {
	var problem protocol.Problem
	if json.Unmarshal(raw, &problem) == nil && problem.Title != "" {
		if problem.Detail != "" {
			return fmt.Errorf("%s: %s (%d)", problem.Title, problem.Detail, status)
		}
		return fmt.Errorf("%s (%d)", problem.Title, status)
	}
	return fmt.Errorf("unexpected control status %d", status)
}
func contentDigestHeader(value string) (string, error) {
	if !strings.HasPrefix(value, "sha256:") {
		return "", errors.New("artifact digest is not sha256")
	}
	raw, err := hex.DecodeString(strings.TrimPrefix(value, "sha256:"))
	if err != nil || len(raw) != sha256.Size {
		return "", errors.New("artifact digest is malformed")
	}
	return "sha-256=:" + base64.StdEncoding.EncodeToString(raw) + ":", nil
}
