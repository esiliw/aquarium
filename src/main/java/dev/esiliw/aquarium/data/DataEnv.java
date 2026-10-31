package dev.esiliw.aquarium.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared bits of the data layer that must also work outside Minecraft (tests / diagnostics). */
public final class DataEnv {
    public static final Logger LOGGER = LoggerFactory.getLogger("Aquarium");

    public static final ExecutorService IO_EXECUTOR = Executors.newFixedThreadPool(6, new java.util.concurrent.ThreadFactory() {
        private final AtomicInteger n = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "aquarium-io-" + this.n.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    private DataEnv() {
    }

    public static String version() {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("aquarium")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
        } catch (Throwable t) {
            return "dev";
        }
    }

    public static Path cacheRoot() {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("aquarium-cache");
        } catch (Throwable t) {
            return Path.of(System.getProperty("aquarium.cache", "build/aquarium-cache"));
        }
    }
}
