package bootstrap

import (
	"os/exec"
	"testing"
)

func TestCommitSkipsWhenNothingStaged(t *testing.T) {
	repo := t.TempDir()
	runGit(t, repo, "init")
	runGit(t, repo, "config", "user.name", "Weave Test")
	runGit(t, repo, "config", "user.email", "weave-test@example.invalid")
	file := GeneratedFile{Path: "plan.json", Content: []byte("{}\n")}
	if err := WriteFiles(repo, []GeneratedFile{file}); err != nil {
		t.Fatal(err)
	}
	runner := GitRunner{Dir: repo}
	if err := runner.Commit([]GeneratedFile{file}, "test: initial"); err != nil {
		t.Fatal(err)
	}
	if err := runner.Commit([]GeneratedFile{file}, "test: no-op"); err != nil {
		t.Fatalf("no-op commit should be skipped without error: %v", err)
	}
}

func runGit(t *testing.T, dir string, args ...string) {
	t.Helper()
	cmd := exec.Command("git", args...)
	cmd.Dir = dir
	out, err := cmd.CombinedOutput()
	if err != nil {
		t.Fatalf("git %v failed: %v\n%s", args, err, out)
	}
}
