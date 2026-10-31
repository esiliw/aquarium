/*
 * Aquarium - a Fabric port of Terrarium (https://github.com/gegy1000/Terrarium)
 * Original code (c) gegy, LGPL-3.0. Generation is rewritten for the 1.20.1 world-gen pipeline.
 */
package dev.esiliw.aquarium.world;

import dev.esiliw.aquarium.data.EarthData;
import dev.esiliw.aquarium.data.GeoProjection;

/** Decides the shape of every block column (ground height, water height). Pure Java so it can be rendered in tests. */
public final class TerrainModel {
    private final EarthSettings settings;
    private final GeoProjection projection;
    private final EarthData data;

    public TerrainModel(EarthSettings settings, GeoProjection projection, EarthData data) {
        this.settings = settings;
        this.projection = projection;
        this.data = data;
    }

    public EarthData data() {
        return this.data;
    }

    /**
     * top = y of the highest solid block, waterTop = y of the highest water block (or MIN_VALUE).
     */
    public record Column(int top, int waterTop, double elevation, Cover cover, boolean ocean, boolean lake, double lat) {
    }

    /** y of the top solid block for a given elevation */
    public int surfaceY(double elevation) {
        double y = elevation / this.settings.verticalScale();
        int sea = this.settings.seaLevel();
        int top;
        if (elevation < 0) {
            top = sea - 1 + (int) Math.floor(y);
            top = Math.min(top, sea - 2);
        } else {
            top = sea + (int) Math.floor(y) + this.settings.heightOffset();
        }
        return Math.max(this.settings.minY() + 1, Math.min(this.settings.maxY() - 1, top));
    }

    public Column column(int x, int z) {
        double lat = this.projection.lat(z + 0.5), lon = this.projection.lon(x + 0.5);
        double e = this.data.elevation(lat, lon);
        int sea = this.settings.seaLevel();

        Boolean outline = this.data.oceanAt(lat, lon);
        boolean ocean = outline != null ? outline : e < 0;
        if (ocean) {
            // the elevation data is coarse and often reads ~0 m right off the coast: make the sea floor drop away
            // from the real coastline, and use the bathymetry wherever it is deeper than that
            double minDepth = 2.0;
            if (outline != null) {
                double coast = this.data.coastDistance(lat, lon, 2500.0);
                minDepth = Math.min(2.0 + coast * 0.03, 70.0);
                minDepth *= 1.0 + 0.15 * dev.esiliw.aquarium.data.Noise.smooth(x / 37.0, z / 37.0, 21);
            }
            e = Math.min(e, -minDepth);
            int top = this.surfaceY(e);
            return new Column(top, sea - 1, e, Cover.WATER, true, false, lat);
        }
        e = Math.max(e, 0.5);
        Cover cover = this.data.cover(lat, lon);
        if (cover == Cover.WATER) cover = Cover.NO;
        // a little dither before rounding to blocks, so flat land doesn't show the data pixels as square 1-block terraces
        int top = this.surfaceY(e + 0.35 * this.settings.verticalScale() * dev.esiliw.aquarium.data.Noise.smooth(x / 9.0, z / 9.0, 31));

        EarthData.Water w = this.data.water(lat, lon);
        if (w.fraction() > 0) {
            // same convention as the sea: the water surface sits one block under ground at that elevation,
            // so lakes near sea level end up exactly at the sea's height instead of one block above it
            int lakeY = this.surfaceY(Math.max(w.level(), 0.5)) - 1;
            // anywhere near land-cover water that lies below the lake level is flooded (no raised walls around lakes)
            boolean isLake = e <= w.level() + 1.0 || (w.fraction() > 0.97 && e <= w.level() + 6.0);
            if (isLake) {
                double shoreMeters = w.shoreDistance() * this.data.coverPixelMeters() * Math.cos(Math.toRadians(lat));
                double depthMeters = Math.min(3.0 + shoreMeters * 0.02, 60.0);
                int depth = Math.max(1, (int) Math.round(depthMeters / this.settings.verticalScale()));
                int bed = Math.min(top, lakeY - depth);
                return new Column(bed, lakeY, e, Cover.WATER, false, true, lat);
            }
            top = Math.max(top, lakeY);
        }
        return new Column(top, Integer.MIN_VALUE, e, cover, false, false, lat);
    }
}
