package com.nepalgo.catalog.dto;

public record WeatherResponse(
        double temperatureCelsius, String condition, String description, int humidityPercent, boolean cached
) {}