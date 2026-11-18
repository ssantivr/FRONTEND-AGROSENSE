package com.agrosense.frontend.entity.enums;

import java.util.List;

public enum SensorType {
    SOIL_MOISTURE,
    AIR_TEMPERATURE,
    SOIL_TEMPERATURE,
    PH,
    CONDUCTIVITY,
    LIGHT,
    RAIN_GAUGE,
    RELATIVE_HUMIDITY;

    /**
     * What the ESP32 measures (the soil probe and the DHT11), in the order the pages show it. The other types stay so
     * the database and the backend keep accepting their old rows, but the application neither offers nor shows them.
     */
    public static final List<SensorType> ESP32 = List.of(SOIL_MOISTURE, AIR_TEMPERATURE, RELATIVE_HUMIDITY);

    public boolean isMeasuredByEsp32() {
        return ESP32.contains(this);
    }
}
