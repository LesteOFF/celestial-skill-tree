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
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

@EventBusSubscriber(modid = NostalgiaSkills.MODID)
public final class EliteMobs {
    private static final String ROLLED_KEY = "nostalgiaskills_elite_rolled";
    private static final String ELITE_KEY = "nostalgiaskills_elite";
    private static final String ORIGINAL_KEY = "nostalgiaskills_elite_original";
    private static final String RADAR_TEAM = "nostalgiaskills_constellated";
    private static final String MODIFIER_KIND = "elite";
    private static final String BASE_HEALTH = "minecraft:generic.max_health|add_multiplied_base";
    private static final String BASE_DAMAGE = "minecraft:generic.attack_damage|add_multiplied_base";
    private static final double BASE_HEALTH_BONUS = 0.5;
    private static final double BASE_DAMAGE_BONUS = 0.25;
    private static final int CONSTELLATION_BUDGET = 1_000;
    private static final Set<MobSpawnType> WILD_SPAWNS = EnumSet.of(MobSpawnType.NATURAL, MobSpawnType.CHUNK_GENERATION,
            MobSpawnType.STRUCTURE, MobSpawnType.PATROL, MobSpawnType.EVENT, MobSpawnType.REINFORCEMENT,
            MobSpawnType.JOCKEY);
    private static final Set<MobSpawnType> SPAWNER_SPAWNS = EnumSet.of(MobSpawnType.SPAWNER, MobSpawnType.TRIAL_SPAWNER);

    private static final List<Predicate<Monster>> RIVAL_CLAIMS = new CopyOnWriteArrayList<>();
    private static final List<ToIntFunction<Monster>> NODE_BONUSES = new CopyOnWriteArrayList<>();

    public enum Stacking {
        EXCLUSIVE, STACK
    }

    private EliteMobs() {
    }

    public static void registerRival(Predicate<Monster> hasClaimed) {
        RIVAL_CLAIMS.add(hasClaimed);
    }

    public static void registerNodeBonus(ToIntFunction<Monster> extraNodes) {
        NODE_BONUSES.add(extraNodes);
    }

    private static int nodeCount(Monster monster) {
        return ServerConfig.ELITE_MOB_NODES.get() + NODE_BONUSES.stream().mapToInt(bonus -> bonus.applyAsInt(monster)).sum();
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        boolean freshMonster = !event.getLevel().isClientSide() && !event.loadedFromDisk() && event.getEntity() instanceof Monster;
        if (!freshMonster || event.getEntity().getPersistentData().contains(ROLLED_KEY)) {
            return;
        }
        Monster monster = (Monster) event.getEntity();
        if (!mayBeConstellated(monster) || monster.getType().is(ModTags.NEVER_CONSTELLATED)) {
            return;
        }
        monster.getPersistentData().putBoolean(ROLLED_KEY, true);
        if (monster.getRandom().nextDouble() >= ServerConfig.ELITE_MOB_CHANCE.get()) {
            return;
        }
        MinecraftServer server = monster.getServer();
        boolean otherModsActFirst = !RIVAL_CLAIMS.isEmpty() || !NODE_BONUSES.isEmpty();
        if (otherModsActFirst && server != null) {
            server.tell(new TickTask(server.getTickCount(), () -> empowerUnlessClaimed(monster)));
        } else {
            empower(monster, monster.getRandom(), nodeCount(monster));
        }
    }

    private static void empowerUnlessClaimed(Monster monster) {
        boolean claimed = ServerConfig.ELITE_STACKING.get() == Stacking.EXCLUSIVE
                && RIVAL_CLAIMS.stream().anyMatch(hasClaimed -> hasClaimed.test(monster));
        if (monster.isAlive() && !claimed) {
            empower(monster, monster.getRandom(), nodeCount(monster));
        }
    }

    private static boolean mayBeConstellated(Monster monster) {
        return fromSpawner(monster) ? ServerConfig.ELITE_SPAWNER_MOBS.get() : WILD_SPAWNS.contains(monster.getSpawnType());
    }

    private static boolean fromSpawner(Mob mob) {
        return SPAWNER_SPAWNS.contains(mob.getSpawnType())
                || mob.level().getBlockState(mob.blockPosition().below()).is(ModTags.COUNTS_AS_SPAWNER);
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        boolean killedByPlayer = BossRewards.isRealPlayer(event.getSource().getEntity()) && !event.getSource().is(ModTags.MACHINE_DAMAGE);
        boolean farmed = entity instanceof Mob mob && fromSpawner(mob) && !ServerConfig.ELITE_SPAWNER_MOBS.get();
        boolean taggedKill = entity.getType().is(ModTags.ALWAYS_DROPS_FRAGMENT) && !farmed;
        if (killedByPlayer && (isElite(entity) || taggedKill)) {
            event.getDrops().add(new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(),
                    new ItemStack(ModItems.STAR_FRAGMENT.get())));
        }
    }

    @SubscribeEvent
    public static void onCopiedElite(EntityJoinLevelEvent event) {
        boolean elite = !event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity living && isElite(living);
        if (!elite) {
            return;
        }
        LivingEntity living = (LivingEntity) event.getEntity();
        if (!living.getPersistentData().hasUUID(ORIGINAL_KEY)) {
            living.getPersistentData().putUUID(ORIGINAL_KEY, living.getUUID());
        } else if (!living.getPersistentData().getUUID(ORIGINAL_KEY).equals(living.getUUID())) {
            demote(living);
        }
    }

    private static void demote(LivingEntity copy) {
        String prefix = MODIFIER_KIND + "/";
        for (Holder.Reference<Attribute> attribute : BuiltInRegistries.ATTRIBUTE.holders().toList()) {
            AttributeInstance instance = copy.getAttribute(attribute);
            if (instance != null) {
                List.copyOf(instance.getModifiers()).stream().map(AttributeModifier::id)
                        .filter(id -> id.getNamespace().equals(NostalgiaSkills.MODID) && id.getPath().startsWith(prefix))
                        .forEach(instance::removeModifier);
            }
        }
        copy.getPersistentData().remove(ELITE_KEY);
        copy.getPersistentData().remove(ORIGINAL_KEY);
        copy.setCustomName(null);
        copy.setHealth(Math.min(copy.getHealth(), copy.getMaxHealth()));
    }

    @SubscribeEvent
    public static void onReloadedElite(EntityJoinLevelEvent event) {
        boolean reloadedElite = !event.getLevel().isClientSide() && event.loadedFromDisk()
                && event.getEntity() instanceof LivingEntity living && isElite(living);
        if (reloadedElite) {
            joinRadarTeam((LivingEntity) event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        boolean tracked = !event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity living && isElite(living);
        if (tracked) {
            Scoreboard scoreboard = event.getLevel().getScoreboard();
            PlayerTeam team = scoreboard.getPlayerTeam(RADAR_TEAM);
            if (team != null) {
                scoreboard.removePlayerFromTeam(event.getEntity().getScoreboardName(), team);
            }
        }
    }

    private static void joinRadarTeam(LivingEntity monster) {
        if (!ServerConfig.ELITE_RADAR_TEAM.get() || monster.getTeam() != null) {
            return;
        }
        Scoreboard scoreboard = monster.level().getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(RADAR_TEAM);
        if (team == null) {
            team = scoreboard.addPlayerTeam(RADAR_TEAM);
            team.setColor(ChatFormatting.LIGHT_PURPLE);
        }
        scoreboard.addPlayerToTeam(monster.getScoreboardName(), team);
    }

    public record Bonus(String attribute, AttributeModifier.Operation operation, double amount) {
    }

    public static List<Bonus> bonuses(LivingEntity monster) {
        String prefix = MODIFIER_KIND + "/";
        List<Bonus> bonuses = new ArrayList<>();
        for (Holder.Reference<Attribute> attribute : BuiltInRegistries.ATTRIBUTE.holders().toList()) {
            AttributeInstance instance = monster.getAttribute(attribute);
            if (instance != null) {
                instance.getModifiers().stream()
                        .filter(modifier -> modifier.id().getNamespace().equals(NostalgiaSkills.MODID) && modifier.id().getPath().startsWith(prefix))
                        .forEach(modifier -> bonuses.add(new Bonus(attribute.key().location().toString(), modifier.operation(), modifier.amount())));
            }
        }
        return bonuses;
    }

    public static boolean isElite(LivingEntity entity) {
        return entity != null && entity.getPersistentData().getBoolean(ELITE_KEY);
    }

    public static boolean constellate(Monster monster) {
        monster.getPersistentData().putBoolean(ROLLED_KEY, true);
        return empower(monster, monster.getRandom(), nodeCount(monster));
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
        monster.getPersistentData().putUUID(ORIGINAL_KEY, monster.getUUID());
        joinRadarTeam(monster);
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
