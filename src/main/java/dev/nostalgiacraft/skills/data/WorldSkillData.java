package dev.nostalgiacraft.skills.data;

import dev.nostalgiacraft.skills.progression.EclipseSchedule;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class WorldSkillData extends SavedData {
    private static final String FILE_NAME = "nostalgiaskills_world";
    private static final String ECLIPSE_DAY = "eclipse_day";
    private static final String ECLIPSE_FORCED = "eclipse_forced";
    private static final String COMMUNITY_XP = "community_xp";

    private long eclipseDay = EclipseSchedule.UNSCHEDULED;
    private boolean eclipseForced;
    private long communityXp;

    public static WorldSkillData of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(WorldSkillData::new, WorldSkillData::load), FILE_NAME);
    }

    private static WorldSkillData load(CompoundTag tag, HolderLookup.Provider registries) {
        WorldSkillData data = new WorldSkillData();
        data.eclipseDay = tag.contains(ECLIPSE_DAY) ? tag.getLong(ECLIPSE_DAY) : EclipseSchedule.UNSCHEDULED;
        data.eclipseForced = tag.getBoolean(ECLIPSE_FORCED);
        data.communityXp = Math.max(0, tag.getLong(COMMUNITY_XP));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong(ECLIPSE_DAY, eclipseDay);
        tag.putBoolean(ECLIPSE_FORCED, eclipseForced);
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

    public boolean eclipseForced() {
        return eclipseForced;
    }

    public void setEclipseForced(boolean forced) {
        eclipseForced = forced;
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
