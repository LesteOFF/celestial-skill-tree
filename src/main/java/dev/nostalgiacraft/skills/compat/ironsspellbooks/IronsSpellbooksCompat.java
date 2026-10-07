package dev.nostalgiacraft.skills.compat.ironsspellbooks;

import dev.nostalgiacraft.skills.compat.OptionalEvents;
import dev.nostalgiacraft.skills.compat.Reflection;
import dev.nostalgiacraft.skills.effects.Triggers;
import dev.nostalgiacraft.skills.graph.SkillEffect;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class IronsSpellbooksCompat {
    public static final String MOD_ID = "irons_spellbooks";
    private static final String EVENTS = "io.redspace.ironsspellbooks.api.events.";
    private static final String MAGIC_DATA = "io.redspace.ironsspellbooks.api.magic.MagicData";

    private IronsSpellbooksCompat() {
    }

    public static void register() {
        OptionalEvents.listen(MOD_ID, EVENTS + "SpellOnCastEvent", IronsSpellbooksCompat::onSpellCast);
        OptionalEvents.listen(MOD_ID, EVENTS + "ChangeManaEvent", IronsSpellbooksCompat::onManaChange);
        Triggers.registerAction("restore_mana", (player, action) ->
                OptionalEvents.guard(MOD_ID, () -> restoreMana(player, action)));
    }

    private static void onSpellCast(PlayerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Triggers.fire(player, Triggers.ON_SPELL_CAST, null);
        }
    }

    private static void onManaChange(PlayerEvent event) {
        boolean emptied = (float) Reflection.call(event, "getOldMana") > 0 && (float) Reflection.call(event, "getNewMana") <= 0;
        if (emptied && event.getEntity() instanceof ServerPlayer player) {
            Triggers.fire(player, Triggers.ON_MANA_EMPTY, null);
        }
    }

    private static void restoreMana(ServerPlayer player, SkillEffect.Action action) {
        Object magicData = Reflection.callStatic(MAGIC_DATA, "getPlayerMagicData", player);
        Reflection.call(magicData, "addMana", (float) action.amount());
    }
}
