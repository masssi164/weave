package main

import "testing"

func TestHelpDoesNotEnterAppPrompts(t *testing.T) {
	if err := run([]string{"app", "--help"}); err != nil {
		t.Fatalf("app --help failed: %v", err)
	}
}

func TestCommandHelpIsAccepted(t *testing.T) {
	for _, command := range []string{"detect", "validate", "plan", "init", "commit", "push"} {
		command := command
		t.Run(command, func(t *testing.T) {
			if err := run([]string{command, "--help"}); err != nil {
				t.Fatalf("%s --help failed: %v", command, err)
			}
		})
	}
}
