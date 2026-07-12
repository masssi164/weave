package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.model.PlatformStatusResponse;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.StatementCallback;
import org.springframework.stereotype.Service;

@Service
public class LocalDependencyReadinessService {

    static final int PERSISTENCE_QUERY_TIMEOUT_SECONDS = 2;

    private final JdbcTemplate jdbcTemplate;

    public LocalDependencyReadinessService(
            Optional<JdbcTemplate> jdbcTemplate,
            Optional<DataSource> dataSource) {
        this.jdbcTemplate = jdbcTemplate.orElseGet(() -> dataSource.map(JdbcTemplate::new).orElse(null));
    }

    public List<PlatformStatusResponse.DiagnosticCheck> checks() {
        if (jdbcTemplate == null) {
            return List.of();
        }
        return List.of(persistenceCheck());
    }

    private PlatformStatusResponse.DiagnosticCheck persistenceCheck() {
        try {
            Integer result = jdbcTemplate.execute((StatementCallback<Integer>) statement -> {
                statement.setQueryTimeout(PERSISTENCE_QUERY_TIMEOUT_SECONDS);
                try (ResultSet resultSet = statement.executeQuery("SELECT 1")) {
                    return resultSet.next() ? resultSet.getInt(1) : null;
                }
            });
            if (Integer.valueOf(1).equals(result)) {
                return new PlatformStatusResponse.DiagnosticCheck(
                        "persistence",
                        "Persistence",
                        "up",
                        "ready",
                        "Configured persistence is reachable.",
                        null);
            }
        } catch (RuntimeException ignored) {
            // Readiness responses must never disclose JDBC URLs, credentials, or driver details.
        }
        return new PlatformStatusResponse.DiagnosticCheck(
                "persistence",
                "Persistence",
                "blocked",
                "blocked",
                "Configured persistence is unavailable.",
                "Restore the configured local persistence dependency and retry readiness.");
    }
}
