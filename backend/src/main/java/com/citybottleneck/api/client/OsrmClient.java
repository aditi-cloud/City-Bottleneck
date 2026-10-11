package com.citybottleneck.api.client;

import com.citybottleneck.api.model.TrafficModels.Coordinate;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class OsrmClient {

    private final RestClient restClient;

    public OsrmClient(@Qualifier("osrmRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public JsonNode route(Coordinate origin, Coordinate destination) {
        String path = String.format(
                Locale.ROOT,
                "/route/v1/driving/%.6f,%.6f;%.6f,%.6f?alternatives=2&steps=true&overview=full&geometries=geojson",
                origin.longitude(),
                origin.latitude(),
                destination.longitude(),
                destination.latitude());
        try {
            JsonNode response = restClient.get().uri(path).retrieve().body(JsonNode.class);
            if (response == null || !"Ok".equals(response.path("code").asText())) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY, "No drivable route was found between those points.");
            }
            return response;
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "The OpenStreetMap routing service is unavailable. Please retry.");
        }
    }
}