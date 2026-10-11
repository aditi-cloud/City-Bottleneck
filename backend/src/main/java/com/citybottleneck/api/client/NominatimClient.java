package com.citybottleneck.api.client;

import com.citybottleneck.api.model.TrafficModels.Coordinate;
import com.citybottleneck.api.model.TrafficModels.Place;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class NominatimClient {

    private static final Duration CACHE_TTL = Duration.ofMinutes(15);
    private static final long MIN_REQUEST_INTERVAL_MILLIS = 1_000;

    private final RestClient restClient;
    private final ConcurrentHashMap<String, CachedPlaces> cache = new ConcurrentHashMap<>();
    private long nextRequestAtMillis;

    public NominatimClient(@Qualifier("nominatimRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public List<Place> search(String query) {
        String cacheKey = query.trim().toLowerCase(Locale.ROOT);
        CachedPlaces cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.places();
        }

        waitForPublicServiceAllowance();
        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/search")
                            .queryParam("q", query)
                            .queryParam("format", "jsonv2")
                            .queryParam("limit", 5)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            List<Place> places = parsePlaces(response);
            cache.put(cacheKey, new CachedPlaces(places, Instant.now().plus(CACHE_TTL)));
            return places;
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "The OpenStreetMap geocoder is unavailable. Please retry.");
        }
    }

    private synchronized void waitForPublicServiceAllowance() {
        long now = System.currentTimeMillis();
        long waitMillis = nextRequestAtMillis - now;
        if (waitMillis > 0) {
            try {
                Thread.sleep(waitMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Geocoding was interrupted.");
            }
        }
        nextRequestAtMillis = System.currentTimeMillis() + MIN_REQUEST_INTERVAL_MILLIS;
    }

    private List<Place> parsePlaces(JsonNode response) {
        if (response == null || !response.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(response.spliterator(), false)
                .filter(item -> item.hasNonNull("lat") && item.hasNonNull("lon"))
                .map(item -> new Place(
                        item.path("display_name").asText("Unknown place"),
                        new Coordinate(item.path("lat").asDouble(), item.path("lon").asDouble())))
                .toList();
    }

    private record CachedPlaces(List<Place> places, Instant expiresAt) {}
}