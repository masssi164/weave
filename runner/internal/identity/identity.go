package identity

import (
	"bytes"
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"encoding/pem"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/masssi164/weave/runner/internal/protocol"
)

const (
	keyFileName = "runner-key.pem"
	certificateFileName = "runner-cert.pem"
	caFileName = "runner-ca.pem"
	metadataFileName = "identity.json"
	maximumResponseBytes = 256 * 1024
)

type Config struct {
	EngineURL           string
	IdentityDirectory   string
	AccessID            string
	EnrollmentSecretFile string
	BootstrapCAFile     string
	RunnerVersion       string
	Labels              map[string]string
}

type Material struct {
	RunnerID             string
	ControlBaseURL       *url.URL
	CertificateExpiresAt time.Time
	HTTPClient           *http.Client
}

type storedMetadata struct {
	RunnerID             string    `json:"runnerId"`
	ControlBaseURL       string    `json:"controlBaseUrl"`
	CertificateExpiresAt time.Time `json:"certificateExpiresAt"`
}

func Ensure(ctx context.Context, config Config, bundleDigest string) (*Material, error) {
	engineURL, err := parseHTTPSURL(config.EngineURL)
	if err != nil {
		return nil, fmt.Errorf("engine URL: %w", err)
	}
	if config.IdentityDirectory == "" || !filepath.IsAbs(config.IdentityDirectory) {
		return nil, errors.New("identity directory must be absolute")
	}
	if err := os.MkdirAll(config.IdentityDirectory, 0o700); err != nil {
		return nil, fmt.Errorf("create identity directory: %w", err)
	}
	if err := os.Chmod(config.IdentityDirectory, 0o700); err != nil {
		return nil, fmt.Errorf("secure identity directory: %w", err)
	}

	paths := identityPaths(config.IdentityDirectory)
	present, err := existingIdentityState(paths)
	if err != nil {
		return nil, err
	}
	if present {
		return loadMaterial(paths, engineURL)
	}
	if config.AccessID == "" || config.EnrollmentSecretFile == "" {
		return nil, errors.New("runner is not enrolled and Access ID/enrollment secret are missing")
	}
	if !filepath.IsAbs(config.EnrollmentSecretFile) {
		return nil, errors.New("enrollment secret file must be absolute")
	}

	secret, err := readSecret(config.EnrollmentSecretFile)
	if err != nil {
		return nil, fmt.Errorf("read enrollment secret: %w", err)
	}
	defer zero(secret)
	key, keyPEM, csrPEM, err := newKeyAndCSR(config.AccessID)
	if err != nil {
		return nil, err
	}

	bootstrapClient, err := bootstrapHTTPClient(config.BootstrapCAFile)
	if err != nil {
		return nil, err
	}
	request := protocol.EnrollmentExchangeRequest{
		CSRPEM: string(csrPEM),
		BundleDigest: bundleDigest,
		RunnerVersion: config.RunnerVersion,
		Labels: copyLabels(config.Labels),
	}
	endpoint := engineURL.ResolveReference(&url.URL{Path: "/runner/v1/enrollments:exchange"})
	var response protocol.EnrollmentExchangeResponse
	if err := postJSON(ctx, bootstrapClient, endpoint, request, map[string]string{
		"X-Weave-Access-Id": config.AccessID,
		"Authorization": "Bearer " + string(secret),
	}, http.StatusCreated, &response); err != nil {
		return nil, fmt.Errorf("exchange runner enrollment: %w", err)
	}
	if err := validateEnrollmentResponse(engineURL, key, response); err != nil {
		return nil, err
	}
	metadata := storedMetadata{
		RunnerID: response.RunnerID,
		ControlBaseURL: response.ControlBaseURL,
		CertificateExpiresAt: response.CertificateExpiresAt,
	}
	metadataJSON, err := json.MarshalIndent(metadata, "", "  ")
	if err != nil {
		return nil, err
	}
	if err := writeIdentityAtomically(paths, keyPEM, []byte(response.ClientCertificatePEM), []byte(response.CACertificatePEM), metadataJSON); err != nil {
		return nil, err
	}
	return loadMaterial(paths, engineURL)
}

type paths struct {
	key string
	certificate string
	ca string
	metadata string
}

func identityPaths(directory string) paths {
	return paths{
		key: filepath.Join(directory, keyFileName),
		certificate: filepath.Join(directory, certificateFileName),
		ca: filepath.Join(directory, caFileName),
		metadata: filepath.Join(directory, metadataFileName),
	}
}

func existingIdentityState(paths paths) (bool, error) {
	files := []string{paths.key, paths.certificate, paths.ca, paths.metadata}
	present := 0
	for _, path := range files {
		_, err := os.Lstat(path)
		switch {
		case err == nil:
			present++
		case errors.Is(err, os.ErrNotExist):
		default:
			return false, fmt.Errorf("inspect identity file %s: %w", path, err)
		}
	}
	if present != 0 && present != len(files) {
		return false, errors.New("runner identity is incomplete; refusing automatic repair")
	}
	return present == len(files), nil
}

func loadMaterial(paths paths, expectedEngine *url.URL) (*Material, error) {
	for _, path := range []string{paths.key, paths.certificate, paths.ca, paths.metadata} {
		info, err := os.Lstat(path)
		if err != nil {
			return nil, err
		}
		if info.Mode()&os.ModeSymlink != 0 || !info.Mode().IsRegular() {
			return nil, fmt.Errorf("identity file %s must be a regular non-symlink file", path)
		}
	}
	metadataRaw, err := os.ReadFile(paths.metadata)
	if err != nil {
		return nil, err
	}
	var metadata storedMetadata
	decoder := json.NewDecoder(bytes.NewReader(metadataRaw))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&metadata); err != nil {
		return nil, fmt.Errorf("decode identity metadata: %w", err)
	}
	controlURL, err := parseHTTPSURL(metadata.ControlBaseURL)
	if err != nil {
		return nil, fmt.Errorf("stored control URL: %w", err)
	}
	if !sameOrigin(expectedEngine, controlURL) {
		return nil, errors.New("stored control URL does not match the configured Engine origin")
	}
	certificate, err := tls.LoadX509KeyPair(paths.certificate, paths.key)
	if err != nil {
		return nil, fmt.Errorf("load runner key pair: %w", err)
	}
	caPEM, err := os.ReadFile(paths.ca)
	if err != nil {
		return nil, err
	}
	roots, err := x509.SystemCertPool()
	if err != nil || roots == nil {
		roots = x509.NewCertPool()
	}
	if !roots.AppendCertsFromPEM(caPEM) {
		return nil, errors.New("runner CA file contains no certificate")
	}
	transport := http.DefaultTransport.(*http.Transport).Clone()
	transport.TLSClientConfig = &tls.Config{
		MinVersion: tls.VersionTLS13,
		Certificates: []tls.Certificate{certificate},
		RootCAs: roots,
	}
	transport.MaxIdleConns = 20
	transport.MaxIdleConnsPerHost = 10
	transport.IdleConnTimeout = 90 * time.Second
	return &Material{
		RunnerID: metadata.RunnerID,
		ControlBaseURL: controlURL,
		CertificateExpiresAt: metadata.CertificateExpiresAt,
		HTTPClient: &http.Client{Transport: transport},
	}, nil
}

func validateEnrollmentResponse(engineURL *url.URL, key *ecdsa.PrivateKey, response protocol.EnrollmentExchangeResponse) error {
	if !strings.HasPrefix(response.RunnerID, "runner_") {
		return errors.New("enrollment response contains an invalid Runner ID")
	}
	controlURL, err := parseHTTPSURL(response.ControlBaseURL)
	if err != nil {
		return fmt.Errorf("enrollment control URL: %w", err)
	}
	if !sameOrigin(engineURL, controlURL) {
		return errors.New("enrollment control URL does not match the configured Engine origin")
	}
	if !response.CertificateExpiresAt.After(time.Now().Add(5 * time.Minute)) {
		return errors.New("enrollment certificate expiry is too close")
	}
	keyDER, err := x509.MarshalPKCS8PrivateKey(key)
	if err != nil {
		return err
	}
	keyPEM := pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: keyDER})
	if _, err := tls.X509KeyPair([]byte(response.ClientCertificatePEM), keyPEM); err != nil {
		return fmt.Errorf("enrollment certificate does not match generated key: %w", err)
	}
	if pool := x509.NewCertPool(); !pool.AppendCertsFromPEM([]byte(response.CACertificatePEM)) {
		return errors.New("enrollment response contains no valid CA certificate")
	}
	return nil
}

func newKeyAndCSR(accessID string) (*ecdsa.PrivateKey, []byte, []byte, error) {
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		return nil, nil, nil, err
	}
	keyDER, err := x509.MarshalPKCS8PrivateKey(key)
	if err != nil {
		return nil, nil, nil, err
	}
	csrDER, err := x509.CreateCertificateRequest(rand.Reader, &x509.CertificateRequest{
		Subject: pkixName("weave-runner-enrollment", accessID),
		SignatureAlgorithm: x509.ECDSAWithSHA256,
	}, key)
	if err != nil {
		return nil, nil, nil, err
	}
	return key,
		pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: keyDER}),
		pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE REQUEST", Bytes: csrDER}),
		nil
}

func pkixName(commonName string, accessID string) pkix.Name {
	return pkix.Name{CommonName: commonName, OrganizationalUnit: []string{accessID}}
}

func bootstrapHTTPClient(caFile string) (*http.Client, error) {
	roots, err := x509.SystemCertPool()
	if err != nil || roots == nil {
		roots = x509.NewCertPool()
	}
	if caFile != "" {
		if !filepath.IsAbs(caFile) {
			return nil, errors.New("bootstrap CA file must be absolute")
		}
		caPEM, err := os.ReadFile(caFile)
		if err != nil {
			return nil, err
		}
		if !roots.AppendCertsFromPEM(caPEM) {
			return nil, errors.New("bootstrap CA file contains no certificate")
		}
	}
	transport := http.DefaultTransport.(*http.Transport).Clone()
	transport.TLSClientConfig = &tls.Config{MinVersion: tls.VersionTLS13, RootCAs: roots}
	return &http.Client{Transport: transport, Timeout: 30 * time.Second}, nil
}

func postJSON(ctx context.Context, client *http.Client, endpoint *url.URL, input any, headers map[string]string, expectedStatus int, output any) error {
	body, err := json.Marshal(input)
	if err != nil {
		return err
	}
	request, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoint.String(), bytes.NewReader(body))
	if err != nil {
		return err
	}
	request.Header.Set("Content-Type", "application/json")
	request.Header.Set("Accept", "application/json, application/problem+json")
	for name, value := range headers {
		request.Header.Set(name, value)
	}
	response, err := client.Do(request)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	raw, err := io.ReadAll(io.LimitReader(response.Body, maximumResponseBytes+1))
	if err != nil {
		return err
	}
	if len(raw) > maximumResponseBytes {
		return errors.New("enrollment response exceeds the supported bound")
	}
	if response.StatusCode != expectedStatus {
		var problem protocol.Problem
		if json.Unmarshal(raw, &problem) == nil && problem.Title != "" {
			return fmt.Errorf("%s (%d)", problem.Title, response.StatusCode)
		}
		return fmt.Errorf("unexpected HTTP status %d", response.StatusCode)
	}
	return json.Unmarshal(raw, output)
}

func writeIdentityAtomically(paths paths, key, certificate, ca, metadata []byte) error {
	directory := filepath.Dir(paths.key)
	temporary, err := os.MkdirTemp(directory, ".identity-")
	if err != nil {
		return err
	}
	defer os.RemoveAll(temporary)
	if err := os.Chmod(temporary, 0o700); err != nil {
		return err
	}
	files := []struct {
		name string
		content []byte
		mode os.FileMode
		destination string
	}{
		{keyFileName, key, 0o600, paths.key},
		{certificateFileName, certificate, 0o600, paths.certificate},
		{caFileName, ca, 0o600, paths.ca},
		{metadataFileName, metadata, 0o600, paths.metadata},
	}
	for _, file := range files {
		temporaryPath := filepath.Join(temporary, file.name)
		if err := os.WriteFile(temporaryPath, file.content, file.mode); err != nil {
			return err
		}
		if err := syncFile(temporaryPath); err != nil {
			return err
		}
	}
	for _, file := range files {
		if err := os.Rename(filepath.Join(temporary, file.name), file.destination); err != nil {
			return err
		}
	}
	return syncDirectory(directory)
}

func syncFile(path string) error {
	file, err := os.Open(path)
	if err != nil {
		return err
	}
	defer file.Close()
	return file.Sync()
}

func syncDirectory(path string) error {
	directory, err := os.Open(path)
	if err != nil {
		return err
	}
	defer directory.Close()
	return directory.Sync()
}

func readSecret(path string) ([]byte, error) {
	info, err := os.Lstat(path)
	if err != nil {
		return nil, err
	}
	if info.Mode()&os.ModeSymlink != 0 || !info.Mode().IsRegular() || info.Size() > 4096 {
		return nil, errors.New("enrollment secret must be a small regular non-symlink file")
	}
	raw, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	secret := bytes.TrimSpace(raw)
	if len(secret) < 32 || len(secret) > 2048 {
		return nil, errors.New("enrollment secret has an invalid length")
	}
	return append([]byte(nil), secret...), nil
}

func parseHTTPSURL(value string) (*url.URL, error) {
	parsed, err := url.Parse(value)
	if err != nil {
		return nil, err
	}
	if parsed.Scheme != "https" || parsed.Host == "" || parsed.User != nil || parsed.RawQuery != "" || parsed.Fragment != "" {
		return nil, errors.New("URL must be an HTTPS origin without credentials, query or fragment")
	}
	parsed.Path = strings.TrimSuffix(parsed.Path, "/")
	return parsed, nil
}

func sameOrigin(left, right *url.URL) bool {
	return strings.EqualFold(left.Scheme, right.Scheme) && strings.EqualFold(left.Host, right.Host)
}

func copyLabels(values map[string]string) map[string]string {
	if values == nil {
		return nil
	}
	result := make(map[string]string, len(values))
	for key, value := range values {
		result[key] = value
	}
	return result
}

func zero(value []byte) {
	for index := range value {
		value[index] = 0
	}
}
