/*
 * Aquarium - a Fabric 1.20.1 port of Terrarium by gegy (https://github.com/gegy1000/Terrarium), LGPL-3.0.
 */
package dev.esiliw.aquarium;

import dev.esiliw.aquarium.command.GeoCommands;
import dev.esiliw.aquarium.world.EarthBiomeSource;
import dev.esiliw.aquarium.world.EarthChunkGenerator;
import dev.esiliw.aquarium.world.SpawnHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class Aquarium implements ModInitializer {
    public static final String ID = "aquarium";
    public static final Logger LOGGER = LoggerFactory.getLogger("Aquarium");
    public static final String VERSION = FabricLoader.getInstance().getModContainer(ID)
            .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");

    /** downloads and geocoding run here so world generation threads aren't all stuck on the network */
    public static final ExecutorService IO_EXECUTOR = Executors.newFixedThreadPool(6, new java.util.concurrent.ThreadFactory() {
        private final AtomicInteger n = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "aquarium-io-" + this.n.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, new ResourceLocation(ID, "earth"), EarthChunkGenerator.CODEC);
        Registry.register(BuiltInRegistries.BIOME_SOURCE, new ResourceLocation(ID, "earth"), EarthBiomeSource.CODEC);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> GeoCommands.register(dispatcher));
        // after vanilla has picked its own initial spawn, move it to the configured real-world place (once per world)
        ServerLifecycleEvents.SERVER_STARTED.register(server -> SpawnHandler.onWorldLoad(server, server.overworld()));
        LOGGER.info("Aquarium {} loaded - Earth data from the Terrarium geo3 server", VERSION);
    }
}
