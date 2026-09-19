package com.meridian.backend.mysql;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The Flyway migrations run against a real MySQL server. H2 (used by the other
// tests) is not MySQL, so this is what proves the SQL and the upgrade path
// really work there. See MySqlTestDatabase for how to run it.
@EnabledIfEnvironmentVariable(named = MySqlTestDatabase.URL_ENV, matches = ".+")
class MySqlMigrationTest {

    private static final String OLD_ORDER_STATUS = "alter table orders modify column status "
            + "enum('CANCELLED','FILLED','PENDING') not null";

    @BeforeAll
    static void onlyAgainstAThrowawayDatabase() {
        MySqlTestDatabase.assertSafeToWipe();
    }

    @BeforeEach
    void startFromAnEmptyDatabase() {
        MySqlTestDatabase.wipe();
    }

    private static int migrationFileCount() throws Exception {
        return new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*__*.sql").length;
    }

    private static int appliedMigrations(JdbcTemplate jdbc) {
        return jdbc.queryForObject("select count(*) from flyway_schema_history where success = 1 and type = 'SQL'",
                Integer.class);
    }

    @Test
    void anEmptyDatabaseIsBuiltByTheMigrationsAndPassesHibernateValidation() throws Exception {
        // Starting the app is the test: Flyway migrates, then Hibernate
        // (ddl-auto=validate) refuses to start if an entity doesn't match.
        try (ConfigurableApplicationContext app = MySqlTestDatabase.boot()) {
            JdbcTemplate jdbc = app.getBean(JdbcTemplate.class);
            assertEquals(migrationFileCount(), appliedMigrations(jdbc));
            assertEquals(13, jdbc.queryForObject("select count(*) from information_schema.tables "
                    + "where table_schema = database() and table_name <> 'flyway_schema_history'", Integer.class));
        }
    }

    @Test
    void theMigrationsBuildTheSameSchemaAsHibernateDoes() {
        // What ddl-auto=create produces is what every database created before
        // Flyway looks like, so the baseline must be identical to it.
        try (ConfigurableApplicationContext app = MySqlTestDatabase.boot(
                "--spring.flyway.enabled=false", "--spring.jpa.hibernate.ddl-auto=create")) {
            // schema is created as the context starts
        }
        Set<String> hibernate = MySqlTestDatabase.describeSchema(MySqlTestDatabase.jdbc());

        MySqlTestDatabase.wipe();
        Flyway.configure().dataSource(MySqlTestDatabase.dataSource()).load().migrate();
        Set<String> migrations = MySqlTestDatabase.describeSchema(MySqlTestDatabase.jdbc());

        Set<String> onlyHibernate = new TreeSet<>(hibernate);
        onlyHibernate.removeAll(migrations);
        Set<String> onlyMigrations = new TreeSet<>(migrations);
        onlyMigrations.removeAll(hibernate);
        assertTrue(onlyHibernate.isEmpty() && onlyMigrations.isEmpty(),
                "Schemas differ.\nOnly Hibernate builds:\n" + String.join("\n", onlyHibernate)
                        + "\nOnly the migrations build:\n" + String.join("\n", onlyMigrations));
    }

    @Test
    void aDatabaseCreatedBeforeFlywayIsBaselinedUpgradedAndKeepsItsData() throws Exception {
        // Recreate an old database: the V1 tables, no Flyway history, and the
        // orders.status column as it was before REJECTED existed.
        Flyway.configure().dataSource(MySqlTestDatabase.dataSource()).target("1").load().migrate();
        JdbcTemplate jdbc = MySqlTestDatabase.jdbc();
        jdbc.execute("drop table flyway_schema_history");
        jdbc.execute(OLD_ORDER_STATUS);
        jdbc.update("insert into users (email, password_hash, created_at) values ('old@example.com', 'x', now(6))");

        try (ConfigurableApplicationContext app = MySqlTestDatabase.boot()) {
            JdbcTemplate live = app.getBean(JdbcTemplate.class);

            assertEquals(1, live.queryForObject(
                    "select count(*) from flyway_schema_history where type = 'BASELINE' and version = '1'", Integer.class));
            assertEquals(migrationFileCount() - 1, appliedMigrations(live)); // everything after V1
            assertTrue(live.queryForObject("select column_type from information_schema.columns "
                    + "where table_schema = database() and table_name = 'orders' and column_name = 'status'",
                    String.class).contains("'REJECTED'"));
            assertEquals(1, live.queryForObject("select count(*) from users where email = 'old@example.com'", Integer.class));
        }

        // Starting again changes nothing.
        try (ConfigurableApplicationContext app = MySqlTestDatabase.boot()) {
            assertEquals(migrationFileCount() - 1, appliedMigrations(app.getBean(JdbcTemplate.class)));
        }
    }
}
