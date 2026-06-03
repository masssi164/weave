package main

import (
	"bufio"
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"strings"
	"time"

	"github.com/masssi164/weave/tools/weave-setup/internal/bootstrap"
)

func main() {
	if err := run(os.Args[1:]); err != nil {
		fmt.Fprintln(os.Stderr, "weave-setup:", err)
		os.Exit(1)
	}
}

func run(args []string) error {
	if len(args) == 0 {
		usage()
		return nil
	}
	if isHelp(args[0]) {
		usage()
		return nil
	}
	if len(args) > 1 && isHelp(args[1]) {
		return commandUsage(args[0])
	}
	switch args[0] {
	case "app":
		return app()
	case "detect":
		return detect(args[1:])
	case "validate":
		return planLike(args[0], args[1:], false, false, false)
	case "plan", "init":
		return planLike(args[0], args[1:], true, false, false)
	case "commit":
		return planLike(args[0], args[1:], true, true, false)
	case "push":
		return planLike(args[0], args[1:], true, true, true)
	default:
		usage()
		return fmt.Errorf("unknown command %q", args[0])
	}
}

func isHelp(arg string) bool {
	return arg == "--help" || arg == "-h" || arg == "help"
}

func commandUsage(command string) error {
	switch command {
	case "app":
		fmt.Println(`weave-setup app - accessible guided terminal app mode

Runs the local first-run setup as a text-only, keyboard-first flow. It prompts for
repository/worktree selection, target CI/CD backend, non-secret provider variables,
and required secret-name hints. It never accepts secret values.`)
		return nil
	case "detect":
		fs := flag.NewFlagSet("detect", flag.ContinueOnError)
		fs.String("repo", ".", "repository path")
		fs.Bool("json", false, "emit JSON")
		fs.Usage()
		return nil
	case "validate", "plan", "init", "commit", "push":
		fs, _ := newPlanFlagSet(command)
		fs.Usage()
		return nil
	default:
		usage()
		return fmt.Errorf("unknown command %q", command)
	}
}

func usage() {
	fmt.Println(`weave-setup - local CI/CD bootstrapper for Weave issue #666

Commands:
  app       Accessible guided terminal app mode for local first-run setup.
  detect    Detect existing CI/CD files in a repo.
  validate  Validate target values without writing files.
  plan      Generate support-safe config/workflow plan files.
  commit    Generate plan files and create a local git commit.
  push      Generate, commit, and push only to the selected target remote/branch.

Use --help on a command for flags. Secret values are never accepted; pass required secret-name hints only.`)
}

func detect(args []string) error {
	fs := flag.NewFlagSet("detect", flag.ExitOnError)
	repo := fs.String("repo", ".", "repository path")
	jsonOut := fs.Bool("json", false, "emit JSON")
	_ = fs.Parse(args)
	d, err := bootstrap.Detect(*repo)
	if err != nil {
		return err
	}
	if *jsonOut {
		enc := json.NewEncoder(os.Stdout)
		enc.SetIndent("", "  ")
		return enc.Encode(d)
	}
	fmt.Printf("GitHub Actions: %v\nGitLab CI: %v\nAzure Pipelines: %v\nForgejo/Gitea: %v\n", d.GitHubActions, d.GitLabCI, d.AzurePipelines, d.ForgejoGitea)
	return nil
}

func planLike(commandName string, args []string, write, commit, push bool) error {
	fs, flags := newPlanFlagSet(commandName)
	_ = fs.Parse(args)
	if *flags.worktree == "" {
		*flags.worktree = *flags.repo
	}
	if *flags.clone != "" {
		if _, err := os.Stat(*flags.worktree); os.IsNotExist(err) {
			if err := bootstrap.CloneRepository(*flags.clone, *flags.worktree); err != nil {
				return err
			}
		}
	}
	req := bootstrap.Request{ExistingRepoPath: *flags.repo, CloneURL: *flags.clone, WorktreePath: *flags.worktree, StorageLocation: *flags.storage, Target: bootstrap.TargetConfig{Target: bootstrap.ProviderTarget(*flags.target), RemoteName: *flags.remoteName, RemoteURL: *flags.remoteURL, Branch: *flags.branch, ProviderVariables: parsePairs(*flags.vars), RequiredSecretNames: parseList(*flags.secretNames)}, AllowExistingCIConflict: *flags.allowConflict}
	if *flags.clone != "" {
		req.ExistingRepoPath = ""
	}
	result, err := bootstrap.BuildPlan(req, time.Unix(0, 0).UTC())
	if err != nil {
		return err
	}
	if !write {
		fmt.Println("validation ok: support-safe bootstrap plan can be generated")
		return nil
	}
	if push && !*flags.yesPush {
		return fmt.Errorf("push requires --yes-push to avoid accidental remote mutation")
	}
	if commit || push {
		if err := (bootstrap.GitRunner{Dir: req.WorktreePath}).StatusClean(); err != nil {
			return err
		}
	}
	if err := bootstrap.WriteFiles(req.WorktreePath, result.Files); err != nil {
		return err
	}
	fmt.Printf("wrote %d support-safe plan files under %s\n", len(result.Files), req.WorktreePath)
	if commit || push {
		runner := bootstrap.GitRunner{Dir: req.WorktreePath}
		if err := runner.Commit(result.Files, "feat(setup): add local CI/CD bootstrap plan"); err != nil {
			return err
		}
		fmt.Println("created bootstrap plan commit")
		if push {
			if err := runner.Push(req.Target.RemoteName, req.Target.Branch); err != nil {
				return err
			}
			fmt.Printf("pushed only to selected target %s/%s\n", req.Target.RemoteName, req.Target.Branch)
		}
	}
	return nil
}

type planFlags struct {
	repo          *string
	clone         *string
	worktree      *string
	storage       *string
	target        *string
	remoteName    *string
	remoteURL     *string
	branch        *string
	vars          *string
	secretNames   *string
	allowConflict *bool
	yesPush       *bool
}

func newPlanFlagSet(commandName string) (*flag.FlagSet, planFlags) {
	fs := flag.NewFlagSet(commandName, flag.ExitOnError)
	flags := planFlags{
		repo:          fs.String("repo", ".", "existing repository path"),
		clone:         fs.String("clone-url", "", "clone URL instead of existing repo"),
		worktree:      fs.String("worktree", "", "chosen worktree path"),
		storage:       fs.String("storage", "", "chosen storage location"),
		target:        fs.String("target", "forgejo", "CI/CD target: forgejo, github-actions, gitlab-ci, azure-devops"),
		remoteName:    fs.String("remote-name", "", "selected target remote name"),
		remoteURL:     fs.String("remote-url", "", "selected target remote URL without credentials"),
		branch:        fs.String("branch", "", "selected target branch"),
		vars:          fs.String("var", "", "comma-separated non-secret KEY=VALUE provider variables"),
		secretNames:   fs.String("secret-name", "", "comma-separated required secret-name hints; values are forbidden"),
		allowConflict: fs.Bool("allow-existing-ci-conflict", false, "allow existing CI files for another target"),
		yesPush:       fs.Bool("yes-push", false, "required for push command"),
	}
	return fs, flags
}

func app() error {
	reader := bufio.NewReader(os.Stdin)
	fmt.Println("Weave local CI/CD bootstrapper app mode (#666)")
	fmt.Println("Accessible terminal flow: all prompts are text, keyboard-only, and screenreader-friendly. A later GUI shell may wrap this same Go core package.")
	repo := prompt(reader, "Existing local repo path (leave empty to clone)")
	clone := ""
	if repo == "" {
		clone = prompt(reader, "Clone URL (no embedded credentials)")
	}
	worktree := prompt(reader, "Worktree path")
	storage := prompt(reader, "Storage/worktree state location")
	target := prompt(reader, "CI/CD target [forgejo|github-actions|gitlab-ci|azure-devops]")
	remoteName := prompt(reader, "Selected target remote name")
	remoteURL := prompt(reader, "Selected target remote URL (no credentials)")
	branch := prompt(reader, "Selected target branch")
	vars := prompt(reader, "Non-secret provider variables KEY=VALUE, comma-separated (optional)")
	secretNames := prompt(reader, "Required secret-name hints, comma-separated (values forbidden)")
	if worktree == "" {
		worktree = repo
	}
	if clone != "" {
		if _, err := os.Stat(worktree); os.IsNotExist(err) {
			if err := bootstrap.CloneRepository(clone, worktree); err != nil {
				return err
			}
		}
	}
	req := bootstrap.Request{ExistingRepoPath: repo, CloneURL: clone, WorktreePath: worktree, StorageLocation: storage, Target: bootstrap.TargetConfig{Target: bootstrap.ProviderTarget(target), RemoteName: remoteName, RemoteURL: remoteURL, Branch: branch, ProviderVariables: parsePairs(vars), RequiredSecretNames: parseList(secretNames)}}
	result, err := bootstrap.BuildPlan(req, time.Unix(0, 0).UTC())
	if err != nil {
		return err
	}
	fmt.Printf("Plan ready for %s. GitHub secrets required: %t. No secret values persisted.\n", result.Plan.Target.Target, result.Plan.Safety.GitHubSecretsRequired)
	if strings.EqualFold(prompt(reader, "Write support-safe plan files now? [yes/no]"), "yes") {
		if err := bootstrap.WriteFiles(worktree, result.Files); err != nil {
			return err
		}
		fmt.Println("Plan files written. Commit/push require explicit CLI commands or a future guided confirmation step.")
	}
	return nil
}

func prompt(reader *bufio.Reader, label string) string {
	fmt.Print(label + ": ")
	value, _ := reader.ReadString('\n')
	return strings.TrimSpace(value)
}

func parseList(raw string) []string {
	if raw == "" {
		return nil
	}
	parts := strings.Split(raw, ",")
	out := make([]string, 0, len(parts))
	for _, p := range parts {
		if v := strings.TrimSpace(p); v != "" {
			out = append(out, v)
		}
	}
	return out
}

func parsePairs(raw string) map[string]string {
	if raw == "" {
		return nil
	}
	out := map[string]string{}
	for _, p := range strings.Split(raw, ",") {
		if p = strings.TrimSpace(p); p != "" {
			kv := strings.SplitN(p, "=", 2)
			if len(kv) == 2 {
				out[strings.TrimSpace(kv[0])] = strings.TrimSpace(kv[1])
			}
		}
	}
	return out
}
