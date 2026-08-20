package com.dalict.joinsuite.hologram;

import java.util.List;

/** 一组悬浮文字配置：多行（每行独立持续时长）+ 兜底时长 + 背景开关 */
public class HologramConfig {

    public static class HologramLine {
        public final String text;
        public final double height;
        public final int duration; // 秒；<=0 时使用 HologramConfig.duration 兜底

        public HologramLine(String text, double height, int duration) {
            this.text = text;
            this.height = height;
            this.duration = duration;
        }
    }

    public final int duration; // 兜底显示秒数（某行未配 duration 时使用）
    public final List<HologramLine> lines;
    public final boolean background; // 是否显示默认半透明背景

    public HologramConfig(int duration, List<HologramLine> lines, boolean background) {
        this.duration = duration;
        this.lines = lines;
        this.background = background;
    }

    public boolean isValid() {
        return lines != null && !lines.isEmpty();
    }

    /** 该行实际生效的持续秒数（行值 > 顶层值 > 15 兜底） */
    public int durationOf(HologramLine line) {
        if (line.duration > 0) return line.duration;
        return duration > 0 ? duration : 15;
    }
}
