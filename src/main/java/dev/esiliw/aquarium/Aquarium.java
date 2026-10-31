/*
 * Aquarium - a Fabric 1.20.1 port of Terrarium by gegy (https://github.com/gegy1000/Terrarium), LGPL-3.0.
 */
package dev.esiliw.aquarium;

import dev.esiliw.aquarium.command.GeoCommands;
import dev.esiliw.aquarium.world.EarthBiomeSource;
import dev.esiliw.aquarium.world.EarthChunkGenerator;
import dev.esiliw.aquarium.world.SpawnHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.data.worldgen.placement.MiscOverworldPlacements;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Aquarium implements ModInitializer {
    public static final String ID = "aquarium";
    public static final Logger LOGGER = LoggerFactory.getLogger("Aquarium");
    public static final String VERSION = FabricLoader.getInstance().getModContainer(ID)
            .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");

    /** downloads and geocoding run here so world generation threads aren't all stuck on the network */
    public static final java.util.concurrent.ExecutorService IO_EXECUTOR = dev.esiliw.aquarium.data.DataEnv.IO_EXECUTOR;

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, new ResourceLocation(ID, "earth"), EarthChunkGenerator.CODEC);
        Registry.register(BuiltInRegistries.BIOME_SOURCE, new ResourceLocation(ID, "earth"), EarthBiomeSource.CODEC);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> GeoCommands.register(dispatcher));
        // after vanilla has picked its own initial spawn, move it to the configured real-world place (once per world)
        ServerLifecycleEvents.SERVER_STARTED.register(server -> SpawnHandler.onWorldLoad(server, server.overworld()));
        // no lava lakes on (or just under) the real Earth's surface
        BiomeModifications.create(new ResourceLocation(ID, "no_lava_lakes")).add(ModificationPhase.REMOVALS,
                BiomeSelectors.foundInOverworld(), ctx -> {
                    ctx.getGenerationSettings().removeFeature(GenerationStep.Decoration.LAKES, MiscOverworldPlacements.LAKE_LAVA_SURFACE);
                    ctx.getGenerationSettings().removeFeature(GenerationStep.Decoration.LAKES, MiscOverworldPlacements.LAKE_LAVA_UNDERGROUND);
                });
        LOGGER.info("Aquarium {} loaded - Earth data from the Terrarium geo3 server", VERSION);
    }
}
