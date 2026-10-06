package dev.nostalgiacraft.skills.registry;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.network.Payloads;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NostalgiaSkills.MODID);

    public static final DeferredItem<Item> ORB_OF_RECONFIGURATION = ITEMS.registerItem("orb_of_reconfiguration",
            OrbOfReconfiguration::new, new Item.Properties().stacksTo(16).rarity(Rarity.RARE));

    public static final DeferredItem<Item> STAR_FRAGMENT = ITEMS.registerItem("star_fragment", StarFragmentItem::new,
            new Item.Properties().rarity(Rarity.UNCOMMON));

    public static final DeferredItem<Item> FALLEN_STAR = ITEMS.registerItem("fallen_star", FallenStarItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(ModItems::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ORB_OF_RECONFIGURATION);
            event.accept(STAR_FRAGMENT);
        }
    }

    public static final class OrbOfReconfiguration extends Item {
        public OrbOfReconfiguration(Properties props) {
            super(props);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer serverPlayer) {
                PacketDistributor.sendToPlayer(serverPlayer, new Payloads.OpenScreen(true));
            }
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext tooltipContext, List<Component> lines, TooltipFlag flag) {
            lines.add(Component.translatable("item.nostalgiaskills.orb_of_reconfiguration.tooltip"));
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return true;
        }
    }
}
