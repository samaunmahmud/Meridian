package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A database created before Flyway is baselined at V1 on the assumption that it already has the V1
 * schema. One created by an older version of the app does not: its orders table predates these four
 * columns, so Hibernate validation refused to start. Add whichever of them is missing; on a database
 * built by the migrations (which has them all) this does nothing.
 *
 * Java rather than SQL because MySQL has no "add column if not exists".
 */
public class V7__AddOrderColumnsMissingFromOldDatabases extends BaseJavaMigration {

    // Same definitions as in V1__baseline.sql.
    private static final Map<String, String> COLUMNS = new LinkedHashMap<>();
    static {
        COLUMNS.put("reserved_amount", "decimal(14,4)");
        COLUMNS.put("rejection_reason", "varchar(255)");
        COLUMNS.put("settlement_currency", "enum('EUR','GBP','USD')");
        COLUMNS.put("settlement_amount", "decimal(14,4)");
    }

    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        Set<String> present = columnsOf(connection, "orders");
        try (Statement statement = connection.createStatement()) {
            for (Map.Entry<String, String> column : COLUMNS.entrySet()) {
                if (!present.contains(column.getKey())) {
                    statement.execute("alter table orders add column " + column.getKey() + " " + column.getValue());
                }
            }
        }
    }

    // Column names read from an empty query, so the check does not depend on how a database
    // cases its table names in its metadata (MySQL lower case, H2 upper case).
    private static Set<String> columnsOf(Connection connection, String table) throws SQLException {
        Set<String> names = new TreeSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("select * from " + table + " where 1 = 0")) {
            ResultSetMetaData meta = rows.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                names.add(meta.getColumnName(i).toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }
}
