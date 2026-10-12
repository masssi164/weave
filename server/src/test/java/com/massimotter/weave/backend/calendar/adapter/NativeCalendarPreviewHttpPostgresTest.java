package com.massimotter.weave.backend.calendar.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarEvent;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarScope;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarWrite;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventVersion;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.TemporalValue;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.WriteIntent;
import com.massimotter.weave.backend.calendar.port.CalendarProviderPort;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationDecision;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationPort;
import com.massimotter.weave.backend.controller.CalendarUserController;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import com.massimotter.weave.backend.providerbinding.adapter.ProviderBindingJpaTestFactory;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import com.massimotter.weave.backend.service.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import com.massimotter.weave.backend.service.calendar.CalendarOccurrenceEngine;
import com.massimotter.weave.backend.service.calendar.CalendarUserApiService;
import com.massimotter.weave.backend.service.calendar.Ical4jRecurrenceEngine;
import com.massimotter.weave.backend.support.HumanJwtTestSupport;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.MethodParameter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import tools.jackson.databind.json.JsonMapper;

/** Native provider and real PostgreSQL readback through the production Calendar HTTP controller. */
@EnabledIfSystemProperty(named = "weave.test.postgres", matches = "true")
class NativeCalendarPreviewHttpPostgresTest {
    private static final String TENANT = "tenant-default";
    private static final CalendarScope WORKSPACE = CalendarScope.workspace();
    private static final Instant FROM = Instant.parse("2026-03-28T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-03-29T00:00:00Z");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void providerOnlyEventBecomesStableOnlyAfterAuthorizedHttpMaterialization() throws Exception {
        DataSource database = JpaTestDatabase.entityFirstDataSource("calendar_preview_http_postgres");
        JdbcTemplate sql = new JdbcTemplate(database);
        ProviderBindingRepository bindings = ProviderBindingJpaTestFactory.create(database);
        bindings.activate(TENANT, "calendar", 0, "weave-native", CalendarUserApiService.CONFIGURATION_REF, Instant.now());
        CalendarProviderPort provider = adapter(database);
        ContextAuthorizationPort rights = mock(ContextAuthorizationPort.class);
        AtomicBoolean revoked = new AtomicBoolean();
        when(rights.check(any())).thenAnswer(call -> revoked.get()
                ? ContextAuthorizationDecision.deny("revoked")
                : ContextAuthorizationDecision.allow("current-member"));
        WorkspaceCapabilityService capabilities = mock(WorkspaceCapabilityService.class);
        AuditEventPublisher audit = mock(AuditEventPublisher.class);
        MockMvc http = http(service(bindings, provider, rights, capabilities, audit));

        String calendar = JSON.readTree(http.perform(get("/api/calendar/calendars")
                        .header("X-Fixture-Actor", "member"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .path("calendars").get(0).path("id").asString();
        CalendarId providerCalendar = new CalendarId("calendar-" + digest(TENANT).substring(0, 40));
        EventId providerEvent = new EventId("provider-only-planning");
        CalendarEvent source = new CalendarEvent(providerCalendar, providerEvent, WORKSPACE,
                "Provider-only planning", "Preserved content",
                TemporalValue.floating(LocalDateTime.parse("2026-03-28T09:00:00")),
                TemporalValue.floating(LocalDateTime.parse("2026-03-28T10:00:00")),
                null, List.of(), null, List.of(), EventVersion.unknown(), Instant.now());
        CalendarEvent persisted = provider.write(new CalendarWrite(source, WriteIntent.CREATE, EventVersion.unknown()));
        assertThat(provider.read(providerCalendar, WORKSPACE, providerEvent).version()).isEqualTo(persisted.version());
        assertThat(mappingCount(sql)).isZero();

        String events = "/api/calendar/calendars/" + calendar + "/events";
        var agenda = JSON.readTree(http.perform(get(events).header("X-Fixture-Actor", "member")
                        .param("from", FROM.toString()).param("to", TO.toString())
                        .param("evaluationTimeZone", "UTC"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(agenda.path("events").size()).isZero();
        assertThat(agenda.path("previews").size()).isEqualTo(1);
        String handle = agenda.path("previews").get(0).path("handle").asString();
        assertThat(handle).startsWith("pv_").doesNotContain(providerEvent.value());
        assertThat(agenda.path("previews").get(0).has("id")).isFalse();
        assertThat(mappingCount(sql)).isZero();

        String preview = events + "/previews/" + handle;
        http.perform(get(preview).header("X-Fixture-Actor", "member"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.title").value("Provider-only planning"))
                .andExpect(jsonPath("$.id").doesNotExist());
        http.perform(post(preview + "/materialization").header("X-Fixture-Actor", "other"))
                .andExpect(status().isNotFound());
        revoked.set(true);
        http.perform(post(preview + "/materialization").header("X-Fixture-Actor", "member"))
                .andExpect(status().isForbidden());
        assertThat(mappingCount(sql)).isZero();
        revoked.set(false);

        String stable = JSON.readTree(http.perform(post(preview + "/materialization")
                        .header("X-Fixture-Actor", "member"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asString();
        assertThat(stable).matches("event:[0-9a-f]{64}");
        http.perform(post(preview + "/materialization").header("X-Fixture-Actor", "member"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(stable));
        assertThat(mappingCount(sql)).isEqualTo(1);
        assertThat(provider.read(providerCalendar, WORKSPACE, providerEvent).title()).isEqualTo("Provider-only planning");

        // A new adapter, binding repository and application service use the same PostgreSQL state.
        MockMvc restarted = http(service(ProviderBindingJpaTestFactory.create(database), adapter(database),
                rights, capabilities, audit));
        restarted.perform(get(events + "/" + stable).header("X-Fixture-Actor", "member"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(stable))
                .andExpect(jsonPath("$.content.title").value("Provider-only planning"));
        var afterRestart = JSON.readTree(restarted.perform(get(events).header("X-Fixture-Actor", "member")
                        .param("from", FROM.toString()).param("to", TO.toString())
                        .param("evaluationTimeZone", "UTC"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(afterRestart.path("events").get(0).path("id").asString()).isEqualTo(stable);
        assertThat(afterRestart.path("previews").size()).isZero();
        assertThat(mappingCount(sql)).isEqualTo(1);
    }

    private static int mappingCount(JdbcTemplate sql) {
        return sql.queryForObject("select count(*) from weave_provider_object_mappings where domain_key = 'calendar'", Integer.class);
    }

    private static CalendarUserApiService service(ProviderBindingRepository bindings, CalendarProviderPort provider,
            ContextAuthorizationPort rights, WorkspaceCapabilityService capabilities, AuditEventPublisher audit) {
        ContextAuthorizationProperties context = new ContextAuthorizationProperties(null, null, null, null, null,
                null, null, null);
        return new CalendarUserApiService(HumanJwtTestSupport.organizationAdmission(),
                OrganizationIdentityContextResolver.configured(context), context, rights, capabilities,
                bindings, List.of(provider), audit);
    }

    private static MockMvc http(CalendarUserApiService service) {
        ApiErrorResponseWriter errors = new ApiErrorResponseWriter(JSON);
        return MockMvcBuilders.standaloneSetup(new CalendarUserController(service, errors))
                .setControllerAdvice(new ApiExceptionHandler(errors))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType() == Jwt.class;
                    }
                    @Override public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                            NativeWebRequest request, WebDataBinderFactory binder) {
                        String actor = request.getHeader("X-Fixture-Actor");
                        return Jwt.withTokenValue("test-" + actor).header("alg", "none").subject(actor)
                                .issuer("https://auth.weave.test/realms/weave")
                                .claim("organization", HumanJwtTestSupport.organizationWithRole("member")).build();
                    }
                }).build();
    }

    private static CalendarProviderPort adapter(DataSource database) {
        NativeCalendarRelationalStore store = new NativeCalendarRelationalStore(new JdbcTemplate(database));
        NativeCalendarProviderAdapter target = new NativeCalendarProviderAdapter(
                JpaTestDatabase.repository(database, CalendarCollectionJpaRepository.class),
                JpaTestDatabase.repository(database, CalendarEventJpaRepository.class),
                JpaTestDatabase.repository(database, CalendarChangeJpaRepository.class),
                JpaTestDatabase.repository(database, CalendarSnapshotChangeRepository.class),
                store, new CalendarOccurrenceEngine(new Ical4jRecurrenceEngine()), ZoneOffset.UTC, Clock.systemUTC());
        return JpaTestDatabase.transactional(database, target);
    }

    private static String digest(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
