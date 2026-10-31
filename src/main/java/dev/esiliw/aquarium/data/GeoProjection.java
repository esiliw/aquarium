/*
 * Aquarium - a Fabric port of Terrarium (https://github.com/gegy1000/Terrarium)
 * Original code (c) gegy, LGPL-3.0.
 */
package dev.esiliw.aquarium.data;

/**
 * Equirectangular projection used by Terrarium: block x grows east, block z grows south,
 * (0, 0) is lat 0 / lon 0, and one block is {@code worldScale} metres at the equator in both directions.
 */
public final class GeoProjection {
    public static final double EQUATOR_CIRCUMFERENCE = 40075016.686;
    private static final double METERS_PER_DEGREE = EQUATOR_CIRCUMFERENCE / 360.0;

    private final double worldScale;

    public GeoProjection(double worldScale) {
        this.worldScale = worldScale;
    }

    public double worldScale() {
        return this.worldScale;
    }

    public double lon(double blockX) {
        return blockX * this.worldScale / METERS_PER_DEGREE;
    }

    public double lat(double blockZ) {
        return -blockZ * this.worldScale / METERS_PER_DEGREE;
    }

    public double blockX(double lon) {
        return lon * METERS_PER_DEGREE / this.worldScale;
    }

    public double blockZ(double lat) {
        return -lat * METERS_PER_DEGREE / this.worldScale;
    }

    /** global pixel x at the given zoom */
    public static double pixelX(double lon, int zoom) {
        return (lon + 180.0) / 360.0 * GeoTileSource.globalWidth(zoom);
    }

    /** global pixel y at the given zoom (row 0 = north pole) */
    public static double pixelY(double lat, int zoom) {
        return (90.0 - lat) / 180.0 * GeoTileSource.globalHeight(zoom);
    }

    public static double lonOfPixel(double px, int zoom) {
        return px / GeoTileSource.globalWidth(zoom) * 360.0 - 180.0;
    }

    public static double latOfPixel(double py, int zoom) {
        return 90.0 - py / GeoTileSource.globalHeight(zoom) * 180.0;
    }
}
