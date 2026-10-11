package com.agrosense.frontend.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/** Turns a series into the {@code points} of an SVG polyline drawn in a 100 x 28 box. */
public final class Sparkline {

    private static final double WIDTH = 100;
    private static final double HEIGHT = 28;
    /** Keeps the stroke from being clipped at the top and bottom edges. */
    private static final double PADDING = 3;

    private Sparkline() {
    }

    /** Null when there are fewer than two values: a single point is not a trend. */
    public static String points(List<BigDecimal> values) {
        if (values == null || values.size() < 2) {
            return null;
        }
        double lowest = values.stream().mapToDouble(BigDecimal::doubleValue).min().orElse(0);
        double highest = values.stream().mapToDouble(BigDecimal::doubleValue).max().orElse(0);
        double span = highest - lowest;
        StringJoiner points = new StringJoiner(" ");
        for (int index = 0; index < values.size(); index++) {
            double x = WIDTH * index / (values.size() - 1);
            // A flat series is drawn through the middle instead of along an edge.
            double level = span == 0 ? 0.5 : (values.get(index).doubleValue() - lowest) / span;
            double y = HEIGHT - PADDING - level * (HEIGHT - 2 * PADDING);
            points.add(String.format(Locale.ROOT, "%.1f,%.1f", x, y));
        }
        return points.toString();
    }
}
