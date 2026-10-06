package dev.nostalgiacraft.skills.registry;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

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

    public static PlayerSkillState state(Player player) {
        return player.getData(SKILLS);
    }

    public static void setState(Player player, PlayerSkillState state) {
        player.setData(SKILLS, state);
    }

    public static SkillCooldowns cooldowns(Player player) {
        return player.getData(COOLDOWNS);
    }
}
