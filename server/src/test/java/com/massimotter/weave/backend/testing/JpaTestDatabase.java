package com.massimotter.weave.backend.testing;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.MatchAlwaysTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Shared JPA bootstrap for focused H2 and Testcontainers PostgreSQL adapter tests. */
public final class JpaTestDatabase {
  /*
   * Server test forks are intentionally serial. Retain only the small number of
   * contexts a focused test may use concurrently and close older Hibernate
   * factories before they can accumulate across the full suite.
   */
  private static final int MAX_CACHED_CONTEXTS = 2;
  private static final Map<DataSource, Context> CONTEXTS = new IdentityHashMap<>();
  private static final Deque<DataSource> CONTEXT_ORDER = new ArrayDeque<>();

  private JpaTestDatabase() {}

  /**
   * Creates an isolated test database without applying migrations.
   *
   * <p>Normal unit feedback uses H2. The {@code postgresJpaTest} Gradle gate sets
   * {@code weave.test.postgres=true}; the same repository tests then use one PostgreSQL container
   * and an isolated schema per test database.
   */
  public static DriverManagerDataSource dataSource(String semanticName) {
    if (!Boolean.getBoolean("weave.test.postgres")) {
      DriverManagerDataSource dataSource = new DriverManagerDataSource();
      dataSource.setDriverClassName("org.h2.Driver");
      dataSource.setUrl(
          "jdbc:h2:mem:"
              + safeName(semanticName)
              + "-"
              + UUID.randomUUID()
              + ";MODE=PostgreSQL;DATABASE_TO_UPPER=true;DB_CLOSE_DELAY=-1");
      dataSource.setUsername("sa");
      dataSource.setPassword("");
      return dataSource;
    }

    PostgreSQLContainer<?> postgres = PostgresHolder.CONTAINER;
    String schema = safeName(semanticName) + "_" + UUID.randomUUID().toString().replace("-", "");
    try (var connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var statement = connection.createStatement()) {
      statement.execute("create schema \"" + schema + "\"");
    } catch (SQLException failure) {
      throw new IllegalStateException("PostgreSQL test schema could not be created", failure);
    }

    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    String separator = postgres.getJdbcUrl().contains("?") ? "&" : "?";
    dataSource.setUrl(postgres.getJdbcUrl() + separator + "currentSchema=" + schema);
    dataSource.setUsername(postgres.getUsername());
    dataSource.setPassword(postgres.getPassword());
    return dataSource;
  }

  /**
   * Creates an isolated database from the entity model for both H2 feedback and authoritative
   * PostgreSQL repository gates.
   *
   * <p>This is the normal repository-test entrypoint. {@code postgresJpaTest} proves the same
   * code-first model against PostgreSQL without a parallel SQL migration authority.
   */
  public static synchronized DriverManagerDataSource entityFirstDataSource(String semanticName) {
    DriverManagerDataSource dataSource = dataSource(semanticName);
    initializeSchema(dataSource);
    return dataSource;
  }

  /** Creates the complete entity-owned schema on a caller-managed test datasource. */
  public static synchronized void initializeSchema(DataSource dataSource) {
    if (!CONTEXTS.containsKey(dataSource)) {
      register(dataSource, create(dataSource, "create"));
    }
  }

  /** Proves that an existing schema matches the complete entity model. */
  public static void validateSchema(DataSource dataSource) {
    LocalContainerEntityManagerFactoryBean factory = entityManagerFactory(dataSource, "validate");
    factory.destroy();
  }

  public static synchronized PlatformTransactionManager transactionManager(DataSource dataSource) {
    return context(dataSource).transactionManager();
  }

  public static synchronized EntityManager entityManager(DataSource dataSource) {
    return context(dataSource).entityManager();
  }

  public static synchronized Statistics statistics(DataSource dataSource) {
    Statistics statistics =
        context(dataSource).entityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    return statistics;
  }

  public static synchronized <R> R repository(DataSource dataSource, Class<R> repositoryType) {
    if (repositoryType == null) {
      throw new IllegalArgumentException("test repository type is required");
    }
    R repository =
        new JpaRepositoryFactory(context(dataSource).entityManager()).getRepository(repositoryType);
    MatchAlwaysTransactionAttributeSource attributes = new MatchAlwaysTransactionAttributeSource();
    attributes.setTransactionAttribute(new DefaultTransactionAttribute());
    TransactionInterceptor transactions = new TransactionInterceptor();
    transactions.setTransactionManager(context(dataSource).transactionManager());
    transactions.setTransactionAttributeSource(attributes);
    transactions.afterPropertiesSet();
    ProxyFactory proxy = new ProxyFactory(repository);
    proxy.addAdvice(transactions);
    return repositoryType.cast(proxy.getProxy());
  }

  @SuppressWarnings("unchecked")
  public static synchronized <T> T transactional(DataSource dataSource, T target) {
    if (target == null) {
      throw new IllegalArgumentException("test transaction target is required");
    }
    ProxyFactory proxy = new ProxyFactory(target);
    proxy.setProxyTargetClass(true);
    TransactionInterceptor transactions = new TransactionInterceptor();
    transactions.setTransactionManager(context(dataSource).transactionManager());
    transactions.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    transactions.afterPropertiesSet();
    proxy.addAdvice(transactions);
    return (T) proxy.getProxy();
  }

  private static Context context(DataSource dataSource) {
    if (dataSource == null) {
      throw new IllegalArgumentException("test DataSource is required");
    }
    Context existing = CONTEXTS.get(dataSource);
    if (existing != null) {
      return existing;
    }
    Context created = create(dataSource, "none");
    register(dataSource, created);
    return created;
  }

  private static Context create(DataSource dataSource, String schemaAction) {
    LocalContainerEntityManagerFactoryBean factory =
        entityManagerFactory(dataSource, schemaAction);
    EntityManagerFactory entityManagerFactory = factory.getObject();
    if (entityManagerFactory == null) {
      throw new IllegalStateException("test EntityManagerFactory was not created");
    }
    JpaTransactionManager transactions = new JpaTransactionManager(entityManagerFactory);
    EntityManager entityManager =
        SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
    return new Context(entityManager, entityManagerFactory, transactions);
  }

  private static void register(DataSource dataSource, Context context) {
    while (CONTEXTS.size() >= MAX_CACHED_CONTEXTS) {
      DataSource oldest = CONTEXT_ORDER.removeFirst();
      Context removed = CONTEXTS.remove(oldest);
      if (removed != null) {
        removed.entityManagerFactory().close();
      }
    }
    CONTEXTS.put(dataSource, context);
    CONTEXT_ORDER.addLast(dataSource);
  }

  private static LocalContainerEntityManagerFactoryBean entityManagerFactory(
      DataSource dataSource, String schemaAction) {
    LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
    factory.setDataSource(dataSource);
    factory.setPackagesToScan("com.massimotter.weave.backend");
    factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
    factory.setJpaPropertyMap(
        Map.of(
            "hibernate.hbm2ddl.auto", schemaAction,
            "hibernate.show_sql", "false",
            "hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy",
            "hibernate.jdbc.time_zone", "UTC"));
    factory.afterPropertiesSet();
    return factory;
  }

  private static String safeName(String semanticName) {
    String normalized =
        semanticName == null
            ? "weave"
            : semanticName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    return normalized.isBlank() ? "weave" : normalized;
  }

  private static final class PostgresHolder {
    private static final PostgreSQLContainer<?> CONTAINER = start();

    private static PostgreSQLContainer<?> start() {
      PostgreSQLContainer<?> container =
          new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
              .withDatabaseName("weave_contract")
              .withUsername("weave")
              .withPassword("weave-test-only");
      container.start();
      return container;
    }
  }

  private record Context(
      EntityManager entityManager,
      EntityManagerFactory entityManagerFactory,
      PlatformTransactionManager transactionManager) {}
}
