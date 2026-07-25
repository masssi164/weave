package com.massimotter.weave.shared.persistence;

/** Single artifact-owned persistence model and Flyway target for both Boot applications. */
public final class SharedPersistenceModel {
    public static final String FLYWAY_LOCATION = "classpath:db/migration";
    public static final String VERSION = "019";

    private SharedPersistenceModel() {
    }
}
