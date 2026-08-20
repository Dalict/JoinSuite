package com.dalict.joinsuite.command;

import com.dalict.joinsuite.JoinSuite;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MuteCommand implements CommandExecutor, TabCompleter {

    private final JoinSuite plugin;

    public MuteCommand(JoinSuite plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.getMessage("command-usage"));
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload":
                if (!sender.hasPermission("joinsuite.reload")) {
                    sender.sendMessage(plugin.getMessage("no-permission"));
                    return true;
                }
                plugin.reloadPlugin();
                sender.sendMessage(plugin.getMessage("reload-success"));
                break;
            case "list":
                if (!sender.hasPermission("joinsuite.list")) {
                    sender.sendMessage(plugin.getMessage("no-permission"));
                    return true;
                }
                List<String> muted = plugin.getMuteStore().mutedNames();
                if (muted.isEmpty()) {
                    sender.sendMessage(plugin.getMessage("list-empty"));
                } else {
                    sender.sendMessage(plugin.getMessage("list-header"));
                    sender.sendMessage(plugin.getMessage("list-format")
                            .replace("{players}", String.join(plugin.getMessage("list-separator"), muted)));
                }
                break;
            case "mute":
            case "unmute":
                if (!sender.hasPermission("joinsuite.mute")) {
                    sender.sendMessage(plugin.getMessage("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.getMessage("command-usage"));
                    return true;
                }
                String targetName = args[1];
                if (targetName.length() < 1 || targetName.length() > 16
                        || !targetName.matches("[a-zA-Z0-9_]+")) {
                    sender.sendMessage(plugin.getMessage("invalid-name"));
                    return true;
                }
                Player online = Bukkit.getPlayerExact(targetName);

                if (sub.equals("mute")) {
                    // 在线玩家记录真实 uuid；离线玩家仅按名字
                    if (plugin.getMuteStore().mute(targetName,
                            online != null ? online.getUniqueId().toString() : null)) {
                        sender.sendMessage(plugin.getMessage("mute-success").replace("{player}", targetName));
                    } else {
                        sender.sendMessage(plugin.getMessage("mute-already").replace("{player}", targetName));
                    }
                } else {
                    if (plugin.getMuteStore().unmute(targetName)) {
                        sender.sendMessage(plugin.getMessage("unmute-success").replace("{player}", targetName));
                    } else {
                        sender.sendMessage(plugin.getMessage("unmute-not-found").replace("{player}", targetName));
                    }
                }
                break;
            default:
                sender.sendMessage(plugin.getMessage("command-usage"));
                break;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String prefix = args[0].toLowerCase();
            if (sender.hasPermission("joinsuite.reload") && "reload".startsWith(prefix)) {
                completions.add("reload");
            }
            if (sender.hasPermission("joinsuite.list") && "list".startsWith(prefix)) {
                completions.add("list");
            }
            if (sender.hasPermission("joinsuite.mute")) {
                if ("mute".startsWith(prefix)) completions.add("mute");
                if ("unmute".startsWith(prefix)) completions.add("unmute");
            }
            return completions;
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("mute") || args[0].equalsIgnoreCase("unmute"))) {
            String prefix = args[1].toLowerCase();
            if (args[0].equalsIgnoreCase("unmute") && sender.hasPermission("joinsuite.mute")) {
                // unmute 补全黑名单（原始大小写）
                return plugin.getMuteStore().mutedNames().stream()
                        .filter(n -> n.toLowerCase().startsWith(prefix))
                        .collect(Collectors.toList());
            }
            // mute 补全在线玩家
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
