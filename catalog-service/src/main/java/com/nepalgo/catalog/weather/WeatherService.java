package com.nepalgo.catalog.weather;

import com.nepalgo.catalog.destination.Destination;
import com.nepalgo.catalog.destination.DestinationNotFoundException;
import com.nepalgo.catalog.destination.DestinationRepository;
import com.nepalgo.catalog.dto.WeatherResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WeatherService {

    private final DestinationRepository destinationRepository;
    private final OpenWeatherMapClient weatherClient;
    private final CacheManager cacheManager;

    public WeatherResponse getWeatherForDestination(Long destinationId) {
        Destination destination = destinationRepository.findById(destinationId)
                .orElseThrow(() -> new DestinationNotFoundException(destinationId));

        if (destination.getLatitude() == null || destination.getLongitude() == null) {
            throw new WeatherUnavailableException("Destination has no coordinates set", null);
        }

        Cache cache = cacheManager.getCache("weather");
        String cacheKey = destination.getLatitude() + "," + destination.getLongitude();

        WeatherResponse cached = cache.get(cacheKey, WeatherResponse.class);
        if (cached != null) {
            return new WeatherResponse(cached.temperatureCelsius(), cached.condition(),
                    cached.description(), cached.humidityPercent(), true);
        }

        try {
            WeatherResponse fresh = weatherClient.fetchWeather(destination.getLatitude(), destination.getLongitude()).get();
            cache.put(cacheKey, fresh);
            return fresh;
        } catch (Exception e) {
            throw new WeatherUnavailableException("Weather data temporarily unavailable", e);
        }
    }
}