package dev.nostalgiacraft.skills.registry;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Player data attachments. The skill state is deliberately NOT copyOnDeath: death copying is done once in the
 * clone event according to the keepOnDeath setting (NeoForge copies non-death clones, e.g. leaving the End,
 * automatically), so the two mechanisms never both run.
 */
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, NostalgiaSkills.MODID);

    public static final Supplier<AttachmentType<PlayerSkillState>> SKILLS = TYPES.register("skills",
            () -> AttachmentType.builder(PlayerSkillState::new).serialize(StateCodecs.STATE).build());

    public static final Supplier<AttachmentType<SkillCooldowns>> COOLDOWNS = TYPES.register("cooldowns",
            () -> AttachmentType.builder(SkillCooldowns::new).serialize(SkillCooldowns.CODEC).copyOnDeath().build());

    private ModAttachments() {
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    public static PlayerSkillState state(Player p) {
        return p.getData(SKILLS);
    }

    public static void setState(Player p, PlayerSkillState s) {
        p.setData(SKILLS, s);
    }

    public static SkillCooldowns cooldowns(Player p) {
        return p.getData(COOLDOWNS);
    }
}
