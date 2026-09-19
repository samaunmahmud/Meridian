package com.meridian.backend.config;

import com.meridian.backend.model.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

// Small, safe upgrades that hibernate.ddl-auto=update cannot do by itself.
//
// On MySQL, Hibernate stores enum fields as native ENUM('A','B',...) columns.
// "update" adds new columns and tables but never changes an existing column,
// so a database created before OrderStatus.REJECTED existed would refuse to
// store it ("Data truncated for column 'status'"). This widens that one
// column at startup, only when it is needed, and does nothing otherwise
// (already up to date, not MySQL, or the column isn't an ENUM).
//
// Runs after Hibernate has created/updated the schema but before scheduled
// jobs start. A real migration tool such as Flyway would replace this.
@Component
public class SchemaUpgrades implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(SchemaUpgrades.class);

    private final JdbcTemplate jdbc;

    public SchemaUpgrades(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void afterSingletonsInstantiated() {
        try {
            widenOrderStatus();
        } catch (RuntimeException e) {
            // Never stop the app from starting over this; the log says what to run by hand.
            log.warn("Could not check/upgrade orders.status. If the app later fails when an order is rejected, run: "
                    + widenStatement(), e);
        }
    }

    boolean isMySql() {
        String product = jdbc.execute((java.sql.Connection c) -> c.getMetaData().getDatabaseProductName());
        return product != null && product.toLowerCase().contains("mysql");
    }

    void widenOrderStatus() {
        if (!isMySql()) return;

        List<String> types = jdbc.queryForList(
                "select column_type from information_schema.columns "
                        + "where table_schema = database() and table_name = 'orders' and column_name = 'status'",
                String.class);
        if (types.isEmpty()) return; // table not there (yet)

        String columnType = types.get(0);
        if (columnType == null || !columnType.toLowerCase().startsWith("enum(")) return;

        boolean missingValue = Arrays.stream(OrderStatus.values())
                .anyMatch(s -> !columnType.contains("'" + s.name() + "'"));
        if (!missingValue) return;

        log.info("Widening orders.status from {} to include all order statuses", columnType);
        jdbc.execute(widenStatement());
    }

    // Same value list Hibernate generates for a new database (alphabetical).
    static String widenStatement() {
        String values = Arrays.stream(OrderStatus.values()).map(Enum::name).sorted()
                .map(n -> "'" + n + "'").collect(Collectors.joining(","));
        return "alter table orders modify column status enum(" + values + ") not null";
    }
}
