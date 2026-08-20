package com.dalict.joinsuite.storage;

import com.dalict.joinsuite.JoinSuite;
import com.dalict.joinsuite.util.ConfigUpdater;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 静音黑名单存储：支持 SQLite / MySQL（database.yml 配置）。
 * 内存缓存供主线程同步查询，JDBC 读写全部异步。
 * uuid 为主键；按名字静音离线玩家时 uuid 暂存为空串，玩家上线后补全。
 */
public class MuteStore {

    private final JoinSuite plugin;
    private Connection connection;
    private String table;
    private boolean mysql;

    // 缓存：小写名字 -> 记录
    private final Map<String, MuteRecord> byName = new ConcurrentHashMap<>();
    // 未完成的异步写任务数（关服时等待其清零，避免丢写入）
    private final AtomicInteger pendingWrites = new AtomicInteger();

    public static class MuteRecord {
        public final String uuid; // 可能为空串（仅按名字记录）
        public final String name; // 原始大小写

        public MuteRecord(String uuid, String name) {
            this.uuid = uuid == null ? "" : uuid;
            this.name = name;
        }
    }

    public MuteStore(JoinSuite plugin) {
        this.plugin = plugin;
    }

    public void init() {
        File file = new File(plugin.getDataFolder(), "database.yml");
        ConfigUpdater.update(plugin, "database.yml", JoinSuite.CONFIG_VERSION);
        if (!file.exists()) plugin.saveResource("database.yml", false);
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        String type = cfg.getString("type", "sqlite").toLowerCase();
        table = cfg.getString("table-prefix", "joinsuite_") + "muted";

        try {
            if (type.equals("mysql")) {
                mysql = true;
                String host = cfg.getString("mysql.host", "localhost");
                int port = cfg.getInt("mysql.port", 3306);
                String db = cfg.getString("mysql.database", "minecraft");
                String user = cfg.getString("mysql.username", "root");
                String pass = cfg.getString("mysql.password", "");
                boolean ssl = cfg.getBoolean("mysql.use-ssl", false);
                connection = DriverManager.getConnection(
                        "jdbc:mysql://" + host + ":" + port + "/" + db +
                                "?useSSL=" + ssl + "&autoReconnect=true&characterEncoding=utf8",
                        user, pass);
            } else {
                File dbFile = new File(plugin.getDataFolder(), "muted.db");
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            }
            createTable(type);
            loadAll();
            migrateLegacyYml();
        } catch (SQLException e) {
            plugin.getLogger().severe("Database init failed, mute feature unavailable: " + e.getMessage());
            connection = null;
        }
    }

    private void createTable(String type) throws SQLException {
        String uuidType = type.equals("mysql") ? "VARCHAR(36) NOT NULL" : "TEXT NOT NULL";
        String indexName = "idx_" + table + "_uuid";
        try (Statement st = connection.createStatement()) {
            if (type.equals("mysql")) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + table + " (" +
                        "uuid " + uuidType + ", name VARCHAR(16) NOT NULL, PRIMARY KEY (name(16)))");
            } else {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + table + " (" +
                        "uuid " + uuidType + ", name TEXT NOT NULL, PRIMARY KEY (name))");
            }
            // uuid 索引：改名同步时按 uuid 反查。
            // MySQL 不支持 CREATE INDEX IF NOT EXISTS（那是 MariaDB 语法），先查元数据
            if (mysql) {
                if (!indexExists(indexName)) {
                    st.executeUpdate("CREATE INDEX " + indexName + " ON " + table + " (uuid)");
                }
            } else {
                st.executeUpdate("CREATE INDEX IF NOT EXISTS " + indexName + " ON " + table + " (uuid)");
            }
        }
    }

    private boolean indexExists(String indexName) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rs = meta.getIndexInfo(null, null, table, false, false)) {
            while (rs.next()) {
                if (indexName.equalsIgnoreCase(rs.getString("INDEX_NAME"))) return true;
            }
        }
        return false;
    }

    private void loadAll() throws SQLException {
        byName.clear();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT uuid, name FROM " + table)) {
            while (rs.next()) {
                String uuid = rs.getString(1);
                String name = rs.getString(2);
                if (name != null && !name.isEmpty()) {
                    byName.put(name.toLowerCase(), new MuteRecord(uuid, name));
                }
            }
        }
    }

    /** 旧版 muted-players.yml 自动迁移，完成后改名 .bak */
    private void migrateLegacyYml() {
        File legacy = new File(plugin.getDataFolder(), "muted-players.yml");
        if (!legacy.exists()) return;
        FileConfiguration yml = YamlConfiguration.loadConfiguration(legacy);
        List<String> names = yml.getStringList("muted");
        int count = 0;
        for (String raw : names) {
            if (raw == null || raw.isEmpty()) continue;
            String name = raw; // 旧文件只存了小写
            if (byName.put(name.toLowerCase(), new MuteRecord("", name)) == null) {
                insert(new MuteRecord("", name));
                count++;
            }
        }
        saveYmlBackup(legacy);
        plugin.getLogger().info("Migrated " + count + " mute records from legacy muted-players.yml.");
    }

    private void saveYmlBackup(File legacy) {
        File bak = new File(plugin.getDataFolder(), "muted-players.yml.bak");
        try {
            java.nio.file.Files.move(legacy.toPath(), bak.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to rename legacy muted-players.yml: " + e.getMessage());
        }
    }

    // ---------- 异步写操作 ----------

    private void insert(MuteRecord record) {
        async(() -> {
            try {
                // SQLite 主键区分大小写，REPLACE 匹配不到大小写不同的旧行，
                // 先按不区分大小写删除再插入，避免产生重复行
                if (!mysql) {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "DELETE FROM " + table + " WHERE name = ? COLLATE NOCASE")) {
                        ps.setString(1, record.name);
                        ps.executeUpdate();
                    }
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        (mysql ? "REPLACE INTO " : "INSERT OR REPLACE INTO ") + table + " (uuid, name) VALUES (?, ?)")) {
                    ps.setString(1, record.uuid);
                    ps.setString(2, record.name);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to write mute record: " + e.getMessage());
            }
        });
    }

    private void delete(String nameLower) {
        async(() -> {
            // MySQL 默认排序规则本身不区分大小写；SQLite 需要 COLLATE NOCASE
            String sql = mysql ? "DELETE FROM " + table + " WHERE name = ?"
                               : "DELETE FROM " + table + " WHERE name = ? COLLATE NOCASE";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, nameLower);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to delete mute record: " + e.getMessage());
            }
        });
    }

    private void async(Runnable task) {
        if (connection == null) return;
        pendingWrites.incrementAndGet();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                task.run();
            } finally {
                pendingWrites.decrementAndGet();
            }
        });
    }

    // ---------- 同步缓存操作（供主线程调用） ----------

    /** 静音一个玩家（在线玩家带 uuid；离线玩家仅按名字） */
    public boolean mute(String name, String uuid) {
        String key = name.toLowerCase();
        if (byName.containsKey(key)) return false;
        byName.put(key, new MuteRecord(uuid == null ? "" : uuid, name));
        insert(new MuteRecord(uuid == null ? "" : uuid, name));
        return true;
    }

    public boolean unmute(String name) {
        MuteRecord removed = byName.remove(name.toLowerCase());
        if (removed == null) return false;
        delete(name.toLowerCase());
        return true;
    }

    /** 玩家上线时补全 uuid / 同步改名 */
    public void syncPlayer(String uuid, String currentName) {
        String key = currentName.toLowerCase();
        MuteRecord existing = byName.get(key);
        if (existing != null) {
            if (!uuid.equals(existing.uuid)) {
                byName.put(key, new MuteRecord(uuid, currentName));
                insert(new MuteRecord(uuid, currentName));
            }
            return;
        }
        // 改名同步：按 uuid 反查旧名字的记录，迁移到新名字
        for (Map.Entry<String, MuteRecord> entry : byName.entrySet()) {
            if (uuid.equals(entry.getValue().uuid) && !entry.getKey().equals(key)) {
                byName.remove(entry.getKey());
                delete(entry.getKey());
                byName.put(key, new MuteRecord(uuid, currentName));
                insert(new MuteRecord(uuid, currentName));
                plugin.getLogger().info("Mute record renamed: " + entry.getValue().name + " -> " + currentName);
                return;
            }
        }
    }

    /** 是否被静音：优先 uuid，其次名字（不区分大小写） */
    public boolean isMuted(String uuid, String name) {
        if (name != null && byName.containsKey(name.toLowerCase())) return true;
        if (uuid != null) {
            for (MuteRecord r : byName.values()) {
                if (uuid.equals(r.uuid)) return true;
            }
        }
        return false;
    }

    /** 黑名单（原始大小写），供 list 与补全使用 */
    public List<String> mutedNames() {
        return byName.values().stream().map(r -> r.name).sorted().collect(Collectors.toList());
    }

    public void close() {
        if (connection == null) return;
        // 等待未完成的异步写入（最多 3 秒），避免关服时丢屏蔽记录
        long deadline = System.currentTimeMillis() + 3000;
        while (pendingWrites.get() > 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (pendingWrites.get() > 0) {
            plugin.getLogger().warning("Timed out waiting for " + pendingWrites.get() + " pending mute writes, they may be lost.");
        }
        try {
            connection.close();
        } catch (SQLException ignored) {}
    }
}
