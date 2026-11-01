package dev.esiliw.aquarium.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.esiliw.aquarium.data.EarthData;
import dev.esiliw.aquarium.data.GeoProjection;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class EarthBiomeSource extends BiomeSource {
    public static final Codec<EarthBiomeSource> CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryOps.retrieveGetter(Registries.BIOME),
            EarthSettings.CODEC.fieldOf("settings").forGetter(s -> s.settings)
    ).apply(i, i.stable(EarthBiomeSource::new)));

    private final EarthSettings settings;
    private final GeoProjection projection;
    private final EarthData data;
    private final Map<ResourceKey<Biome>, Holder<Biome>> holders = new HashMap<>();

    public EarthBiomeSource(HolderGetter<Biome> biomes, EarthSettings settings) {
        this.settings = settings;
        this.projection = settings.projection();
        this.data = settings.createData();
        for (ResourceKey<Biome> key : EarthBiomes.ALL) this.holders.put(key, biomes.getOrThrow(key));
    }

    @Override
    protected Codec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return EarthBiomes.ALL.stream().map(this.holders::get);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        double bx = (quartX << 2) + 2, bz = (quartZ << 2) + 2;
        double lat = this.projection.lat(bz), lon = this.projection.lon(bx);
        double e = this.data.elevation(lat, lon);
        Boolean outline = this.data.oceanAt(lat, lon);
        boolean ocean = outline != null ? outline : e < 0;
        Cover cover;
        if (ocean) {
            cover = Cover.WATER;
            e = Math.min(e, -2.0);
        } else {
            cover = this.data.cover(lat, lon);
            if (cover == Cover.WATER) cover = this.data.water(lat, lon).fraction() >= 0.5 ? Cover.WATER : Cover.GRASSLAND;
            e = Math.max(e, 0.5);
        }
        return this.holders.get(EarthBiomes.select(cover, e, lat, ocean, dev.esiliw.aquarium.data.Climate.at(lat, lon)));
    }
}
