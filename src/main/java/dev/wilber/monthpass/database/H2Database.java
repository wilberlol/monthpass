package dev.wilber.monthpass.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.wilber.monthpass.MonthPass;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class H2Database extends AbstractDatabase {

    public H2Database(MonthPass plugin) {
        super(plugin);
    }

    @Override
    protected HikariDataSource createDataSource() throws Exception {
        String filePath = plugin.getConfigManager().getH2File();
        // 確保父目錄存在
        File file = new File(filePath);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:file:" + file.getAbsolutePath() + ";AUTO_SERVER=FALSE");
        config.setDriverClassName("org.h2.Driver");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setPoolName("MonthPass-H2");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        return new HikariDataSource(config);
    }

    @Override
    protected void createTables(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS player_cards (" +
                "id INTEGER AUTO_INCREMENT PRIMARY KEY," +
                "player_uuid VARCHAR(36) NOT NULL," +
                "player_name VARCHAR(36) NOT NULL," +
                "card_id VARCHAR(64) NOT NULL," +
                "expiry_date BIGINT NOT NULL," +
                "last_claim_date VARCHAR(10)," +
                "activated_at BIGINT NOT NULL," +
                "UNIQUE(player_uuid, card_id)" +
                ")";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    @Override
    protected String buildUpsertSql() {
        // H2 支援 MERGE INTO
        return "MERGE INTO player_cards (player_uuid, player_name, card_id, expiry_date, last_claim_date, activated_at) " +
                "KEY(player_uuid, card_id) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
    }
}
