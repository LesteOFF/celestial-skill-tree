package dev.nostalgiacraft.skills.compat;

import dev.nostalgiacraft.skills.compat.arsnouveau.ArsNouveauCompat;
import dev.nostalgiacraft.skills.compat.bettercombat.BetterCombatCompat;
import dev.nostalgiacraft.skills.compat.champions.ChampionsCompat;
import dev.nostalgiacraft.skills.compat.curios.CuriosCompat;
import dev.nostalgiacraft.skills.compat.dummmmmmy.DummmmmmyCompat;
import dev.nostalgiacraft.skills.compat.ironsspellbooks.IronsSpellbooksCompat;
import dev.nostalgiacraft.skills.compat.l2hostility.L2HostilityCompat;
import dev.nostalgiacraft.skills.compat.parcool.ParCoolCompat;
import dev.nostalgiacraft.skills.compat.scguns.ScorchedGunsCompat;
import net.neoforged.fml.ModList;

import java.util.Map;

public final class Integrations {
    private static final Map<String, Runnable> REGISTRATION_BY_MOD = Map.of(
            IronsSpellbooksCompat.MOD_ID, IronsSpellbooksCompat::register,
            ArsNouveauCompat.MOD_ID, ArsNouveauCompat::register,
            ParCoolCompat.MOD_ID, ParCoolCompat::register,
            ScorchedGunsCompat.MOD_ID, ScorchedGunsCompat::register,
            DummmmmmyCompat.MOD_ID, DummmmmmyCompat::register,
            BetterCombatCompat.MOD_ID, BetterCombatCompat::register,
            CuriosCompat.MOD_ID, CuriosCompat::register,
            ChampionsCompat.MOD_ID, ChampionsCompat::register,
            L2HostilityCompat.MOD_ID, L2HostilityCompat::register);

    private Integrations() {
    }

    public static void register() {
        REGISTRATION_BY_MOD.forEach((mod, registration) -> {
            if (ModList.get().isLoaded(mod)) {
                OptionalEvents.guard(mod, registration);
            }
        });
    }
}
