package dev.nostalgiacraft.skills.effects;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.api.SkillEvents;
import dev.nostalgiacraft.skills.config.ServerConfig;
import dev.nostalgiacraft.skills.data.SkillTreeManager;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.SkillNodeDefinition;
import dev.nostalgiacraft.skills.progression.AllocationService;
import dev.nostalgiacraft.skills.progression.Holdings;
import dev.nostalgiacraft.skills.progression.LedgerService;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.registry.ModItems;
import dev.nostalgiacraft.skills.registry.ModTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@EventBusSubscriber(modid = NostalgiaSkills.MODID)
public final class EliteMobs {
    private static final String ROLLED_KEY = "nostalgiaskills_elite_rolled";
    private static final String ELITE_KEY = "nostalgiaskills_elite";
    private static final String MODIFIER_KIND = "elite";
    private static final String BASE_HEALTH = "minecraft:generic.max_health|add_multiplied_base";
    private static final String BASE_DAMAGE = "minecraft:generic.attack_damage|add_multiplied_base";
    private static final double BASE_HEALTH_BONUS = 0.5;
    private static final double BASE_DAMAGE_BONUS = 0.25;
    private static final int CONSTELLATION_BUDGET = 1_000;
    private static final Set<MobSpawnType> WILD_SPAWNS = EnumSet.of(MobSpawnType.NATURAL, MobSpawnType.CHUNK_GENERATION,
            MobSpawnType.STRUCTURE, MobSpawnType.PATROL, MobSpawnType.EVENT, MobSpawnType.REINFORCEMENT,
            MobSpawnType.JOCKEY);

    private EliteMobs() {
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        boolean freshMonster = !event.getLevel().isClientSide() && !event.loadedFromDisk() && event.getEntity() instanceof Monster;
        if (!freshMonster || event.getEntity().getPersistentData().contains(ROLLED_KEY)) {
            return;
        }
        Monster monster = (Monster) event.getEntity();
        if (!WILD_SPAWNS.contains(monster.getSpawnType()) || monster.getType().is(ModTags.NEVER_CONSTELLATED)) {
            return;
        }
        monster.getPersistentData().putBoolean(ROLLED_KEY, true);
        if (monster.getRandom().nextDouble() < ServerConfig.ELITE_MOB_CHANCE.get()) {
            empower(monster, monster.getRandom(), ServerConfig.ELITE_MOB_NODES.get());
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        boolean taggedKill = entity.getType().is(ModTags.ALWAYS_DROPS_FRAGMENT) && event.getSource().getEntity() instanceof Player;
        if (isElite(entity) || taggedKill) {
            event.getDrops().add(new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(),
                    new ItemStack(ModItems.STAR_FRAGMENT.get())));
        }
    }

    public static boolean isElite(LivingEntity entity) {
        return entity.getPersistentData().getBoolean(ELITE_KEY);
    }

    public static boolean empower(Monster monster, RandomSource random, int nodeCount) {
        Optional<SkillGraphSnapshot> graph = SkillTreeManager.graph();
        if (graph.isEmpty()) {
            return false;
        }
        applyModifier(monster, BASE_HEALTH, BASE_HEALTH_BONUS);
        applyModifier(monster, BASE_DAMAGE, BASE_DAMAGE_BONUS);
        PlayerSkillState constellation = randomConstellation(graph.get(), random, nodeCount);
        CompiledBuild.compile(graph.get(), constellation).attributeSums().forEach((sumKey, sum) -> applyModifier(monster, sumKey, sum));
        monster.setHealth(monster.getMaxHealth());
        monster.setCustomName(Component.translatable("entity.nostalgiaskills.elite", monster.getType().getDescription())
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        monster.getPersistentData().putBoolean(ELITE_KEY, true);
        SkillEvents.post(new SkillEvents.EliteSpawned(monster));
        return true;
    }

    private static void applyModifier(Monster monster, String sumKey, double amount) {
        String[] attributeAndOperation = sumKey.split("\\|");
        ResourceLocation id = EffectEngine.modifierId(MODIFIER_KIND, sumKey);
        BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(attributeAndOperation[0])).map(monster::getAttribute)
                .ifPresent(instance -> addOrIncrease(instance, id, amount, EffectEngine.op(attributeAndOperation[1])));
    }

    private static void addOrIncrease(AttributeInstance instance, ResourceLocation id, double amount,
                                      AttributeModifier.Operation operation) {
        double existing = Optional.ofNullable(instance.getModifier(id)).map(AttributeModifier::amount).orElse(0.0);
        instance.removeModifier(id);
        instance.addPermanentModifier(new AttributeModifier(id, existing + amount, operation));
    }

    private static PlayerSkillState randomConstellation(SkillGraphSnapshot graph, RandomSource random, int nodeCount) {
        PlayerSkillState constellation = new PlayerSkillState();
        LedgerService.grant(constellation, CONSTELLATION_BUDGET, CONSTELLATION_BUDGET, true);
        List<String> roots = graph.roots();
        AllocationService.confirm(graph, constellation, List.of(roots.get(random.nextInt(roots.size()))));
        for (int i = 0; i < nodeCount; i++) {
            List<String> reachable = reachableNodes(graph, constellation);
            if (reachable.isEmpty()) {
                break;
            }
            AllocationService.confirm(graph, constellation, List.of(reachable.get(random.nextInt(reachable.size()))));
        }
        return constellation;
    }

    private static List<String> reachableNodes(SkillGraphSnapshot graph, PlayerSkillState constellation) {
        Holdings holdings = Holdings.of(graph, constellation);
        return graph.nodes().values().stream()
                .filter(node -> !holdings.contains(node.id()) && AllocationService.rejectionOf(graph, node, holdings).isEmpty())
                .map(SkillNodeDefinition::id).toList();
    }
}
