# MonthPass — Minecraft 月卡系統插件

> 適用於 **Purpur / Paper 1.21.1**，支援 SQLite、MySQL、H2 三種資料庫，並整合 Vault、PlayerPoints 雙貨幣系統。

---

## 功能概覽

| 功能 | 說明 |
|------|------|
| 🎫 多張月卡 | 可同時持有多種月卡（VIP、MVP…），天數可疊加 |
| ⏳ 時間制 | 依真實時間倒數，離線也會減少 |
| ✍️ 每日簽到 | 各月卡獨立簽到，可獎勵物品、執行指令 |
| 🛫 月卡飛行 | 指定世界允許飛行，自動偵測並補正 |
| 💰 雙貨幣商店 | 各月卡可分別用 Vault 金錢或 PlayerPoints 積分購買 |
| 📢 提醒系統 | 未簽到時定時提醒，可在 config 調整間隔 |
| 📦 PlaceholderAPI | 提供 `%monthpass_*%` 系列 Placeholder |
| 🔄 熱重載 | 相容 PlugManX，支援 `/monthpass reload` |

---

## 安裝需求

- Java 21+
- Purpur / Paper 1.21.x
- **（選用）** Vault + 任一經濟插件（用於 Vault 購買）
- **（選用）** [PlayerPoints](https://github.com/Rosewood-Development/PlayerPoints)（用於積分購買）
- **（選用）** [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI)（Placeholder 支援）

---

## 安裝步驟

1. 將 `monthpass.jar` 放入 `plugins/` 資料夾
2. 重啟伺服器
3. 編輯 `plugins/MonthPass/config.yml` 設定月卡與資料庫
4. `/monthpass reload` 套用設定

---

## 指令

主指令：`/monthpass`　別名：`/mp`

### 玩家指令

| 指令 | 說明 | 所需權限 |
|------|------|----------|
| `/mp` | 顯示說明 | 無 |
| `/mp sign` | 領取今日簽到獎勵 | 無 |
| `/mp check` | 查看自己的月卡狀態 | 無 |
| `/mp buy <月卡ID>` | 購買月卡 | 無（可在 config 限制） |
| `/mp fly` | 在當前世界開啟月卡飛行 | 需持有含當前世界的月卡 |

### 管理員指令

> 管理員指令可由 **Console** 執行。

| 指令 | 說明 | 所需權限 |
|------|------|----------|
| `/mp give <玩家> <月卡ID> <天數>` | 給予月卡（可疊加天數，格式：`30d`） | `monthpass.admin` |
| `/mp set <玩家> <月卡ID> <天數>` | 強制設定剩餘天數 | `monthpass.admin` |
| `/mp remove <玩家> <月卡ID>` | 移除月卡 | `monthpass.admin` |
| `/mp check <玩家>` | 查看指定玩家的月卡狀態 | `monthpass.check.other` |
| `/mp list <月卡ID>` | 列出所有持有該月卡的玩家 | `monthpass.admin` |
| `/mp reload` | 重新載入設定檔 | `monthpass.admin` |

---

## 權限

| 權限節點 | 說明 | 預設 |
|----------|------|------|
| `monthpass.admin` | 所有管理指令（give/set/remove/list/reload） | OP |
| `monthpass.check.other` | 查看其他玩家的月卡（`/mp check <玩家>`） | OP |
| `monthpass.fly.bypass` | **跳過**月卡飛行檢查（持有此權限者不受月卡限制） | false |
| `monthpass.vip` | VIP 月卡專屬權限（由插件自動賦予） | — |
| `monthpass.mvp` | MVP 月卡專屬權限（由插件自動賦予） | — |

> ⚠️ **注意**：OP 玩家預設擁有 `monthpass.fly.bypass`（透過萬用 OP 權限），因此 OP 帳號不會受月卡飛行控制，測試時請使用非 OP 帳號。

---

## PlaceholderAPI

| Placeholder | 說明 | 範例輸出 |
|-------------|------|----------|
| `%monthpass_has_<卡ID>%` | 是否持有月卡 | `true` / `false` |
| `%monthpass_days_<卡ID>%` | 剩餘天數 | `25` |
| `%monthpass_expire_<卡ID>%` | 到期日期 | `2026/05/12` |
| `%monthpass_claimed_<卡ID>%` | 今日是否已簽到 | `true` / `false` |
| `%monthpass_all_cards%` | 持有的所有月卡（逗號分隔） | `VIP 月卡, MVP 月卡` |

---

## config.yml 說明

```yaml
database:
  type: sqlite        # sqlite / mysql / h2
  mysql:
    host: localhost
    port: 3306
    database: monthpass
    username: root
    password: ''
    pool-size: 10

timezone: Asia/Taipei
date-format: "yyyy/MM/dd"

sign-reminder:
  enable: true
  interval: 60        # 提醒間隔（秒），今日已簽到後自動停止

fly:
  enable: true                  # 是否啟用飛行模組
  enforce-interval-ticks: 40    # 飛行補正週期（ticks，最低 10）

points-display-unit: '點'       # PlayerPoints 積分顯示單位

cards:
  vip:
    display-name: '&6VIP 月卡'
    permission: 'monthpass.vip'

    on-activate:
      commands:
        - 'eco give {player} 1000'    # 首次啟用時執行

    on-expire:
      commands:
        - 'broadcast {player} 的VIP月卡已到期'

    daily-reward:
      commands:
        - 'eco give {player} 100'
      items:
        - material: DIAMOND
          amount: 1
          name: '&b每日簽到鑽石'
          lore:
            - '&7來自 &6VIP月卡 &7的每日獎勵'
            - '&7日期：{date}'
          custom-model-data: 0
          enchantments: {}
          item-flags:
            - HIDE_ENCHANTS

    fly:
      enable: true
      worlds:             # 允許飛行的世界名稱
        - world
        - world_nether

    expiry-warning:
      days: 3             # 提前幾天提醒到期
      message: '&c你的 &6{card} &c還剩 &e{days} &c天到期！'

    shop:
      enable: true
      currency: vault     # vault（金錢）或 points（PlayerPoints）
      price: 500.0
      days: 30
      buy-permission: ''  # 購買所需權限，空字串=所有人可買
```

---

## 月卡飛行說明

- 玩家執行 `/mp fly` 後，插件檢查是否持有當前世界在允許清單內的月卡
- 如符合條件則賦予飛行，並在世界切換時自動撤銷不符合條件的飛行
- 插件每 `enforce-interval-ticks` 個 ticks 補正一次飛行狀態，防止其他插件（Multiverse-Core、Residence 等）重置
- **Creative / Spectator 模式玩家不受影響**
- 持有 `monthpass.fly.bypass` 的玩家完全跳過月卡飛行管理

---

## 資料庫

| 類型 | 說明 |
|------|------|
| SQLite | 預設，零設定，資料存於 `plugins/MonthPass/data.db` |
| H2 | 嵌入式，資料存於 `plugins/MonthPass/data.mv.db` |
| MySQL | 需填入連線資訊，建議生產環境使用 |

切換資料庫類型後需重啟伺服器，資料不會自動遷移。

---

## 相依插件

| 插件 | 類型 | 用途 |
|------|------|------|
| Vault | 軟相依 | 金錢貨幣購買月卡 |
| PlayerPoints | 軟相依 | 積分購買月卡（透過 Reflection 整合，無需特定版本） |
| PlaceholderAPI | 軟相依 | Placeholder 支援 |

---

## 建置（開發者）

```bash
# 需要 Java 21、Maven
mvn clean package
# 產出：target/monthpass.jar
```

---

## License

MIT © wilber
