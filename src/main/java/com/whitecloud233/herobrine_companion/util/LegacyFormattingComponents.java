package com.whitecloud233.herobrine_companion.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

public final class LegacyFormattingComponents {
    private static final char SECTION_SIGN = '\u00A7';

    private LegacyFormattingComponents() {
    }

    public static MutableComponent parse(String text) {
        return parse(text, Style.EMPTY);
    }

    public static MutableComponent parse(String text, Style baseStyle) {
        Style safeBaseStyle = baseStyle == null ? Style.EMPTY : baseStyle;
        String normalized = LegacyFormattingText.normalize(text);
        MutableComponent result = Component.empty();
        Style currentStyle = safeBaseStyle;
        StringBuilder segment = new StringBuilder();

        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if (current == SECTION_SIGN && index + 1 < normalized.length()) {
                appendSegment(result, segment, currentStyle);
                char code = Character.toLowerCase(normalized.charAt(++index));
                ChatFormatting formatting = ChatFormatting.getByCode(code);
                if (formatting != null) {
                    currentStyle = applyFormatting(safeBaseStyle, currentStyle, formatting);
                    continue;
                }
                segment.append(SECTION_SIGN).append(code);
                continue;
            }
            segment.append(current);
        }

        appendSegment(result, segment, currentStyle);
        return result;
    }

    private static void appendSegment(MutableComponent result, StringBuilder segment, Style style) {
        if (segment.isEmpty()) {
            return;
        }
        result.append(Component.literal(segment.toString()).setStyle(style));
        segment.setLength(0);
    }

    private static Style applyFormatting(Style baseStyle, Style currentStyle, ChatFormatting formatting) {
        if (formatting == ChatFormatting.RESET) {
            return baseStyle;
        }
        if (formatting.isColor()) {
            return baseStyle.withColor(TextColor.fromLegacyFormat(formatting));
        }
        return switch (formatting) {
            case BOLD -> currentStyle.withBold(true);
            case ITALIC -> currentStyle.withItalic(true);
            case UNDERLINE -> currentStyle.withUnderlined(true);
            case STRIKETHROUGH -> currentStyle.withStrikethrough(true);
            case OBFUSCATED -> currentStyle.withObfuscated(true);
            default -> currentStyle;
        };
    }
}
