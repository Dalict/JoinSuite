package com.dalict.joinsuite.listener;

import com.dalict.joinsuite.JoinSuite;
import com.dalict.joinsuite.JoinSuite.AnnounceConfig;
import com.dalict.joinsuite.JoinSuite.Group;
import com.dalict.joinsuite.JoinSuite.SoundConfig;
import com.dalict.joinsuite.hologram.HologramConfig;
import com.dalict.joinsuite.util.ColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;

public class PlayerJoinLeaveListener implements Listener {

    private final JoinSuite plugin;
    private final boolean papiEnabled;
    private long lastJoinSound = 0;
    private long lastLeaveSound = 0;
    private long lastNewbieSound = 0;

    public PlayerJoinLeaveListener(JoinSuite plugin) {
        this.plugin = plugin;
        this.papiEnabled = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 上线时同步屏蔽记录（补全 uuid / 同步改名）
        plugin.getMuteStore().syncPlayer(player.getUniqueId().toString(), player.getName());

        boolean opBypass = plugin.getConfig().getBoolean("op-bypass", false) && player.isOp();
        boolean blocked = plugin.getMuteStore().isMuted(player.getUniqueId().toString(), player.getName());
        boolean msgModule = !opBypass && !blocked && plugin.isModuleActive(player, plugin.getMessageModule());

        if (opBypass) {
            if (msgModule) event.setJoinMessage(null);
            debug("OP bypass: " + player.getName() + " joined");
            return;
        }

        if (blocked) {
            debug("Blocked (skip): " + player.getName() + " joined");
            return;
        }

        // 各模块独立判定（开关 + 权限）
        boolean soundModule = plugin.isModuleActive(player, plugin.getSoundModule());
        boolean holoModule = plugin.isModuleActive(player, plugin.getHologramModule());

        // 新玩家 > 权限组 > 默认组，各功能独立回落：某层没配置的功能取下一层
        boolean isNewbie = !player.hasPlayedBefore() && plugin.isNewbieEnabled();
        Group group = plugin.isGroupsEnabled() ? plugin.getGroupForPlayer(player) : null;

        SoundConfig sound = resolve(isNewbie, plugin.getNewbieJoinSound(),
                group != null ? group.joinSound : null,
                plugin.isDefaultEnabled() ? plugin.getDefaultJoinSound() : null);
        HologramConfig hologram = resolve(isNewbie, plugin.getNewbieJoinHologram(),
                group != null ? group.joinHologram : null,
                plugin.isDefaultEnabled() ? plugin.getDefaultJoinHologram() : null);
        String message = resolve(isNewbie, plugin.getNewbieJoinMessage(),
                group != null ? group.joinMessage : null,
                plugin.isDefaultEnabled() ? plugin.getDefaultJoinMessage() : null);
        AnnounceConfig announce = resolve(isNewbie, plugin.getNewbieJoinAnnounce(),
                group != null ? group.joinAnnounce : null,
                plugin.isDefaultEnabled() ? plugin.getDefaultJoinAnnounce() : null);

        // 消息
        if (msgModule) {
            event.setJoinMessage(null);
            if (message != null && !message.isEmpty()) {
                broadcast(message, player);
            }
        }

        // 悬浮文字（不含冷却，每个玩家各自位置；使用玩家精确坐标，不吸附方块）
        if (holoModule && hologram != null) {
            plugin.getHologramManager().spawn(player, cloneHologram(hologram, player));
        }

        // 音效（保留冷却）
        if (soundModule && sound != null) {
            playSoundWithCooldown(sound, isNewbie, player);
        }

        // 加入公告（chat / title / subtitle / actionbar），仅加入玩家本人可见
        boolean announceModule = plugin.isModuleActive(player, plugin.getAnnounceModule());
        if (announceModule && announce != null) {
            sendAnnounce(announce, player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        boolean opBypass = plugin.getConfig().getBoolean("op-bypass", false) && player.isOp();
        boolean blocked = plugin.getMuteStore().isMuted(player.getUniqueId().toString(), player.getName());
        boolean msgModule = !opBypass && !blocked && plugin.isModuleActive(player, plugin.getMessageModule());

        if (opBypass) {
            if (msgModule) event.setQuitMessage(null);
            debug("OP bypass: " + player.getName() + " left");
            return;
        }

        if (blocked) {
            debug("Blocked (skip): " + player.getName() + " left");
            return;
        }

        boolean soundModule = plugin.isModuleActive(player, plugin.getSoundModule());
        boolean holoModule = plugin.isModuleActive(player, plugin.getHologramModule());

        Group group = plugin.isGroupsEnabled() ? plugin.getGroupForPlayer(player) : null;

        SoundConfig sound = resolve(false, null,
                group != null ? group.leaveSound : null,
                plugin.isDefaultEnabled() ? plugin.getDefaultLeaveSound() : null);
        HologramConfig hologram = resolve(false, null,
                group != null ? group.leaveHologram : null,
                plugin.isDefaultEnabled() ? plugin.getDefaultLeaveHologram() : null);
        String message = resolve(false, null,
                group != null ? group.leaveMessage : null,
                plugin.isDefaultEnabled() ? plugin.getDefaultLeaveMessage() : null);

        if (msgModule) {
            event.setQuitMessage(null);
            if (message != null && !message.isEmpty()) {
                broadcast(message, player);
            }
        }

        if (holoModule && hologram != null) {
            plugin.getHologramManager().spawn(player, cloneHologram(hologram, player));
        }

        if (soundModule && sound != null) {
            int cooldown = plugin.getConfig().getInt("cooldown.leave", 10);
            long now = System.currentTimeMillis();
            if (cooldown > 0 && now - lastLeaveSound < cooldown * 1000L) {
                lastLeaveSound = now;
                debug("Leave cooldown: " + player.getName() + " (timer reset)");
            } else {
                lastLeaveSound = now;
                playGlobalSound(sound);
            }
        }
    }

    /** 三层取值：新玩家层（仅新玩家且已配置）> 权限组层 > 默认组层；某层为 null 取下一层 */
    private <T> T resolve(boolean isNewbie, T newbieValue, T groupValue, T defaultValue) {
        if (isNewbie && newbieValue != null) return newbieValue;
        if (groupValue != null) return groupValue;
        return defaultValue;
    }

    /** 音效冷却（加入事件，含新玩家独立冷却） */
    private boolean playSoundWithCooldown(SoundConfig sound, boolean isNewbie, Player player) {
        long now = System.currentTimeMillis();
        if (isNewbie) {
            int cooldown = plugin.getConfig().getInt("cooldown.newbie", 0);
            if (cooldown > 0 && now - lastNewbieSound < cooldown * 1000L) {
                lastNewbieSound = now;
                debug("Newbie cooldown: " + player.getName() + " (timer reset)");
                return false;
            }
            lastNewbieSound = now;
        } else {
            int cooldown = plugin.getConfig().getInt("cooldown.join", 10);
            if (cooldown > 0 && now - lastJoinSound < cooldown * 1000L) {
                lastJoinSound = now;
                debug("Join cooldown: " + player.getName() + " (timer reset)");
                return false;
            }
            lastJoinSound = now;
        }
        debug("Playing sound: " + player.getName() +
                " | sound: " + (sound.sound != null ? sound.sound.name() : "null") +
                " | volume: " + sound.volume + " | pitch: " + sound.pitch);
        playGlobalSound(sound);
        return true;
    }

    /** 加入公告：按 mode 列表逐个发送（chat=聊天栏 / title=大标题 / subtitle=小标题 / actionbar=动作栏），仅发给加入玩家本人。
     *  Paper 系走 Adventure；纯 Spigot 走旧式 sendTitle / BungeeCord 动作栏 API。 */
    @SuppressWarnings("deprecation")
    private void sendAnnounce(AnnounceConfig announce, Player player) {
        String raw = formatRaw(announce.text, player);
        boolean paper = plugin.isPaperLike();
        for (String mode : announce.modes) {
            switch (mode) {
                case "title":
                case "subtitle": {
                    if (paper) {
                        Component text = ColorUtil.component(raw);
                        Component main = mode.equals("title") ? text : Component.empty();
                        Component sub = mode.equals("subtitle") ? text : Component.empty();
                        Title title = Title.title(main, sub, Title.Times.times(
                                Duration.ofMillis(announce.fadeIn * 50L),
                                Duration.ofMillis(announce.stay * 50L),
                                Duration.ofMillis(announce.fadeOut * 50L)));
                        player.showTitle(title);
                    } else {
                        String text = ColorUtil.displayText(raw);
                        player.sendTitle(
                                mode.equals("title") ? text : "",
                                mode.equals("subtitle") ? text : "",
                                announce.fadeIn, announce.stay, announce.fadeOut);
                    }
                    break;
                }
                case "actionbar":
                    if (paper) {
                        player.sendActionBar(ColorUtil.component(raw));
                    } else {
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                                TextComponent.fromLegacyText(ColorUtil.displayText(raw)));
                    }
                    break;
                default: // chat
                    if (paper) {
                        player.sendMessage(ColorUtil.component(raw));
                    } else {
                        player.sendMessage(ColorUtil.displayText(raw));
                    }
                    break;
            }
        }
        debug("Announce (" + String.join("+", announce.modes) + ") sent to " + player.getName());
    }

    private HologramConfig cloneHologram(HologramConfig source, Player player) {
        java.util.List<HologramConfig.HologramLine> lines = new java.util.ArrayList<>();
        for (HologramConfig.HologramLine line : source.lines) {
            lines.add(new HologramConfig.HologramLine(formatRaw(line.text, player), line.height, line.duration));
        }
        return new HologramConfig(source.duration, lines, source.background);
    }

    /** 占位符替换（不处理颜色）：内置占位符 + PlaceholderAPI（若安装） */
    private String formatRaw(String text, Player player) {
        String result = text.replace("{player}", player.getName())
                .replace("{world}", player.getWorld().getName())
                .replace("{time}", plugin.formatTime());
        if (papiEnabled) {
            result = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, result);
        }
        return result;
    }

    /** 占位符替换 + 真彩色组件（仅 Paper 系使用） */
    private Component format(String text, Player player) {
        return ColorUtil.component(formatRaw(text, player));
    }

    /** 全服广播：Paper 系走 Adventure 组件；纯 Spigot 走 legacy 字符串（§x 真彩色同样生效） */
    private void broadcast(String text, Player player) {
        if (plugin.isPaperLike()) {
            Component component = format(text, player);
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                online.sendMessage(component);
            }
            Bukkit.getConsoleSender().sendMessage(component);
        } else {
            String legacy = ColorUtil.displayText(formatRaw(text, player));
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                online.sendMessage(legacy);
            }
            Bukkit.getConsoleSender().sendMessage(legacy);
        }
    }

    private void playGlobalSound(SoundConfig config) {
        if (config == null || config.sound == null) return;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            player.playSound(player.getLocation(), config.sound, config.volume, config.pitch);
        }
    }

    private void debug(String msg) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[Debug] " + msg);
        }
    }
}
