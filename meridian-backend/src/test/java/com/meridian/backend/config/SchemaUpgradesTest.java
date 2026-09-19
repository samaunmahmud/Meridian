package com.meridian.backend.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchemaUpgradesTest {

    private JdbcTemplate jdbc;
    private SchemaUpgrades upgrades;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        upgrades = spy(new SchemaUpgrades(jdbc));
    }

    private void columnTypeIs(String type) {
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of(type));
    }

    @Test
    void anOldEnumWithoutRejectedIsWidened() {
        doReturn(true).when(upgrades).isMySql();
        columnTypeIs("enum('CANCELLED','FILLED','PENDING')");

        upgrades.widenOrderStatus();

        verify(jdbc).execute("alter table orders modify column status enum('CANCELLED','FILLED','PENDING','REJECTED') not null");
    }

    @Test
    void anUpToDateEnumIsLeftAlone() {
        doReturn(true).when(upgrades).isMySql();
        columnTypeIs("enum('CANCELLED','FILLED','PENDING','REJECTED')");

        upgrades.widenOrderStatus();

        verify(jdbc, never()).execute(anyString());
    }

    @Test
    void aPlainVarcharColumnIsLeftAlone() {
        doReturn(true).when(upgrades).isMySql();
        columnTypeIs("varchar(255)");

        upgrades.widenOrderStatus();

        verify(jdbc, never()).execute(anyString());
    }

    @Test
    void otherDatabasesAreNeverTouched() {
        doReturn(false).when(upgrades).isMySql();

        upgrades.widenOrderStatus();

        verify(jdbc, never()).queryForList(anyString(), eq(String.class));
        verify(jdbc, never()).execute(anyString());
    }

    @Test
    void aFailureNeverStopsTheAppFromStarting() {
        doReturn(true).when(upgrades).isMySql();
        when(jdbc.queryForList(anyString(), eq(String.class))).thenThrow(new RuntimeException("boom"));

        upgrades.afterSingletonsInstantiated(); // must not throw
    }
}
