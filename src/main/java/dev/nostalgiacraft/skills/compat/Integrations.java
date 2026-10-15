package dev.nostalgiacraft.skills.compat;

import dev.nostalgiacraft.skills.compat.apotheosis.ApotheosisCompat;
import dev.nostalgiacraft.skills.compat.arsnouveau.ArsNouveauCompat;
import dev.nostalgiacraft.skills.compat.bettercombat.BetterCombatCompat;
import dev.nostalgiacraft.skills.compat.bettermobcombat.BetterMobCombatCompat;
import dev.nostalgiacraft.skills.compat.champions.ChampionsCompat;
import dev.nostalgiacraft.skills.compat.curios.CuriosCompat;
import dev.nostalgiacraft.skills.compat.dummmmmmy.DummmmmmyCompat;
import dev.nostalgiacraft.skills.compat.enigmaticlegacy.EnigmaticLegacyCompat;
import dev.nostalgiacraft.skills.compat.forbiddenarcanus.ForbiddenArcanusCompat;
import dev.nostalgiacraft.skills.compat.ironsspellbooks.IronsSpellbooksCompat;
import dev.nostalgiacraft.skills.compat.l2hostility.L2HostilityCompat;
import dev.nostalgiacraft.skills.compat.mowziesmobs.MowziesMobsCompat;
import dev.nostalgiacraft.skills.compat.occultism.OccultismCompat;
import dev.nostalgiacraft.skills.compat.pandora.PandoraCompat;
import dev.nostalgiacraft.skills.compat.parcool.ParCoolCompat;
import dev.nostalgiacraft.skills.compat.relics.RelicsCompat;
import dev.nostalgiacraft.skills.compat.reliquary.ReliquaryCompat;
import dev.nostalgiacraft.skills.compat.scguns.ScorchedGunsCompat;
import net.neoforged.fml.ModList;

import java.util.Map;
import java.util.Set;

public final class Integrations {
    private static final Map<String, Runnable> REGISTRATION_BY_MOD = Map.ofEntries(
            Map.entry(IronsSpellbooksCompat.MOD_ID, IronsSpellbooksCompat::register),
            Map.entry(ArsNouveauCompat.MOD_ID, ArsNouveauCompat::register),
            Map.entry(ParCoolCompat.MOD_ID, ParCoolCompat::register),
            Map.entry(ScorchedGunsCompat.MOD_ID, ScorchedGunsCompat::register),
            Map.entry(DummmmmmyCompat.MOD_ID, DummmmmmyCompat::register),
            Map.entry(BetterCombatCompat.MOD_ID, BetterCombatCompat::register),
            Map.entry(CuriosCompat.MOD_ID, CuriosCompat::register),
            Map.entry(PandoraCompat.MOD_ID, PandoraCompat::register),
            Map.entry(MowziesMobsCompat.MOD_ID, MowziesMobsCompat::register),
            Map.entry(ChampionsCompat.MOD_ID, ChampionsCompat::register),
            Map.entry(L2HostilityCompat.MOD_ID, L2HostilityCompat::register),
            Map.entry(OccultismCompat.MOD_ID, OccultismCompat::register),
            Map.entry(ApotheosisCompat.MOD_ID, ApotheosisCompat::register),
            Map.entry(BetterMobCombatCompat.MOD_ID, BetterMobCombatCompat::register),
            Map.entry(RelicsCompat.MOD_ID, RelicsCompat::register),
            Map.entry(EnigmaticLegacyCompat.MOD_ID, EnigmaticLegacyCompat::register),
            Map.entry(ForbiddenArcanusCompat.MOD_ID, ForbiddenArcanusCompat::register),
            Map.entry(ReliquaryCompat.MOD_ID, ReliquaryCompat::register));

    private Integrations() {
    }

    public static Set<String> modIds() {
        return REGISTRATION_BY_MOD.keySet();
    }

    public static void register() {
        REGISTRATION_BY_MOD.forEach((mod, registration) -> {
            if (ModList.get().isLoaded(mod)) {
                OptionalEvents.guard(mod, registration);
            }
        });
    }
}
