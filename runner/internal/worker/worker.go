package worker

import (
	"context"
	"errors"
	"fmt"
	"math"
	"sync"
	"time"
)

var (
	// ErrNoWork is returned by Client.Claim when the bounded long poll completed without a task.
	ErrNoWork = errors.New("no runnable task")
	// ErrLeaseLost means another attempt owns the task or the Engine revoked the current lease.
	ErrLeaseLost = errors.New("task lease lost")
)

// Lease contains only Engine-issued execution coordinates. It never contains a local executable,
// secret reference or internal endpoint.
type Lease struct {
	TaskID            string
	LeaseID           string
	FencingEpoch      int64
	CapabilityID      string
	CapabilityVersion string
	Attempt           int
	IdempotencyKey    string
	LeaseExpiresAt    time.Time
	HardDeadline      time.Time
	Input             []byte
	TraceParent       string
}

func (lease Lease) Validate(now time.Time) error {
	if lease.TaskID == "" || lease.LeaseID == "" || lease.CapabilityID == "" || lease.CapabilityVersion == "" {
		return errors.New("lease identity is incomplete")
	}
	if lease.FencingEpoch < 1 || lease.Attempt < 1 || lease.IdempotencyKey == "" {
		return errors.New("lease fencing coordinates are invalid")
	}
	if !lease.LeaseExpiresAt.After(now) {
		return errors.New("lease is already expired")
	}
	if !lease.HardDeadline.After(now) || lease.HardDeadline.Before(lease.LeaseExpiresAt) {
		return errors.New("lease deadline is invalid")
	}
	return nil
}

// Directive is returned by a fenced heartbeat. An Engine may extend the lease or request local
// cancellation; a heartbeat can never change the task identity or fencing epoch.
type Directive struct {
	LeaseExpiresAt time.Time
	Cancel         bool
}

// Result is the bounded structured result produced by one local handler. Artifact bytes are sent
// through the separate artifact protocol and are referenced here by their immutable manifests.
type Result struct {
	Payload   []byte
	Artifacts []Artifact
}

type Artifact struct {
	Path      string
	Kind      string
	MediaType string
	Size      int64
	Digest    string
}

type Failure struct {
	Code      string
	Message   string
	Retryable bool
}

// Client is the outbound-only Engine control boundary. Implementations must attach the current
// Runner identity and the lease ID/fencing epoch to every task mutation.
type Client interface {
	Claim(context.Context, time.Duration) (*Lease, error)
	Heartbeat(context.Context, Lease) (Directive, error)
	Complete(context.Context, Lease, Result) error
	Fail(context.Context, Lease, Failure) error
}

// Executor resolves the public capability coordinates to one local, administrator-provided
// handler. Implementations are responsible for terminating the local process when ctx is done.
type Executor interface {
	Execute(context.Context, Lease) (Result, error)
}

type Config struct {
	ClaimWait         time.Duration
	HeartbeatInterval time.Duration
	CompletionTimeout time.Duration
	RetryInitial      time.Duration
	RetryMaximum      time.Duration
	MaximumRetries    int
}

func (config Config) validate() error {
	if config.ClaimWait < time.Second || config.ClaimWait > 30*time.Second {
		return errors.New("claim wait must be between one and thirty seconds")
	}
	if config.HeartbeatInterval < time.Second || config.HeartbeatInterval >= config.ClaimWait {
		return errors.New("heartbeat interval must be positive and shorter than claim wait")
	}
	if config.CompletionTimeout < time.Second || config.CompletionTimeout > time.Minute {
		return errors.New("completion timeout must be between one second and one minute")
	}
	if config.RetryInitial <= 0 || config.RetryMaximum < config.RetryInitial || config.RetryMaximum > time.Minute {
		return errors.New("retry bounds are invalid")
	}
	if config.MaximumRetries < 0 || config.MaximumRetries > 10 {
		return errors.New("maximum retries must be between zero and ten")
	}
	return nil
}

type Runner struct {
	client   Client
	executor Executor
	config   Config
	now      func() time.Time
}

func New(client Client, executor Executor, config Config) (*Runner, error) {
	if client == nil || executor == nil {
		return nil, errors.New("worker client and executor are required")
	}
	if err := config.validate(); err != nil {
		return nil, err
	}
	return &Runner{client: client, executor: executor, config: config, now: time.Now}, nil
}

// Run continuously performs bounded long polls. All durable scheduling state remains in the
// Engine; a Runner restart therefore loses no task authority.
func (runner *Runner) Run(ctx context.Context) error {
	for {
		if err := ctx.Err(); err != nil {
			return err
		}
		lease, err := runner.client.Claim(ctx, runner.config.ClaimWait)
		switch {
		case err == nil:
		case errors.Is(err, ErrNoWork):
			continue
		case errors.Is(err, context.Canceled), errors.Is(err, context.DeadlineExceeded):
			return err
		default:
			if err := sleep(ctx, runner.config.RetryInitial); err != nil {
				return err
			}
			continue
		}
		if lease == nil {
			return errors.New("Engine returned an empty lease without ErrNoWork")
		}
		if err := runner.execute(ctx, *lease); err != nil && !errors.Is(err, ErrLeaseLost) {
			// Task-specific failures are already reported. Only local invariant failures stop the
			// process so an operator can repair the Runner instead of silently looping.
			var invariant *InvariantError
			if errors.As(err, &invariant) {
				return err
			}
		}
	}
}

type executionOutcome struct {
	result Result
	err    error
}

func (runner *Runner) execute(parent context.Context, lease Lease) error {
	now := runner.now()
	if err := lease.Validate(now); err != nil {
		return &InvariantError{Message: "invalid Engine lease: " + err.Error()}
	}
	ctx, cancel := context.WithDeadline(parent, lease.HardDeadline)
	defer cancel()

	outcomes := make(chan executionOutcome, 1)
	go func() {
		result, err := runner.executor.Execute(ctx, lease)
		outcomes <- executionOutcome{result: result, err: err}
	}()

	heartbeat := time.NewTicker(runner.config.HeartbeatInterval)
	defer heartbeat.Stop()

	current := lease
	for {
		select {
		case outcome := <-outcomes:
			if outcome.err != nil {
				failure := Failure{Code: "HANDLER_FAILED", Message: supportSafe(outcome.err), Retryable: true}
				return runner.reportFailure(parent, current, failure)
			}
			return runner.complete(parent, current, outcome.result)
		case <-heartbeat.C:
			directive, err := runner.client.Heartbeat(ctx, current)
			if errors.Is(err, ErrLeaseLost) {
				cancel()
				return ErrLeaseLost
			}
			if err != nil {
				// Network loss does not immediately invalidate a still-current lease. Stop before
				// expiry, however, because completing after expiry would violate fencing.
				if !current.LeaseExpiresAt.After(runner.now().Add(runner.config.HeartbeatInterval)) {
					cancel()
					return ErrLeaseLost
				}
				continue
			}
			if directive.Cancel {
				cancel()
				return ErrLeaseLost
			}
			if !directive.LeaseExpiresAt.After(runner.now()) || directive.LeaseExpiresAt.After(current.HardDeadline) {
				cancel()
				return &InvariantError{Message: "Engine returned an invalid lease extension"}
			}
			current.LeaseExpiresAt = directive.LeaseExpiresAt
		case <-ctx.Done():
			failure := Failure{Code: "TASK_DEADLINE_EXCEEDED", Message: "The local handler exceeded its task deadline.", Retryable: false}
			return runner.reportFailure(parent, current, failure)
		}
	}
}

func (runner *Runner) complete(parent context.Context, lease Lease, result Result) error {
	return runner.retryMutation(parent, lease, func(ctx context.Context) error {
		return runner.client.Complete(ctx, lease, result)
	})
}

func (runner *Runner) reportFailure(parent context.Context, lease Lease, failure Failure) error {
	return runner.retryMutation(parent, lease, func(ctx context.Context) error {
		return runner.client.Fail(ctx, lease, failure)
	})
}

func (runner *Runner) retryMutation(parent context.Context, lease Lease, mutation func(context.Context) error) error {
	backoff := runner.config.RetryInitial
	var last error
	for attempt := 0; attempt <= runner.config.MaximumRetries; attempt++ {
		if !lease.LeaseExpiresAt.After(runner.now()) {
			return ErrLeaseLost
		}
		ctx, cancel := context.WithTimeout(parent, runner.config.CompletionTimeout)
		err := mutation(ctx)
		cancel()
		if err == nil {
			return nil
		}
		if errors.Is(err, ErrLeaseLost) {
			return ErrLeaseLost
		}
		last = err
		if attempt == runner.config.MaximumRetries {
			break
		}
		remaining := time.Until(lease.LeaseExpiresAt)
		if remaining <= backoff {
			return ErrLeaseLost
		}
		if err := sleep(parent, backoff); err != nil {
			return err
		}
		backoff = time.Duration(math.Min(float64(runner.config.RetryMaximum), float64(backoff*2)))
	}
	return fmt.Errorf("report task outcome after retries: %w", last)
}

func sleep(ctx context.Context, duration time.Duration) error {
	timer := time.NewTimer(duration)
	defer timer.Stop()
	select {
	case <-timer.C:
		return nil
	case <-ctx.Done():
		return ctx.Err()
	}
}

func supportSafe(err error) string {
	if err == nil {
		return ""
	}
	message := err.Error()
	if len(message) > 512 {
		message = message[:512]
	}
	return message
}

// InvariantError means local or Engine contract state is structurally unsafe. Unlike one failed
// task, this stops the Runner process and requires operator attention.
type InvariantError struct {
	Message string
}

func (err *InvariantError) Error() string { return err.Message }

// MutexExecutor serializes a non-concurrent local handler without changing Engine scheduling.
// It is useful for company capabilities that access a singleton local SDK or workspace.
type MutexExecutor struct {
	delegate Executor
	mutex    sync.Mutex
}

func Serial(delegate Executor) Executor {
	return &MutexExecutor{delegate: delegate}
}

func (executor *MutexExecutor) Execute(ctx context.Context, lease Lease) (Result, error) {
	executor.mutex.Lock()
	defer executor.mutex.Unlock()
	return executor.delegate.Execute(ctx, lease)
}
