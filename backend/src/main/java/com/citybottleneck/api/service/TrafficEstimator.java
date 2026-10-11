package com.citybottleneck.api.service;

import com.citybottleneck.api.model.TrafficModels.TrafficPeriod;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class TrafficEstimator {

    public TrafficPeriod periodFor(LocalDateTime departureTime) {
        int hour = departureTime.getHour();
        DayOfWeek day = departureTime.getDayOfWeek();
        boolean weekday = day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;

        if (weekday && hour >= 7 && hour < 10) {
            return new TrafficPeriod(
                    "Weekday morning peak", 1.35, "The model applies a 35% planning uplift for weekday morning travel.");
        }
        if (weekday && hour >= 16 && hour < 19) {
            return new TrafficPeriod(
                    "Weekday evening peak", 1.40, "The model applies a 40% planning uplift for weekday evening travel.");
        }
        if (weekday && hour >= 11 && hour < 14) {
            return new TrafficPeriod(
                    "Weekday midday", 1.10, "The model applies a 10% planning uplift for weekday midday travel.");
        }
        if (!weekday && hour >= 10 && hour < 18) {
            return new TrafficPeriod(
                    "Weekend daytime", 1.05, "The model applies a 5% planning uplift for weekend daytime travel.");
        }
        return new TrafficPeriod(
                "Off-peak", 1.0, "No time-of-day uplift is applied outside the modeled peak windows.");
    }

    public long estimatedDurationSeconds(long baselineSeconds, TrafficPeriod period) {
        return (long) Math.ceil(baselineSeconds * period.delayMultiplier());
    }

    public long estimatedManeuverDelaySeconds(String maneuverType) {
        return switch (maneuverType) {
            case "merge", "on ramp", "off ramp", "roundabout", "rotary" -> 35;
            case "turn", "fork", "end of road", "new name" -> 20;
            default -> 0;
        };
    }

    public int pressureScore(double segmentDurationSeconds, String maneuverType, TrafficPeriod period) {
        int durationPoints = (int) Math.min(40, Math.round(segmentDurationSeconds / 3.0));
        int maneuverPoints = switch (maneuverType) {
            case "merge", "on ramp", "off ramp", "roundabout", "rotary" -> 35;
            case "turn", "fork", "end of road", "new name" -> 25;
            default -> 0;
        };
        int peakPoints = period.delayMultiplier() > 1.0 ? 20 : 0;
        return Math.min(100, durationPoints + maneuverPoints + peakPoints);
    }

    public String severityFor(int pressureScore) {
        if (pressureScore >= 70) return "high";
        if (pressureScore >= 45) return "moderate";
        return "low";
    }
}