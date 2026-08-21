package com.dalict.joinsuite.hologram;

import com.dalict.joinsuite.JoinSuite;
import com.dalict.joinsuite.util.ColorUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 悬浮文字管理（1.19.4+）：TextDisplay 逐行生成，Billboard.CENTER 让每个
 * 玩家视角里文字都朝向自己，到期移除。
 * 每个玩家同一时间只保留一组悬浮：生成新悬浮前立即移除该玩家旧悬浮，
 * 因此不会堆叠，加入/离开悬浮也绝不会同屏。
 * 防持久化：setPersistent(false) + scoreboard 标签 + 区块加载/启动时清理残留
 * （兼容清理 2.0.0 旧版盔甲架残留）。
 * 本类所有方法均只在主线程调用（事件与调度任务），无需加锁。
 */
public class HologramManager implements Listener {

    public static final String HOLO_TAG = "joinsuite_hologram";

    private final JoinSuite plugin;
    /** 每个玩家两条独立轨道的活跃悬浮：
     *  newbie = 新玩家首次加入悬浮（独立存续，不被普通悬浮顶掉）；
     *  normal = 普通加入/离开悬浮（同轨道内互相替换，绝不堆叠/同屏）。 */
    private final Map<UUID, List<TextDisplay>> newbieByPlayer = new HashMap<>();
    private final Map<UUID, List<TextDisplay>> normalByPlayer = new HashMap<>();

    public HologramManager(JoinSuite plugin) {
        this.plugin = plugin;
    }

    /** 为玩家生成一组悬浮文字（text 已由调用方完成占位符替换）。
     *  只替换同轨道的旧悬浮：普通加入/离开互相顶掉；新玩家首次加入悬浮与普通悬浮互不影响、可共存。
     *  每行按自己的 duration 独立计时消失。 */
    public void spawn(Player player, HologramConfig config, boolean newbie) {
        if (config == null || !config.isValid()) return;

        UUID uuid = player.getUniqueId();
        Map<UUID, List<TextDisplay>> track = newbie ? newbieByPlayer : normalByPlayer;
        removeTrack(track, uuid);

        List<TextDisplay> displays = new ArrayList<>();
        for (HologramConfig.HologramLine line : config.lines) {
            Location loc = player.getLocation().clone().add(0, line.height, 0);
            TextDisplay display = player.getWorld().spawn(loc, TextDisplay.class, d -> {
                d.setBillboard(Display.Billboard.CENTER); // 始终朝向观察者
                d.setText(ColorUtil.displayText(line.text));
                d.setSeeThrough(false);
                d.setShadowed(false);
                if (config.background) {
                    d.setDefaultBackground(true);
                } else {
                    d.setDefaultBackground(false);
                    d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0)); // 完全透明
                }
                d.setPersistent(false); // 关键：区块卸载保存时丢弃，不写入存档
                d.addScoreboardTag(HOLO_TAG);
            });
            displays.add(display);

            long ticks = config.durationOf(line) * 20L;
            plugin.getServer().getScheduler().runTaskLater(
                    plugin, () -> expireOne(uuid, display), ticks);
        }
        track.put(uuid, displays);
    }

    /** 某一行到期：从两条轨道中移除该实体（被新悬浮替换过的实体已删，直接跳过） */
    private void expireOne(UUID uuid, TextDisplay display) {
        removeFromTrack(newbieByPlayer, uuid, display);
        removeFromTrack(normalByPlayer, uuid, display);
        if (!display.isDead()) display.remove();
    }

    private void removeFromTrack(Map<UUID, List<TextDisplay>> track, UUID uuid, TextDisplay display) {
        List<TextDisplay> list = track.get(uuid);
        if (list == null) return;
        list.remove(display);
        if (list.isEmpty()) track.remove(uuid);
    }

    private void removeTrack(Map<UUID, List<TextDisplay>> track, UUID uuid) {
        List<TextDisplay> displays = track.remove(uuid);
        if (displays != null) removeEntities(displays);
    }

    /** 立即移除某玩家当前的全部悬浮（两条轨道一起） */
    public void removePlayerHologram(UUID uuid) {
        removeTrack(newbieByPlayer, uuid);
        removeTrack(normalByPlayer, uuid);
    }

    private void removeEntities(List<TextDisplay> displays) {
        for (TextDisplay display : displays) {
            if (!display.isDead()) display.remove();
        }
    }

    /** 移除所有活跃悬浮（重载/禁用时调用） */
    public void removeAllActive() {
        for (List<TextDisplay> displays : newbieByPlayer.values()) {
            removeEntities(displays);
        }
        for (List<TextDisplay> displays : normalByPlayer.values()) {
            removeEntities(displays);
        }
        newbieByPlayer.clear();
        normalByPlayer.clear();
    }

    /** 启动时清理已加载区块中的残留（旧版本遗留、异常关闭） */
    public void cleanupLoadedChunks() {
        int removed = 0;
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                removed += purgeChunk(chunk);
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("Removed " + removed + " residual hologram entities.");
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // 新加载的区块如果带有标签残留（理论上 setPersistent(false) 后不会出现），立即清除
        purgeChunk(event.getChunk());
    }

    private int purgeChunk(org.bukkit.Chunk chunk) {
        int removed = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity.getScoreboardTags().contains(HOLO_TAG)
                    && (entity instanceof TextDisplay || entity instanceof org.bukkit.entity.ArmorStand)) {
                entity.remove();
                removed++;
            }
        }
        return removed;
    }
}
