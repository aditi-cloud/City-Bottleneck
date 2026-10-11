package com.citybottleneck.api.service;

import com.citybottleneck.api.client.NominatimClient;
import com.citybottleneck.api.model.TrafficModels.Coordinate;
import com.citybottleneck.api.model.TrafficModels.Place;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlaceResolver {

    private static final Pattern COORDINATE_PAIR = Pattern.compile(
            "^\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)\\s*$");

    private final NominatimClient nominatimClient;

    public PlaceResolver(NominatimClient nominatimClient) {
        this.nominatimClient = nominatimClient;
    }

    public Place resolve(String input) {
        Matcher matcher = COORDINATE_PAIR.matcher(input);
        if (matcher.matches()) {
            double latitude = Double.parseDouble(matcher.group(1));
            double longitude = Double.parseDouble(matcher.group(2));
            if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coordinates are outside valid bounds.");
            }
            return new Place("Selected map point", new Coordinate(latitude, longitude));
        }

        List<Place> matches = nominatimClient.search(input);
        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No place matched: " + input);
        }
        return matches.get(0);
    }

    public List<Place> search(String query) {
        if (query == null || query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A place query is required.");
        }
        return nominatimClient.search(query.trim());
    }
}