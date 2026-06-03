package bootstrap

import (
	"bytes"
	"fmt"
	"os/exec"
	"strings"
)

type GitRunner struct{ Dir string }

func CloneRepository(cloneURL, worktreePath string) error {
	if err := ValidateRemoteURL(cloneURL); err != nil {
		return err
	}
	cmd := exec.Command("git", "clone", cloneURL, worktreePath)
	var stdout, stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr
	if err := cmd.Run(); err != nil {
		return fmt.Errorf("git clone failed: %w: %s", err, strings.TrimSpace(stderr.String()))
	}
	return nil
}

func (g GitRunner) StatusClean() error {
	out, err := g.run("status", "--porcelain")
	if err != nil {
		return err
	}
	if strings.TrimSpace(out) != "" {
		return fmt.Errorf("dirty worktree: commit or stash changes before bootstrapper commit/push")
	}
	return nil
}

func (g GitRunner) Commit(files []GeneratedFile, message string) error {
	args := []string{"add"}
	for _, f := range files {
		args = append(args, f.Path)
	}
	if _, err := g.run(args...); err != nil {
		return err
	}
	_, err := g.run("commit", "-m", message)
	return err
}

func (g GitRunner) Push(remote, branch string) error {
	if remote == "" || branch == "" {
		return fmt.Errorf("remote and branch are required for selected-target push")
	}
	_, err := g.run("push", remote, fmt.Sprintf("HEAD:%s", branch))
	return err
}

func (g GitRunner) run(args ...string) (string, error) {
	cmd := exec.Command("git", args...)
	cmd.Dir = g.Dir
	var stdout, stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr
	if err := cmd.Run(); err != nil {
		return stdout.String(), fmt.Errorf("git %s failed: %w: %s", strings.Join(args, " "), err, strings.TrimSpace(stderr.String()))
	}
	return stdout.String(), nil
}
