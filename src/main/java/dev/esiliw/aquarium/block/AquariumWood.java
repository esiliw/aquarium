package dev.esiliw.aquarium.block;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.registry.CompostingChanceRegistry;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.registry.StrippableBlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.grower.AbstractTreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

import java.util.ArrayList;
import java.util.List;

/** Palm and eucalyptus: a full vanilla-style wood set each (no signs or boats yet), plus the tree. */
public final class AquariumWood {
    public record Wood(String name, Block log, Block wood, Block strippedLog, Block strippedWood, Block planks, Block stairs, Block slab,
                       Block fence, Block fenceGate, Block door, Block trapdoor, Block button, Block pressurePlate, Block leaves,
                       Block sapling, Block pottedSapling) {
        public List<Block> building() {
            return List.of(this.log, this.wood, this.strippedLog, this.strippedWood, this.planks, this.stairs, this.slab, this.fence,
                    this.fenceGate, this.door, this.trapdoor, this.pressurePlate, this.button);
        }
    }

    public static final Wood PALM = wood("palm", true);
    public static final Wood EUCALYPTUS = wood("eucalyptus", false);
    public static final List<Wood> ALL = List.of(PALM, EUCALYPTUS);

    private AquariumWood() {
    }

    private static ResourceLocation id(String name) {
        return new ResourceLocation("aquarium", name);
    }

    private static Block register(String name, Block block, List<Item> items, boolean door) {
        Registry.register(BuiltInRegistries.BLOCK, id(name), block);
        if (items != null) {
            Item item = door ? new DoubleHighBlockItem(block, new Item.Properties()) : new BlockItem(block, new Item.Properties());
            items.add(Registry.register(BuiltInRegistries.ITEM, id(name), item));
        }
        return block;
    }

    private static Wood wood(String n, boolean onSand) {
        List<Item> items = new ArrayList<>();
        BlockSetType set = BlockSetType.OAK;
        Block log = register(n + "_log", new RotatedPillarBlock(Properties.copy(Blocks.OAK_LOG)), items, false);
        Block wood = register(n + "_wood", new RotatedPillarBlock(Properties.copy(Blocks.OAK_WOOD)), items, false);
        Block sLog = register("stripped_" + n + "_log", new RotatedPillarBlock(Properties.copy(Blocks.STRIPPED_OAK_LOG)), items, false);
        Block sWood = register("stripped_" + n + "_wood", new RotatedPillarBlock(Properties.copy(Blocks.STRIPPED_OAK_WOOD)), items, false);
        Block planks = register(n + "_planks", new Block(Properties.copy(Blocks.OAK_PLANKS)), items, false);
        Block stairs = register(n + "_stairs", new StairBlock(planks.defaultBlockState(), Properties.copy(Blocks.OAK_STAIRS)), items, false);
        Block slab = register(n + "_slab", new SlabBlock(Properties.copy(Blocks.OAK_SLAB)), items, false);
        Block fence = register(n + "_fence", new FenceBlock(Properties.copy(Blocks.OAK_FENCE)), items, false);
        Block gate = register(n + "_fence_gate", new FenceGateBlock(Properties.copy(Blocks.OAK_FENCE_GATE), WoodType.OAK), items, false);
        Block door = register(n + "_door", new DoorBlock(Properties.copy(Blocks.OAK_DOOR), set), items, true);
        Block trapdoor = register(n + "_trapdoor", new TrapDoorBlock(Properties.copy(Blocks.OAK_TRAPDOOR), set), items, false);
        Block button = register(n + "_button", new ButtonBlock(Properties.copy(Blocks.OAK_BUTTON), set, 30, true), items, false);
        Block plate = register(n + "_pressure_plate", new PressurePlateBlock(PressurePlateBlock.Sensitivity.EVERYTHING,
                Properties.copy(Blocks.OAK_PRESSURE_PLATE), set), items, false);
        Block leaves = register(n + "_leaves", new LeavesBlock(Properties.copy(Blocks.OAK_LEAVES)), items, false);
        ResourceKey<ConfiguredFeature<?, ?>> tree = ResourceKey.create(Registries.CONFIGURED_FEATURE, id(n));
        AbstractTreeGrower grower = new AbstractTreeGrower() {
            @Override
            protected ResourceKey<ConfiguredFeature<?, ?>> getConfiguredFeature(RandomSource random, boolean flowers) {
                return tree;
            }
        };
        Block sapling = register(n + "_sapling", onSand
                ? new SaplingBlock(grower, Properties.copy(Blocks.OAK_SAPLING)) {
                    @Override
                    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
                        return super.mayPlaceOn(state, level, pos) || state.is(BlockTags.SAND);   // palms grow on beaches
                    }
                }
                : new SaplingBlock(grower, Properties.copy(Blocks.OAK_SAPLING)), items, false);
        Block potted = register("potted_" + n + "_sapling", new FlowerPotBlock(sapling, Properties.copy(Blocks.POTTED_OAK_SAPLING)), null, false);
        Wood w = new Wood(n, log, wood, sLog, sWood, planks, stairs, slab, fence, gate, door, trapdoor, button, plate, leaves, sapling, potted);

        StrippableBlockRegistry.register(log, sLog);
        StrippableBlockRegistry.register(wood, sWood);
        FlammableBlockRegistry f = FlammableBlockRegistry.getDefaultInstance();
        for (Block b : List.of(log, wood, sLog, sWood)) f.add(b, 5, 5);
        for (Block b : List.of(planks, stairs, slab, fence, gate)) f.add(b, 5, 20);
        f.add(leaves, 30, 60);
        CompostingChanceRegistry.INSTANCE.add(leaves.asItem(), 0.3f);
        CompostingChanceRegistry.INSTANCE.add(sapling.asItem(), 0.3f);

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.BUILDING_BLOCKS).register(e -> {
            for (Block b : w.building()) e.accept(b);
        });
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.NATURAL_BLOCKS).register(e -> {
            e.accept(log);
            e.accept(leaves);
            e.accept(sapling);
        });
        return w;
    }

    public static void init() {
        // static initialiser registers everything
    }
}
