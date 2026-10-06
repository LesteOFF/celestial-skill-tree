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

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(ModItems::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            e.accept(ORB_OF_RECONFIGURATION);
        }
    }

    /**
     * Using the orb only opens the reset confirmation. The orb is consumed by the server when (and only when)
     * the confirmed reset succeeds.
     */
    public static final class OrbOfReconfiguration extends Item {
        public OrbOfReconfiguration(Properties props) {
            super(props);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer sp) {
                PacketDistributor.sendToPlayer(sp, new Payloads.OpenScreen(true));
            }
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
            lines.add(Component.translatable("item.nostalgiaskills.orb_of_reconfiguration.tooltip"));
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return true;
        }
    }
}
