package com.massimotter.weave.backend.service;

import javax.sql.DataSource;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.actuate.jdbc.DataSourceHealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Support-safe persistence readiness boundary backed by Spring Boot Actuator.
 */
@Component
public final class PersistenceHealthProbe {
    private final DataSourceHealthIndicator indicator;

    public PersistenceHealthProbe(DataSource dataSource) {
        this.indicator = new DataSourceHealthIndicator(dataSource, "select 1");
    }

    public boolean ready() {
        return Status.UP.equals(indicator.health().getStatus());
    }
}
