package dev.wilber.monthpass.command;

import dev.wilber.monthpass.MonthPass;
import dev.wilber.monthpass.config.CardDefinition;
import dev.wilber.monthpass.config.ConfigManager;
import dev.wilber.monthpass.config.ShopConfig;
import dev.wilber.monthpass.database.PlayerCardData;
import dev.wilber.monthpass.hook.PlayerPointsHook;
import dev.wilber.monthpass.manager.CardManager;
import dev.wilber.monthpass.manager.FlyManager;
import dev.wilber.monthpass.util.ColorUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Map;

public class MonthPassCommand implements CommandExecutor, TabCompleter {

    private final MonthPass plugin;
    private final CardManager cardManager;
    private final FlyManager flyManager;
    private final ConfigManager configManager;

    private static final List<String> DAY_SUGGESTIONS = Arrays.asList(
            "7d", "14d", "30d", "45d", "60d", "90d"
    );

    public MonthPassCommand(MonthPass plugin, CardManager cardManager,
                            FlyManager flyManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.cardManager = cardManager;
        this.flyManager = flyManager;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "give":
                return handleGive(sender, args);
            case "set":
                return handleSet(sender, args);
            case "remove":
                return handleRemove(sender, args);
            case "check":
                return handleCheck(sender, args);
            case "sign":
                return handleSign(sender, args);
            case "list":
                return handleList(sender, args);
            case "reload":
                return handleReload(sender, args);
            case "buy":
                return handleBuy(sender, args);
            case "fly":
                return handleFly(sender);
            default:
                sendHelp(sender);
                return true;
        }
    }

    // ========================= 子指令 =========================

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!hasAdminPermission(sender)) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(configManager.getMessage("help-give"));
            return true;
        }

        String playerName = args[1];
        String cardId = args[2];
        int days = parseDays(args[3]);

        if (days <= 0) {
            sender.sendMessage(configManager.getMessage("invalid-days"));
            return true;
        }

        if (!configManager.cardExists(cardId)) {
            sender.sendMessage(configManager.getMessage("card-not-found", "card", cardId));
            return true;
        }

        OfflinePlayer target = findPlayer(playerName);
        if (target == null) {
            sender.sendMessage(configManager.getMessage("player-not-found", "player", playerName));
            return true;
        }

        cardManager.giveCard(sender, target, cardId, days);
        return true;
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (!hasAdminPermission(sender)) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(configManager.getMessage("help-set"));
            return true;
        }

        String playerName = args[1];
        String cardId = args[2];
        int days = parseDays(args[3]);

        if (days <= 0) {
            sender.sendMessage(configManager.getMessage("invalid-days"));
            return true;
        }

        if (!configManager.cardExists(cardId)) {
            sender.sendMessage(configManager.getMessage("card-not-found", "card", cardId));
            return true;
        }

        OfflinePlayer target = findPlayer(playerName);
        if (target == null) {
            sender.sendMessage(configManager.getMessage("player-not-found", "player", playerName));
            return true;
        }

        cardManager.setCard(sender, target, cardId, days);
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (!hasAdminPermission(sender)) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(configManager.getMessage("help-remove"));
            return true;
        }

        String playerName = args[1];
        String cardId = args[2];

        if (!configManager.cardExists(cardId)) {
            sender.sendMessage(configManager.getMessage("card-not-found", "card", cardId));
            return true;
        }

        OfflinePlayer target = findPlayer(playerName);
        if (target == null) {
            sender.sendMessage(configManager.getMessage("player-not-found", "player", playerName));
            return true;
        }

        cardManager.removeCard(sender, target, cardId);
        return true;
    }

    private boolean handleCheck(CommandSender sender, String[] args) {
        ZoneId tz = configManager.getTimezone();

        if (args.length >= 2) {
            // 查別人
            if (!(sender instanceof Player)) {
                // Console 可以查任何人
            } else if (!sender.hasPermission("monthpass.check.other")) {
                sender.sendMessage(configManager.getMessage("no-permission"));
                return true;
            }

            String playerName = args[1];
            OfflinePlayer target = findPlayer(playerName);
            if (target == null) {
                sender.sendMessage(configManager.getMessage("player-not-found", "player", playerName));
                return true;
            }

            String displayName = target.getName() != null ? target.getName() : playerName;
            cardManager.getPlayerCards(target.getUniqueId()).thenAccept(cards -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sendCheckOutput(sender, displayName, cards, tz);
                });
            });
        } else {
            // 查自己
            if (!(sender instanceof Player)) {
                sender.sendMessage(configManager.getMessage("console-not-allowed"));
                return true;
            }
            Player player = (Player) sender;
            List<PlayerCardData> cards = cardManager.getPlayerCardsFromCache(player.getUniqueId());
            sendCheckOutput(sender, player.getName(), cards, tz);
        }
        return true;
    }

    private void sendCheckOutput(CommandSender sender, String playerName,
                                 List<PlayerCardData> cards, ZoneId tz) {
        sender.sendMessage(configManager.getMessage("check-header", "player", playerName));

        List<PlayerCardData> validCards = cards.stream()
                .filter(c -> !c.isExpired())
                .collect(Collectors.toList());

        if (validCards.isEmpty()) {
            sender.sendMessage(configManager.getMessageNoPrefix("check-no-cards"));
            return;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(configManager.getDateFormat());

        for (PlayerCardData card : validCards) {
            CardDefinition def = configManager.getCard(card.getCardId());
            String displayName = def != null ? ColorUtil.color(def.getDisplayName()) : card.getCardId();
            long days = card.getRemainingDays(tz);
            String expire = Instant.ofEpochMilli(card.getExpiryDate())
                    .atZone(tz)
                    .toLocalDate()
                    .format(formatter);
            boolean claimed = card.isClaimedToday(tz);
            String claimedStr = claimed
                    ? configManager.getMessageNoPrefix("check-card-line-claimed-yes")
                    : configManager.getMessageNoPrefix("check-card-line-claimed-no");

            sender.sendMessage(configManager.getMessage("check-card-line",
                    "card", displayName,
                    "days", String.valueOf(days),
                    "expire", expire,
                    "claimed", claimedStr));
        }
    }

    private boolean handleFly(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(configManager.getMessage("console-not-allowed"));
            return true;
        }
        Player player = (Player) sender;

        if (!configManager.isFlyModuleEnabled()) {
            player.sendMessage(configManager.getMessage("fly-module-disabled"));
            return true;
        }

        if (flyManager.grantFly(player)) {
            player.sendMessage(configManager.getMessage("fly-enabled"));
        } else {
            player.sendMessage(configManager.getMessage("fly-no-permission",
                    "world", player.getWorld().getName()));
        }
        return true;
    }

    private boolean handleBuy(CommandSender sender, String[] args) {
        // 只允許玩家，不允許 console
        if (!(sender instanceof Player)) {
            sender.sendMessage(configManager.getMessage("console-not-allowed"));
            return true;
        }
        Player player = (Player) sender;

        if (args.length < 2) {
            sender.sendMessage(configManager.getMessageNoPrefix("help-buy"));
            return true;
        }

        String cardId = args[1];
        CardDefinition def = configManager.getCard(cardId);
        if (def == null) {
            player.sendMessage(configManager.getMessage("card-not-found", "card", cardId));
            return true;
        }

        // 確認此卡有開放購買
        ShopConfig shop = def.getShop();
        if (shop == null || !shop.isEnable()) {
            player.sendMessage(configManager.getMessage("buy-not-available",
                    "card", ColorUtil.color(def.getDisplayName())));
            return true;
        }

        // 確認玩家有購買此卡的權限（若卡片設定了 buy-permission）
        if (!shop.getBuyPermission().isEmpty() && !player.hasPermission(shop.getBuyPermission())) {
            player.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        int days = shop.getDays();
        String cardDisplay = ColorUtil.color(def.getDisplayName());

        // ── 依貨幣種類處理扣款 ──────────────────────────────────────────
        String priceStr; // 給訊息用的格式化金額字串

        if (shop.getCurrency() == ShopConfig.CurrencyType.POINTS) {
            // ── PlayerPoints ─────────────────────────────────────────────
            PlayerPointsHook ppHook = plugin.getPlayerPointsHook();
            if (!ppHook.isAvailable()) {
                player.sendMessage(configManager.getMessage("buy-no-playerpoints"));
                return true;
            }

            int price = shop.getPriceAsInt();
            String unit = configManager.getPointsUnit();
            priceStr = price + " " + unit;

            int balance = ppHook.look(player.getUniqueId());
            if (balance < price) {
                player.sendMessage(configManager.getMessage("buy-insufficient-funds",
                        "card", cardDisplay,
                        "price", priceStr,
                        "balance", balance + " " + unit));
                return true;
            }
            ppHook.take(player.getUniqueId(), price);

        } else {
            // ── Vault Economy（預設）────────────────────────────────────
            Economy economy = plugin.getEconomy();
            if (economy == null) {
                player.sendMessage(configManager.getMessage("buy-no-economy"));
                return true;
            }

            double price = shop.getPrice();
            priceStr = economy.format(price);

            if (!economy.has(player, price)) {
                double balance = economy.getBalance(player);
                player.sendMessage(configManager.getMessage("buy-insufficient-funds",
                        "card", cardDisplay,
                        "price", priceStr,
                        "balance", economy.format(balance)));
                return true;
            }
            economy.withdrawPlayer(player, price);
        }

        // 先送購買成功訊息，buyCard 內部再送 receive-card（顯示有效期）
        player.sendMessage(configManager.getMessage("buy-success",
                "card", cardDisplay,
                "days", String.valueOf(days),
                "price", priceStr));

        cardManager.buyCard(player, cardId, days);
        return true;
    }

    private boolean handleSign(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(configManager.getMessage("console-not-allowed"));
            return true;
        }
        cardManager.sign((Player) sender);
        return true;
    }

    private boolean handleList(CommandSender sender, String[] args) {
        if (!hasAdminPermission(sender)) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(configManager.getMessage("help-list"));
            return true;
        }

        String cardId = args[1];
        if (!configManager.cardExists(cardId)) {
            sender.sendMessage(configManager.getMessage("card-not-found", "card", cardId));
            return true;
        }

        ZoneId tz = configManager.getTimezone();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(configManager.getDateFormat());

        cardManager.getDatabase().getCardHolders(cardId).thenAccept(holders -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(configManager.getMessage("list-header", "card", cardId));

                List<PlayerCardData> validHolders = holders.stream()
                        .filter(c -> !c.isExpired())
                        .collect(Collectors.toList());

                if (validHolders.isEmpty()) {
                    sender.sendMessage(configManager.getMessageNoPrefix("list-empty"));
                    return;
                }

                for (PlayerCardData card : validHolders) {
                    long days = card.getRemainingDays(tz);
                    String expire = Instant.ofEpochMilli(card.getExpiryDate())
                            .atZone(tz)
                            .toLocalDate()
                            .format(formatter);
                    sender.sendMessage(configManager.getMessage("list-entry",
                            "player", card.getPlayerName(),
                            "days", String.valueOf(days),
                            "expire", expire));
                }
            });
        });
        return true;
    }

    private boolean handleReload(CommandSender sender, String[] args) {
        if (!hasAdminPermission(sender)) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }
        plugin.reload();
        sender.sendMessage(configManager.getMessage("reload-success"));
        return true;
    }

    // ========================= 輔助方法 =========================

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(configManager.getMessage("help-header"));
        if (sender instanceof Player) {
            // 玩家看得到 buy、sign、check、fly
            sender.sendMessage(configManager.getMessageNoPrefix("help-buy"));
            sender.sendMessage(configManager.getMessageNoPrefix("help-sign"));
            sender.sendMessage(configManager.getMessageNoPrefix("help-check"));
            sender.sendMessage(configManager.getMessageNoPrefix("help-fly"));
        }
        if (hasAdminPermission(sender)) {
            sender.sendMessage(configManager.getMessageNoPrefix("help-give"));
            sender.sendMessage(configManager.getMessageNoPrefix("help-set"));
            sender.sendMessage(configManager.getMessageNoPrefix("help-remove"));
            sender.sendMessage(configManager.getMessageNoPrefix("help-list"));
            sender.sendMessage(configManager.getMessageNoPrefix("help-reload"));
        }
    }

    private boolean hasAdminPermission(CommandSender sender) {
        if (!(sender instanceof Player)) return true; // Console 有所有權限
        return sender.hasPermission("monthpass.admin");
    }

    /**
     * 解析天數字串，支援 "30d"、"30" 格式
     */
    private int parseDays(String input) {
        try {
            if (input.toLowerCase().endsWith("d")) {
                return Integer.parseInt(input.substring(0, input.length() - 1));
            }
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * 尋找玩家（在線或離線）
     */
    @SuppressWarnings("deprecation")
    private OfflinePlayer findPlayer(String name) {
        // 先找在線玩家
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;

        // 再找離線玩家
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore() || offline.isOnline()) return offline;

        return null;
    }

    // ========================= Tab Completion =========================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList("buy", "sign", "check", "fly"));
            if (hasAdminPermission(sender)) {
                subs.addAll(Arrays.asList("give", "set", "remove", "list", "reload"));
            }
            return filterStartsWith(subs, args[0]);
        }

        String sub = args[0].toLowerCase();

        if (args.length == 2) {
            switch (sub) {
                case "give":
                case "set":
                case "remove":
                case "check":
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                case "list":
                    return filterStartsWith(new ArrayList<>(configManager.getCards().keySet()), args[1]);
                case "buy":
                    // 只顯示 shop.enable=true 的卡
                    return configManager.getCards().entrySet().stream()
                            .filter(e -> e.getValue().getShop() != null && e.getValue().getShop().isEnable())
                            .map(Map.Entry::getKey)
                            .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            switch (sub) {
                case "give":
                case "set":
                case "remove":
                    // cardId
                    return filterStartsWith(new ArrayList<>(configManager.getCards().keySet()), args[2]);
            }
        }

        if (args.length == 4) {
            switch (sub) {
                case "give":
                case "set":
                    return filterStartsWith(DAY_SUGGESTIONS, args[3]);
            }
        }

        return completions;
    }

    private List<String> filterStartsWith(List<String> list, String prefix) {
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
}
