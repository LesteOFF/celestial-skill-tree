package dev.lesteoff.celestialskilltree.block;

import com.mojang.serialization.MapCodec;
import dev.lesteoff.celestialskilltree.network.Payloads;
import dev.lesteoff.celestialskilltree.progression.WorldClock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

public final class TelescopeBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<TelescopeBlock> CODEC = simpleCodec(TelescopeBlock::new);
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 16, 12);

    public TelescopeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer stargazer) {
            lookThrough(level, pos, stargazer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private static void lookThrough(Level level, BlockPos pos, ServerPlayer stargazer) {
        boolean starsVisible = level.dimension() == Level.OVERWORLD && WorldClock.isNight(level.getDayTime())
                && level.canSeeSky(pos.above());
        if (starsVisible) {
            PacketDistributor.sendToPlayer(stargazer, new Payloads.Stargaze());
        } else {
            stargazer.displayClientMessage(Component.translatable("message.celestialskilltree.telescope.no_stars"), true);
        }
    }
}
