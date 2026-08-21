package com.dalict.joinsuite;

import com.dalict.joinsuite.command.MuteCommand;
import com.dalict.joinsuite.hologram.HologramConfig;
import com.dalict.joinsuite.hologram.HologramManager;
import com.dalict.joinsuite.hook.JoinSuiteExpansion;
import com.dalict.joinsuite.listener.PlayerJoinLeaveListener;
import com.dalict.joinsuite.storage.MuteStore;
import com.dalict.joinsuite.util.ConfigUpdater;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class JoinSuite extends JavaPlugin {

    /** 配置结构版本（结构变更时递增；跳过含 4 的版本号） */
    public static final int CONFIG_VERSION = 2;

    private FileConfiguration messagesConfig;
    private File messagesFile;

    // 分组配置
    private File groupsFile;
    private FileConfiguration groupsConfig;
    private boolean enableDefault;
    private boolean enableGroups;
    private boolean newbieEnabled;
    private SoundConfig newbieJoinSound;
    private HologramConfig newbieJoinHologram;
    private String newbieJoinMessage;
    private AnnounceConfig newbieJoinAnnounce;
    private SoundConfig defaultJoinSound;
    private SoundConfig defaultLeaveSound;
    private HologramConfig defaultJoinHologram;
    private HologramConfig defaultLeaveHologram;
    private String defaultJoinMessage;
    private String defaultLeaveMessage;
    private AnnounceConfig defaultJoinAnnounce;
    private List<Group> groups = new ArrayList<>();

    // 功能模块（音效/悬浮/消息/加入公告）：开关 + 独立权限节点
    private ModuleConfig soundModule = new ModuleConfig(true, "joinsuite.module.sound");
    private ModuleConfig hologramModule = new ModuleConfig(true, "joinsuite.module.hologram");
    private ModuleConfig messageModule = new ModuleConfig(false, "joinsuite.module.message");
    private ModuleConfig announceModule = new ModuleConfig(false, "joinsuite.module.announce");
    private SimpleDateFormat timeFormat;

    private HologramManager hologramManager;
    private MuteStore muteStore;

    @Override
    public void onEnable() {
        ConfigUpdater.update(this, "config.yml", CONFIG_VERSION);
        saveDefaultConfig();
        loadFeatureFlags();
        loadMessages();
        loadGroups();
        registerPermissions();

        hologramManager = new HologramManager(this);
        getServer().getPluginManager().registerEvents(hologramManager, this);
        hologramManager.cleanupLoadedChunks();

        muteStore = new MuteStore(this);
        muteStore.init();

        getCommand("joinsuite").setExecutor(new MuteCommand(this));
        getCommand("joinsuite").setTabCompleter(new MuteCommand(this));

        getServer().getPluginManager().registerEvents(new PlayerJoinLeaveListener(this), this);

        // PlaceholderAPI 软依赖：注册自身占位符 + 文本占位符解析
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new JoinSuiteExpansion(this).register();
            getLogger().info("PlaceholderAPI detected: PAPI placeholders enabled in texts, %joinsuite_*% placeholders registered.");
        }

        getLogger().info("JoinSuite 2.8.2 enabled!");
    }

    @Override
    public void onDisable() {
        if (hologramManager != null) hologramManager.removeAllActive();
        if (muteStore != null) muteStore.close();
        getLogger().info("JoinSuite disabled!");
    }

    public void reloadPlugin() {
        saveDefaultConfig();
        reloadConfig();
        loadFeatureFlags();
        loadMessages();
        loadGroups();
        registerPermissions();
        hologramManager.removeAllActive();
        getLogger().info("Configuration and messages reloaded.");
    }

    private void loadFeatureFlags() {
        String pattern = getConfig().getString("time-format", "HH:mm");
        try {
            timeFormat = new SimpleDateFormat(pattern);
        } catch (Exception e) {
            timeFormat = new SimpleDateFormat("HH:mm");
        }
    }

    // ---------- 语言文件加载 ----------
    private void loadMessages() {
        String language = getConfig().getString("language", "zh_CN");
        File langDir = new File(getDataFolder(), "lang");
        if (!langDir.exists()) langDir.mkdirs();

        messagesFile = new File(langDir, language + ".yml");

        if (!messagesFile.exists()) {
            String resPath = "lang/" + language + ".yml";
            if (getResource(resPath) != null) {
                saveResource(resPath, false);
            }
        }

        if (!messagesFile.exists()) {
            getLogger().warning("Language file " + language + ".yml not found, falling back to zh_CN");
            messagesFile = new File(langDir, "zh_CN.yml");
            if (!messagesFile.exists()) {
                saveResource("lang/zh_CN.yml", false);
            }
        }

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        ConfigUpdater.update(this, "lang/" + messagesFile.getName(), CONFIG_VERSION);
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile); // 重读补全后的文件
    }

    // ---------- 分组加载 ----------
    private void loadGroups() {
        if (groupsFile == null) {
            groupsFile = new File(getDataFolder(), "groups.yml");
        }
        ConfigUpdater.update(this, "groups.yml", CONFIG_VERSION);
        if (!groupsFile.exists()) {
            saveResource("groups.yml", false);
        }
        groupsConfig = YamlConfiguration.loadConfiguration(groupsFile);

        // 功能模块：开关 + 独立权限节点（groups.yml 最顶部）
        soundModule = loadModule("sound", "joinsuite.module.sound", true);
        hologramModule = loadModule("hologram", "joinsuite.module.hologram", true);
        messageModule = loadModule("message", "joinsuite.module.message", false);
        announceModule = loadModule("announce", "joinsuite.module.announce", false);

        enableDefault = groupsConfig.getBoolean("enable-default", true);
        enableGroups = groupsConfig.getBoolean("enable-groups", false);

        ConfigurationSection newbieSection = groupsConfig.getConfigurationSection("newbie");
        if (newbieSection != null) {
            newbieEnabled = newbieSection.getBoolean("enabled", false);
            newbieJoinSound = loadSoundConfig(newbieSection, "join-sound");
            newbieJoinHologram = loadHologramConfig(newbieSection, "join-hologram");
            newbieJoinMessage = newbieSection.getString("join-message");
            newbieJoinAnnounce = loadAnnounceConfig(newbieSection, "join-announce");
        } else {
            newbieEnabled = false;
            newbieJoinSound = null;
            newbieJoinHologram = null;
            newbieJoinMessage = null;
            newbieJoinAnnounce = null;
        }

        ConfigurationSection defaultSection = groupsConfig.getConfigurationSection("default");
        if (defaultSection != null) {
            defaultJoinSound = loadSoundConfig(defaultSection, "join-sound");
            defaultLeaveSound = loadSoundConfig(defaultSection, "leave-sound");
            defaultJoinHologram = loadHologramConfig(defaultSection, "join-hologram");
            defaultLeaveHologram = loadHologramConfig(defaultSection, "leave-hologram");
            defaultJoinMessage = defaultSection.getString("join-message");
            defaultLeaveMessage = defaultSection.getString("leave-message");
            defaultJoinAnnounce = loadAnnounceConfig(defaultSection, "join-announce");
        } else {
            defaultJoinSound = null;
            defaultLeaveSound = null;
            defaultJoinHologram = null;
            defaultLeaveHologram = null;
            defaultJoinMessage = null;
            defaultLeaveMessage = null;
            defaultJoinAnnounce = null;
        }

        groups.clear();
        ConfigurationSection groupsSection = groupsConfig.getConfigurationSection("groups");
        if (groupsSection != null) {
            for (String key : groupsSection.getKeys(false)) {
                ConfigurationSection groupSection = groupsSection.getConfigurationSection(key);
                if (groupSection == null) continue;

                String permission = groupSection.getString("permission");
                if (permission == null || permission.isEmpty()) continue;

                groups.add(new Group(
                        permission,
                        loadSoundConfig(groupSection, "join-sound"),
                        loadSoundConfig(groupSection, "leave-sound"),
                        loadHologramConfig(groupSection, "join-hologram"),
                        loadHologramConfig(groupSection, "leave-hologram"),
                        groupSection.getString("join-message"),
                        groupSection.getString("leave-message"),
                        loadAnnounceConfig(groupSection, "join-announce")));
            }
        }
    }

    private ModuleConfig loadModule(String name, String defaultPermission, boolean defaultEnabled) {
        ConfigurationSection section = groupsConfig.getConfigurationSection("modules." + name);
        if (section == null) return new ModuleConfig(defaultEnabled, defaultPermission);
        return new ModuleConfig(
                section.getBoolean("enabled", defaultEnabled),
                section.getString("permission", defaultPermission));
    }

    /**
     * 把模块权限节点和分组权限节点注册进 Bukkit 权限注册表。
     * 注册后 LuckPerms 等权限插件可以对这些节点进行 Tab 补全。
     */
    private void registerPermissions() {
        registerPermission(soundModule, "JoinSuite 音效模块（默认所有人生效，可在权限插件中否定以关闭）", true);
        registerPermission(hologramModule, "JoinSuite 悬浮文字模块（默认所有人生效，可在权限插件中否定以关闭）", true);
        registerPermission(messageModule, "JoinSuite 消息模块（默认所有人生效，可在权限插件中否定以关闭）", true);
        registerPermission(announceModule, "JoinSuite 加入公告模块（默认所有人生效，可在权限插件中否定以关闭）", true);
        for (Group group : groups) {
            registerPermission(new ModuleConfig(true, group.permission),
                    "JoinSuite 分组：" + group.permission + "（需授予后生效）", false);
        }
    }

    private void registerPermission(ModuleConfig module, String description, boolean defaultGranted) {
        String node = module.permission;
        if (node == null || node.isEmpty()) return;
        if (Bukkit.getPluginManager().getPermission(node) != null) return;
        Bukkit.getPluginManager().addPermission(new Permission(node, description,
                defaultGranted ? PermissionDefault.TRUE : PermissionDefault.FALSE));
    }

    private SoundConfig loadSoundConfig(ConfigurationSection section, String path) {
        ConfigurationSection soundSection = section.getConfigurationSection(path);
        if (soundSection == null) return null;
        String name = soundSection.getString("sound");
        float volume = (float) soundSection.getDouble("volume", 1.0);
        float pitch = (float) soundSection.getDouble("pitch", 1.0);
        return new SoundConfig(name, volume, pitch);
    }

    private HologramConfig loadHologramConfig(ConfigurationSection section, String path) {
        ConfigurationSection holoSection = section.getConfigurationSection(path);
        if (holoSection == null || !holoSection.getBoolean("enabled", false)) return null;

        int defaultDuration = holoSection.getInt("duration", 15);
        List<HologramConfig.HologramLine> lines = new ArrayList<>();
        for (Map<?, ?> raw : holoSection.getMapList("lines")) {
            Object text = raw.get("text");
            Object height = raw.get("height");
            Object duration = raw.get("duration");
            if (text == null) continue;
            double h = height instanceof Number ? ((Number) height).doubleValue() : 2.0;
            int dur = duration instanceof Number ? ((Number) duration).intValue() : defaultDuration;
            lines.add(new HologramConfig.HologramLine(String.valueOf(text), h, dur));
        }
        return new HologramConfig(defaultDuration, lines, holoSection.getBoolean("background", true));
    }

    /** 加载加入公告配置（join-announce 段） */
    private AnnounceConfig loadAnnounceConfig(ConfigurationSection section, String path) {
        ConfigurationSection ann = section.getConfigurationSection(path);
        if (ann == null || !ann.getBoolean("enabled", true)) return null;

        String text = ann.getString("text", "");
        if (text.isEmpty()) return null;
        return new AnnounceConfig(
                ann.getString("mode", "chat").toLowerCase(),
                text,
                ann.getInt("fade-in", 10),
                ann.getInt("stay", 60),
                ann.getInt("fade-out", 10));
    }

    // ---------- Getters ----------
    public boolean isNewbieEnabled() { return newbieEnabled; }
    public SoundConfig getNewbieJoinSound() { return newbieJoinSound; }
    public HologramConfig getNewbieJoinHologram() { return newbieJoinHologram; }
    public String getNewbieJoinMessage() { return newbieJoinMessage; }
    public AnnounceConfig getNewbieJoinAnnounce() { return newbieJoinAnnounce; }

    public boolean isDefaultEnabled() { return enableDefault; }
    public SoundConfig getDefaultJoinSound() { return defaultJoinSound; }
    public SoundConfig getDefaultLeaveSound() { return defaultLeaveSound; }
    public HologramConfig getDefaultJoinHologram() { return defaultJoinHologram; }
    public HologramConfig getDefaultLeaveHologram() { return defaultLeaveHologram; }
    public String getDefaultJoinMessage() { return defaultJoinMessage; }
    public String getDefaultLeaveMessage() { return defaultLeaveMessage; }
    public AnnounceConfig getDefaultJoinAnnounce() { return defaultJoinAnnounce; }

    public boolean isGroupsEnabled() { return enableGroups; }

    public ModuleConfig getSoundModule() { return soundModule; }
    public ModuleConfig getHologramModule() { return hologramModule; }
    public ModuleConfig getMessageModule() { return messageModule; }
    public ModuleConfig getAnnounceModule() { return announceModule; }

    /** 模块对该玩家是否生效：模块开启 + （权限为空 或 玩家拥有权限） */
    public boolean isModuleActive(Player player, ModuleConfig module) {
        if (module == null || !module.enabled) return false;
        return module.permission == null || module.permission.isEmpty()
                || player.hasPermission(module.permission);
    }

    public String formatTime() { return timeFormat.format(new Date()); }

    public Group getGroupForPlayer(Player player) {
        if (!enableGroups) return null;
        for (Group group : groups) {
            if (player.hasPermission(group.permission)) {
                return group;
            }
        }
        return null;
    }

    public HologramManager getHologramManager() { return hologramManager; }
    public MuteStore getMuteStore() { return muteStore; }

    // ---------- 消息获取 ----------
    public String getMessage(String key) {
        String raw = messagesConfig.getString(key);
        if (raw == null) {
            getLogger().warning("Missing message key: " + key);
            return org.bukkit.ChatColor.translateAlternateColorCodes('&', "&c消息配置异常，请联系管理员");
        }
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', raw);
    }

    // ---------- 内部类 ----------
    /** 功能模块配置：开关 + 权限节点（节点为空表示无需权限） */
    public static class ModuleConfig {
        public final boolean enabled;
        public final String permission;

        public ModuleConfig(boolean enabled, String permission) {
            this.enabled = enabled;
            this.permission = permission == null ? "" : permission;
        }
    }

    public static class Group {
        public final String permission;
        public final SoundConfig joinSound;
        public final SoundConfig leaveSound;
        public final HologramConfig joinHologram;
        public final HologramConfig leaveHologram;
        public final String joinMessage;
        public final String leaveMessage;
        public final AnnounceConfig joinAnnounce;

        public Group(String permission, SoundConfig joinSound, SoundConfig leaveSound,
                     HologramConfig joinHologram, HologramConfig leaveHologram,
                     String joinMessage, String leaveMessage, AnnounceConfig joinAnnounce) {
            this.permission = permission;
            this.joinSound = joinSound;
            this.leaveSound = leaveSound;
            this.joinHologram = joinHologram;
            this.leaveHologram = leaveHologram;
            this.joinMessage = joinMessage;
            this.leaveMessage = leaveMessage;
            this.joinAnnounce = joinAnnounce;
        }
    }

    /** 加入公告配置：chat=聊天栏 / title=大标题 / subtitle=小标题 / actionbar=动作栏 */
    public static class AnnounceConfig {
        public final String mode;
        public final String text;
        public final int fadeIn;  // 仅 title/subtitle 生效（tick）
        public final int stay;
        public final int fadeOut;

        public AnnounceConfig(String mode, String text, int fadeIn, int stay, int fadeOut) {
            this.mode = mode;
            this.text = text;
            this.fadeIn = fadeIn;
            this.stay = stay;
            this.fadeOut = fadeOut;
        }
    }

    public static class SoundConfig {
        public final Sound sound;
        public final float volume;
        public final float pitch;

        public SoundConfig(String soundName, float volume, float pitch) {
            Sound s = null;
            if (soundName != null && !soundName.isEmpty()) {
                try {
                    s = Sound.valueOf(soundName.toUpperCase());
                } catch (IllegalArgumentException ignored) {}
            }
            this.sound = s;
            this.volume = volume;
            this.pitch = pitch;
        }
    }
}
