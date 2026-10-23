package dev.lesteoff.celestialskilltree.compat.champions;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.effects.StolenNodes;
import dev.lesteoff.celestialskilltree.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import top.theillusivec4.champions.api.affix.AffixType;
import top.theillusivec4.champions.api.affix.EmptyAffixData;
import top.theillusivec4.champions.api.affix.handler.HandlerRegistry;
import top.theillusivec4.champions.api.affix.handler.event.AttackEvent;
import top.theillusivec4.champions.neoforge.registry.NeoForgeAffixTypeRegistry;

public final class AstralAffix extends AffixType<EmptyAffixData> {
    private static final String NAME = "astral";
    private static final int SECONDS_PER_STRENGTH = 15;
    private static final String COOLDOWN_KEY = CelestialSkillTree.MODID + ":astral_affix";
    private static final int STEAL_COOLDOWN_TICKS = 100;

    static void register(IEventBus modBus) {
        DeferredRegister<AffixType<?>> affixes = DeferredRegister.create(NeoForgeAffixTypeRegistry.REGISTRY_KEY, CelestialSkillTree.MODID);
        affixes.register(NAME, AstralAffix::new);
        affixes.register(modBus);
    }

    @Override
    public void registerHandlers(HandlerRegistry<EmptyAffixData> registry) {
        registry.on(AttackEvent.class, (champion, data, strength, attack) -> {
            if (attack.target() instanceof ServerPlayer player && offCooldown(player)) {
                StolenNodes.stealNewest(player, Math.max(1, strength) * SECONDS_PER_STRENGTH);
            }
        });
    }

    private static boolean offCooldown(ServerPlayer player) {
        return ModAttachments.cooldowns(player).tryUse(COOLDOWN_KEY, player.serverLevel().getGameTime(), STEAL_COOLDOWN_TICKS);
    }
}
