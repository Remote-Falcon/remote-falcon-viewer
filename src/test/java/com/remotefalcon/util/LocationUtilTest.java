package com.remotefalcon.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocationUtilTest {

    @Test
    @DisplayName("Returns zero distance for identical coordinates")
    void returnsZeroForSameCoords() {
        Double d = LocationUtil.asTheCrowFlies(40.0, -74.0, 40.0, -74.0);
        assertEquals(0.0, d);
    }

    @Test
    @DisplayName("Returns positive distance for different coordinates")
    void returnsPositiveForDifferentCoords() {
        Double d = LocationUtil.asTheCrowFlies(40.0, -74.0, 41.0, -73.5);
        assertTrue(d > 0.0);
    }
}
