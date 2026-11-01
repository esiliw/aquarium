package dev.esiliw.aquarium.client;

import dev.esiliw.aquarium.block.AquariumBlocks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.RenderType;

public class AquariumClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockRenderLayerMap.INSTANCE.putBlock(AquariumBlocks.RICE, RenderType.cutout());
        for (net.minecraft.world.level.block.Block f : AquariumBlocks.FLOWERS) BlockRenderLayerMap.INSTANCE.putBlock(f, RenderType.cutout());
        for (String f : new String[]{"lavender", "edelweiss", "lupine", "hibiscus", "desert_pea"})
            BlockRenderLayerMap.INSTANCE.putBlock(net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(new net.minecraft.resources.ResourceLocation("aquarium", "potted_" + f)), RenderType.cutout());
        for (dev.esiliw.aquarium.block.AquariumWood.Wood w : dev.esiliw.aquarium.block.AquariumWood.ALL) {
            BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.cutout(), w.door(), w.trapdoor(), w.sapling(), w.pottedSapling());
            BlockRenderLayerMap.INSTANCE.putBlock(w.leaves(), RenderType.cutoutMipped());
        }
    }
}
