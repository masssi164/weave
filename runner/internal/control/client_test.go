package control

import (
	"context"
	"net/http"
	"net/http/httptest"
	"net/url"
	"testing"

	"github.com/masssi164/weave/runner/internal/protocol"
)

type observedClaimRequest struct {
	path     string
	rawQuery string
	prefer   string
}

func TestClaimUsesPreferWaitHeaderWithoutLegacyQuery(t *testing.T) {
	observed := make(chan observedClaimRequest, 1)
	server := httptest.NewTLSServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		observed <- observedClaimRequest{
			path:     request.URL.Path,
			rawQuery: request.URL.RawQuery,
			prefer:   request.Header.Get("Prefer"),
		}
		writer.Header().Set("Cache-Control", "no-store")
		writer.Header().Set("Preference-Applied", "wait=7")
		writer.Header().Set("Retry-After", "1")
		writer.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()

	base, err := url.Parse(server.URL)
	if err != nil {
		t.Fatalf("parse test server URL: %v", err)
	}
	client, err := New(base, server.Client())
	if err != nil {
		t.Fatalf("create control client: %v", err)
	}

	lease, err := client.Claim(context.Background(), 7, protocol.ClaimRequest{
		RunnerID:     "runner_contract_01",
		BundleDigest: "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
		Capabilities: []protocol.CapabilityRef{{ID: "internal.asset.lookup", Version: "1.0.0"}},
		AvailableSlots: 1,
	})
	if err != nil {
		t.Fatalf("claim: %v", err)
	}
	if lease != nil {
		t.Fatal("expected no lease for HTTP 204")
	}

	request := <-observed
	if request.path != "/runner/v1/tasks:claim" {
		t.Fatalf("unexpected claim path: %s", request.path)
	}
	if request.rawQuery != "" {
		t.Fatalf("legacy query parameters remain on task claim: %s", request.rawQuery)
	}
	if request.prefer != "wait=7" {
		t.Fatalf("unexpected Prefer header: %q", request.prefer)
	}
}

func TestClaimRejectsWaitOutsideProtocolBounds(t *testing.T) {
	base, err := url.Parse("https://weave.example")
	if err != nil {
		t.Fatalf("parse base URL: %v", err)
	}
	client, err := New(base, http.DefaultClient)
	if err != nil {
		t.Fatalf("create control client: %v", err)
	}
	request := protocol.ClaimRequest{
		RunnerID:       "runner_contract_01",
		BundleDigest:   "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
		Capabilities:   []protocol.CapabilityRef{{ID: "internal.asset.lookup", Version: "1.0.0"}},
		AvailableSlots: 1,
	}

	for _, waitSeconds := range []int{-1, 31} {
		if _, err := client.Claim(context.Background(), waitSeconds, request); err == nil {
			t.Fatalf("waitSeconds %d was accepted", waitSeconds)
		}
	}
}
