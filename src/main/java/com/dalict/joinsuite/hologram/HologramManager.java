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
    /** 每个玩家当前活跃的悬浮批次 */
    private final Map<UUID, List<TextDisplay>> activeByPlayer = new HashMap<>();

    public HologramManager(JoinSuite plugin) {
        this.plugin = plugin;
    }

    /** 为玩家生成一组悬浮文字（自动替换该玩家仍在显示的旧悬浮），text 已由调用方完成占位符替换。
     *  每行按自己的 duration 独立计时消失。 */
    public void spawn(Player player, HologramConfig config) {
        if (config == null || !config.isValid()) return;

        // 立即移除该玩家旧的悬浮（防止堆叠，以及加入/离开悬浮同屏）
        removePlayerHologram(player.getUniqueId());

        UUID uuid = player.getUniqueId();
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
        activeByPlayer.put(uuid, displays);
    }

    /** 某一行到期：从该玩家的批次中移除单个实体（被新悬浮替换过则实体已删，直接跳过） */
    private void expireOne(UUID uuid, TextDisplay display) {
        List<TextDisplay> list = activeByPlayer.get(uuid);
        if (list != null) {
            list.remove(display);
            if (list.isEmpty()) activeByPlayer.remove(uuid);
        }
        if (!display.isDead()) display.remove();
    }

    /** 立即移除某玩家当前的全部悬浮 */
    public void removePlayerHologram(UUID uuid) {
        List<TextDisplay> displays = activeByPlayer.remove(uuid);
        if (displays != null) removeEntities(displays);
    }

    private void removeEntities(List<TextDisplay> displays) {
        for (TextDisplay display : displays) {
            if (!display.isDead()) display.remove();
        }
    }

    /** 移除所有活跃悬浮（重载/禁用时调用） */
    public void removeAllActive() {
        for (List<TextDisplay> displays : activeByPlayer.values()) {
            removeEntities(displays);
        }
        activeByPlayer.clear();
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
