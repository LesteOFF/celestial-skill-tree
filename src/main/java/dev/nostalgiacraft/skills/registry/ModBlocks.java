package dev.nostalgiacraft.skills.registry;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.block.TelescopeBlock;
import dev.nostalgiacraft.skills.block.XpBasinBlock;
import dev.nostalgiacraft.skills.block.XpBasinBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NostalgiaSkills.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,
            NostalgiaSkills.MODID);

    public static final DeferredBlock<XpBasinBlock> STELLAR_CONDENSER = BLOCKS.registerBlock("stellar_condenser", XpBasinBlock::new,
            basinProperties(MapColor.COLOR_PURPLE));
    public static final DeferredBlock<XpBasinBlock> COMMUNITY_BASIN = BLOCKS.registerBlock("community_basin", XpBasinBlock::new,
            basinProperties(MapColor.COLOR_CYAN));
    public static final DeferredBlock<TelescopeBlock> TELESCOPE = BLOCKS.registerBlock("telescope", TelescopeBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(2f).sound(SoundType.COPPER).noOcclusion());
    public static final DeferredItem<BlockItem> TELESCOPE_ITEM = ModItems.ITEMS.registerSimpleBlockItem(TELESCOPE);
    public static final DeferredItem<BlockItem> STELLAR_CONDENSER_ITEM = ModItems.ITEMS.registerSimpleBlockItem(STELLAR_CONDENSER);
    public static final DeferredItem<BlockItem> COMMUNITY_BASIN_ITEM = ModItems.ITEMS.registerSimpleBlockItem(COMMUNITY_BASIN);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<XpBasinBlockEntity>> XP_BASIN_ENTITY = BLOCK_ENTITIES.register(
            "xp_basin", () -> BlockEntityType.Builder.of(XpBasinBlockEntity::new, STELLAR_CONDENSER.get(), COMMUNITY_BASIN.get()).build(null));

    private ModBlocks() {
    }

    private static BlockBehaviour.Properties basinProperties(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(3.5f, 6f).requiresCorrectToolForDrops().sound(SoundType.AMETHYST);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(ModBlocks::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, XP_BASIN_ENTITY.get(), (basin, side) -> basin.tank());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, XP_BASIN_ENTITY.get(), (basin, side) -> basin.output());
    }
}
