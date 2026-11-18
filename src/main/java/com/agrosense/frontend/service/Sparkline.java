package com.agrosense.frontend.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

public final class Sparkline {

    private static final double WIDTH = 100;
    private static final double HEIGHT = 28;
    private static final double PADDING = 3;

    private Sparkline() {
    }

    public static String points(List<BigDecimal> values) {
        // Fewer than three hourly averages are not a trend.
        if (values == null || values.size() < 3) {
            return null;
        }
        double lowest = values.stream().mapToDouble(BigDecimal::doubleValue).min().orElse(0);
        double highest = values.stream().mapToDouble(BigDecimal::doubleValue).max().orElse(0);
        double span = highest - lowest;
        StringJoiner points = new StringJoiner(" ");
        for (int index = 0; index < values.size(); index++) {
            double x = WIDTH * index / (values.size() - 1);
            double level = span == 0 ? 0.5 : (values.get(index).doubleValue() - lowest) / span;
            double y = HEIGHT - PADDING - level * (HEIGHT - 2 * PADDING);
            points.add(String.format(Locale.ROOT, "%.1f,%.1f", x, y));
        }
        return points.toString();
    }
}
