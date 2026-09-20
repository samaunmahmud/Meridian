package com.meridian.backend.mysql;

import com.meridian.backend.retention.HistoryRetention;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The nightly history clean-up on a real MySQL with a year-sized table: about 300 000 prices, one a minute for
 * 208 days, generated inside MySQL. It must remove the bulk of them, keep the last week whole, keep one row per
 * hour (then per day), never remove the newest price, and finish in reasonable time.
 */
@EnabledIfEnvironmentVariable(named = MySqlTestDatabase.URL_ENV, matches = ".+")
class MySqlRetentionTest {

    private static final int MINUTES = 208 * 24 * 60;

    private ConfigurableApplicationContext app;
    private JdbcTemplate jdbc;

    @BeforeAll
    static void onlyAgainstAThrowawayDatabase() {
        MySqlTestDatabase.assertSafeToWipe();
    }

    @BeforeEach
    void startTheApp() {
        MySqlTestDatabase.wipe();
        app = MySqlTestDatabase.boot();
        jdbc = app.getBean(JdbcTemplate.class);
    }

    @AfterEach
    void stopTheApp() {
        app.close();
    }

    private long scalar(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    @Test
    void thinsAYearSizedPriceTableQuicklyAndCorrectly() {
        jdbc.update("insert into tickers (symbol, name, exchange, asset_type) values ('RET', 'Retention test', 'TEST', 'STOCK')");
        Long tickerId = jdbc.queryForObject("select id from tickers where symbol = 'RET'", Long.class);
        jdbc.execute((java.sql.Connection con) -> {
            try (java.sql.Statement st = con.createStatement()) {
                st.execute("set session cte_max_recursion_depth = 1000000");
                st.execute("insert into price_history (ticker_id, price, recorded_at) "
                        + "with recursive s(n) as (select 0 union all select n + 1 from s where n < " + (MINUTES - 1) + ") "
                        + "select " + tickerId + ", 100 + (n % 50), date_sub(utc_timestamp(6), interval n minute) from s");
            }
            return null;
        });
        long before = scalar("select count(*) from price_history");
        long lastWeekBefore = scalar("select count(*) from price_history where recorded_at > date_sub(utc_timestamp(6), interval 7 day)");
        long newestId = scalar("select id from price_history order by recorded_at desc, id limit 1");
        assertThat(before).isEqualTo(MINUTES);

        long started = System.nanoTime();
        HistoryRetention.Result result = app.getBean(HistoryRetention.class).run();
        long seconds = (System.nanoTime() - started) / 1_000_000_000;

        long after = scalar("select count(*) from price_history");
        System.out.println("MySQL retention: " + before + " -> " + after + " rows in " + seconds + " s");
        assertThat(seconds).as("seconds for %d rows", before).isLessThan(120);
        assertThat(result.priceRows()).isEqualTo((int) (before - after));
        assertThat(after).as("most of the year's rows are gone").isLessThan(before / 10);
        assertThat(scalar("select count(*) from price_history where recorded_at > date_sub(utc_timestamp(6), interval 7 day)"))
                .as("the last 7 days keep every row").isBetween(lastWeekBefore - 2, lastWeekBefore + 2);
        assertThat(scalar("select count(*) - count(distinct date_format(recorded_at, '%Y-%m-%d %H')) from price_history "
                + "where recorded_at <= date_sub(utc_timestamp(6), interval 7 day) and recorded_at > date_sub(utc_timestamp(6), interval 90 day)"))
                .as("between 7 and 90 days old: at most one row per hour").isBetween(0L, 2L);
        assertThat(scalar("select count(*) - count(distinct date(recorded_at)) from price_history "
                + "where recorded_at <= date_sub(utc_timestamp(6), interval 90 day)"))
                .as("older than 90 days: at most one row per day").isBetween(0L, 2L);
        assertThat(scalar("select count(*) from price_history where id = " + newestId)).as("the newest price survives").isEqualTo(1);

        HistoryRetention.Result again = app.getBean(HistoryRetention.class).run();
        assertThat(again.priceRows()).as("a second run has nothing left to remove").isZero();
    }
}
