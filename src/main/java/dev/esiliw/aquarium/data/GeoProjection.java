/*
 * Aquarium - a Fabric port of Terrarium (https://github.com/gegy1000/Terrarium)
 * Original code (c) gegy, LGPL-3.0.
 */
package dev.esiliw.aquarium.data;

/**
 * Maps lat/lon to blocks: block x grows east, block z grows south, (0, 0) is lat 0 / lon 0,
 * and one block is {@code worldScale} metres at the equator.
 * <ul>
 * <li>EQUIRECTANGULAR - Terrarium's original: degrees map linearly, so everything away from the equator is stretched
 * east-west by 1/cos(lat).</li>
 * <li>MERCATOR - conformal: true shapes everywhere (north is always straight up), scale grows with latitude
 * (one block = worldScale * cos(lat) metres). Valid up to +-85.05 degrees.</li>
 * </ul>
 */
public final class GeoProjection {
    public static final double EQUATOR_CIRCUMFERENCE = 40075016.686;
    private static final double METERS_PER_DEGREE = EQUATOR_CIRCUMFERENCE / 360.0;
    private static final double EARTH_RADIUS = EQUATOR_CIRCUMFERENCE / (2 * Math.PI);
    public static final double MERCATOR_MAX_LAT = 85.05112878;

    public enum Type {
        EQUIRECTANGULAR, MERCATOR
    }

    private final double worldScale;
    private final Type type;

    public GeoProjection(double worldScale) {
        this(worldScale, Type.EQUIRECTANGULAR);
    }

    public GeoProjection(double worldScale, Type type) {
        this.worldScale = worldScale;
        this.type = type;
    }

    public Type type() {
        return this.type;
    }

    /** real metres covered by one block (horizontally) at this latitude */
    public double metersPerBlock(double lat) {
        return this.type == Type.MERCATOR ? this.worldScale * Math.cos(Math.toRadians(lat)) : this.worldScale;
    }

    public double worldScale() {
        return this.worldScale;
    }

    public double lon(double blockX) {
        return blockX * this.worldScale / METERS_PER_DEGREE;
    }

    public double lat(double blockZ) {
        if (this.type == Type.MERCATOR) {
            double y = -blockZ * this.worldScale / EARTH_RADIUS;
            double lat = Math.toDegrees(2.0 * Math.atan(Math.exp(y)) - Math.PI / 2.0);
            return Math.max(-MERCATOR_MAX_LAT, Math.min(MERCATOR_MAX_LAT, lat));
        }
        return -blockZ * this.worldScale / METERS_PER_DEGREE;
    }

    public double blockX(double lon) {
        return lon * METERS_PER_DEGREE / this.worldScale;
    }

    public double blockZ(double lat) {
        if (this.type == Type.MERCATOR) {
            double phi = Math.toRadians(Math.max(-MERCATOR_MAX_LAT, Math.min(MERCATOR_MAX_LAT, lat)));
            return -EARTH_RADIUS * Math.log(Math.tan(Math.PI / 4.0 + phi / 2.0)) / this.worldScale;
        }
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
