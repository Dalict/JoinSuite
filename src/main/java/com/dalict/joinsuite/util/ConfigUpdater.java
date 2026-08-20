package com.dalict.joinsuite.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * 配置文件自动更新：
 * 文件内 config-version 低于当前版本时，先把旧文件备份为 *.old，
 * 再以 jar 内默认配置补全缺失的键（用户已有值一律保留），写回并更新版本号。
 * 注意：已废弃的旧键不会删除（无害）；新补的键不带注释（Bukkit 限制）。
 */
public final class ConfigUpdater {

    private ConfigUpdater() {}

    /**
     * @param relativePath 相对插件目录的路径，如 "config.yml" 或 "lang/zh_CN.yml"
     * @param currentVersion 当前配置结构版本
     */
    public static void update(JavaPlugin plugin, String relativePath, int currentVersion) {
        File file = new File(plugin.getDataFolder(), relativePath);
        if (!file.exists()) return; // 首次安装，稍后由 saveResource 生成全新文件

        FileConfiguration user = YamlConfiguration.loadConfiguration(file);
        int fileVersion = user.getInt("config-version", 0);
        if (fileVersion >= currentVersion) return;

        // 备份旧文件，出问题可随时回滚
        File backup = new File(plugin.getDataFolder(), relativePath + ".old");
        try {
            if (backup.getParentFile() != null) backup.getParentFile().mkdirs();
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to back up " + relativePath + ": " + e.getMessage());
        }

        // 读取 jar 内打包的最新默认配置
        InputStream res = plugin.getResource(relativePath);
        if (res == null) return;
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(res, StandardCharsets.UTF_8));

        // 只补缺失的键，用户的值永远优先
        int added = 0;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) continue;
            if (!user.contains(key)) {
                user.set(key, defaults.get(key));
                added++;
            }
        }
        user.set("config-version", currentVersion);

        try {
            user.save(file);
            plugin.getLogger().info(relativePath + " auto-updated from v" + fileVersion + " to v"
                    + currentVersion + " (" + added + " missing keys filled, existing values kept, backup: "
                    + relativePath + ".old)");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to write updated " + relativePath + ": " + e.getMessage());
        }
    }
}
