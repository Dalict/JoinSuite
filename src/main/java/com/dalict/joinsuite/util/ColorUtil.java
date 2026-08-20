package com.dalict.joinsuite.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 颜色工具：支持 & 传统代码与 &#RRGGBB 16进制颜色。
 * 聊天/标题/动作栏走 Adventure Component（真 16 进制）；
 * TextDisplay 文本用 §x§R§R§G§G§B§B 序列（服务端原生解析的真彩色）。
 */
public final class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([0-9a-fA-F]{6})");

    /** 解析 §x 十六进制序列的 legacy 反序列化器（&#hex 先转 §x 再解析为真彩色组件） */
    private static final LegacyComponentSerializer LEGACY_HEX = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private ColorUtil() {}

    /** 聊天/标题/动作栏用：解析为带真 16 进制颜色的 Adventure Component */
    public static Component component(String input) {
        return LEGACY_HEX.deserialize(displayText(input));
    }

    /** TextDisplay 用：&#RRGGBB 转为 §x§R§R§G§G§B§B 真彩色序列，& 代码转 § */
    public static String displayText(String input) {
        Matcher m = HEX_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            sb.append(input, last, m.start());
            sb.append("§x");
            for (char c : m.group(1).toCharArray()) {
                sb.append('§').append(Character.toLowerCase(c));
            }
            last = m.end();
        }
        sb.append(input.substring(last));
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }
}
