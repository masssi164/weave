package bundle

import (
	"encoding/json"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"testing"
)

func TestPublicContractIgnoresPrivateExecutionMetadata(t *testing.T) {
	first := loadFixture(t, fixture{
		handlerName: "cmdb-a",
		arguments:   []string{"--site", "a"},
		environment: []string{"CMDB_A_TOKEN"},
		artifacts:   []string{"cmdb-report", "inventory"},
	})
	second := loadFixture(t, fixture{
		handlerName: "cmdb-b",
		arguments:   []string{"--site", "b"},
		environment: []string{"CMDB_B_TOKEN"},
		artifacts:   []string{"inventory", "cmdb-report"},
	})

	if first.LocalDigest == second.LocalDigest {
		t.Fatal("private local bundle digests must change with execution metadata")
	}
	if first.Public.BundleDigest != second.Public.BundleDigest {
		t.Fatalf("public bundle digest changed with private metadata: %s != %s", first.Public.BundleDigest, second.Public.BundleDigest)
	}
	if first.Public.Capabilities[0].ContractDigest != second.Public.Capabilities[0].ContractDigest {
		t.Fatalf("public capability contract changed with private metadata: %s != %s", first.Public.Capabilities[0].ContractDigest, second.Public.Capabilities[0].ContractDigest)
	}
	if !slices.Equal(first.Public.Capabilities[0].ArtifactTypes, []string{"cmdb-report", "inventory"}) {
		t.Fatalf("artifact types were not canonicalized: %#v", first.Public.Capabilities[0].ArtifactTypes)
	}

	published, err := json.Marshal(first.Public)
	if err != nil {
		t.Fatal(err)
	}
	for _, privateValue := range []string{"cmdb-a", "CMDB_A_TOKEN", "--site"} {
		if strings.Contains(string(published), privateValue) {
			t.Fatalf("public bundle leaked private execution value %q", privateValue)
		}
	}
}

func TestPublicContractChangesWithAgentVisibleSemantics(t *testing.T) {
	base := loadFixture(t, fixture{handlerName: "cmdb", title: "Look up internal assets"})
	changedTitle := loadFixture(t, fixture{handlerName: "cmdb", title: "Look up production assets"})
	changedSchema := loadFixture(t, fixture{
		handlerName: "cmdb",
		title:       "Look up internal assets",
		inputSchema: `{"type":"object","properties":{"asset":{"type":"string"}},"required":["asset"]}`,
	})

	if base.Public.Capabilities[0].ContractDigest == changedTitle.Public.Capabilities[0].ContractDigest {
		t.Fatal("tool title must participate in the public capability contract")
	}
	if base.Public.Capabilities[0].ContractDigest == changedSchema.Public.Capabilities[0].ContractDigest {
		t.Fatal("input schema must participate in the public capability contract")
	}
	if base.Public.BundleDigest == changedTitle.Public.BundleDigest {
		t.Fatal("public bundle digest must change when an agent-visible capability changes")
	}
}

func TestEquivalentJSONSchemaKeyOrderProducesOneContract(t *testing.T) {
	first := loadFixture(t, fixture{
		handlerName: "cmdb-a",
		inputSchema: `{"type":"object","properties":{"asset":{"type":"string"}},"required":["asset"]}`,
	})
	second := loadFixture(t, fixture{
		handlerName: "cmdb-b",
		inputSchema: `{"required":["asset"],"properties":{"asset":{"type":"string"}},"type":"object"}`,
	})

	if first.Public.Capabilities[0].InputSchemaDigest != second.Public.Capabilities[0].InputSchemaDigest {
		t.Fatal("equivalent JSON object key order must not change the schema digest")
	}
	if first.Public.Capabilities[0].ContractDigest != second.Public.Capabilities[0].ContractDigest {
		t.Fatal("equivalent JSON object key order must not change the capability contract")
	}
}

type fixture struct {
	handlerName string
	arguments   []string
	environment []string
	artifacts   []string
	title       string
	inputSchema string
}

func loadFixture(t *testing.T, value fixture) *Loaded {
	t.Helper()
	root := t.TempDir()
	handler := filepath.Join(root, value.handlerName)
	if err := os.WriteFile(handler, []byte("#!/bin/sh\ncat\n"), 0o700); err != nil {
		t.Fatal(err)
	}
	inputSchema := value.inputSchema
	if inputSchema == "" {
		inputSchema = `{"type":"object","additionalProperties":false}`
	}
	outputSchema := `{"additionalProperties":false,"type":"object"}`
	inputPath := filepath.Join(root, "input.schema.json")
	outputPath := filepath.Join(root, "output.schema.json")
	if err := os.WriteFile(inputPath, []byte(inputSchema), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(outputPath, []byte(outputSchema), 0o600); err != nil {
		t.Fatal(err)
	}
	artifacts := value.artifacts
	if artifacts == nil {
		artifacts = []string{"cmdb-report"}
	}
	title := value.title
	if title == "" {
		title = "Look up internal assets"
	}
	bundlePath := filepath.Join(root, "capabilities.json")
	bundle := map[string]any{
		"schemaVersion": "weave.runner.capability-bundle/v1",
		"bundleId":      "internal.cmdb",
		"bundleVersion": "1.0.0",
		"capabilities": []any{map[string]any{
			"id":            "internal.cmdb.lookup",
			"version":       "1.0.0",
			"title":         title,
			"description":   "Returns a bounded internal asset record.",
			"inputSchema":   inputPath,
			"outputSchema":  outputPath,
			"effect":        "READ_ONLY",
			"artifactTypes": artifacts,
			"execution": map[string]any{
				"handler":              handler,
				"arguments":            value.arguments,
				"environmentAllowlist": value.environment,
				"networkProfile":       "internal-read",
				"timeoutSeconds":       60,
				"maxOutputBytes":       4096,
			},
		}},
	}
	raw, err := json.Marshal(bundle)
	if err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(bundlePath, raw, 0o600); err != nil {
		t.Fatal(err)
	}
	loaded, err := Load(bundlePath)
	if err != nil {
		t.Fatal(err)
	}
	return loaded
}
