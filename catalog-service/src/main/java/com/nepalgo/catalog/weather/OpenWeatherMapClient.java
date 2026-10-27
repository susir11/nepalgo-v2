package com.nepalgo.catalog.weather;

import com.nepalgo.catalog.dto.WeatherResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.concurrent.CompletableFuture;

@Component
public class OpenWeatherMapClient {

    private final RestClient restClient = RestClient.create();

    @Value("${nepalgo.weather.api-key}")
    private String apiKey;

    // @CircuitBreaker + @TimeLimiter only work on methods returning
    // CompletableFuture (or similar async types) — this is a Resilience4j
    // requirement, not a style choice. fallbackMethod runs automatically
    // when the circuit is open OR the call times out OR it throws.
    @CircuitBreaker(name = "weatherApi", fallbackMethod = "fallback")
    @TimeLimiter(name = "weatherApi")
    public CompletableFuture<WeatherResponse> fetchWeather(java.math.BigDecimal lat, java.math.BigDecimal lon) {
        return CompletableFuture.supplyAsync(() -> {
            OpenWeatherMapApiResponse response = restClient.get()
                    .uri("https://api.openweathermap.org/data/2.5/weather?lat={lat}&lon={lon}&appid={key}&units=metric",
                            lat, lon, apiKey)
                    .retrieve()
                    .body(OpenWeatherMapApiResponse.class);

            if (response == null || response.main() == null || response.weather() == null || response.weather().isEmpty()) {
                throw new IllegalStateException("Unexpected empty response from OpenWeatherMap");
            }

            var weatherEntry = response.weather().get(0);
            return new WeatherResponse(
                    response.main().temp(), weatherEntry.main(), weatherEntry.description(),
                    response.main().humidity(), false);
        });
    }

    private CompletableFuture<WeatherResponse> fallback(java.math.BigDecimal lat, java.math.BigDecimal lon, Throwable t) {
        throw new WeatherUnavailableException("Weather data temporarily unavailable", t);
    }

    // Signature must match fetchWeather's, plus one extra Throwable param —
    // Resilience4j wires this by reflection, so the match has to be exact.
    private CompletableFuture<WeatherResponse> fallback(String region, Throwable t) {
        throw new WeatherUnavailableException("Weather data temporarily unavailable for " + region, t);
    }

    // Minimal shape matching OpenWeatherMap's JSON — only the fields we
    // actually use, Jackson ignores the rest automatically.
    private record OpenWeatherMapApiResponse(Main main, java.util.List<WeatherEntry> weather) {}
    private record Main(double temp, int humidity) {}
    private record WeatherEntry(String main, String description) {}
}