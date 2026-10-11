package com.citybottleneck.api.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class TrafficModels {

    private TrafficModels() {}

    public record Coordinate(double latitude, double longitude) {}

    public record Place(String displayName, Coordinate location) {}

    public record AnalysisRequest(
            @NotBlank @Size(max = 160) String origin,
            @NotBlank @Size(max = 160) String destination,
            @NotNull LocalDateTime departureTime) {}

    public record ScenarioRequest(
            @NotBlank @Size(max = 160) String origin,
            @NotBlank @Size(max = 160) String destination,
            @NotNull LocalDateTime departureTime,
            @DecimalMin("0.0") @DecimalMax("50.0") double interventionPercent) {}

    public record TrafficPeriod(String label, double delayMultiplier, String description) {}

    public record Bottleneck(
            String id,
            String label,
            String roadName,
            Coordinate location,
            double distanceFromStartMeters,
            double segmentDistanceMeters,
            int pressureScore,
            long estimatedDelaySeconds,
            String severity,
            String reason,
            String maneuver) {}

    public record RouteOption(
            String id,
            int rank,
            String summary,
            double distanceMeters,
            long baselineDurationSeconds,
            long estimatedDurationSeconds,
            long estimatedDelaySeconds,
            List<Coordinate> geometry,
            List<Bottleneck> bottlenecks) {}

    public record ScenarioSuggestion(
            String id,
            String title,
            double delayReductionPercent,
            long projectedDurationSeconds,
            long projectedSecondsSaved,
            String assumption) {}

    public record AnalysisResponse(
            Place origin,
            Place destination,
            String source,
            String methodologyNotice,
            LocalDateTime departureTime,
            TrafficPeriod period,
            RouteOption recommendedRoute,
            List<RouteOption> alternatives,
            List<ScenarioSuggestion> suggestedScenarios) {}

    public record ScenarioResponse(
            String methodologyNotice,
            double interventionPercent,
            long baselineDurationSeconds,
            long projectedDurationSeconds,
            long projectedSecondsSaved,
            List<String> targetedBottleneckIds,
            RouteOption baselineRoute) {}

    public record ModelInfo(
            String routingSource,
            String geocodingSource,
            String observedTrafficAvailable,
            String estimateMethod,
            List<String> limitations) {}
}