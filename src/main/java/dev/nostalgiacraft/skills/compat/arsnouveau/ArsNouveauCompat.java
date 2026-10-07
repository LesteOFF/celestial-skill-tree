package dev.nostalgiacraft.skills.compat.arsnouveau;

import dev.nostalgiacraft.skills.compat.OptionalEvents;
import dev.nostalgiacraft.skills.compat.Reflection;
import dev.nostalgiacraft.skills.effects.Triggers;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

public final class ArsNouveauCompat {
    public static final String MOD_ID = "ars_nouveau";
    private static final String EVENTS = "com.hollingsworth.arsnouveau.api.event.";

    private ArsNouveauCompat() {
    }

    public static void register() {
        OptionalEvents.listen(MOD_ID, EVENTS + "SpellCastEvent", ArsNouveauCompat::onGlyphCast);
        OptionalEvents.listen(MOD_ID, EVENTS + "SpellResolveEvent$Post", ArsNouveauCompat::onSpellResolve);
    }

    private static void onGlyphCast(LivingEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Triggers.fire(player, Triggers.ON_GLYPH_CAST, null);
        }
    }

    private static void onSpellResolve(Event event) {
        if (Reflection.field(event, "shooter") instanceof ServerPlayer player) {
            Triggers.fire(player, Triggers.ON_SPELL_RESOLVE, null);
        }
    }
}
