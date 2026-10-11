package com.agrosense.frontend.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The outline of an estate: its corners as {latitude, longitude}, in drawing order, without repeating the
 * first one at the end. It travels and is stored as JSON, {@code [[1.2136,-77.2811],...]}.
 */
public record Boundary(List<double[]> corners) {

    public static final int MIN_CORNERS = 3;
    public static final int MAX_CORNERS = 200;

    private static final Pattern SHAPE = Pattern.compile(
            "\\s*\\[\\s*(\\[\\s*-?\\d+(\\.\\d+)?\\s*,\\s*-?\\d+(\\.\\d+)?\\s*]\\s*,?\\s*)*]\\s*");
    private static final Pattern CORNER = Pattern.compile("\\[\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)\\s*]");
    private static final double EARTH_RADIUS_M = 6_371_008.8;

    /**
     * Reads the JSON form. Null or blank means "no outline" and gives null.
     *
     * @throws IllegalArgumentException when the text is not a list of 3 to 200 valid corners
     */
    public static Boundary parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        if (json.length() > 8000 || !SHAPE.matcher(json).matches()) {
            throw new IllegalArgumentException("Not a list of corners");
        }
        List<double[]> corners = new ArrayList<>();
        Matcher corner = CORNER.matcher(json);
        while (corner.find()) {
            double latitude = Double.parseDouble(corner.group(1));
            double longitude = Double.parseDouble(corner.group(2));
            if (Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
                throw new IllegalArgumentException("Corner outside the valid ranges");
            }
            corners.add(new double[] {latitude, longitude});
        }
        if (corners.size() < MIN_CORNERS || corners.size() > MAX_CORNERS) {
            throw new IllegalArgumentException("Between 3 and 200 corners are needed");
        }
        return new Boundary(List.copyOf(corners));
    }

    /** The form that is stored: seven decimals, about a centimetre on the ground. */
    public String toJson() {
        return corners.stream()
                .map(corner -> String.format(Locale.ROOT, "[%.7f,%.7f]", corner[0], corner[1]))
                .collect(Collectors.joining(",", "[", "]"));
    }

    /** The middle of the corners: where the estate's pin goes when no point was given. */
    public double[] centre() {
        double latitude = corners.stream().mapToDouble(corner -> corner[0]).average().orElse(0);
        double longitude = corners.stream().mapToDouble(corner -> corner[1]).average().orElse(0);
        return new double[] {latitude, longitude};
    }

    /**
     * Enclosed area in hectares. The corners are laid flat around their centre, which is exact enough for
     * a farm: the error stays far below a square metre per hectare at these sizes.
     */
    public BigDecimal areaHa() {
        double[] centre = centre();
        double metresPerDegreeLatitude = Math.toRadians(1) * EARTH_RADIUS_M;
        double metresPerDegreeLongitude = metresPerDegreeLatitude * Math.cos(Math.toRadians(centre[0]));
        double twiceArea = 0;
        for (int index = 0; index < corners.size(); index++) {
            double[] from = corners.get(index);
            double[] to = corners.get((index + 1) % corners.size());
            double fromX = (from[1] - centre[1]) * metresPerDegreeLongitude;
            double fromY = (from[0] - centre[0]) * metresPerDegreeLatitude;
            double toX = (to[1] - centre[1]) * metresPerDegreeLongitude;
            double toY = (to[0] - centre[0]) * metresPerDegreeLatitude;
            twiceArea += fromX * toY - toX * fromY;
        }
        return BigDecimal.valueOf(Math.abs(twiceArea) / 2 / 10_000).setScale(2, RoundingMode.HALF_UP);
    }
}
