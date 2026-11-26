package dev.lesteoff.celestialskilltree.data;

import dev.lesteoff.celestialskilltree.generation.DrawJson;
import dev.lesteoff.celestialskilltree.generation.TreeDraw;
import dev.lesteoff.celestialskilltree.progression.CommunityProgress;
import dev.lesteoff.celestialskilltree.progression.SkyEvent;
import dev.lesteoff.celestialskilltree.progression.SkyEventSchedule;
import dev.lesteoff.celestialskilltree.progression.WorldClock;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WorldSkillData extends SavedData {
    private static final String FILE_NAME = "celestialskilltree_world";
    private static final String ECLIPSE_DAY = "eclipse_day";
    private static final String SHOWER_DAY = "shower_day";
    private static final String FORCED_EVENT = "forced_event";
    private static final String LEGACY_ECLIPSE_FORCED = "eclipse_forced";
    private static final String LEGACY_COMMUNITY_XP = "community_xp";
    private static final String COMMUNITY_STARS = "community_stars";
    private static final String COMMUNITY_PATRONS = "community_patrons";
    private static final String PATRON_ID = "id";
    private static final String PATRON_NAME = "name";
    private static final String PATRON_STARS = "stars";
    private static final String TREE_DRAW = "tree_draw";
    private static final String SKY_DAY = "sky_day";
    private static final String SKY_TIME_OF_DAY = "sky_time_of_day";

    private long eclipseDay = SkyEventSchedule.UNSCHEDULED;
    private long showerDay = SkyEventSchedule.UNSCHEDULED;
    private SkyEvent forcedEvent = SkyEvent.NONE;
    private long legacyCommunityXp;
    private Map<String, Long> communityStars = Map.of();
    private final Map<UUID, CommunityProgress.Patron> patrons = new HashMap<>();
    private String treeDraw = "";
    private long skyDay = SkyEventSchedule.UNSCHEDULED;
    private long skyTimeOfDay;

    public static WorldSkillData of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(WorldSkillData::new, WorldSkillData::load), FILE_NAME);
    }

    private static WorldSkillData load(CompoundTag tag, HolderLookup.Provider registries) {
        WorldSkillData data = new WorldSkillData();
        data.eclipseDay = scheduledDay(tag, ECLIPSE_DAY);
        data.showerDay = scheduledDay(tag, SHOWER_DAY);
        data.forcedEvent = tag.getBoolean(LEGACY_ECLIPSE_FORCED) ? SkyEvent.ECLIPSE : SkyEvent.byName(tag.getString(FORCED_EVENT));
        data.legacyCommunityXp = Math.max(0, tag.getLong(LEGACY_COMMUNITY_XP));
        data.communityStars = amounts(tag.getCompound(COMMUNITY_STARS));
        for (Tag entry : tag.getList(COMMUNITY_PATRONS, Tag.TAG_COMPOUND)) {
            CompoundTag patron = (CompoundTag) entry;
            data.patrons.put(patron.getUUID(PATRON_ID),
                    new CommunityProgress.Patron(patron.getString(PATRON_NAME), amounts(patron.getCompound(PATRON_STARS))));
        }
        data.treeDraw = tag.getString(TREE_DRAW);
        data.skyDay = scheduledDay(tag, SKY_DAY);
        data.skyTimeOfDay = tag.getLong(SKY_TIME_OF_DAY);
        return data;
    }

    private static Map<String, Long> amounts(CompoundTag tag) {
        Map<String, Long> amounts = new HashMap<>();
        tag.getAllKeys().forEach(key -> amounts.put(key, Math.max(0, tag.getLong(key))));
        return Map.copyOf(amounts);
    }

    private static CompoundTag amountsTag(Map<String, Long> amounts) {
        CompoundTag tag = new CompoundTag();
        amounts.forEach(tag::putLong);
        return tag;
    }

    private static long scheduledDay(CompoundTag tag, String key) {
        return tag.contains(key) ? tag.getLong(key) : SkyEventSchedule.UNSCHEDULED;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong(ECLIPSE_DAY, eclipseDay);
        tag.putLong(SHOWER_DAY, showerDay);
        tag.putString(FORCED_EVENT, forcedEvent.id());
        if (legacyCommunityXp > 0) {
            tag.putLong(LEGACY_COMMUNITY_XP, legacyCommunityXp);
        }
        tag.put(COMMUNITY_STARS, amountsTag(communityStars));
        ListTag patronList = new ListTag();
        patrons.forEach((id, patron) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(PATRON_ID, id);
            entry.putString(PATRON_NAME, patron.name());
            entry.put(PATRON_STARS, amountsTag(patron.stars()));
            patronList.add(entry);
        });
        tag.put(COMMUNITY_PATRONS, patronList);
        tag.putString(TREE_DRAW, treeDraw);
        tag.putLong(SKY_DAY, skyDay);
        tag.putLong(SKY_TIME_OF_DAY, skyTimeOfDay);
        return tag;
    }

    public long eclipseDay() {
        return eclipseDay;
    }

    public void setEclipseDay(long day) {
        eclipseDay = day;
        setDirty();
    }

    public long showerDay() {
        return showerDay;
    }

    public void setShowerDay(long day) {
        showerDay = day;
        setDirty();
    }

    public SkyEvent forcedEvent() {
        return forcedEvent;
    }

    public void setForcedEvent(SkyEvent event) {
        forcedEvent = event;
        setDirty();
    }

    public long skyDay(long dayTime) {
        long timeOfDay = WorldClock.timeOfDay(dayTime);
        long today = skyDay == SkyEventSchedule.UNSCHEDULED ? WorldClock.day(dayTime)
                : SkyEventSchedule.dayAfter(skyDay, skyTimeOfDay, timeOfDay);
        if (today != skyDay) {
            setDirty();
        }
        skyDay = today;
        skyTimeOfDay = timeOfDay;
        return today;
    }

    public TreeDraw treeDraw() {
        return DrawJson.read(treeDraw);
    }

    public void setTreeDraw(TreeDraw draw) {
        String written = DrawJson.write(draw);
        if (!written.equals(treeDraw)) {
            treeDraw = written;
            setDirty();
        }
    }

    public long takeLegacyCommunityXp() {
        long legacy = legacyCommunityXp;
        if (legacy > 0) {
            legacyCommunityXp = 0;
            setDirty();
        }
        return legacy;
    }

    public Map<String, Long> communityStars() {
        return communityStars;
    }

    public void setCommunityStars(Map<String, Long> stars) {
        communityStars = Map.copyOf(stars);
        setDirty();
    }

    public Map<UUID, CommunityProgress.Patron> patrons() {
        return Map.copyOf(patrons);
    }

    public void credit(UUID id, String name, String starId, long xp) {
        CommunityProgress.Patron patron = patrons.getOrDefault(id, new CommunityProgress.Patron(name, Map.of()));
        patrons.put(id, patron.credited(name, starId, xp));
        setDirty();
    }
}
