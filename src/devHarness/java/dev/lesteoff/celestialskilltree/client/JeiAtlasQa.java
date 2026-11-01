package dev.lesteoff.celestialskilltree.client;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.registry.ModBlocks;
import dev.lesteoff.celestialskilltree.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = CelestialSkillTree.MODID, value = Dist.CLIENT)
public final class JeiAtlasQa {
    private static final boolean ENABLED = Boolean.getBoolean("celestialskilltree.atlas");
    private static final String REPORT_FILE = "celestialskilltree_jei_atlas.txt";
    private static final int RUNTIME_TICKS = 400;
    private static final int SETTLE_TICKS = 20;

    private static QaScript script;

    private JeiAtlasQa() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!ENABLED) {
            return;
        }
        if (script == null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.getOverlay() != null || minecraft.screen == null) {
                return;
            }
            script = plan();
        }
        script.tick();
    }

    private static QaScript plan() {
        QaScript qa = new QaScript("JEI Atlas QA", REPORT_FILE, InWorldQa::situation);
        new InWorldQa(qa).enterWorld();
        qa.waitFor("wait for JEI", JeiAtlasPages::ready, RUNTIME_TICKS);
        qa.then("check every item has a page", () -> ModItems.ITEMS.getEntries().forEach(item ->
                qa.expect("JEI has a page for " + item.getId().getPath(), JeiAtlasPages.hasPage(new ItemStack(item.get())))));
        addPage(qa, ModItems.STAR_FRAGMENT, "gallery_jei_atlas.png");
        addPage(qa, ModBlocks.TELESCOPE_ITEM, "celestialskilltree_jei_atlas_telescope.png");
        return qa;
    }

    private static void addPage(QaScript qa, ItemLike item, String shot) {
        qa.step("open the page of " + shot, () -> {
            VisualQa.resize(VisualQa.GALLERY_RESOLUTION);
            Minecraft.getInstance().options.hideGui = true;
            JeiAtlasPages.show(new ItemStack(item));
        }, () -> qa.ticks() >= SETTLE_TICKS, 2 * SETTLE_TICKS);
        qa.then("capture " + shot, () -> qa.capture(shot));
    }
}
