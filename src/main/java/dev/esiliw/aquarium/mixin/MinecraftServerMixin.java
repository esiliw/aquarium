package dev.esiliw.aquarium.mixin;

import dev.esiliw.aquarium.world.SpawnHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** New Earth worlds start at the chosen real-world place right away, instead of vanilla's spawn search near 0, 0. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "setInitialSpawn", at = @At("HEAD"), cancellable = true)
    private static void aquarium$earthSpawn(ServerLevel level, ServerLevelData data, boolean bonusChest, boolean debug, CallbackInfo ci) {
        if (SpawnHandler.setInitialSpawn(level, data)) ci.cancel();
    }
}
