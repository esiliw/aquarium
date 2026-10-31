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
 */
public record EarthSettings(double worldScale, double verticalScale, int heightOffset, int seaLevel, int minY, int height,
                            double spawnLat, double spawnLon) {
    public static final Codec<EarthSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.doubleRange(0.1, 100000).optionalFieldOf("world_scale", 40.0).forGetter(EarthSettings::worldScale),
            Codec.doubleRange(0.1, 100000).optionalFieldOf("vertical_scale", 40.0).forGetter(EarthSettings::verticalScale),
            Codec.INT.optionalFieldOf("height_offset", 0).forGetter(EarthSettings::heightOffset),
            Codec.INT.optionalFieldOf("sea_level", 63).forGetter(EarthSettings::seaLevel),
            Codec.INT.optionalFieldOf("min_y", -64).forGetter(EarthSettings::minY),
            Codec.INT.optionalFieldOf("height", 384).forGetter(EarthSettings::height),
            Codec.doubleRange(-90, 90).optionalFieldOf("spawn_lat", 37.9122).forGetter(EarthSettings::spawnLat),
            Codec.doubleRange(-180, 180).optionalFieldOf("spawn_lon", 139.0613).forGetter(EarthSettings::spawnLon)
    ).apply(i, EarthSettings::new));

    public GeoProjection projection() {
        return new GeoProjection(this.worldScale);
    }

    public EarthData createData() {
        return EarthData.forScale(this.worldScale);
    }

    public int maxY() {
        return this.minY + this.height - 1;
    }
}
