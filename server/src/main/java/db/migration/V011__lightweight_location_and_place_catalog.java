package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Keeps the production MySQL DDL and the H2 CI schema in sync. MySQL's
 * MODIFY syntax is not accepted by modern H2 even in MySQL compatibility mode.
 */
public class V011__lightweight_location_and_place_catalog extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        boolean h2 = connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("H2");
        String[] coordinateColumns = {
                "pickup_latitude", "pickup_longitude", "destination_latitude", "destination_longitude"};
        for (String column : coordinateColumns) {
            if (!columnAllowsNull(connection, "ride_order", column)) {
                String sql = h2
                        ? "ALTER TABLE ride_order ALTER COLUMN " + column + " DROP NOT NULL"
                        : "ALTER TABLE ride_order MODIFY " + column + " DECIMAL(10,7) NULL";
                execute(connection, sql);
            }
        }
        if (!tableExists(connection, "place_catalog")) {
            execute(connection, """
                CREATE TABLE place_catalog (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    name VARCHAR(120) NOT NULL,
                    address_text VARCHAR(255) NOT NULL,
                    latitude DECIMAL(10,7) NULL,
                    longitude DECIMAL(10,7) NULL,
                    coordinate_system VARCHAR(20) NOT NULL DEFAULT 'WGS84',
                    city VARCHAR(80) NULL,
                    district VARCHAR(80) NULL,
                    category VARCHAR(60) NULL,
                    aliases VARCHAR(500) NULL,
                    enabled BOOLEAN NOT NULL DEFAULT TRUE,
                    usage_count BIGINT NOT NULL DEFAULT 0,
                    last_used_at TIMESTAMP(6) NULL,
                    source VARCHAR(30) NOT NULL DEFAULT 'ADMIN',
                    created_at TIMESTAMP(6) NOT NULL,
                    updated_at TIMESTAMP(6) NOT NULL,
                    version BIGINT NOT NULL DEFAULT 0,
                    INDEX idx_place_catalog_name (name),
                    INDEX idx_place_catalog_enabled_usage (enabled, usage_count),
                    INDEX idx_place_catalog_city_district (city, district)
                )
                """);
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet tables = metadata.getTables(null, null, null, new String[] {"TABLE"})) {
            while (tables.next()) {
                if (tableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) return true;
            }
        }
        return false;
    }

    private boolean columnAllowsNull(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(null, null, null, null)) {
            while (columns.next()) {
                if (tableName.equalsIgnoreCase(columns.getString("TABLE_NAME"))
                        && columnName.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    return columns.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls;
                }
            }
        }
        throw new SQLException("Missing expected column " + tableName + "." + columnName);
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
