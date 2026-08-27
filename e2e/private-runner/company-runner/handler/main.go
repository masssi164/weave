package main

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"
)

const (
	defaultAPIBaseURL = "http://internal-api:8080"
	defaultTokenFile  = "/run/secrets/internal_api_token"
	maximumInputBytes = 64 * 1024
	maximumBodyBytes  = 256 * 1024
)

type lookupInput struct {
	AssetID string `json:"assetId"`
}

type assetResult struct {
	AssetID         string   `json:"assetId"`
	Kind            string   `json:"kind"`
	Status          string   `json:"status"`
	DisplayName     string   `json:"displayName"`
	RelatedServices []string `json:"relatedServices,omitempty"`
}

func main() {
	if len(os.Args) != 2 {
		fatal("exactly one operation is required")
	}

	var err error
	switch os.Args[1] {
	case "lookup":
		err = lookup()
	case "detect":
		err = detect()
	default:
		err = errors.New("unsupported operation")
	}
	if err != nil {
		fatal(err.Error())
	}
}

func lookup() error {
	inputRaw, err := io.ReadAll(io.LimitReader(os.Stdin, maximumInputBytes+1))
	if err != nil {
		return fmt.Errorf("read input: %w", err)
	}
	if len(inputRaw) > maximumInputBytes {
		return errors.New("input exceeds the accepted bound")
	}
	decoder := json.NewDecoder(bytes.NewReader(inputRaw))
	decoder.DisallowUnknownFields()
	var input lookupInput
	if err := decoder.Decode(&input); err != nil {
		return fmt.Errorf("decode input: %w", err)
	}
	if input.AssetID == "" || len(input.AssetID) > 64 {
		return errors.New("assetId is invalid")
	}

	var result assetResult
	if err := requestJSON("/v1/assets/"+url.PathEscape(input.AssetID), &result); err != nil {
		return err
	}
	return json.NewEncoder(os.Stdout).Encode(result)
}

func detect() error {
	var result struct {
		Scope     string           `json:"scope"`
		Entities  []map[string]any `json:"entities"`
		Relations []map[string]any `json:"relations"`
	}
	if err := requestJSON("/v1/topology", &result); err != nil {
		return err
	}
	return json.NewEncoder(os.Stdout).Encode(result)
}

func requestJSON(path string, output any) error {
	baseValue := strings.TrimSpace(os.Getenv("INTERNAL_API_BASE_URL"))
	if baseValue == "" {
		baseValue = defaultAPIBaseURL
	}
	baseURL, err := url.Parse(baseValue)
	if err != nil || baseURL.Scheme == "" || baseURL.Host == "" || baseURL.Fragment != "" {
		return errors.New("internal API base URL is invalid")
	}
	if baseURL.Scheme != "http" && baseURL.Scheme != "https" {
		return errors.New("internal API base URL must use HTTP or HTTPS")
	}

	tokenFile := strings.TrimSpace(os.Getenv("INTERNAL_API_TOKEN_FILE"))
	if tokenFile == "" {
		tokenFile = defaultTokenFile
	}
	tokenRaw, err := os.ReadFile(tokenFile)
	if err != nil {
		return errors.New("internal API credential is unavailable")
	}
	if len(tokenRaw) == 0 || len(tokenRaw) > 4096 {
		return errors.New("internal API credential is invalid")
	}
	token := strings.TrimSpace(string(tokenRaw))
	defer zero(tokenRaw)
	if token == "" {
		return errors.New("internal API credential is empty")
	}

	endpoint := baseURL.ResolveReference(&url.URL{Path: path})
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint.String(), nil)
	if err != nil {
		return err
	}
	request.Header.Set("Accept", "application/json")
	request.Header.Set("Authorization", "Bearer "+token)

	response, err := http.DefaultClient.Do(request)
	if err != nil {
		return errors.New("internal API request failed")
	}
	defer response.Body.Close()
	body, err := io.ReadAll(io.LimitReader(response.Body, maximumBodyBytes+1))
	if err != nil {
		return errors.New("internal API response could not be read")
	}
	if len(body) > maximumBodyBytes {
		return errors.New("internal API response exceeds the accepted bound")
	}
	if response.StatusCode != http.StatusOK {
		return fmt.Errorf("internal API returned status %d", response.StatusCode)
	}
	decoder := json.NewDecoder(bytes.NewReader(body))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(output); err != nil {
		return errors.New("internal API returned invalid JSON")
	}
	return nil
}

func zero(value []byte) {
	for index := range value {
		value[index] = 0
	}
}

func fatal(message string) {
	_, _ = fmt.Fprintln(os.Stderr, message)
	os.Exit(1)
}
