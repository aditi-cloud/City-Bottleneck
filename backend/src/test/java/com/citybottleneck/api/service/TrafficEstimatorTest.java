package com.citybottleneck.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TrafficEstimatorTest {

    private final TrafficEstimator estimator = new TrafficEstimator();

    @Test
    void assignsWeekdayMorningPeakMultiplier() {
        var period = estimator.periodFor(LocalDateTime.of(2026, 10, 7, 8, 30));

        assertEquals("Weekday morning peak", period.label());
        assertEquals(1.35, period.delayMultiplier());
    }

    @Test
    void assignsWeekendDaytimeMultiplier() {
        var period = estimator.periodFor(LocalDateTime.of(2026, 10, 10, 12, 0));

        assertEquals("Weekend daytime", period.label());
        assertEquals(1.05, period.delayMultiplier());
    }

    @Test
    void doesNotApplyPeakMultiplierOutsideConfiguredWindows() {
        var period = estimator.periodFor(LocalDateTime.of(2026, 10, 7, 22, 0));

        assertEquals("Off-peak", period.label());
        assertEquals(1.0, period.delayMultiplier());
    }
}