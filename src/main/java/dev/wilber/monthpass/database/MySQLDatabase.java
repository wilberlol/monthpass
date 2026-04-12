package dev.wilber.monthpass.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.wilber.monthpass.MonthPass;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MySQLDatabase extends AbstractDatabase {

    public MySQLDatabase(MonthPass plugin) {
        super(plugin);
    }

    @Override
    protected HikariDataSource createDataSource() throws Exception {
        String host = plugin.getConfigManager().getMySQLHost();
        int port = plugin.getConfigManager().getMySQLPort();
        String database = plugin.getConfigManager().getMySQLDatabase();
        String username = plugin.getConfigManager().getMySQLUsername();
        String password = plugin.getConfigManager().getMySQLPassword();
        int poolSize = plugin.getConfigManager().getMySQLPoolSize();
        long connectionTimeout = plugin.getConfigManager().getMySQLConnectionTimeout();

        String url = String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&characterEncoding=utf8&allowPublicKeyRetrieval=true",
                host, port, database
        );

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setPoolName("MonthPass-MySQL");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");

        return new HikariDataSource(config);
    }

    @Override
    protected void createTables(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS player_cards (" +
                "id INT NOT NULL AUTO_INCREMENT," +
                "player_uuid VARCHAR(36) NOT NULL," +
                "player_name VARCHAR(36) NOT NULL," +
                "card_id VARCHAR(64) NOT NULL," +
                "expiry_date BIGINT NOT NULL," +
                "last_claim_date VARCHAR(10)," +
                "activated_at BIGINT NOT NULL," +
                "PRIMARY KEY (id)," +
                "UNIQUE KEY uk_player_card (player_uuid, card_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    @Override
    protected String buildUpsertSql() {
        // MySQL 使用 INSERT ... ON DUPLICATE KEY UPDATE
        return "INSERT INTO player_cards (player_uuid, player_name, card_id, expiry_date, last_claim_date, activated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "player_name = VALUES(player_name), " +
                "expiry_date = VALUES(expiry_date), " +
                "last_claim_date = VALUES(last_claim_date), " +
                "activated_at = VALUES(activated_at)";
    }
}
