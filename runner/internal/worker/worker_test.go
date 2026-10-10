package worker

import (
	"context"
	"errors"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

func TestExecutionCompletesWithTheOriginalFencedLease(t *testing.T) {
	now := time.Now()
	client := &fakeClient{completeFailures: 1}
	executor := executorFunc(func(context.Context, Lease) (Result, error) {
		return Result{Payload: []byte(`{"status":"ok"}`)}, nil
	})
	runner := testRunner(client, executor)
	lease := testLease(now)

	if err := runner.execute(context.Background(), lease); err != nil {
		t.Fatalf("execute: %v", err)
	}
	if got := client.completeCalls.Load(); got != 2 {
		t.Fatalf("expected idempotent completion retry, got %d calls", got)
	}
	if client.completed.LeaseID != lease.LeaseID || client.completed.FencingEpoch != lease.FencingEpoch {
		t.Fatal("completion did not retain the original lease fencing coordinates")
	}
}

func TestLeaseLossCancelsTheLocalExecutorAndDoesNotComplete(t *testing.T) {
	now := time.Now()
	client := &fakeClient{heartbeatError: ErrLeaseLost}
	cancelled := make(chan struct{})
	executor := executorFunc(func(ctx context.Context, _ Lease) (Result, error) {
		<-ctx.Done()
		close(cancelled)
		return Result{}, ctx.Err()
	})
	runner := testRunner(client, executor)
	lease := testLease(now)

	if err := runner.execute(context.Background(), lease); !errors.Is(err, ErrLeaseLost) {
		t.Fatalf("expected lease loss, got %v", err)
	}
	select {
	case <-cancelled:
	case <-time.After(time.Second):
		t.Fatal("executor did not observe cancellation after lease loss")
	}
	if got := client.completeCalls.Load(); got != 0 {
		t.Fatalf("stale execution completed %d times", got)
	}
}

func TestInvalidLeaseStopsTheRunnerAsAnInvariantFailure(t *testing.T) {
	runner := testRunner(&fakeClient{}, executorFunc(func(context.Context, Lease) (Result, error) {
		return Result{}, nil
	}))
	lease := testLease(time.Now())
	lease.FencingEpoch = 0

	var invariant *InvariantError
	if err := runner.execute(context.Background(), lease); !errors.As(err, &invariant) {
		t.Fatalf("expected invariant error, got %v", err)
	}
}

func testRunner(client *fakeClient, executor Executor) *Runner {
	return &Runner{
		client:   client,
		executor: executor,
		config: Config{
			ClaimWait:         50 * time.Millisecond,
			HeartbeatInterval: 5 * time.Millisecond,
			CompletionTimeout: 50 * time.Millisecond,
			RetryInitial:      time.Millisecond,
			RetryMaximum:      5 * time.Millisecond,
			MaximumRetries:    2,
		},
		now: time.Now,
	}
}

func testLease(now time.Time) Lease {
	return Lease{
		TaskID:            "task_01",
		LeaseID:           "lease_01",
		FencingEpoch:      3,
		CapabilityID:      "internal.cmdb.lookup",
		CapabilityVersion: "1.0.0",
		Attempt:           1,
		IdempotencyKey:    "task_01-attempt-1",
		LeaseExpiresAt:    now.Add(5 * time.Second),
		HardDeadline:      now.Add(10 * time.Second),
		Input:             []byte(`{"id":"nextcloud"}`),
	}
}

type executorFunc func(context.Context, Lease) (Result, error)

func (function executorFunc) Execute(ctx context.Context, lease Lease) (Result, error) {
	return function(ctx, lease)
}

type fakeClient struct {
	mutex            sync.Mutex
	heartbeatError   error
	completeFailures int
	completeCalls    atomic.Int32
	completed        Lease
}

func (client *fakeClient) Claim(context.Context, time.Duration) (*Lease, error) {
	return nil, ErrNoWork
}

func (client *fakeClient) Heartbeat(context.Context, Lease) (Directive, error) {
	if client.heartbeatError != nil {
		return Directive{}, client.heartbeatError
	}
	return Directive{LeaseExpiresAt: time.Now().Add(time.Second)}, nil
}

func (client *fakeClient) Complete(_ context.Context, lease Lease, _ Result) error {
	client.completeCalls.Add(1)
	client.mutex.Lock()
	defer client.mutex.Unlock()
	client.completed = lease
	if client.completeFailures > 0 {
		client.completeFailures--
		return errors.New("temporary network failure")
	}
	return nil
}

func (client *fakeClient) Fail(context.Context, Lease, Failure) error {
	return nil
}
