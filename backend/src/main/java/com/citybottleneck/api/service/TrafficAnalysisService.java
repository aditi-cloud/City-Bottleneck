package com.citybottleneck.api.service;

import com.citybottleneck.api.client.OsrmClient;
import com.citybottleneck.api.model.TrafficModels.AnalysisRequest;
import com.citybottleneck.api.model.TrafficModels.AnalysisResponse;
import com.citybottleneck.api.model.TrafficModels.Bottleneck;
import com.citybottleneck.api.model.TrafficModels.Coordinate;
import com.citybottleneck.api.model.TrafficModels.ModelInfo;
import com.citybottleneck.api.model.TrafficModels.Place;
import com.citybottleneck.api.model.TrafficModels.RouteOption;
import com.citybottleneck.api.model.TrafficModels.ScenarioRequest;
import com.citybottleneck.api.model.TrafficModels.ScenarioResponse;
import com.citybottleneck.api.model.TrafficModels.ScenarioSuggestion;
import com.citybottleneck.api.model.TrafficModels.TrafficPeriod;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TrafficAnalysisService {

    private static final String METHODOLOGY_NOTICE =
            "Routes use OpenStreetMap data through OSRM. Travel-time uplifts and pressure points are heuristic estimates, not live traffic observations.";

    private final PlaceResolver placeResolver;
    private final OsrmClient osrmClient;
    private final TrafficEstimator trafficEstimator;

    public TrafficAnalysisService(
            PlaceResolver placeResolver, OsrmClient osrmClient, TrafficEstimator trafficEstimator) {
        this.placeResolver = placeResolver;
        this.osrmClient = osrmClient;
        this.trafficEstimator = trafficEstimator;
    }

    public List<Place> geocode(String query) {
        return placeResolver.search(query);
    }

    public AnalysisResponse analyze(AnalysisRequest request) {
        Place origin = placeResolver.resolve(request.origin());
        Place destination = placeResolver.resolve(request.destination());
        JsonNode response = osrmClient.route(origin.location(), destination.location());
        JsonNode routeNodes = response.path("routes");
        if (!routeNodes.isArray() || routeNodes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "No route was found for those points.");
        }

        TrafficPeriod period = trafficEstimator.periodFor(request.departureTime());
        List<RouteOption> routes = new ArrayList<>();
        for (int index = 0; index < routeNodes.size(); index++) {
            routes.add(toRouteOption(routeNodes.get(index), index + 1, period));
        }

        RouteOption recommended = routes.get(0);
        List<RouteOption> alternatives = routes.size() > 1 ? routes.subList(1, routes.size()) : List.of();
        return new AnalysisResponse(
                origin,
                destination,
                "OpenStreetMap Nominatim + OSRM public routing service",
                METHODOLOGY_NOTICE,
                request.departureTime(),
                period,
                recommended,
                alternatives,
                suggestedScenarios(recommended, period, request.departureTime()));
    }

    public ScenarioResponse compareScenario(ScenarioRequest request) {
        AnalysisResponse analysis = analyze(new AnalysisRequest(
                request.origin(), request.destination(), request.departureTime()));
        RouteOption baseline = analysis.recommendedRoute();
        List<Bottleneck> targeted = baseline.bottlenecks().stream().limit(3).toList();
        long candidateDelay = targeted.stream().mapToLong(Bottleneck::estimatedDelaySeconds).sum();
        long projectedSavings = Math.min(
                baseline.estimatedDelaySeconds(),
                Math.round(candidateDelay * request.interventionPercent() / 100.0));
        long projectedDuration = Math.max(0, baseline.estimatedDurationSeconds() - projectedSavings);

        return new ScenarioResponse(
                "What-if only: assumes the selected intervention reduces modeled delay at the top three candidate points by the entered percentage. It does not predict a real-world traffic outcome.",
                request.interventionPercent(),
                baseline.estimatedDurationSeconds(),
                projectedDuration,
                projectedSavings,
                targeted.stream().map(Bottleneck::id).toList(),
                baseline);
    }

    public ModelInfo modelInfo() {
        return new ModelInfo(
                "OSRM public demo server using OpenStreetMap road data",
                "Nominatim OpenStreetMap search",
                "No; the API does not receive live speeds, queues, counts, or incident feeds.",
                "OSRM route duration is adjusted by a transparent weekday/weekend time window. Candidate points are ranked from OSRM route-step duration and maneuver type.",
                List.of(
                        "Rush-hour multipliers are generic assumptions, not calibrated to a specific city.",
                        "Pressure points are route features to investigate, not confirmed congestion locations.",
                        "The public routing/geocoding services have usage policies and are not guaranteed production infrastructure.",
                        "The entered departure time is treated as a local wall-clock value; no timezone conversion is applied."));
    }

    private RouteOption toRouteOption(JsonNode route, int rank, TrafficPeriod period) {
        long baselineSeconds = Math.max(0, Math.round(route.path("duration").asDouble()));
        long estimatedSeconds = trafficEstimator.estimatedDurationSeconds(baselineSeconds, period);
        long estimatedDelay = Math.max(0, estimatedSeconds - baselineSeconds);
        double distance = route.path("distance").asDouble();
        List<Coordinate> geometry = parseGeometry(route.path("geometry").path("coordinates"));
        List<Bottleneck> bottlenecks = parseBottlenecks(route.path("legs"), rank, period);

        return new RouteOption(
                "route-" + rank,
                rank,
                routeSummary(route.path("legs"), rank),
                distance,
                baselineSeconds,
                estimatedSeconds,
                estimatedDelay,
                geometry,
                bottlenecks);
    }

    private List<Bottleneck> parseBottlenecks(JsonNode legs, int routeRank, TrafficPeriod period) {
        List<Bottleneck> candidates = new ArrayList<>();
        double distanceFromStart = 0;
        int stepIndex = 0;

        for (JsonNode leg : legs) {
            for (JsonNode step : leg.path("steps")) {
                double stepDistance = step.path("distance").asDouble();
                double stepDuration = step.path("duration").asDouble();
                JsonNode maneuver = step.path("maneuver");
                String maneuverType = maneuver.path("type").asText("continue").replace('_', ' ');
                int score = trafficEstimator.pressureScore(stepDuration, maneuverType, period);
                Coordinate location = parseCoordinate(maneuver.path("location"));

                if (location != null && !"arrive".equals(maneuverType) && score >= 35) {
                    long modeledDelay = Math.max(0, Math.round(stepDuration * (period.delayMultiplier() - 1.0)))
                            + trafficEstimator.estimatedManeuverDelaySeconds(maneuverType);
                    String roadName = step.path("name").asText("");
                    candidates.add(new Bottleneck(
                            "r" + routeRank + "-p" + stepIndex,
                            roadName.isBlank() ? displayManeuver(maneuverType) : roadName,
                            roadName,
                            location,
                            distanceFromStart,
                            stepDistance,
                            score,
                            modeledDelay,
                            trafficEstimator.severityFor(score),
                            reasonFor(stepDuration, maneuverType, period),
                            maneuverType));
                }
                distanceFromStart += stepDistance;
                stepIndex++;
            }
        }

        return candidates.stream()
                .sorted(Comparator.comparingInt(Bottleneck::pressureScore).reversed())
                .limit(5)
                .toList();
    }

    private List<Coordinate> parseGeometry(JsonNode coordinates) {
        List<Coordinate> points = new ArrayList<>();
        for (JsonNode point : coordinates) {
            Coordinate coordinate = parseCoordinate(point);
            if (coordinate != null) points.add(coordinate);
        }
        return points;
    }

    private Coordinate parseCoordinate(JsonNode point) {
        if (!point.isArray() || point.size() < 2) return null;
        return new Coordinate(point.get(1).asDouble(), point.get(0).asDouble());
    }

    private String routeSummary(JsonNode legs, int rank) {
        Set<String> names = new LinkedHashSet<>();
        for (JsonNode leg : legs) {
            for (JsonNode step : leg.path("steps")) {
                String name = step.path("name").asText("").trim();
                if (!name.isEmpty()) names.add(name);
                if (names.size() == 3) break;
            }
            if (names.size() == 3) break;
        }
        return names.isEmpty() ? "Route option " + rank : String.join(" / ", names);
    }

    private List<ScenarioSuggestion> suggestedScenarios(
            RouteOption route, TrafficPeriod period, LocalDateTime departureTime) {
        long offPeakDuration = route.baselineDurationSeconds();
        long offPeakSavings = Math.max(0, route.estimatedDurationSeconds() - offPeakDuration);
        long pressurePointDelay = route.bottlenecks().stream()
                .limit(3)
                .mapToLong(Bottleneck::estimatedDelaySeconds)
                .sum();
        long targetedSavings = Math.min(
                route.estimatedDelaySeconds(), Math.round(pressurePointDelay * 0.15));

        return List.of(
                new ScenarioSuggestion(
                        "off-peak",
                        "Compare with off-peak travel",
                        0,
                        offPeakDuration,
                        offPeakSavings,
                        period.delayMultiplier() > 1.0
                                ? "Uses the same route's OSRM baseline duration with no time-of-day uplift."
                                : "The selected time is already outside this model's peak windows."),
                new ScenarioSuggestion(
                        "target-pressure-points",
                        "Test a 15% reduction at top candidates",
                        15,
                        Math.max(0, route.estimatedDurationSeconds() - targetedSavings),
                        targetedSavings,
                        "Assumes a 15% reduction in modeled delay across up to three highest-ranked candidate points."));
    }

    private String reasonFor(double durationSeconds, String maneuverType, TrafficPeriod period) {
        List<String> reasons = new ArrayList<>();
        if (!"continue".equals(maneuverType) && !"new name".equals(maneuverType)) {
            reasons.add("The route includes a " + maneuverType + " maneuver here.");
        }
        if (durationSeconds >= 90) {
            reasons.add("This route step has a comparatively long modeled traversal time.");
        }
        if (period.delayMultiplier() > 1.0) {
            reasons.add(period.description());
        }
        reasons.add("No observed queue or speed data is available at this point.");
        return String.join(" ", reasons);
    }

    private String displayManeuver(String maneuverType) {
        return maneuverType.isBlank() ? "Route segment" : Character.toUpperCase(maneuverType.charAt(0)) + maneuverType.substring(1);
    }
}