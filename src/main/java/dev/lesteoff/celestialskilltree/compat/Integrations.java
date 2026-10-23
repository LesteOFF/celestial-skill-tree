package dev.lesteoff.celestialskilltree.compat;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.compat.apotheosis.ApotheosisCompat;
import dev.lesteoff.celestialskilltree.compat.arsnouveau.ArsNouveauCompat;
import dev.lesteoff.celestialskilltree.compat.bettercombat.BetterCombatCompat;
import dev.lesteoff.celestialskilltree.compat.bettermobcombat.BetterMobCombatCompat;
import dev.lesteoff.celestialskilltree.compat.champions.ChampionsCompat;
import dev.lesteoff.celestialskilltree.compat.curios.CuriosCompat;
import dev.lesteoff.celestialskilltree.compat.dummmmmmy.DummmmmmyCompat;
import dev.lesteoff.celestialskilltree.compat.enigmaticlegacy.EnigmaticLegacyCompat;
import dev.lesteoff.celestialskilltree.compat.forbiddenarcanus.ForbiddenArcanusCompat;
import dev.lesteoff.celestialskilltree.compat.ironsspellbooks.IronsSpellbooksCompat;
import dev.lesteoff.celestialskilltree.compat.kubejs.CelestialSkillTreePlugin;
import dev.lesteoff.celestialskilltree.compat.l2hostility.L2HostilityCompat;
import dev.lesteoff.celestialskilltree.compat.mowziesmobs.MowziesMobsCompat;
import dev.lesteoff.celestialskilltree.compat.occultism.OccultismCompat;
import dev.lesteoff.celestialskilltree.compat.pandora.PandoraCompat;
import dev.lesteoff.celestialskilltree.compat.parcool.ParCoolCompat;
import dev.lesteoff.celestialskilltree.compat.relics.RelicsCompat;
import dev.lesteoff.celestialskilltree.compat.reliquary.ReliquaryCompat;
import dev.lesteoff.celestialskilltree.compat.scguns.ScorchedGunsCompat;
import net.neoforged.fml.ModList;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

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

    public static Map<String, Boolean> installed() {
        Map<String, Boolean> active = new TreeMap<>();
        Stream.concat(REGISTRATION_BY_MOD.keySet().stream(), Stream.of(CelestialSkillTreePlugin.MOD_ID))
                .filter(ModList.get()::isLoaded)
                .forEach(mod -> active.put(mod, !OptionalEvents.isDisabled(mod)));
        return active;
    }

    public static void logSummary() {
        Map<String, Boolean> installed = installed();
        List<String> off = installed.entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey).toList();
        CelestialSkillTree.LOGGER.info("Integrations active: {}; switched off: {}",
                installed.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList(), off.isEmpty() ? "none" : off);
    }

    public static void register() {
        REGISTRATION_BY_MOD.forEach((mod, registration) -> {
            if (ModList.get().isLoaded(mod)) {
                OptionalEvents.guard(mod, registration);
            }
        });
    }
}
