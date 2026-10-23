package dev.nostalgiacraft.skills.compat.ironsspellbooks;

import dev.nostalgiacraft.skills.compat.OptionalEvents;
import dev.nostalgiacraft.skills.compat.Reflection;
import dev.nostalgiacraft.skills.effects.Triggers;
import dev.nostalgiacraft.skills.graph.SkillEffect;
import dev.nostalgiacraft.skills.registry.ModAttributes;
import dev.nostalgiacraft.skills.server.Discoveries;
import dev.nostalgiacraft.skills.text.EffectPhrases;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class IronsSpellbooksCompat {
    public static final String MOD_ID = "irons_spellbooks";
    private static final String EVENTS = "io.redspace.ironsspellbooks.api.events.";
    private static final String SPELL_DISCOVERY = "nostalgiaskills:spell/";
    private static final String MAGIC_DATA = "io.redspace.ironsspellbooks.api.magic.MagicData";

    private IronsSpellbooksCompat() {
    }

    public static void register() {
        Reflection.requireTypes(MAGIC_DATA);
        OptionalEvents.listen(MOD_ID, EVENTS + "SpellOnCastEvent", IronsSpellbooksCompat::onSpellCast);
        OptionalEvents.listen(MOD_ID, EVENTS + "ChangeManaEvent", IronsSpellbooksCompat::onManaChange);
        OptionalEvents.listen(MOD_ID, EVENTS + "SpellCooldownAddedEvent$Pre", IronsSpellbooksCompat::onCooldownAdded);
        Triggers.registerAction("restore_mana", (player, action) ->
                OptionalEvents.guard(MOD_ID, () -> restoreMana(player, action)));
        Triggers.registerAction("blood_for_mana", (player, action) ->
                OptionalEvents.guard(MOD_ID, () -> payManaWithBlood(player, action)));
    }

    private static void onSpellCast(PlayerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Triggers.fire(player, Triggers.ON_SPELL_CAST, null);
            String spell = (String) Reflection.call(event, "getSpellId");
            Discoveries.reveal(player, SPELL_DISCOVERY + spell.replace(':', '/'));
        }
    }

    private static void onManaChange(PlayerEvent event) {
        boolean emptied = (float) Reflection.call(event, "getOldMana") > 0 && (float) Reflection.call(event, "getNewMana") <= 0;
        if (emptied && event.getEntity() instanceof ServerPlayer player) {
            Triggers.fire(player, Triggers.ON_MANA_EMPTY, null);
        }
    }

    private static void onCooldownAdded(Event event) {
        boolean echoes = Reflection.call(event, "getEntity") instanceof ServerPlayer player
                && player.getRandom().nextDouble() < player.getAttributeValue(ModAttributes.SPELL_ECHO_CHANCE);
        if (echoes) {
            ((ICancellableEvent) event).setCanceled(true);
        }
    }

    private static void payManaWithBlood(ServerPlayer player, SkillEffect.Action action) {
        float price = (float) (action.amount() / EffectPhrases.MANA_PER_HEALTH);
        if (player.getHealth() > price) {
            player.setHealth(player.getHealth() - price);
            restoreMana(player, action);
        }
    }

    private static void restoreMana(ServerPlayer player, SkillEffect.Action action) {
        Object magicData = Reflection.callStatic(MAGIC_DATA, "getPlayerMagicData", player);
        Reflection.call(magicData, "addMana", (float) action.amount());
    }
}
