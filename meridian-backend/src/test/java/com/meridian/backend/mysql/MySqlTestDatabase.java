package com.meridian.backend.mysql;

import com.meridian.backend.BackendApplication;
import org.flywaydb.core.Flyway;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

// Shared setup for the tests that run against a real MySQL server (the normal
// tests use H2). They only run when MERIDIAN_TEST_MYSQL_URL is set, e.g.
//   MERIDIAN_TEST_MYSQL_URL='jdbc:mysql://localhost:3306/meridian_test?useSSL=false&allowPublicKeyRetrieval=true'
//   MERIDIAN_TEST_MYSQL_USER=root  MERIDIAN_TEST_MYSQL_PASSWORD=...
//   mvn test -Dtest='MySql*'
// These tests DROP EVERYTHING in that database, so it must be named *_test.
final class MySqlTestDatabase {

    static final String URL_ENV = "MERIDIAN_TEST_MYSQL_URL";

    private MySqlTestDatabase() {
    }

    static String url() {
        return System.getenv(URL_ENV);
    }

    static String user() {
        return System.getenv().getOrDefault("MERIDIAN_TEST_MYSQL_USER", "root");
    }

    static String password() {
        return System.getenv().getOrDefault("MERIDIAN_TEST_MYSQL_PASSWORD", "");
    }

    static void assertSafeToWipe() {
        String database = URI.create(url().substring("jdbc:".length())).getPath().replace("/", "");
        if (!database.endsWith("_test")) {
            throw new IllegalStateException("Refusing to wipe database '" + database
                    + "': the MySQL tests only run against a database whose name ends in _test");
        }
    }

    static DriverManagerDataSource dataSource() {
        return new DriverManagerDataSource(url(), user(), password());
    }

    static JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource());
    }

    // Drops every table, leaving an empty database.
    static void wipe() {
        Flyway.configure().dataSource(dataSource()).cleanDisabled(false).load().clean();
    }

    // Starts the whole application against the MySQL test database, exactly as
    // it would start for real (Flyway migrates, then Hibernate validates).
    static ConfigurableApplicationContext boot(String... extraArgs) {
        List<String> args = new ArrayList<>(List.of(
                "--spring.profiles.active=test",
                "--spring.datasource.url=" + url(),
                "--spring.datasource.username=" + user(),
                "--spring.datasource.password=" + password(),
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
                "--server.port=0"));
        args.addAll(List.of(extraArgs));
        return SpringApplication.run(BackendApplication.class, args.toArray(String[]::new));
    }

    // A comparable description of the tables, columns, indexes and foreign
    // keys, ignoring the auto-generated constraint names.
    static Set<String> describeSchema(JdbcTemplate jdbc) {
        Set<String> out = new TreeSet<>();
        jdbc.query("select table_name t, column_name c, column_type ty, is_nullable n, column_default d, extra e "
                + "from information_schema.columns where table_schema = database() "
                + "and table_name <> 'flyway_schema_history'", rs -> {
            out.add("column " + rs.getString("t") + "." + rs.getString("c") + " " + rs.getString("ty")
                    + " nullable=" + rs.getString("n") + " default=" + rs.getString("d") + " " + rs.getString("e"));
        });
        jdbc.query("select table_name t, non_unique u, group_concat(column_name order by seq_in_index) cols "
                + "from information_schema.statistics where table_schema = database() "
                + "and table_name <> 'flyway_schema_history' group by table_name, index_name, non_unique", rs -> {
            out.add("index " + rs.getString("t") + "(" + rs.getString("cols") + ") "
                    + (rs.getInt("u") == 0 ? "unique" : "non-unique"));
        });
        jdbc.query("select table_name t, column_name c, referenced_table_name rt, referenced_column_name rc "
                + "from information_schema.key_column_usage where table_schema = database() "
                + "and referenced_table_name is not null", rs -> {
            out.add("foreign key " + rs.getString("t") + "." + rs.getString("c")
                    + " -> " + rs.getString("rt") + "." + rs.getString("rc"));
        });
        return out;
    }
}
