package dev.lesteoff.celestialskilltree.compat.jade;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.block.XpBasinBlock;
import dev.lesteoff.celestialskilltree.block.XpBasinBlockEntity;
import dev.lesteoff.celestialskilltree.effects.EliteMobs;
import dev.lesteoff.celestialskilltree.graph.SkillSign;
import dev.lesteoff.celestialskilltree.registry.ModAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

@WailaPlugin
public final class JadePlugin implements IWailaPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(CelestialSkillTree.MODID, "constellation");
    private static final String SIGN = "sign";
    private static final String SPENT = "spent";
    private static final String BONUSES = "bonuses";
    private static final String ATTRIBUTE = "attribute";
    private static final String OPERATION = "operation";
    private static final String AMOUNT = "amount";
    private static final int PERCENT = 100;
    private static final int MAX_LINES = 8;

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerEntityDataProvider(new ConstellationData(), LivingEntity.class);
        registration.registerBlockDataProvider(new BasinProgress(), XpBasinBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addConfig(UID, true);
        registration.registerEntityComponent(new ConstellationTooltip(), LivingEntity.class);
        registration.addConfig(BasinProgress.UID, true);
        registration.registerBlockComponent(new BasinProgress(), XpBasinBlock.class);
    }

    private static final class ConstellationData implements IServerDataProvider<EntityAccessor> {
        @Override
        public void appendServerData(CompoundTag data, EntityAccessor accessor) {
            if (accessor.getEntity() instanceof ServerPlayer player) {
                data.putString(SIGN, ModAttachments.state(player).sign().orElse(""));
                data.putInt(SPENT, ModAttachments.state(player).activeSpentPoints());
            } else if (accessor.getEntity() instanceof LivingEntity monster && EliteMobs.isElite(monster)) {
                ListTag bonuses = new ListTag();
                EliteMobs.bonuses(monster).forEach(bonus -> bonuses.add(bonusTag(bonus)));
                data.put(BONUSES, bonuses);
            }
        }

        private static CompoundTag bonusTag(EliteMobs.Bonus bonus) {
            CompoundTag tag = new CompoundTag();
            tag.putString(ATTRIBUTE, bonus.attribute());
            tag.putString(OPERATION, bonus.operation().name());
            tag.putDouble(AMOUNT, bonus.amount());
            return tag;
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    private static final class ConstellationTooltip implements IEntityComponentProvider {
        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (data.contains(SIGN) && !data.getString(SIGN).isEmpty()) {
                tooltip.add(Component.translatable("jade.celestialskilltree.sign", Component.translatable(SkillSign.nameKey(data.getString(SIGN))))
                        .withStyle(ChatFormatting.GOLD));
                tooltip.add(Component.translatable("jade.celestialskilltree.spent", data.getInt(SPENT)).withStyle(ChatFormatting.GRAY));
            }
            if (data.contains(BONUSES, Tag.TAG_LIST)) {
                ListTag bonuses = data.getList(BONUSES, Tag.TAG_COMPOUND);
                tooltip.add(Component.translatable("jade.celestialskilltree.constellation").withStyle(ChatFormatting.LIGHT_PURPLE));
                for (int index = 0; index < Math.min(bonuses.size(), MAX_LINES); index++) {
                    tooltip.add(line(bonuses.getCompound(index)));
                }
            }
        }

        private static Component line(CompoundTag bonus) {
            String attributeId = bonus.getString(ATTRIBUTE);
            boolean flat = AttributeModifier.Operation.valueOf(bonus.getString(OPERATION)) == AttributeModifier.Operation.ADD_VALUE
                    && !attributeId.startsWith(CelestialSkillTree.MODID + ":");
            double amount = bonus.getDouble(AMOUNT);
            String value = flat ? String.format("%+.2f", amount) : String.format("%+.1f%%", amount * PERCENT);
            Component name = BuiltInRegistries.ATTRIBUTE.getOptional(ResourceLocation.parse(attributeId))
                    .map(attribute -> (Component) Component.translatable(attribute.getDescriptionId())).orElse(Component.literal(attributeId));
            return Component.empty().append(name).append(" " + value).withStyle(ChatFormatting.GRAY);
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }
}
