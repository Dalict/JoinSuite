package com.dalict.joinsuite.hook;

import com.dalict.joinsuite.JoinSuite;
import com.dalict.joinsuite.JoinSuite.Group;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

/**
 * JoinSuite 的 PlaceholderAPI 扩展。
 * 提供：%joinsuite_group% / %joinsuite_muted% / %joinsuite_time%
 */
public class JoinSuiteExpansion extends PlaceholderExpansion {

    private final JoinSuite plugin;

    public JoinSuiteExpansion(JoinSuite plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "joinsuite";
    }

    @Override
    public String getAuthor() {
        return "Dalict";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /** 重载插件时保持注册，不随 PAPI reload 掉线 */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        switch (params.toLowerCase()) {
            case "group":
                if (player == null) return "";
                Group group = plugin.getGroupForPlayer(player);
                return group != null ? group.permission : "none";
            case "muted":
            case "blocked":
                if (player == null) return "";
                return String.valueOf(plugin.getMuteStore()
                        .isMuted(player.getUniqueId().toString(), player.getName()));
            case "time":
                return plugin.formatTime();
            default:
                return null;
        }
    }
}
