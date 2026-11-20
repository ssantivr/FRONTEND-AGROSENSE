package com.agrosense.frontend.entity.enums;

/** Where a reading came from (sensor_readings.source). Same values as the CHECK ck_sensor_readings_source. */
public enum ReadingSource {
    /** A device event (POST /api/internal/events) or an MQTT message. */
    DEVICE,
    /** The ESP32 read over TCP by Esp32TcpIngestionService. */
    ESP32_TCP,
    /** The virtual sensor (VirtualSensorSimulator), only while no real reading arrives. */
    SIMULATED,
    /** The sample rows of database/seed_demo.sql. */
    SEED,
    /** Typed by a user through POST /api/sensors/readings. */
    MANUAL
}
