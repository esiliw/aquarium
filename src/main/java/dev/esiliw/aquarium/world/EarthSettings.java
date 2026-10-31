package dev.esiliw.aquarium.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.esiliw.aquarium.data.EarthData;
import dev.esiliw.aquarium.data.GeoProjection;

/**
 * Generator settings, stored in the world (level.dat) and set by the world presets.
 *
 * @param worldScale    metres per block horizontally (Terrarium's default is 40)
 * @param verticalScale metres per block vertically
 * @param heightOffset  extra blocks added to all land
 * @param seaLevel      y of the sea surface (water fills below this)
 * @param minY          bottom of the dimension (must match the dimension type)
 * @param height        total height of the dimension (must match the dimension type)
 * @param spawnLat      spawn latitude
 * @param spawnLon      spawn longitude
 * @param projection    "mercator" (true shapes) or "equirectangular" (Terrarium's original; the default so worlds made
 *                      before this setting existed keep their layout)
 * @param osmSnapshot   OpenFreeMap planet snapshot the world is pinned to (so OSM edits never change it later)
 * @param osmWays       draw OSM roads, railways and stations on the ground as a building guide (open data only)
 * @param dataSource    "open" (AWS terrain tiles, ESA WorldCover, OpenStreetMap) or "terrarium" (gegy's server; the
 *                      default so older worlds keep generating the same way)
 */
public record EarthSettings(double worldScale, double verticalScale, int heightOffset, int seaLevel, int minY, int height,
                            double spawnLat, double spawnLon, GeoProjection.Type projectionType,
                            EarthData.Source dataSource, String osmSnapshot, boolean osmWays) {
    private static final Codec<EarthData.Source> SOURCE_CODEC = Codec.STRING.comapFlatMap(s -> {
        try {
            return com.mojang.serialization.DataResult.success(EarthData.Source.valueOf(s.toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return com.mojang.serialization.DataResult.error(() -> "Unknown data source " + s + " (use open or terrarium)");
        }
    }, t -> t.name().toLowerCase(java.util.Locale.ROOT));
    private static final Codec<GeoProjection.Type> TYPE_CODEC = Codec.STRING.comapFlatMap(s -> {
        try {
            return com.mojang.serialization.DataResult.success(GeoProjection.Type.valueOf(s.toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return com.mojang.serialization.DataResult.error(() -> "Unknown projection " + s + " (use mercator or equirectangular)");
        }
    }, t -> t.name().toLowerCase(java.util.Locale.ROOT));

    /** default start: Malmö, Sweden */
    public static final double MALMO_LAT = 55.6050, MALMO_LON = 13.0038;
    public static final int TALL_MIN_Y = -512, TALL_HEIGHT = 2544;

    /** the settings a new world starts from */
    public static EarthSettings defaults() {
        return new EarthSettings(10, autoVerticalScale(10), 0, 63, TALL_MIN_Y, TALL_HEIGHT, MALMO_LAT, MALMO_LON,
                GeoProjection.Type.MERCATOR, EarthData.Source.OPEN, dev.esiliw.aquarium.data.OsmWater.DEFAULT_SNAPSHOT, true);
    }

    /** metres per block vertically when not set: like the horizontal scale, but at least 5 so Mount Everest fits */
    public static double autoVerticalScale(double worldScale) {
        return Math.max(5.0, worldScale);
    }

    public static final Codec<EarthSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.doubleRange(0.1, 100000).optionalFieldOf("world_scale", 40.0).forGetter(EarthSettings::worldScale),
            Codec.doubleRange(0.1, 100000).optionalFieldOf("vertical_scale", 40.0).forGetter(EarthSettings::verticalScale),
            Codec.INT.optionalFieldOf("height_offset", 0).forGetter(EarthSettings::heightOffset),
            Codec.INT.optionalFieldOf("sea_level", 63).forGetter(EarthSettings::seaLevel),
            Codec.INT.optionalFieldOf("min_y", -64).forGetter(EarthSettings::minY),
            Codec.INT.optionalFieldOf("height", 384).forGetter(EarthSettings::height),
            Codec.doubleRange(-90, 90).optionalFieldOf("spawn_lat", MALMO_LAT).forGetter(EarthSettings::spawnLat),
            Codec.doubleRange(-180, 180).optionalFieldOf("spawn_lon", MALMO_LON).forGetter(EarthSettings::spawnLon),
            TYPE_CODEC.optionalFieldOf("projection", GeoProjection.Type.EQUIRECTANGULAR).forGetter(EarthSettings::projectionType),
            SOURCE_CODEC.optionalFieldOf("data_source", EarthData.Source.TERRARIUM).forGetter(EarthSettings::dataSource),
            Codec.STRING.optionalFieldOf("osm_snapshot", dev.esiliw.aquarium.data.OsmWater.DEFAULT_SNAPSHOT).forGetter(EarthSettings::osmSnapshot),
            Codec.BOOL.optionalFieldOf("osm_ways", false).forGetter(EarthSettings::osmWays)
    ).apply(i, EarthSettings::new));

    public GeoProjection projection() {
        return new GeoProjection(this.worldScale, this.projectionType);
    }

    public EarthData createData() {
        // Mercator blocks get smaller away from the equator: pick data detail for ~60 degrees latitude
        if (this.dataSource == EarthData.Source.OPEN) return EarthData.get(this.worldScale, EarthData.Source.OPEN, this.osmSnapshot, this.osmWays);
        return EarthData.forScale(this.projectionType == GeoProjection.Type.MERCATOR ? this.worldScale * 0.5 : this.worldScale);
    }

    public int maxY() {
        return this.minY + this.height - 1;
    }
}
