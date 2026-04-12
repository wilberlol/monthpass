package dev.wilber.monthpass.database;

import com.zaxxer.hikari.HikariDataSource;
import dev.wilber.monthpass.MonthPass;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public abstract class AbstractDatabase implements Database {

    protected final MonthPass plugin;
    protected HikariDataSource dataSource;
    protected final Executor executor = ForkJoinPool.commonPool();

    public AbstractDatabase(MonthPass plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() throws Exception {
        dataSource = createDataSource();
        try (Connection conn = getConnection()) {
            createTables(conn);
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    protected abstract HikariDataSource createDataSource() throws Exception;

    protected abstract void createTables(Connection conn) throws SQLException;

    protected Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * 從 ResultSet 中解析 PlayerCardData
     */
    protected PlayerCardData mapRow(ResultSet rs) throws SQLException {
        PlayerCardData data = new PlayerCardData();
        data.setPlayerUuid(UUID.fromString(rs.getString("player_uuid")));
        data.setPlayerName(rs.getString("player_name"));
        data.setCardId(rs.getString("card_id"));
        data.setExpiryDate(rs.getLong("expiry_date"));
        data.setActivatedAt(rs.getLong("activated_at"));
        String lastClaim = rs.getString("last_claim_date");
        if (lastClaim != null && !lastClaim.isEmpty()) {
            data.setLastClaimDate(LocalDate.parse(lastClaim));
        }
        return data;
    }

    @Override
    public CompletableFuture<List<PlayerCardData>> getPlayerCards(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<PlayerCardData> list = new ArrayList<>();
            String sql = "SELECT * FROM player_cards WHERE player_uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapRow(rs));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("getPlayerCards 失敗：" + e.getMessage());
            }
            return list;
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<PlayerCardData>> getPlayerCard(UUID playerUuid, String cardId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM player_cards WHERE player_uuid = ? AND card_id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, cardId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("getPlayerCard 失敗：" + e.getMessage());
            }
            return Optional.<PlayerCardData>empty();
        }, executor);
    }

    @Override
    public CompletableFuture<Void> savePlayerCard(PlayerCardData data) {
        return CompletableFuture.runAsync(() -> {
            String sql = buildUpsertSql();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, data.getPlayerUuid().toString());
                ps.setString(2, data.getPlayerName());
                ps.setString(3, data.getCardId());
                ps.setLong(4, data.getExpiryDate());
                ps.setString(5, data.getLastClaimDate() != null ? data.getLastClaimDate().toString() : null);
                ps.setLong(6, data.getActivatedAt());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("savePlayerCard 失敗：" + e.getMessage());
            }
        }, executor);
    }

    /**
     * 各 DB 子類別提供 upsert SQL
     */
    protected abstract String buildUpsertSql();

    @Override
    public CompletableFuture<Void> removePlayerCard(UUID playerUuid, String cardId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM player_cards WHERE player_uuid = ? AND card_id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, cardId);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("removePlayerCard 失敗：" + e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> updateLastClaimDate(UUID playerUuid, String cardId, LocalDate date) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE player_cards SET last_claim_date = ? WHERE player_uuid = ? AND card_id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, date != null ? date.toString() : null);
                ps.setString(2, playerUuid.toString());
                ps.setString(3, cardId);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("updateLastClaimDate 失敗：" + e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> updateExpiryDate(UUID playerUuid, String cardId, long expiryDate) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE player_cards SET expiry_date = ? WHERE player_uuid = ? AND card_id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, expiryDate);
                ps.setString(2, playerUuid.toString());
                ps.setString(3, cardId);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("updateExpiryDate 失敗：" + e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<PlayerCardData>> getCardHolders(String cardId) {
        return CompletableFuture.supplyAsync(() -> {
            List<PlayerCardData> list = new ArrayList<>();
            String sql = "SELECT * FROM player_cards WHERE card_id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, cardId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapRow(rs));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("getCardHolders 失敗：" + e.getMessage());
            }
            return list;
        }, executor);
    }

    @Override
    public CompletableFuture<List<PlayerCardData>> getAllExpiredCards() {
        return CompletableFuture.supplyAsync(() -> {
            List<PlayerCardData> list = new ArrayList<>();
            String sql = "SELECT * FROM player_cards WHERE expiry_date <= ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, System.currentTimeMillis());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapRow(rs));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("getAllExpiredCards 失敗：" + e.getMessage());
            }
            return list;
        }, executor);
    }
}
