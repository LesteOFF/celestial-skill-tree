package dev.lesteoff.celestialskilltree.client;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.client.screen.SkillTreeScreen;
import dev.lesteoff.celestialskilltree.compat.Reflection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Optional;
import java.util.stream.Stream;

@EventBusSubscriber(modid = CelestialSkillTree.MODID, value = Dist.CLIENT)
public final class LegendaryTabsQa {
    private static final boolean ENABLED = Boolean.getBoolean("celestialskilltree.legendarytabs");
    private static final String REPORT_FILE = "celestialskilltree_legendarytabs.txt";
    private static final String SHOT_PREFIX = "celestialskilltree_legendarytabs_";
    private static final String PNG = ".png";
    private static final String TAB_BUTTON = "sfiomn.legendarytabs.client.screens.TabButton";
    private static final String TAB_ID = "celestial_skill_tree";
    private static final String TAB_TOOLTIP = "Celestial Skill Tree";
    private static final String TAB_ICON = "celestialskilltree:textures/gui/legendary_tab.png";
    private static final int SETTLE_TICKS = 20;
    private static final int OPEN_TICKS = 100;

    private static final String GALLERY_SHOT = "gallery_legendary_tabs.png";

    private static QaScript script;
    private static boolean sawNoScreen;

    private LegendaryTabsQa() {
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
        QaScript qa = new QaScript("Legendary Tabs QA", REPORT_FILE, InWorldQa::situation);
        new InWorldQa(qa).enterWorld();
        qa.then("check Legendary Tabs is loaded", () -> qa.expect("Legendary Tabs is loaded", Reflection.exists(TAB_BUTTON)));
        qa.step("open the inventory at the gallery resolution", () -> {
            openInventory();
            VisualQa.resize(VisualQa.GALLERY_RESOLUTION);
            Minecraft.getInstance().options.hideGui = true;
        }, () -> qa.ticks() >= SETTLE_TICKS, 2 * SETTLE_TICKS);
        qa.then("find the skill tree tab", () -> {
            qa.note("tabs on the inventory: " + tabIds());
            qa.expect("the inventory shows the skill tree tab", skillTreeTab().isPresent());
            Object tab = Reflection.field(skillTreeTab().orElseThrow(), "tabBase");
            qa.expect("the tab tooltip", TAB_TOOLTIP, ((Component) Reflection.call(tab, "getTooltip")).getString());
            qa.expect("the tab icon", TAB_ICON, Reflection.call(tab, "getIconTexture").toString());
        });
        qa.step("hover the skill tree tab", LegendaryTabsQa::hoverSkillTreeTab, () -> qa.ticks() >= SETTLE_TICKS, 2 * SETTLE_TICKS);
        qa.then("capture the inventory", () -> qa.capture(GALLERY_SHOT));
        qa.step("click the skill tree tab", () -> {
            Minecraft.getInstance().options.hideGui = false;
            clickSkillTreeTab();
        }, LegendaryTabsQa::treeOpened, OPEN_TICKS);
        qa.pause("let the tree settle", SETTLE_TICKS);
        qa.then("capture the tree", () -> {
            qa.expect("the tab opened the skill tree", Minecraft.getInstance().screen instanceof SkillTreeScreen);
            qa.expect("the tab opened the tree without closing the inventory first", !sawNoScreen);
            qa.capture(SHOT_PREFIX + "tree" + PNG);
        });
        return qa;
    }

    private static void openInventory() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new InventoryScreen(minecraft.player));
    }

    private static Optional<AbstractWidget> skillTreeTab() {
        return tabButtons().filter(button -> TAB_ID.equals(tabId(button))).findFirst();
    }

    private static String tabIds() {
        return tabButtons().map(LegendaryTabsQa::tabId).toList().toString();
    }

    private static Stream<AbstractWidget> tabButtons() {
        return Minecraft.getInstance().screen.children().stream()
                .filter(child -> Reflection.isInstance(TAB_BUTTON, child))
                .map(AbstractWidget.class::cast);
    }

    private static String tabId(AbstractWidget button) {
        return (String) Reflection.call(Reflection.field(button, "tabBase"), "getId");
    }

    private static void hoverSkillTreeTab() {
        AbstractWidget tab = skillTreeTab().orElseThrow();
        Minecraft minecraft = Minecraft.getInstance();
        double scale = minecraft.getWindow().getGuiScale();
        ObfuscationReflectionHelper.setPrivateValue(MouseHandler.class, minecraft.mouseHandler, (tab.getX() + tab.getWidth() / 2.0) * scale, "xpos");
        ObfuscationReflectionHelper.setPrivateValue(MouseHandler.class, minecraft.mouseHandler, (tab.getY() + tab.getHeight() / 2.0) * scale, "ypos");
    }

    private static boolean treeOpened() {
        Screen screen = Minecraft.getInstance().screen;
        sawNoScreen |= screen == null;
        return screen instanceof SkillTreeScreen;
    }

    private static void clickSkillTreeTab() {
        AbstractWidget tab = skillTreeTab().orElseThrow();
        Minecraft.getInstance().screen.mouseClicked(tab.getX() + tab.getWidth() / 2.0, tab.getY() + tab.getHeight() / 2.0, 0);
    }
}
