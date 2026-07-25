package com.massimotter.weave.backend.testing;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.MatchAlwaysTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.PlatformTransactionManager;

/** Shared JPA bootstrap for focused H2 and Testcontainers PostgreSQL adapter tests. */
public final class JpaTestDatabase {
    private static final int MAX_CACHED_CONTEXTS = 8;
    private static final Map<DataSource, Context> CONTEXTS = new IdentityHashMap<>();
    private static final Deque<DataSource> CONTEXT_ORDER = new ArrayDeque<>();

    private JpaTestDatabase() {
    }

    public static synchronized PlatformTransactionManager transactionManager(DataSource dataSource) {
        return context(dataSource).transactionManager();
    }

    public static synchronized EntityManager entityManager(DataSource dataSource) {
        return context(dataSource).entityManager();
    }

    public static synchronized Statistics statistics(DataSource dataSource) {
        Statistics statistics = context(dataSource)
                .entityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.setStatisticsEnabled(true);
        return statistics;
    }

    public static synchronized <R> R repository(DataSource dataSource, Class<R> repositoryType) {
        if (repositoryType == null) {
            throw new IllegalArgumentException("test repository type is required");
        }
        R repository = new JpaRepositoryFactory(context(dataSource).entityManager())
                .getRepository(repositoryType);
        MatchAlwaysTransactionAttributeSource attributes =
                new MatchAlwaysTransactionAttributeSource();
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
        evictOldestContextWhenFull();
        Context created = create(dataSource);
        CONTEXTS.put(dataSource, created);
        CONTEXT_ORDER.addLast(dataSource);
        return created;
    }

    private static void evictOldestContextWhenFull() {
        while (CONTEXTS.size() >= MAX_CACHED_CONTEXTS) {
            DataSource oldest = CONTEXT_ORDER.removeFirst();
            Context removed = CONTEXTS.remove(oldest);
            if (removed != null) {
                removed.entityManagerFactory().close();
            }
        }
    }

    private static Context create(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("com.massimotter.weave.backend");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of(
                "hibernate.hbm2ddl.auto", "none",
                "hibernate.show_sql", "false",
                "hibernate.jdbc.time_zone", "UTC"));
        factory.afterPropertiesSet();
        EntityManagerFactory entityManagerFactory = factory.getObject();
        if (entityManagerFactory == null) {
            throw new IllegalStateException("test EntityManagerFactory was not created");
        }
        JpaTransactionManager transactions = new JpaTransactionManager(entityManagerFactory);
        EntityManager entityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
        return new Context(entityManager, entityManagerFactory, transactions);
    }

    private record Context(
            EntityManager entityManager,
            EntityManagerFactory entityManagerFactory,
            PlatformTransactionManager transactionManager) {
    }
}
