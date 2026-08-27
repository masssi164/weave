package bundle

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestLoadDerivesPublicBundleWithoutExecutionMetadata(t *testing.T) {
	t.Parallel()
	root := t.TempDir()
	handler := filepath.Join(root, "handler.sh")
	inputSchema := filepath.Join(root, "input.schema.json")
	outputSchema := filepath.Join(root, "output.schema.json")
	mustWrite(t, handler, "#!/bin/sh\ncat\n", 0o700)
	mustWrite(t, inputSchema, `{"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object","required":["name"],"properties":{"name":{"type":"string"}},"additionalProperties":false}`, 0o600)
	mustWrite(t, outputSchema, `{"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object"}`, 0o600)

	bundlePath := filepath.Join(root, "capabilities.json")
	bundleJSON := `{
		"schemaVersion":"weave.runner.capability-bundle/v1",
		"bundleId":"company.internal",
		"bundleVersion":"1.0.0",
		"capabilities":[{
			"id":"internal.echo",
			"version":"1.0.0",
			"title":"Internal echo",
			"inputSchema":` + quote(inputSchema) + `,
			"outputSchema":` + quote(outputSchema) + `,
			"effect":"READ_ONLY",
			"execution":{
				"handler":` + quote(handler) + `,
				"arguments":["--company-only"],
				"environmentAllowlist":["INTERNAL_TOKEN"]
			}
		}]
	}`
	mustWrite(t, bundlePath, bundleJSON, 0o600)

	loaded, err := Load(bundlePath)
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}
	publicJSON, err := json.Marshal(loaded.Public)
	if err != nil {
		t.Fatalf("marshal public bundle: %v", err)
	}
	public := string(publicJSON)
	for _, forbidden := range []string{handler, "--company-only", "INTERNAL_TOKEN", "workingDirectory", "environmentAllowlist"} {
		if strings.Contains(public, forbidden) {
			t.Fatalf("public bundle leaked %q: %s", forbidden, public)
		}
	}
	if !strings.Contains(public, `"inputSchema"`) || !strings.Contains(public, `"outputSchema"`) {
		t.Fatalf("public bundle omitted schemas: %s", public)
	}

	capability, ok := loaded.Find(loaded.CapabilityReferences()[0])
	if !ok {
		t.Fatal("loaded capability missing")
	}
	if err := capability.InputSchema.Validate(map[string]any{"name": "Weave"}); err != nil {
		t.Fatalf("valid input rejected: %v", err)
	}
	if err := capability.InputSchema.Validate(map[string]any{"unexpected": true}); err == nil {
		t.Fatal("invalid input accepted")
	}
}

func TestLoadRejectsShellOnlyHandlerAndUnknownFields(t *testing.T) {
	t.Parallel()
	root := t.TempDir()
	handler := filepath.Join(root, "handler.sh")
	schema := filepath.Join(root, "schema.json")
	mustWrite(t, handler, "echo unsafe\n", 0o600)
	mustWrite(t, schema, `true`, 0o600)
	bundlePath := filepath.Join(root, "capabilities.json")
	mustWrite(t, bundlePath, `{
		"schemaVersion":"weave.runner.capability-bundle/v1",
		"bundleId":"company.internal",
		"bundleVersion":"1.0.0",
		"unknown":true,
		"capabilities":[{
			"id":"internal.echo","version":"1.0.0","title":"Echo",
			"inputSchema":`+quote(schema)+`,"outputSchema":`+quote(schema)+`,
			"effect":"READ_ONLY","execution":{"handler":`+quote(handler)+`}
		}]
	}`, 0o600)

	if _, err := Load(bundlePath); err == nil {
		t.Fatal("bundle with unknown field and non-executable handler was accepted")
	}
}

func mustWrite(t *testing.T, path string, content string, mode os.FileMode) {
	t.Helper()
	if err := os.WriteFile(path, []byte(content), mode); err != nil {
		t.Fatalf("write %s: %v", path, err)
	}
}

func quote(value string) string {
	raw, _ := json.Marshal(value)
	return string(raw)
}
