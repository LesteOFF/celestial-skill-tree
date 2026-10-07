package dev.nostalgiacraft.skills.data;

import dev.nostalgiacraft.skills.progression.SkyEvent;
import dev.nostalgiacraft.skills.progression.SkyEventSchedule;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class WorldSkillData extends SavedData {
    private static final String FILE_NAME = "nostalgiaskills_world";
    private static final String ECLIPSE_DAY = "eclipse_day";
    private static final String SHOWER_DAY = "shower_day";
    private static final String FORCED_EVENT = "forced_event";
    private static final String LEGACY_ECLIPSE_FORCED = "eclipse_forced";
    private static final String COMMUNITY_XP = "community_xp";

    private long eclipseDay = SkyEventSchedule.UNSCHEDULED;
    private long showerDay = SkyEventSchedule.UNSCHEDULED;
    private SkyEvent forcedEvent = SkyEvent.NONE;
    private long communityXp;

    public static WorldSkillData of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(WorldSkillData::new, WorldSkillData::load), FILE_NAME);
    }

    private static WorldSkillData load(CompoundTag tag, HolderLookup.Provider registries) {
        WorldSkillData data = new WorldSkillData();
        data.eclipseDay = scheduledDay(tag, ECLIPSE_DAY);
        data.showerDay = scheduledDay(tag, SHOWER_DAY);
        data.forcedEvent = tag.getBoolean(LEGACY_ECLIPSE_FORCED) ? SkyEvent.ECLIPSE : SkyEvent.byName(tag.getString(FORCED_EVENT));
        data.communityXp = Math.max(0, tag.getLong(COMMUNITY_XP));
        return data;
    }

    private static long scheduledDay(CompoundTag tag, String key) {
        return tag.contains(key) ? tag.getLong(key) : SkyEventSchedule.UNSCHEDULED;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong(ECLIPSE_DAY, eclipseDay);
        tag.putLong(SHOWER_DAY, showerDay);
        tag.putString(FORCED_EVENT, forcedEvent.id());
        tag.putLong(COMMUNITY_XP, communityXp);
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

    public long communityXp() {
        return communityXp;
    }

    public void setCommunityXp(long xp) {
        communityXp = Math.max(0, xp);
        setDirty();
    }
}
