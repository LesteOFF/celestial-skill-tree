package dev.lesteoff.celestialskilltree.server;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.api.SkillEvents;
import dev.lesteoff.celestialskilltree.data.SkillTreeManager;
import dev.lesteoff.celestialskilltree.progression.SkyEvent;
import dev.lesteoff.celestialskilltree.registry.ModAttachments;
import dev.lesteoff.celestialskilltree.registry.ModCriteria;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Set;

@EventBusSubscriber(modid = CelestialSkillTree.MODID)
public final class Milestones {
    public static final String FIRST_SKILL = "first_skill";
    public static final String KEYSTONE = "keystone";
    public static final String MANY_SKILLS = "many_skills";
    public static final String ECLIPSE = "eclipse";
    public static final String DONATION = "donation";
    private static final int MANY_SKILLS_COUNT = 50;

    private Milestones() {
    }

    public static void reach(ServerPlayer player, String milestone) {
        ModCriteria.MILESTONE.get().trigger(player, milestone);
    }

    @SubscribeEvent
    public static void onLearned(SkillEvents.NodesLearned event) {
        checkBuild(event.getPlayer());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        checkBuild(player);
        if (SkyEvents.current() == SkyEvent.ECLIPSE && player.level().dimension() == Level.OVERWORLD) {
            reach(player, ECLIPSE);
        }
    }

    @SubscribeEvent
    public static void onSkyEventChanged(SkillEvents.SkyEventChanged event) {
        if (event.getNow() == SkyEvent.ECLIPSE) {
            event.getServer().overworld().players().forEach(player -> reach(player, ECLIPSE));
        }
    }

    private static void checkBuild(ServerPlayer player) {
        Set<String> learned = ModAttachments.state(player).allocations().keySet();
        if (!learned.isEmpty()) {
            reach(player, FIRST_SKILL);
        }
        if (learned.size() >= MANY_SKILLS_COUNT) {
            reach(player, MANY_SKILLS);
        }
        if (SkillTreeManager.graph().filter(graph -> graph.anyKeystone(learned)).isPresent()) {
            reach(player, KEYSTONE);
        }
    }
}
