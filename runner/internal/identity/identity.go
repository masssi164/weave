package identity

import (
	"bytes"
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
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

const maximumResponseBytes = 256 * 1024

type Config struct {
	EngineURL            string
	IdentityDirectory    string
	AccessID             string
	EnrollmentSecretFile string
	BootstrapCAFile      string
	RunnerVersion        string
	Labels               map[string]string
}
type Material struct {
	RunnerID             string
	ControlBaseURL       *url.URL
	CertificateExpiresAt time.Time
	HTTPClient           *http.Client
}
type metadata struct {
	RunnerID             string    `json:"runnerId"`
	ControlBaseURL       string    `json:"controlBaseUrl"`
	CertificateExpiresAt time.Time `json:"certificateExpiresAt"`
}
type paths struct {
	key         string
	certificate string
	ca          string
	metadata    string
}

func Ensure(ctx context.Context, config Config, bundleDigest string) (*Material, error) {
	engine, err := parseOrigin(config.EngineURL)
	if err != nil {
		return nil, fmt.Errorf("engine URL: %w", err)
	}
	if !filepath.IsAbs(config.IdentityDirectory) {
		return nil, errors.New("identity directory must be absolute")
	}
	if err := os.MkdirAll(config.IdentityDirectory, 0o700); err != nil {
		return nil, err
	}
	if err := os.Chmod(config.IdentityDirectory, 0o700); err != nil {
		return nil, err
	}
	paths := identityPaths(config.IdentityDirectory)
	present, err := identityPresent(paths)
	if err != nil {
		return nil, err
	}
	if present {
		return load(paths, engine)
	}
	if config.AccessID == "" || !filepath.IsAbs(config.EnrollmentSecretFile) {
		return nil, errors.New("unenrolled Runner requires Access ID and absolute secret file")
	}
	secret, err := readSecret(config.EnrollmentSecretFile)
	if err != nil {
		return nil, err
	}
	defer zero(secret)
	key, keyPEM, csrPEM, err := newKeyAndCSR(config.AccessID)
	if err != nil {
		return nil, err
	}
	client, err := bootstrapClient(config.BootstrapCAFile)
	if err != nil {
		return nil, err
	}
	requestBody := protocol.EnrollmentRequest{CSRPEM: string(csrPEM), BundleDigest: bundleDigest, RunnerVersion: config.RunnerVersion, Labels: copyLabels(config.Labels)}
	endpoint := engine.ResolveReference(&url.URL{Path: "/runner/v1/enrollments:exchange"})
	var response protocol.EnrollmentResponse
	if err := postJSON(ctx, client, endpoint, requestBody, map[string]string{"X-Weave-Access-Id": config.AccessID, "Authorization": "Bearer " + string(secret)}, http.StatusCreated, &response); err != nil {
		return nil, fmt.Errorf("enroll Runner: %w", err)
	}
	if err := validateResponse(engine, key, response); err != nil {
		return nil, err
	}
	stored := metadata{RunnerID: response.RunnerID, ControlBaseURL: response.ControlBaseURL, CertificateExpiresAt: response.CertificateExpiresAt}
	metadataJSON, _ := json.MarshalIndent(stored, "", "  ")
	if err := writeIdentity(paths, keyPEM, []byte(response.ClientCertificatePEM), []byte(response.CACertificatePEM), metadataJSON); err != nil {
		return nil, err
	}
	return load(paths, engine)
}

func identityPaths(directory string) paths {
	return paths{key: filepath.Join(directory, "runner-key.pem"), certificate: filepath.Join(directory, "runner-cert.pem"), ca: filepath.Join(directory, "runner-ca.pem"), metadata: filepath.Join(directory, "identity.json")}
}

func identityPresent(value paths) (bool, error) {
	files := []string{value.key, value.certificate, value.ca, value.metadata}
	count := 0
	for _, path := range files {
		_, err := os.Lstat(path)
		if err == nil {
			count++
			continue
		}
		if !errors.Is(err, os.ErrNotExist) {
			return false, err
		}
	}
	if count != 0 && count != len(files) {
		return false, errors.New("Runner identity is incomplete; refusing automatic repair")
	}
	return count == len(files), nil
}

func load(value paths, expected *url.URL) (*Material, error) {
	for _, path := range []string{value.key, value.certificate, value.ca, value.metadata} {
		info, err := os.Lstat(path)
		if err != nil {
			return nil, err
		}
		if info.Mode()&os.ModeSymlink != 0 || !info.Mode().IsRegular() {
			return nil, fmt.Errorf("identity file %s is not a regular file", path)
		}
	}
	if info, err := os.Stat(value.key); err != nil || info.Mode().Perm()&0o077 != 0 {
		return nil, errors.New("Runner private key permissions are too broad")
	}
	raw, err := os.ReadFile(value.metadata)
	if err != nil {
		return nil, err
	}
	var stored metadata
	decoder := json.NewDecoder(bytes.NewReader(raw))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&stored); err != nil {
		return nil, err
	}
	control, err := parseOrigin(stored.ControlBaseURL)
	if err != nil {
		return nil, err
	}
	if !sameOrigin(expected, control) {
		return nil, errors.New("stored control URL differs from configured Engine origin")
	}
	certificate, err := tls.LoadX509KeyPair(value.certificate, value.key)
	if err != nil {
		return nil, err
	}
	if len(certificate.Certificate) == 0 {
		return nil, errors.New("Runner certificate chain is empty")
	}
	leaf, err := x509.ParseCertificate(certificate.Certificate[0])
	if err != nil {
		return nil, err
	}
	if time.Now().After(leaf.NotAfter) {
		return nil, errors.New("Runner certificate is expired")
	}
	caPEM, err := os.ReadFile(value.ca)
	if err != nil {
		return nil, err
	}
	roots, err := x509.SystemCertPool()
	if err != nil || roots == nil {
		roots = x509.NewCertPool()
	}
	if !roots.AppendCertsFromPEM(caPEM) {
		return nil, errors.New("Runner CA file contains no certificate")
	}
	transport := http.DefaultTransport.(*http.Transport).Clone()
	transport.TLSClientConfig = &tls.Config{MinVersion: tls.VersionTLS13, Certificates: []tls.Certificate{certificate}, RootCAs: roots}
	transport.MaxIdleConns = 20
	transport.MaxIdleConnsPerHost = 10
	transport.IdleConnTimeout = 90 * time.Second
	return &Material{RunnerID: stored.RunnerID, ControlBaseURL: control, CertificateExpiresAt: leaf.NotAfter, HTTPClient: &http.Client{Transport: transport}}, nil
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
	csrDER, err := x509.CreateCertificateRequest(rand.Reader, &x509.CertificateRequest{Subject: pkix.Name{CommonName: "weave-runner-enrollment", OrganizationalUnit: []string{accessID}}, SignatureAlgorithm: x509.ECDSAWithSHA256}, key)
	if err != nil {
		return nil, nil, nil, err
	}
	return key, pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: keyDER}), pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE REQUEST", Bytes: csrDER}), nil
}

func validateResponse(engine *url.URL, key *ecdsa.PrivateKey, response protocol.EnrollmentResponse) error {
	if !strings.HasPrefix(response.RunnerID, "runner_") {
		return errors.New("invalid Runner ID")
	}
	control, err := parseOrigin(response.ControlBaseURL)
	if err != nil {
		return err
	}
	if !sameOrigin(engine, control) {
		return errors.New("control URL differs from enrollment origin")
	}
	if !response.CertificateExpiresAt.After(time.Now().Add(5 * time.Minute)) {
		return errors.New("certificate expires too soon")
	}
	keyDER, err := x509.MarshalPKCS8PrivateKey(key)
	if err != nil {
		return err
	}
	keyPEM := pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: keyDER})
	if _, err := tls.X509KeyPair([]byte(response.ClientCertificatePEM), keyPEM); err != nil {
		return fmt.Errorf("certificate does not match generated key: %w", err)
	}
	pool := x509.NewCertPool()
	if !pool.AppendCertsFromPEM([]byte(response.CACertificatePEM)) {
		return errors.New("invalid CA certificate")
	}
	return nil
}

func bootstrapClient(caFile string) (*http.Client, error) {
	roots, err := x509.SystemCertPool()
	if err != nil || roots == nil {
		roots = x509.NewCertPool()
	}
	if caFile != "" {
		if !filepath.IsAbs(caFile) {
			return nil, errors.New("bootstrap CA file must be absolute")
		}
		raw, err := os.ReadFile(caFile)
		if err != nil {
			return nil, err
		}
		if !roots.AppendCertsFromPEM(raw) {
			return nil, errors.New("bootstrap CA file contains no certificate")
		}
	}
	transport := http.DefaultTransport.(*http.Transport).Clone()
	transport.TLSClientConfig = &tls.Config{MinVersion: tls.VersionTLS13, RootCAs: roots}
	return &http.Client{Transport: transport, Timeout: 30 * time.Second}, nil
}

func postJSON(ctx context.Context, client *http.Client, endpoint *url.URL, input any, headers map[string]string, expected int, output any) error {
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
	for key, value := range headers {
		request.Header.Set(key, value)
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
		return errors.New("response exceeds supported bound")
	}
	if response.StatusCode != expected {
		var problem protocol.Problem
		if json.Unmarshal(raw, &problem) == nil && problem.Title != "" {
			return fmt.Errorf("%s (%d)", problem.Title, response.StatusCode)
		}
		return fmt.Errorf("unexpected HTTP status %d", response.StatusCode)
	}
	return json.Unmarshal(raw, output)
}

func writeIdentity(value paths, key, certificate, ca, metadata []byte) error {
	directory := filepath.Dir(value.key)
	temporary, err := os.MkdirTemp(directory, ".identity-")
	if err != nil {
		return err
	}
	defer os.RemoveAll(temporary)
	if err := os.Chmod(temporary, 0o700); err != nil {
		return err
	}
	files := []struct {
		name        string
		content     []byte
		destination string
	}{{"runner-key.pem", key, value.key}, {"runner-cert.pem", certificate, value.certificate}, {"runner-ca.pem", ca, value.ca}, {"identity.json", metadata, value.metadata}}
	for _, file := range files {
		path := filepath.Join(temporary, file.name)
		if err := os.WriteFile(path, file.content, 0o600); err != nil {
			return err
		}
		handle, err := os.Open(path)
		if err != nil {
			return err
		}
		if err := handle.Sync(); err != nil {
			handle.Close()
			return err
		}
		handle.Close()
	}
	for _, file := range files {
		if err := os.Rename(filepath.Join(temporary, file.name), file.destination); err != nil {
			return err
		}
	}
	dir, err := os.Open(directory)
	if err == nil {
		err = dir.Sync()
		dir.Close()
	}
	return err
}
func readSecret(path string) ([]byte, error) {
	info, err := os.Lstat(path)
	if err != nil {
		return nil, err
	}
	if info.Mode()&os.ModeSymlink != 0 || !info.Mode().IsRegular() || info.Size() > 4096 {
		return nil, errors.New("enrollment secret must be a small regular file")
	}
	raw, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	secret := bytes.TrimSpace(raw)
	if len(secret) < 32 || len(secret) > 2048 {
		return nil, errors.New("invalid enrollment secret length")
	}
	return append([]byte(nil), secret...), nil
}
func parseOrigin(value string) (*url.URL, error) {
	parsed, err := url.Parse(value)
	if err != nil {
		return nil, err
	}
	if parsed.Scheme != "https" || parsed.Host == "" || parsed.User != nil || parsed.RawQuery != "" || parsed.Fragment != "" {
		return nil, errors.New("URL must be an HTTPS origin")
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
