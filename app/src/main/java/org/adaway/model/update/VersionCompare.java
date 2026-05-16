package org.adaway.model.update;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Semver-style version comparison.
 * <p>
 * Parses each version into the list of integer segments it contains (any sequence of
 * digits, separators ignored), then compares element by element. Works for tags like
 * {@code 6.5.1-c}, {@code v6.5.1}, {@code 6.5.10-c} — no per-segment numeric limit.
 */
public final class VersionCompare {
    private static final Pattern SEGMENT = Pattern.compile("\\d+");

    private VersionCompare() {
    }

    /**
     * Compare two version strings.
     *
     * @return negative if {@code a < b}, positive if {@code a > b}, zero if equal.
     */
    public static int compare(@NonNull String a, @NonNull String b) {
        int[] left = parse(a);
        int[] right = parse(b);
        int len = Math.max(left.length, right.length);
        for (int i = 0; i < len; i++) {
            int l = i < left.length ? left[i] : 0;
            int r = i < right.length ? right[i] : 0;
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        return 0;
    }

    private static int[] parse(String version) {
        Matcher matcher = SEGMENT.matcher(version);
        List<Integer> segments = new ArrayList<>();
        while (matcher.find()) {
            try {
                segments.add(Integer.parseInt(matcher.group()));
            } catch (NumberFormatException ignored) {
                // Segment too large for int — treat as 0 rather than crash.
            }
        }
        if (segments.isEmpty()) {
            return new int[]{0};
        }
        int[] result = new int[segments.size()];
        for (int i = 0; i < segments.size(); i++) {
            result[i] = segments.get(i);
        }
        return result;
    }
}
