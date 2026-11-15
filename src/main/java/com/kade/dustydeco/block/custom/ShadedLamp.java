package com.kade.dustydeco.block.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public class ShadedLamp extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty LAMPS = IntegerProperty.create("lamps", 1, 8);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final EnumProperty<AttachFace> FACE = BlockStateProperties.ATTACH_FACE;

    private static final VoxelShape SHAPE_NORTH_1 = Block.box(7, 4, 10, 9, 14, 16);
    private static final VoxelShape SHAPE_NORTH_2 = Block.box(4, 4, 10, 12, 14, 16);
    private static final VoxelShape SHAPE_FLOOR = Block.box(7, 0, 7, 9, 10, 9);

    private static final Map<Direction, VoxelShape> SHAPES_1 = buildShapes(SHAPE_NORTH_1);
    private static final Map<Direction, VoxelShape> SHAPES_2 = buildShapes(SHAPE_NORTH_2);

    private static final VoxelShape SHAPE_CEILING = Block.box(6, 0, 6, 10, 16, 10);

    private static int maxLamps(BlockState state) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> 1;
            case WALL -> 2;
            case CEILING -> 8;
        };
    }

    private static Direction supportDirection(BlockState state) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> Direction.DOWN;
            case CEILING -> Direction.UP;
            case WALL -> state.getValue(FACING).getOpposite();
        };
    }

    private static Map<Direction, VoxelShape> buildShapes(VoxelShape north) {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        VoxelShape shape = north;
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            map.put(dir, shape);
            shape = rotateY90(shape);
        }
        return map;
    }

    private static VoxelShape rotateY90(VoxelShape shape) {
        VoxelShape[] result = {Shapes.empty()};
        shape.forAllBoxes((x1, y1, z1, x2, y2, z2) ->
                result[0] = Shapes.or(result[0],
                        Shapes.box(1 - z2, y1, x1, 1 - z1, y2, x2)));
        return result[0];
    }

    public ShadedLamp(Properties properties) {
        super(properties);
        this.registerDefaultState(this.getStateDefinition().any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LAMPS, 1)
                .setValue(LIT, false)
                .setValue(FACE, AttachFace.WALL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LAMPS, LIT, FACE);
    }

    @Override
    public InteractionResult use(BlockState blockState, Level level, BlockPos blockPos, Player player, InteractionHand interactionHand, BlockHitResult blockHitResult) {
        ItemStack held = player.getItemInHand(interactionHand);
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }

        if (interactionHand != InteractionHand.MAIN_HAND || player.getItemInHand(interactionHand).is(this.asItem())) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            boolean lit = !blockState.getValue(LIT);
            level.setBlock(blockPos, blockState.setValue(LIT, lit), Block.UPDATE_ALL);
            level.playSound(null, blockPos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3f, lit ? 0.6f : 0.5f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
        return switch (blockState.getValue(FACE)) {
            case FLOOR -> SHAPE_FLOOR;
            case CEILING -> SHAPE_CEILING;
            case WALL -> (blockState.getValue(LAMPS) == 1 ? SHAPES_1 : SHAPES_2).get(blockState.getValue(FACING));
        };
    }

    @Override
    public boolean canBeReplaced(BlockState blockState, BlockPlaceContext ctx) {
        if (!ctx.isSecondaryUseActive()
                && ctx.getItemInHand().is(this.asItem())
                && blockState.getValue(LAMPS) < maxLamps(blockState)) {
            return true;
        }
        return super.canBeReplaced(blockState, ctx);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState existing = ctx.getLevel().getBlockState(ctx.getClickedPos());
        if (existing.is(this) && existing.getValue(LAMPS) < maxLamps(existing)) {
            return existing.setValue(LAMPS, existing.getValue(LAMPS) + 1);
        }

        Direction clicked = ctx.getClickedFace();
        BlockState base = this.defaultBlockState().setValue(LAMPS, 1);
        return switch (clicked) {
            case UP -> base.setValue(FACE, AttachFace.FLOOR);
            case DOWN -> base.setValue(FACE, AttachFace.CEILING);
            default -> base.setValue(FACE, AttachFace.WALL).setValue(FACING, clicked);
        };
    }

    @Override
    public boolean canSurvive(BlockState blockState, LevelReader levelReader, BlockPos blockPos) {
        Direction dir = supportDirection(blockState);
        BlockPos supportPos = blockPos.relative(dir);
        return levelReader.getBlockState(supportPos).isFaceSturdy(levelReader, supportPos, dir.getOpposite());
    }

    @Override
    public BlockState updateShape(BlockState blockState, Direction direction, BlockState blockState2, LevelAccessor levelAccessor, BlockPos blockPos, BlockPos blockPos2) {
        if (direction == supportDirection(blockState) && !blockState.canSurvive(levelAccessor, blockPos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return  super.updateShape(blockState, direction, blockState2, levelAccessor, blockPos, blockPos2);
    }
}
