package com.whitecloud233.herobrine_companion.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LegacyFormattingText {
    private static final String SECTION_SIGN = "\u00A7";
    private static final String BROKEN_SECTION_SIGN_GBK = "\u6402";
    private static final String BROKEN_SECTION_SIGN_DOUBLE = "\u93BC\u4FC2";
    private static final String BROKEN_SECTION_SIGN_LATIN1 = "\u00C2\u00A7";
    private static final Pattern BROKEN_SECTION_PATTERN = Pattern.compile(
            "(?i)(?:\\\\u00a7|"
                    + Pattern.quote(BROKEN_SECTION_SIGN_LATIN1)
                    + "|"
                    + Pattern.quote(BROKEN_SECTION_SIGN_GBK)
                    + "|"
                    + Pattern.quote(BROKEN_SECTION_SIGN_DOUBLE)
                    + ")([0-9A-FK-ORX])");
    private static final Pattern LEGACY_CODE_PATTERN = Pattern.compile("(?i)" + Pattern.quote(SECTION_SIGN) + "[0-9A-FK-ORX]");

    private LegacyFormattingText() {
    }

    public static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String normalized = text.replace(BROKEN_SECTION_SIGN_LATIN1, SECTION_SIGN);
        Matcher matcher = BROKEN_SECTION_PATTERN.matcher(normalized);
        return matcher.replaceAll(Matcher.quoteReplacement(SECTION_SIGN) + "$1");
    }

    public static String stripCodes(String text) {
        return LEGACY_CODE_PATTERN.matcher(normalize(text)).replaceAll("");
    }
}
