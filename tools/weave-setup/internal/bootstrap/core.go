package bootstrap

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
	"time"
)

type ProviderTarget string

const (
	TargetForgejo ProviderTarget = "forgejo"
	TargetGitHub  ProviderTarget = "github-actions"
	TargetGitLab  ProviderTarget = "gitlab-ci"
	TargetAzure   ProviderTarget = "azure-devops"
)

var supportedTargets = map[ProviderTarget]bool{
	TargetForgejo: true,
	TargetGitHub:  true,
	TargetGitLab:  true,
	TargetAzure:   true,
}

type Detection struct {
	GitHubActions  []string `json:"githubActions"`
	GitLabCI       []string `json:"gitlabCi"`
	AzurePipelines []string `json:"azurePipelines"`
	ForgejoGitea   []string `json:"forgejoGitea"`
}

type TargetConfig struct {
	Target              ProviderTarget    `json:"target"`
	RemoteName          string            `json:"remoteName"`
	RemoteURL           string            `json:"remoteUrl"`
	Branch              string            `json:"branch"`
	ProviderVariables   map[string]string `json:"providerVariables,omitempty"`
	RequiredSecretNames []string          `json:"requiredSecretNames,omitempty"`
}

type Request struct {
	ExistingRepoPath        string
	CloneURL                string
	WorktreePath            string
	StorageLocation         string
	Target                  TargetConfig
	AllowExistingCIConflict bool
}

type Plan struct {
	ArtifactKind string       `json:"artifactKind"`
	Issue        int          `json:"issue"`
	SupportSafe  bool         `json:"supportSafe"`
	GeneratedAt  string       `json:"generatedAt"`
	Repo         RepoPlan     `json:"repo"`
	Detection    Detection    `json:"detection"`
	Target       TargetConfig `json:"target"`
	Actions      []string     `json:"actions"`
	Safety       Safety       `json:"safety"`
}

type RepoPlan struct {
	Mode            string `json:"mode"`
	WorktreePath    string `json:"worktreePath"`
	StorageLocation string `json:"storageLocation"`
}

type Safety struct {
	SecretValuesPersisted       bool     `json:"secretValuesPersisted"`
	GitHubSecretsRequired       bool     `json:"githubSecretsRequired"`
	PushRequiresExplicitRequest bool     `json:"pushRequiresExplicitRequest"`
	SelectedTargetOnly          bool     `json:"selectedTargetOnly"`
	ForbiddenPersistence        []string `json:"forbiddenPersistence"`
}

type GeneratedFile struct {
	Path    string
	Content []byte
}

type Result struct {
	Plan  Plan
	Files []GeneratedFile
}

var forbiddenValuePatterns = []*regexp.Regexp{
	regexp.MustCompile(`(?i)bearer\s+[a-z0-9._\-]+`),
	regexp.MustCompile(`(?i)gh[pousr]_[a-z0-9_]{12,}`),
	regexp.MustCompile(`(?i)(token|secret|password|private[_-]?key)\s*[:=]\s*[^\s,}\"]+`),
}

var secretishName = regexp.MustCompile(`(?i)(token|secret|password|private[_-]?key|credential)`)

func Detect(repoPath string) (Detection, error) {
	var d Detection
	addMatches := func(pattern string, dest *[]string) error {
		matches, err := filepath.Glob(filepath.Join(repoPath, filepath.FromSlash(pattern)))
		if err != nil {
			return err
		}
		for _, m := range matches {
			rel, err := filepath.Rel(repoPath, m)
			if err != nil {
				return err
			}
			*dest = append(*dest, filepath.ToSlash(rel))
		}
		sort.Strings(*dest)
		return nil
	}
	for _, p := range []struct {
		pattern string
		dest    *[]string
	}{
		{".github/workflows/*.yml", &d.GitHubActions}, {".github/workflows/*.yaml", &d.GitHubActions},
		{".gitlab-ci.yml", &d.GitLabCI}, {".gitlab-ci.yaml", &d.GitLabCI},
		{"azure-pipelines.yml", &d.AzurePipelines}, {"azure-pipelines.yaml", &d.AzurePipelines},
		{".forgejo/workflows/*.yml", &d.ForgejoGitea}, {".forgejo/workflows/*.yaml", &d.ForgejoGitea},
		{".gitea/workflows/*.yml", &d.ForgejoGitea}, {".gitea/workflows/*.yaml", &d.ForgejoGitea},
	} {
		if err := addMatches(p.pattern, p.dest); err != nil {
			return d, err
		}
	}
	return d, nil
}

func Validate(req Request, d Detection) error {
	if req.ExistingRepoPath == "" && req.CloneURL == "" {
		return errors.New("choose an existing repo or clone URL")
	}
	if req.ExistingRepoPath != "" && req.CloneURL != "" {
		return errors.New("choose either existing repo or clone URL, not both")
	}
	if req.WorktreePath == "" {
		return errors.New("worktree path is required")
	}
	if req.StorageLocation == "" {
		return errors.New("storage location is required")
	}
	if !supportedTargets[req.Target.Target] {
		return fmt.Errorf("unsupported CI/CD target %q", req.Target.Target)
	}
	if req.Target.RemoteName == "" {
		return errors.New("selected target remote name is required")
	}
	if req.Target.Branch == "" {
		return errors.New("selected target branch is required")
	}
	if err := validateRemoteURL(req.Target.RemoteURL); err != nil {
		return err
	}
	if req.Target.Target == TargetForgejo && strings.EqualFold(req.Target.RemoteName, "origin") && len(d.GitHubActions) > 0 && !req.AllowExistingCIConflict {
		return errors.New("existing GitHub Actions workflows detected while Forgejo target is selected; choose a Forgejo remote or explicitly allow the existing-CI conflict")
	}
	return validateNoSecrets(req.Target)
}

func validateRemoteURL(raw string) error {
	if raw == "" {
		return errors.New("selected target remote URL is required")
	}
	if u, err := url.Parse(raw); err == nil && u.Scheme != "" {
		if u.User != nil {
			if _, hasPassword := u.User.Password(); hasPassword || u.Scheme != "ssh" || secretishName.MatchString(u.User.Username()) {
				return errors.New("remote URL must not contain username/password credentials or token material")
			}
		}
		switch u.Scheme {
		case "https", "http", "ssh":
			return nil
		}
		return fmt.Errorf("unsupported remote URL scheme %q", u.Scheme)
	}
	if regexp.MustCompile(`^[\w.-]+@[\w.-]+:.+`).MatchString(raw) {
		return nil
	}
	return errors.New("remote URL must be https/http/ssh or scp-like git syntax")
}

func validateNoSecrets(target TargetConfig) error {
	for name, value := range target.ProviderVariables {
		if secretishName.MatchString(name) {
			return fmt.Errorf("provider variable %q looks secret-bearing; supply only non-secret variables and required secret-name hints", name)
		}
		if containsForbiddenSecret(value) {
			return fmt.Errorf("provider variable %q contains a raw secret-like value", name)
		}
	}
	for _, name := range target.RequiredSecretNames {
		if name == "" {
			return errors.New("required secret-name hints must not be empty")
		}
		if containsForbiddenSecret(name) {
			return fmt.Errorf("required secret-name hint %q contains a raw secret-like value", name)
		}
	}
	if target.Target == TargetForgejo {
		for _, name := range target.RequiredSecretNames {
			if strings.HasPrefix(strings.ToUpper(name), "GH_") || strings.HasPrefix(strings.ToUpper(name), "GITHUB_") {
				return errors.New("GitHub secret-name hints are not required for the Forgejo path")
			}
		}
	}
	return nil
}

func containsForbiddenSecret(value string) bool {
	for _, p := range forbiddenValuePatterns {
		if p.MatchString(value) {
			return true
		}
	}
	return false
}

func BuildPlan(req Request, now time.Time) (Result, error) {
	repoPath := req.ExistingRepoPath
	if repoPath == "" {
		repoPath = req.WorktreePath
	}
	d, err := Detect(repoPath)
	if err != nil && req.ExistingRepoPath != "" {
		return Result{}, err
	}
	if err := Validate(req, d); err != nil {
		return Result{}, err
	}
	target := req.Target
	target.ProviderVariables = sortedMap(target.ProviderVariables)
	sort.Strings(target.RequiredSecretNames)
	mode := "existing"
	if req.CloneURL != "" {
		mode = "clone"
	}
	plan := Plan{
		ArtifactKind: "weave-local-cicd-bootstrap-plan-v1",
		Issue:        666,
		SupportSafe:  true,
		GeneratedAt:  now.UTC().Format(time.RFC3339),
		Repo:         RepoPlan{Mode: mode, WorktreePath: req.WorktreePath, StorageLocation: req.StorageLocation},
		Detection:    d,
		Target:       target,
		Actions:      []string{"validate_repo_state", "generate_support_safe_plan", "commit_when_requested", "push_selected_target_when_requested"},
		Safety:       Safety{SecretValuesPersisted: false, GitHubSecretsRequired: target.Target == TargetGitHub, PushRequiresExplicitRequest: true, SelectedTargetOnly: true, ForbiddenPersistence: []string{"secretValue", "tokenValue", "rawCiLog", "rawProviderPayload", "credentialBearingUrl", "tenantUrl", "memberContent"}},
	}
	planJSON, err := json.MarshalIndent(plan, "", "  ")
	if err != nil {
		return Result{}, err
	}
	planJSON = append(planJSON, '\n')
	workflow := []byte(renderWorkflowPlan(plan))
	for _, content := range [][]byte{planJSON, workflow} {
		if containsForbiddenSecret(string(content)) {
			return Result{}, errors.New("generated artifact contains forbidden secret-like content")
		}
	}
	return Result{Plan: plan, Files: []GeneratedFile{{Path: ".weave/setup/bootstrap-plan.json", Content: planJSON}, {Path: fmt.Sprintf(".weave/setup/workflows/%s-setup.plan.json", target.Target), Content: workflow}}}, nil
}

func WriteFiles(root string, files []GeneratedFile) error {
	for _, f := range files {
		path := filepath.Join(root, filepath.FromSlash(f.Path))
		if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
			return err
		}
		if err := os.WriteFile(path, f.Content, 0o644); err != nil {
			return err
		}
	}
	return nil
}

type WorkflowPlan struct {
	ArtifactKind          string         `json:"artifactKind"`
	Version               int            `json:"version"`
	SelectedTarget        ProviderTarget `json:"selectedTarget"`
	RemoteName            string         `json:"remoteName"`
	Branch                string         `json:"branch"`
	GitHubSecretsRequired bool           `json:"githubSecretsRequired"`
	RequiredSecretNames   []string       `json:"requiredSecretNames"`
	NextActions           []string       `json:"nextActions"`
}

func renderWorkflowPlan(plan Plan) string {
	workflow := WorkflowPlan{
		ArtifactKind:          "weave-local-cicd-workflow-plan-v1",
		Version:               1,
		SelectedTarget:        plan.Target.Target,
		RemoteName:            plan.Target.RemoteName,
		Branch:                plan.Target.Branch,
		GitHubSecretsRequired: plan.Safety.GitHubSecretsRequired,
		RequiredSecretNames:   plan.Target.RequiredSecretNames,
		NextActions: []string{
			"validate selected target runner readiness",
			"dispatch only through the selected target after explicit approval",
		},
	}
	content, err := json.MarshalIndent(workflow, "", "  ")
	if err != nil {
		panic(err)
	}
	return string(append(content, '\n'))
}

func sortedMap(in map[string]string) map[string]string {
	if in == nil {
		return nil
	}
	keys := make([]string, 0, len(in))
	for k := range in {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	out := make(map[string]string, len(in))
	for _, k := range keys {
		out[k] = in[k]
	}
	return out
}

type RunnerServiceState string

const (
	RunnerMissing    RunnerServiceState = "runner_missing"
	RunnerRegistered RunnerServiceState = "runner_registered"
	RunnerOffline    RunnerServiceState = "runner_offline"
)

type RunnerReadinessStatus string

const (
	ReadinessBlockedRunnerMissing RunnerReadinessStatus = "runner_missing"
	ReadinessBlockedRunnerOffline RunnerReadinessStatus = "runner_offline"
	ReadinessBlockedSecretMissing RunnerReadinessStatus = "runner_secret_missing"
	ReadinessDispatchAllowed      RunnerReadinessStatus = "dispatch_allowed"
)

type RunnerReadinessInput struct {
	ProviderKey                  string
	WorkflowRef                  string
	RunnerState                  RunnerServiceState
	RequiredSecretNames          []string
	PresentSecretNames           []string
	CustomerOwnedSecretMechanism string
}

type RunnerReadinessResult struct {
	ProviderKey        string                `json:"providerKey"`
	WorkflowRef        string                `json:"workflowRef"`
	Status             RunnerReadinessStatus `json:"status"`
	MissingNames       []string              `json:"missingNames,omitempty"`
	DispatchAllowed    bool                  `json:"dispatchAllowed"`
	SupportSafeSummary string                `json:"supportSafeSummary"`
}

type RepositoryState struct {
	IsGitRepo                  bool
	Dirty                      bool
	Remotes                    []string
	Branches                   []string
	RunnerRegistrationPresent  bool
	RequiredSecretNamesPresent []string
}

func EvaluateRunnerReadiness(input RunnerReadinessInput) RunnerReadinessResult {
	providerKey := input.ProviderKey
	if providerKey == "" {
		providerKey = "local-forgejo-actions"
	}
	workflowRef := input.WorkflowRef
	if workflowRef == "" {
		workflowRef = "weave-admin-setup-e2e"
	}
	result := RunnerReadinessResult{ProviderKey: providerKey, WorkflowRef: workflowRef, DispatchAllowed: false}
	switch input.RunnerState {
	case RunnerRegistered:
		// continue below to SecretRef validation
	case RunnerOffline:
		result.Status = ReadinessBlockedRunnerOffline
		result.SupportSafeSummary = "Runner registration exists but is not online; dispatch is blocked before provider mutation."
		return result
	default:
		result.Status = ReadinessBlockedRunnerMissing
		result.MissingNames = []string{"FORGEJO_ACTIONS_RUNNER_REGISTRATION"}
		result.SupportSafeSummary = "Runner readiness is missing; register a customer-owned Forgejo Actions runner before dispatch."
		return result
	}
	missing := missingNames(input.RequiredSecretNames, input.PresentSecretNames)
	if len(missing) > 0 {
		result.Status = ReadinessBlockedSecretMissing
		result.MissingNames = missing
		result.SupportSafeSummary = "Required SecretRef or variable names are missing; values stay outside Weave evidence."
		return result
	}
	result.Status = ReadinessDispatchAllowed
	result.DispatchAllowed = true
	result.SupportSafeSummary = "Runner and required SecretRef names are ready; dispatch still requires explicit admin approval."
	return result
}

func ValidateRepositoryState(state RepositoryState, target TargetConfig) error {
	if !state.IsGitRepo {
		return errors.New("selected worktree must be a git repository")
	}
	if state.Dirty {
		return errors.New("dirty worktree must be committed or stashed before bootstrapper commit/push")
	}
	if !contains(state.Remotes, target.RemoteName) {
		return fmt.Errorf("selected target remote %q is missing", target.RemoteName)
	}
	if !contains(state.Branches, target.Branch) {
		return fmt.Errorf("selected target branch %q is missing", target.Branch)
	}
	if target.Target == TargetForgejo && !state.RunnerRegistrationPresent {
		return errors.New("Forgejo runner registration is missing")
	}
	for _, name := range target.RequiredSecretNames {
		if !contains(state.RequiredSecretNamesPresent, name) {
			return fmt.Errorf("required secret-name hint %q is missing", name)
		}
	}
	return nil
}

func ValidateRemoteURL(raw string) error { return validateRemoteURL(raw) }

func missingNames(required []string, present []string) []string {
	missing := make([]string, 0)
	for _, name := range required {
		if !contains(present, name) {
			missing = append(missing, name)
		}
	}
	sort.Strings(missing)
	return missing
}

func contains(values []string, needle string) bool {
	for _, value := range values {
		if value == needle {
			return true
		}
	}
	return false
}
