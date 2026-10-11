package com.citybottleneck.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.citybottleneck.api.client.NominatimClient;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PlaceResolverTest {

    private final PlaceResolver resolver = new PlaceResolver(mock(NominatimClient.class));

    @Test
    void acceptsMapCoordinatesInLatitudeLongitudeOrder() {
        var place = resolver.resolve("40.7128, -74.0060");

        assertEquals(40.7128, place.location().latitude());
        assertEquals(-74.0060, place.location().longitude());
    }

    @Test
    void rejectsCoordinatesOutsideGeographicBounds() {
        assertThrows(ResponseStatusException.class, () -> resolver.resolve("95, 20"));
    }
}