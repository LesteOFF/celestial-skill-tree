package dev.nostalgiacraft.skills.registry;

import com.mojang.serialization.Codec;
import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, NostalgiaSkills.MODID);

    public static final Supplier<AttachmentType<PlayerSkillState>> SKILLS = TYPES.register("skills",
            () -> AttachmentType.builder(PlayerSkillState::new).serialize(StateCodecs.STATE).build());

    public static final Supplier<AttachmentType<SkillCooldowns>> COOLDOWNS = TYPES.register("cooldowns",
            () -> AttachmentType.builder(SkillCooldowns::new).serialize(SkillCooldowns.CODEC).copyOnDeath().build());

    public static final Supplier<AttachmentType<Set<String>>> BOSS_KILLS = TYPES.register("boss_kills",
            () -> AttachmentType.builder((Supplier<Set<String>>) HashSet::new)
                    .serialize(Codec.STRING.listOf().xmap(list -> (Set<String>) new HashSet<>(list), ArrayList::new))
                    .copyOnDeath().build());

    public static final Supplier<AttachmentType<Map<String, Long>>> STOLEN_NODES = TYPES.register("stolen_nodes",
            () -> AttachmentType.builder((Supplier<Map<String, Long>>) HashMap::new)
                    .serialize(Codec.unboundedMap(Codec.STRING, Codec.LONG).xmap(map -> (Map<String, Long>) new HashMap<>(map), Map::copyOf))
                    .copyOnDeath().build());

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
