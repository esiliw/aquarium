package dev.esiliw.aquarium.block;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowlFoodItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SandBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

/** Aquarium's own blocks: black volcanic sand, salt crust for salt flats, and rice for flooded paddies. */
public final class AquariumBlocks {
    public static final Block BLACK_SAND = block("black_sand", new SandBlock(0x1E1C1F,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).instrument(NoteBlockInstrument.SNARE).strength(0.5f).sound(SoundType.SAND)));
    public static final Block SALT_CRUST = block("salt_crust", new Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.6f).sound(SoundType.CALCITE)));
    public static final Block RICE = Registry.register(BuiltInRegistries.BLOCK, id("rice"), new RiceBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().randomTicks().instabreak().sound(SoundType.CROP)
                    .pushReaction(PushReaction.DESTROY)));

    public static final Item BLACK_SAND_ITEM = item("black_sand", new BlockItem(BLACK_SAND, new Item.Properties()));
    public static final Item SALT_CRUST_ITEM = item("salt_crust", new BlockItem(SALT_CRUST, new Item.Properties()));
    /** the grain is also what you plant, like carrots */
    public static final Item RICE_ITEM = item("rice", new ItemNameBlockItem(RICE, new Item.Properties()));
    public static final Item RICE_BOWL = item("rice_bowl", new BowlFoodItem(new Item.Properties().stacksTo(1)
            .food(new FoodProperties.Builder().nutrition(6).saturationMod(0.6f).build())));

    private AquariumBlocks() {
    }

    private static ResourceLocation id(String name) {
        return new ResourceLocation("aquarium", name);
    }

    private static Block block(String name, Block b) {
        return Registry.register(BuiltInRegistries.BLOCK, id(name), b);
    }

    private static Item item(String name, Item i) {
        return Registry.register(BuiltInRegistries.ITEM, id(name), i);
    }

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.NATURAL_BLOCKS).register(e -> {
            e.accept(BLACK_SAND_ITEM);
            e.accept(SALT_CRUST_ITEM);
            e.accept(RICE_ITEM);
        });
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(e -> e.accept(RICE_BOWL));
    }
}
