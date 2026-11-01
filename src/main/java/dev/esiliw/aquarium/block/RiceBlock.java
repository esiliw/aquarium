package dev.esiliw.aquarium.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Rice: two blocks tall, the lower half standing in water (it holds the water itself, so harvesting leaves the paddy
 * flooded), the upper half above the surface. Eight growth stages like wheat.
 */
public class RiceBlock extends DoublePlantBlock implements LiquidBlockContainer, BonemealableBlock {
    public static final int MAX_AGE = 7;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_7;

    public RiceBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER).setValue(AGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF, AGE);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(BlockTags.DIRT) || state.is(Blocks.FARMLAND) || state.is(Blocks.MUD) || state.is(Blocks.CLAY)
                || state.is(BlockTags.SAND);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        FluidState fluid = level.getFluidState(pos);
        // planted into one block of still water, with open air above for the upper half
        if (!fluid.is(Fluids.WATER) || !fluid.isSource()) return null;
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).isAir()) return null;
        return this.defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        level.setBlock(pos.above(), this.defaultBlockState().setValue(HALF, DoubleBlockHalf.UPPER).setValue(AGE, state.getValue(AGE)), 3);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
        return this.mayPlaceOn(level.getBlockState(pos.below()), level, pos.below());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BlockState result = super.updateShape(state, dir, neighbor, level, pos, neighborPos);
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            if (!result.is(this)) return Blocks.WATER.defaultBlockState();       // the plant is gone, the water stays
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return result;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockPos lp = lowerOf(state, pos);
            BlockState l = level.getBlockState(lp);
            boolean plant = l.is(this) && l.getValue(HALF) == DoubleBlockHalf.LOWER;
            if (player.isCreative()) {
                // breaking the top in creative: leave water where the bottom stood, not air
                if (plant && state.getValue(HALF) == DoubleBlockHalf.UPPER) level.setBlock(lp, Blocks.WATER.defaultBlockState(), 35);
            } else if (plant) {
                // one harvest per plant, whichever half is hit (the loot table goes by the age)
                Block.dropResources(l, level, lp, null, player, player.getMainHandItem());
            }
        }
        // DoublePlantBlock's own handling would leave air in the paddy, so only the plain block effects here
        level.levelEvent(player, 2001, pos, Block.getId(state));
        level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.BLOCK_DESTROY, pos, net.minecraft.world.level.gameevent.GameEvent.Context.of(player, state));
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public boolean canPlaceLiquid(BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        return false;
    }

    // ------------------------------------------------------------------ growth

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER && state.getValue(AGE) < MAX_AGE;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getRawBrightness(pos.above(), 0) >= 9 && random.nextInt(4) == 0) this.grow(level, pos, state, 1);
    }

    private void grow(Level level, BlockPos lower, BlockState state, int by) {
        int age = Math.min(MAX_AGE, state.getValue(AGE) + by);
        level.setBlock(lower, state.setValue(AGE, age), 2);
        BlockState up = level.getBlockState(lower.above());
        if (up.is(this)) level.setBlock(lower.above(), up.setValue(AGE, age), 2);
    }

    private static BlockPos lowerOf(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, boolean client) {
        return state.getValue(AGE) < MAX_AGE;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        BlockPos lower = lowerOf(state, pos);
        BlockState l = level.getBlockState(lower);
        if (l.is(this)) this.grow(level, lower, l, 2 + random.nextInt(3));
    }
}
