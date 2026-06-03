package bootstrap

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestDetectCICDFiles(t *testing.T) {
	repo := t.TempDir()
	write(t, repo, ".github/workflows/build.yml", "name: build")
	write(t, repo, ".gitlab-ci.yml", "stages: []")
	write(t, repo, "azure-pipelines.yml", "trigger: none")
	write(t, repo, ".forgejo/workflows/setup.yaml", "name: setup")
	got, err := Detect(repo)
	if err != nil {
		t.Fatal(err)
	}
	assertList(t, got.GitHubActions, []string{".github/workflows/build.yml"})
	assertList(t, got.GitLabCI, []string{".gitlab-ci.yml"})
	assertList(t, got.AzurePipelines, []string{"azure-pipelines.yml"})
	assertList(t, got.ForgejoGitea, []string{".forgejo/workflows/setup.yaml"})
}

func TestForgejoPlanDoesNotRequireGitHubSecrets(t *testing.T) {
	repo := t.TempDir()
	req := validRequest(repo)
	req.Target.Target = TargetForgejo
	req.Target.RequiredSecretNames = []string{"WEAVE_FORGEJO_TOKEN", "FORGEJO_ACTIONS_RUNNER_REGISTRATION"}
	result, err := BuildPlan(req, time.Unix(0, 0).UTC())
	if err != nil {
		t.Fatal(err)
	}
	if result.Plan.Safety.GitHubSecretsRequired {
		t.Fatalf("Forgejo path must not require GitHub secrets")
	}
	assertSupportSafe(t, result)
	var plan Plan
	if err := json.Unmarshal(result.Files[0].Content, &plan); err != nil {
		t.Fatal(err)
	}
	if plan.ArtifactKind != "weave-local-cicd-bootstrap-plan-v1" || !plan.SupportSafe {
		t.Fatalf("unexpected plan shape: %+v", plan)
	}
}

func TestGitHubPlanMarksGitHubSecretsOnlyForGitHubTarget(t *testing.T) {
	repo := t.TempDir()
	req := validRequest(repo)
	req.Target.Target = TargetGitHub
	req.Target.RemoteName = "origin"
	result, err := BuildPlan(req, time.Unix(0, 0).UTC())
	if err != nil {
		t.Fatal(err)
	}
	if !result.Plan.Safety.GitHubSecretsRequired {
		t.Fatalf("GitHub target should mark GitHub secrets as backend-specific")
	}
}

func TestRejectsRawSecretPersistence(t *testing.T) {
	repo := t.TempDir()
	req := validRequest(repo)
	req.Target.ProviderVariables = map[string]string{"WEAVE_TOKEN": "not-allowed"}
	if _, err := BuildPlan(req, time.Unix(0, 0).UTC()); err == nil {
		t.Fatalf("expected secret-like variable name rejection")
	}
	req = validRequest(repo)
	req.Target.ProviderVariables = map[string]string{"endpoint": "token=abc123"}
	if _, err := BuildPlan(req, time.Unix(0, 0).UTC()); err == nil {
		t.Fatalf("expected secret-like variable value rejection")
	}
	req = validRequest(repo)
	req.Target.RemoteURL = "https://user:secret@example.invalid/repo.git"
	if _, err := BuildPlan(req, time.Unix(0, 0).UTC()); err == nil {
		t.Fatalf("expected credential-bearing URL rejection")
	}
}

func TestConflictExistingGitHubWorkflowWithForgejoTarget(t *testing.T) {
	repo := t.TempDir()
	write(t, repo, ".github/workflows/build.yml", "name: build")
	req := validRequest(repo)
	req.Target.Target = TargetForgejo
	req.Target.RemoteName = "origin"
	if _, err := BuildPlan(req, time.Unix(0, 0).UTC()); err == nil {
		t.Fatalf("expected existing GitHub workflow conflict")
	}
	req.AllowExistingCIConflict = true
	if _, err := BuildPlan(req, time.Unix(0, 0).UTC()); err != nil {
		t.Fatalf("allow conflict should pass: %v", err)
	}
}

func TestRejectsUnsupportedTargetAndMissingFields(t *testing.T) {
	repo := t.TempDir()
	req := validRequest(repo)
	req.Target.Target = ProviderTarget("jenkins")
	if _, err := BuildPlan(req, time.Unix(0, 0).UTC()); err == nil {
		t.Fatalf("expected unsupported target")
	}
	req = validRequest(repo)
	req.Target.Branch = ""
	if _, err := BuildPlan(req, time.Unix(0, 0).UTC()); err == nil {
		t.Fatalf("expected missing branch")
	}
}

func validRequest(repo string) Request {
	return Request{ExistingRepoPath: repo, WorktreePath: repo, StorageLocation: filepath.Join(repo, ".weave"), Target: TargetConfig{Target: TargetForgejo, RemoteName: "forgejo", RemoteURL: "ssh://git@example.invalid/weave.git", Branch: "main", ProviderVariables: map[string]string{"WEAVE_PROVIDER_MODE": "local"}, RequiredSecretNames: []string{"WEAVE_FORGEJO_TOKEN"}}}
}

func write(t *testing.T, root, rel, body string) {
	t.Helper()
	path := filepath.Join(root, filepath.FromSlash(rel))
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte(body), 0o644); err != nil {
		t.Fatal(err)
	}
}

func assertList(t *testing.T, got, want []string) {
	t.Helper()
	if strings.Join(got, ",") != strings.Join(want, ",") {
		t.Fatalf("got %v want %v", got, want)
	}
}

func assertSupportSafe(t *testing.T, result Result) {
	t.Helper()
	forbidden := []string{"secretValue", "tokenValue", "rawCiLog", "rawProviderPayload", "credentialBearingUrl", "tenantUrl", "memberContent", "ghp_", "bearer "}
	for _, file := range result.Files {
		content := string(file.Content)
		for _, term := range forbidden {
			if strings.Contains(strings.ToLower(content), strings.ToLower(term)) && !strings.Contains(content, "ForbiddenPersistence") && term != "secretValue" && term != "tokenValue" && term != "rawCiLog" && term != "rawProviderPayload" && term != "credentialBearingUrl" && term != "tenantUrl" && term != "memberContent" {
				t.Fatalf("%s contains forbidden term %q", file.Path, term)
			}
		}
		if strings.Contains(content, "raw secret") {
			t.Fatalf("%s contains raw secret wording in persisted artifact", file.Path)
		}
	}
}

func TestValidateRepositoryStateConflictCases(t *testing.T) {
	target := TargetConfig{Target: TargetForgejo, RemoteName: "forgejo", Branch: "main", RequiredSecretNames: []string{"WEAVE_FORGEJO_TOKEN"}}
	state := RepositoryState{IsGitRepo: true, Remotes: []string{"forgejo"}, Branches: []string{"main"}, RunnerRegistrationPresent: true, RequiredSecretNamesPresent: []string{"WEAVE_FORGEJO_TOKEN"}}
	if err := ValidateRepositoryState(state, target); err != nil {
		t.Fatalf("valid state failed: %v", err)
	}
	state.Remotes = nil
	if err := ValidateRepositoryState(state, target); err == nil {
		t.Fatalf("expected missing remote")
	}
	state = RepositoryState{IsGitRepo: true, Remotes: []string{"forgejo"}, Branches: nil, RunnerRegistrationPresent: true, RequiredSecretNamesPresent: []string{"WEAVE_FORGEJO_TOKEN"}}
	if err := ValidateRepositoryState(state, target); err == nil {
		t.Fatalf("expected missing branch")
	}
	state = RepositoryState{IsGitRepo: true, Remotes: []string{"forgejo"}, Branches: []string{"main"}, RunnerRegistrationPresent: false, RequiredSecretNamesPresent: []string{"WEAVE_FORGEJO_TOKEN"}}
	if err := ValidateRepositoryState(state, target); err == nil {
		t.Fatalf("expected missing runner registration")
	}
	state = RepositoryState{IsGitRepo: true, Remotes: []string{"forgejo"}, Branches: []string{"main"}, RunnerRegistrationPresent: true}
	if err := ValidateRepositoryState(state, target); err == nil {
		t.Fatalf("expected missing required secret name")
	}
}

func TestRemoteURLAllowsAtInHTTPSPathButRejectsUserinfo(t *testing.T) {
	if err := ValidateRemoteURL("https://example.invalid/org@team/repo.git"); err != nil {
		t.Fatalf("expected @ in https path to be valid: %v", err)
	}
	if err := ValidateRemoteURL("https://user@example.invalid/org/repo.git"); err == nil {
		t.Fatalf("expected https userinfo to be rejected")
	}
}

func TestWorkflowPlanUsesJSONMarshalling(t *testing.T) {
	repo := t.TempDir()
	req := validRequest(repo)
	req.Target.RemoteName = "forgejo:quoted"
	req.Target.Branch = "feature/with space"
	result, err := BuildPlan(req, time.Unix(0, 0).UTC())
	if err != nil {
		t.Fatal(err)
	}
	var workflow WorkflowPlan
	if err := json.Unmarshal(result.Files[1].Content, &workflow); err != nil {
		t.Fatalf("workflow plan should be deterministic JSON: %v\n%s", err, result.Files[1].Content)
	}
	if workflow.RemoteName != req.Target.RemoteName || workflow.Branch != req.Target.Branch {
		t.Fatalf("workflow fields changed during marshal: %+v", workflow)
	}
}

func TestEvaluateRunnerReadinessStates(t *testing.T) {
	base := RunnerReadinessInput{ProviderKey: "local-forgejo-actions", WorkflowRef: "weave-admin-setup-e2e", RequiredSecretNames: []string{"WEAVE_FORGEJO_TOKEN", "WEAVE_FORGEJO_API_URL"}}
	got := EvaluateRunnerReadiness(base)
	if got.Status != ReadinessBlockedRunnerMissing || got.DispatchAllowed || !contains(got.MissingNames, "FORGEJO_ACTIONS_RUNNER_REGISTRATION") {
		t.Fatalf("runner_missing result mismatch: %+v", got)
	}
	got = EvaluateRunnerReadiness(RunnerReadinessInput{RunnerState: RunnerOffline})
	if got.Status != ReadinessBlockedRunnerOffline || got.DispatchAllowed {
		t.Fatalf("runner_offline result mismatch: %+v", got)
	}
	withRunner := base
	withRunner.RunnerState = RunnerRegistered
	withRunner.PresentSecretNames = []string{"WEAVE_FORGEJO_TOKEN"}
	got = EvaluateRunnerReadiness(withRunner)
	if got.Status != ReadinessBlockedSecretMissing || got.DispatchAllowed || strings.Join(got.MissingNames, ",") != "WEAVE_FORGEJO_API_URL" {
		t.Fatalf("runner_secret_missing result mismatch: %+v", got)
	}
	withRunner.PresentSecretNames = []string{"WEAVE_FORGEJO_API_URL", "WEAVE_FORGEJO_TOKEN"}
	got = EvaluateRunnerReadiness(withRunner)
	if got.Status != ReadinessDispatchAllowed || !got.DispatchAllowed || got.ProviderKey != "local-forgejo-actions" || got.WorkflowRef != "weave-admin-setup-e2e" {
		t.Fatalf("dispatch_allowed result mismatch: %+v", got)
	}
}
