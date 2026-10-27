package com.massimotter.weave.backend.dogfood;

import static com.massimotter.weave.backend.dogfood.DogfoodPreparationProtocol.*;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

/** Fixed one-shot process. It never listens, creates a human principal, or runs ordinary bootstraps. */
public final class DogfoodPreparationCli {
    public static final int MAXIMUM_SECONDS = 180;
    private DogfoodPreparationCli() {}

    public static void main(String[] args) {
        AtomicReference<Request> request = new AtomicReference<>();
        AtomicBoolean publicationClaimed = new AtomicBoolean();
        deadline(MAXIMUM_SECONDS * 1000L, System.out, request, publicationClaimed, () -> Runtime.getRuntime().halt(124));
        int exit = run(args, System.in, System.out, request, publicationClaimed);
        System.exit(exit);
    }

    /** Hard termination never waits on a blocked stdout pipe or an uninterruptible provider call. */
    static Thread deadline(long maximumMillis, PrintStream output, AtomicReference<Request> request,
            AtomicBoolean publicationClaimed, Runnable terminate) {
        Thread watchdog = Thread.ofPlatform().daemon().name("dogfood-operator-deadline").unstarted(() -> {
            long end = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(maximumMillis);
            try {
                Thread.sleep(Math.max(0, maximumMillis - 250));
                if (publicationClaimed.compareAndSet(false, true)) {
                    // Publication is best effort; an uncertain/lost result never authorizes another invitation.
                    Thread.ofPlatform().daemon().name("dogfood-operator-deadline-receipt")
                            .start(() -> emit(output, failure(request.get(), "operation-incomplete")));
                }
                long remaining = end - System.nanoTime();
                if (remaining > 0) java.util.concurrent.TimeUnit.NANOSECONDS.sleep(remaining);
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            terminate.run();
        });
        watchdog.start();
        return watchdog;
    }

    static int run(String[] args, InputStream input, PrintStream output, AtomicReference<Request> parsed) {
        return run(args, input, output, parsed, new AtomicBoolean());
    }

    private static int run(String[] args, InputStream input, PrintStream output, AtomicReference<Request> parsed,
            AtomicBoolean publicationClaimed) {
        Request request = null;
        Envelope result;
        try {
            require(args != null && args.length == 0, "invalid-request");
            request = read(input);
            parsed.set(request);
            SpringApplication app = application(request);
            try (var context = app.run()) {
                result = context.getBean(DogfoodPreparationService.class).execute(request);
            }
        } catch (Blocked blocked) { result = failure(request, blocked.boundary); }
        catch (Exception failure) { result = failure(request, request == null ? "invalid-request" : "schema-authority-unavailable"); }
        if (!publicationClaimed.compareAndSet(false, true)) return 2;
        emit(output, result);
        return "passed".equals(result.receipt().status()) ? 0 : 2;
    }

    static SpringApplication application(Request request) {
        SpringApplication app = new SpringApplication(DogfoodPreparationConfiguration.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setAdditionalProfiles("dogfood-operator-cli");
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        app.setRegisterShutdownHook(false);
        app.setDefaultProperties(Map.of("spring.main.web-application-type", "none", "spring.main.log-startup-info", "false",
                "spring.datasource.hikari.connection-timeout", "10000",
                "spring.jpa.properties.jakarta.persistence.query.timeout", "15000",
                "spring.jpa.properties.jakarta.persistence.lock.timeout", "15000"));
        app.addInitializers(new DogfoodPreparationPreflight(request));
        return app;
    }

    static Envelope failure(Request r, String boundary) {
        return new Envelope("weave.dogfood-operator-preparation-envelope/v1", new Receipt(
                "weave.dogfood-operator-preparation-receipt/v1", "dogfood-operator", "dogfood-operator", r == null ? null : actor(r),
                r == null ? null : r.operation(), "blocked", "incomplete", boundary, "BLOCKED", r == null ? null : r.nonce(),
                r == null ? null : r.plan().planId(), r == null ? null : "dogfood", r == null ? null : r.candidateCommit(),
                r == null ? null : r.sourceTree(), r == null ? null : r.specificationCommit(), r == null ? null : r.plan().organizationOrigin(),
                r == null ? null : r.apiBaseUrl(), r == null ? null : r.plan().issuerUrl(), null, r == null ? null : "workspace-default",
                null, null, r == null ? List.of() : r.plan().members().stream().map(m -> new MemberReceipt(
                        m.slot(), null, null, null, null, null, "UNAVAILABLE", "NOT_REQUESTED")).toList(), true, false), new PrivateData(null));
    }
    private static synchronized void emit(PrintStream output, Envelope result) {
        output.println(MARKER + JSON.writeValueAsString(result));
        output.flush();
    }
}
