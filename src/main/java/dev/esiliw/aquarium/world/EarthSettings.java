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
 */
public record EarthSettings(double worldScale, double verticalScale, int heightOffset, int seaLevel, int minY, int height,
                            double spawnLat, double spawnLon, GeoProjection.Type projectionType) {
    private static final Codec<GeoProjection.Type> TYPE_CODEC = Codec.STRING.comapFlatMap(s -> {
        try {
            return com.mojang.serialization.DataResult.success(GeoProjection.Type.valueOf(s.toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return com.mojang.serialization.DataResult.error(() -> "Unknown projection " + s + " (use mercator or equirectangular)");
        }
    }, t -> t.name().toLowerCase(java.util.Locale.ROOT));

    public static final Codec<EarthSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.doubleRange(0.1, 100000).optionalFieldOf("world_scale", 40.0).forGetter(EarthSettings::worldScale),
            Codec.doubleRange(0.1, 100000).optionalFieldOf("vertical_scale", 40.0).forGetter(EarthSettings::verticalScale),
            Codec.INT.optionalFieldOf("height_offset", 0).forGetter(EarthSettings::heightOffset),
            Codec.INT.optionalFieldOf("sea_level", 63).forGetter(EarthSettings::seaLevel),
            Codec.INT.optionalFieldOf("min_y", -64).forGetter(EarthSettings::minY),
            Codec.INT.optionalFieldOf("height", 384).forGetter(EarthSettings::height),
            Codec.doubleRange(-90, 90).optionalFieldOf("spawn_lat", 37.9122).forGetter(EarthSettings::spawnLat),
            Codec.doubleRange(-180, 180).optionalFieldOf("spawn_lon", 139.0613).forGetter(EarthSettings::spawnLon),
            TYPE_CODEC.optionalFieldOf("projection", GeoProjection.Type.EQUIRECTANGULAR).forGetter(EarthSettings::projectionType)
    ).apply(i, EarthSettings::new));

    public GeoProjection projection() {
        return new GeoProjection(this.worldScale, this.projectionType);
    }

    public EarthData createData() {
        // Mercator blocks get smaller away from the equator: pick data detail for ~60 degrees latitude
        return EarthData.forScale(this.projectionType == GeoProjection.Type.MERCATOR ? this.worldScale * 0.5 : this.worldScale);
    }

    public int maxY() {
        return this.minY + this.height - 1;
    }
}
