package com.citybottleneck.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.citybottleneck.api.client.OsrmClient;
import com.citybottleneck.api.model.TrafficModels.AnalysisRequest;
import com.citybottleneck.api.model.TrafficModels.Coordinate;
import com.citybottleneck.api.model.TrafficModels.Place;
import com.citybottleneck.api.model.TrafficModels.ScenarioRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TrafficAnalysisServiceTest {

    private final PlaceResolver placeResolver = mock(PlaceResolver.class);
    private final OsrmClient osrmClient = mock(OsrmClient.class);
    private final TrafficAnalysisService service =
            new TrafficAnalysisService(placeResolver, osrmClient, new TrafficEstimator());
    private final Place origin = new Place("Origin", new Coordinate(48.1, 2.1));
    private final Place destination = new Place("Destination", new Coordinate(48.2, 2.2));
    private final LocalDateTime departureTime = LocalDateTime.of(2026, 10, 7, 8, 0);

    @Test
    void convertsOsrmRouteAndGeometryIntoAnalysisResponse() throws Exception {
        when(placeResolver.resolve("Origin")).thenReturn(origin);
        when(placeResolver.resolve("Destination")).thenReturn(destination);
        when(osrmClient.route(origin.location(), destination.location())).thenReturn(routeResponse());

        var response = service.analyze(new AnalysisRequest("Origin", "Destination", departureTime));

        assertEquals("Weekday morning peak", response.period().label());
        assertEquals(135, response.recommendedRoute().estimatedDurationSeconds());
        assertEquals(48.1, response.recommendedRoute().geometry().get(0).latitude());
        assertEquals(2.1, response.recommendedRoute().geometry().get(0).longitude());
        assertEquals(1, response.recommendedRoute().bottlenecks().size());
        assertEquals("high", response.recommendedRoute().bottlenecks().get(0).severity());
    }

    @Test
    void boundsScenarioSavingsByEstimatedRouteDelay() throws Exception {
        when(placeResolver.resolve("Origin")).thenReturn(origin);
        when(placeResolver.resolve("Destination")).thenReturn(destination);
        when(osrmClient.route(origin.location(), destination.location())).thenReturn(routeResponse());

        var response = service.compareScenario(
                new ScenarioRequest("Origin", "Destination", departureTime, 20));

        assertEquals(135, response.baselineDurationSeconds());
        assertEquals(124, response.projectedDurationSeconds());
        assertEquals(11, response.projectedSecondsSaved());
        assertEquals(1, response.targetedBottleneckIds().size());
    }

    private com.fasterxml.jackson.databind.JsonNode routeResponse() throws Exception {
        return new ObjectMapper().readTree("""
                {
                  "code": "Ok",
                  "routes": [{
                    "distance": 1000,
                    "duration": 100,
                    "geometry": {"coordinates": [[2.1, 48.1], [2.2, 48.2]]},
                    "legs": [{"steps": [{
                      "distance": 1000,
                      "duration": 100,
                      "name": "Main Street",
                      "maneuver": {"type": "turn", "location": [2.12, 48.12]}
                    }]}]
                  }]
                }
                """);
    }
}