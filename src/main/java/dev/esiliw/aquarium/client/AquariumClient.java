package dev.esiliw.aquarium.client;

import dev.esiliw.aquarium.block.AquariumBlocks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.RenderType;

public class AquariumClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockRenderLayerMap.INSTANCE.putBlock(AquariumBlocks.RICE, RenderType.cutout());
    }
}
