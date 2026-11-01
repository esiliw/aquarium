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
    public record Column(int top, int waterTop, double elevation, Cover cover, boolean ocean, boolean lake, double lat, byte way, int buildingTop,
                         byte climate) {
        public Column(int top, int waterTop, double elevation, Cover cover, boolean ocean, boolean lake, double lat) {
            this(top, waterTop, elevation, cover, ocean, lake, lat, dev.esiliw.aquarium.data.OsmWays.NONE, Integer.MIN_VALUE,
                    dev.esiliw.aquarium.data.Climate.NONE);
        }

        /** y of the highest non-air block: ground, water or building */
        public int highest() {
            return Math.max(this.top, Math.max(this.waterTop, this.buildingTop));
        }
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
        if (this.data.source() == EarthData.Source.OPEN) return this.columnOpen(x, z);
        double lat = this.projection.lat(z + 0.5), lon = this.projection.lon(x + 0.5);
        double e = this.data.elevation(lat, lon);
        int sea = this.settings.seaLevel();

        Boolean outline = this.data.oceanAt(lat, lon);
        boolean ocean = outline != null ? outline : e < 0;
        if (ocean) {
            // the elevation data is coarse and often reads ~0 m right off the coast: make the sea floor drop away
            // from the real coastline, and use the bathymetry wherever it is deeper than that
            double minDepth = 2.0, maxDepth = Double.MAX_VALUE;
            if (outline != null) {
                double coast = this.data.coastDistance(lat, lon, 30000.0);
                minDepth = Math.min(2.0 + coast * 0.03, 70.0);
                minDepth *= 1.0 + 0.15 * dev.esiliw.aquarium.data.Noise.smooth(x / 37.0, z / 37.0, 21);
                maxDepth = 2.0 + coast * 0.03 + 3.0 * (coast / 1000.0) * (coast / 1000.0);   // shelf first, deep sea further out
            }
            double filled = this.data.bathymetry(lat, lon);
            if (!Double.isNaN(filled)) e = Math.min(e, Math.max(filled, -maxDepth));
            e = Math.min(e, -minDepth);
            int top = this.surfaceY(e);
            return new Column(top, sea - 1, e, Cover.WATER, true, false, lat);
        }
        EarthData.Water w = this.data.water(lat, lon);
        // sea-connected land-cover water on the land side of the outlines: a lagoon if the ground is at sea level and it is
        // clearly inside the coastline (the outlines miss some lagoons); right at the coast the outlines are trusted instead
        if (w.marineFraction() > 0.5 && e <= 1.0 && outline != null && this.data.coastDistance(lat, lon, 400.0) >= 300.0) {
            int top = Math.min(this.surfaceY(Math.min(e, -1.5)), sea - 2);
            return new Column(top, sea - 1, e, Cover.WATER, true, false, lat);
        }
        e = Math.max(e, 0.5);
        Cover cover = this.data.cover(lat, lon);
        if (cover == Cover.WATER) cover = Cover.NO;
        // a little dither before rounding to blocks, so flat land doesn't show the data pixels as square 1-block terraces
        int top = this.surfaceY(Math.max(0.5, e + 0.35 * this.settings.verticalScale() * dev.esiliw.aquarium.data.Noise.smooth(x / 9.0, z / 9.0, 31)));

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

    /** open data: AWS elevation + bathymetry, OSM sea / lakes / rivers, WorldCover land cover */
    private Column columnOpen(int x, int z) {
        double lat = this.projection.lat(z + 0.5), lon = this.projection.lon(x + 0.5);
        double e = this.data.elevation(lat, lon);
        int sea = this.settings.seaLevel();
        dev.esiliw.aquarium.data.OsmWater.Sample w = this.data.osm(lat, lon);
        byte cls = w.known() ? w.cls() : (e < 0 ? dev.esiliw.aquarium.data.OsmWater.OCEAN : dev.esiliw.aquarium.data.OsmWater.LAND);
        double vs = this.settings.verticalScale();

        if (cls == dev.esiliw.aquarium.data.OsmWater.OCEAN) {
            // real bathymetry, but never shallower than a gentle shelf away from the OSM coastline
            double minDepth = w.known() ? Math.min(1.5 + w.dist() * 0.03, 60.0) : 2.0;
            // near the coast the fine data has the real detail; further out blend to the smooth coarse sea floor
            double far = Math.min(1.0, Math.max(0.0, (w.dist() - 300.0) / 1200.0));
            if (far > 0) e = e * (1 - far) + this.data.seaFloor(lat, lon) * far;
            minDepth *= 1.0 + 0.15 * dev.esiliw.aquarium.data.Noise.smooth(x / 37.0, z / 37.0, 21);
            e = Math.min(e, -minDepth);
            return new Column(Math.min(this.surfaceY(e), sea - 2), sea - 1, e, Cover.WATER, true, false, lat);
        }
        if (cls != dev.esiliw.aquarium.data.OsmWater.LAND) {
            // lakes and rivers: flat surface at the lowest nearby ground, bed deepening away from the bank
            double level = this.data.waterLevel(lat, lon);
            int waterTop = level < 0.5 ? sea - 1 : this.surfaceY(level) - 1;
            double depthM = switch (cls) {
                case dev.esiliw.aquarium.data.OsmWater.STREAM -> 1.0;
                case dev.esiliw.aquarium.data.OsmWater.RIVER -> Math.min(1.5 + w.dist() * 0.08, 10.0);
                default -> Math.min(2.0 + w.dist() * 0.06, 40.0);
            };
            int depth = Math.max(1, (int) Math.round(depthM / vs));
            int top = Math.min(this.surfaceY(Math.max(e, 0.5)), waterTop - depth);
            return new Column(top, waterTop, e, Cover.WATER, false, true, lat);
        }
        e = Math.max(e, 0.5);
        Cover cover = this.data.cover(lat, lon);
        if (cover == Cover.WATER) cover = Cover.NO;
        int top = this.surfaceY(Math.max(0.5, e + 0.35 * vs * dev.esiliw.aquarium.data.Noise.smooth(x / 9.0, z / 9.0, 31)));
        int buildingTop = Integer.MIN_VALUE;
        if (!Double.isNaN(w.buildingBase())) {
            // flat roof: ground at the building's centre + its height, in true proportion to the footprint
            int floors = Math.max(1, (int) Math.round(w.buildingHeight() / this.projection.metersPerBlock(lat)));
            int roof = this.surfaceY(Math.max(0.5, w.buildingBase())) + floors;
            if (roof > top) buildingTop = roof;
        }
        return new Column(top, Integer.MIN_VALUE, e, cover, false, false, lat, w.way(), buildingTop, dev.esiliw.aquarium.data.Climate.at(lat, lon));
    }
}
