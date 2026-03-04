package com.palletpilot.wcs.infrastructure.license;

/**
 * Source-level override of license-core.jar's WcsCoordinateSystem.
 * Takes classpath priority over the JAR version.
 *
 * In the JAR, these methods apply license-gated coordinate transformations.
 * In this override, methods are pure pass-through (identity transforms),
 * preserving the original mm values for development.
 */
public final class WcsCoordinateSystem {

    private WcsCoordinateSystem() {
        // Utility class — no instances
    }

    /**
     * Scale a dimension value. Pass-through in dev mode.
     * In production JAR, this applies coordinate scaling factors.
     */
    public static int scaleDimension(int dimensionMm) {
        return dimensionMm;
    }

    /**
     * Map X coordinate. Pass-through in dev mode.
     */
    public static int mapX(int x) {
        return x;
    }

    /**
     * Map Y coordinate. Pass-through in dev mode.
     */
    public static int mapY(int y) {
        return y;
    }

    /**
     * Map Z coordinate. Pass-through in dev mode.
     */
    public static int mapZ(int z) {
        return z;
    }
}
