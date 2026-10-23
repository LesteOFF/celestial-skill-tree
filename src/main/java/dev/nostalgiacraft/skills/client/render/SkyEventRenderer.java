package dev.nostalgiacraft.skills.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.client.ClientSkillState;
import dev.nostalgiacraft.skills.config.ClientConfig;
import dev.nostalgiacraft.skills.progression.SkyEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = NostalgiaSkills.MODID, value = Dist.CLIENT)
public final class SkyEventRenderer {
    private static final ResourceLocation MOON = texture("eclipse_moon");
    private static final ResourceLocation CORONA = texture("eclipse_corona");
    private static final ResourceLocation RAYS = texture("eclipse_rays");
    private static final float SKY_DISTANCE = 100f;
    private static final float ECLIPSE_SIZE = 30f;
    private static final float RAYS_SIZE = 45f;
    private static final float INNER_RAYS_SIZE = 34f;
    private static final float INNER_RAYS_ALPHA = 0.6f;
    private static final float INNER_RAYS_OFFSET_DEGREES = 22.5f;
    private static final float RAYS_DEGREES_PER_TICK = 0.15f;
    private static final float INNER_RAYS_DEGREES_PER_TICK = -0.075f;
    private static final int SPIN_PERIOD_TICKS = 4800;
    private static final float PULSE_SPEED = 0.06f;
    private static final float PULSE_DEPTH = 0.25f;
    private static final float APPROACH_DEGREES = 25f;
    private static final float FULL_TURN_DEGREES = 360f;

    private SkyEventRenderer() {
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(NostalgiaSkills.MODID, "textures/environment/" + name + ".png");
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        boolean overworld = level != null && level.dimension() == Level.OVERWORLD;
        SkyTint.tick(overworld ? ClientSkillState.worldRules().skyEvent() : SkyEvent.NONE);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!SkyTint.active()) {
            return;
        }
        Vec3 tinted = SkyTint.sky(new Vec3(event.getRed(), event.getGreen(), event.getBlue()), (float) event.getPartialTick());
        event.setRed((float) tinted.x);
        event.setGreen((float) tinted.y);
        event.setBlue((float) tinted.z);
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || level == null || !SkyTint.active()) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float strength = SkyTint.eclipseStrength(partialTick);
        if (strength <= 0f) {
            return;
        }
        float sunAngle = level.getTimeOfDay(partialTick) * FULL_TURN_DEGREES;
        Matrix4f overSun = overSun(event.getModelViewMatrix(), sunAngle + (1f - strength) * APPROACH_DEGREES);
        float visibility = 1f - level.getRainLevel(partialTick);
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        drawCorona(overSun, visibility * strength, animationTime(level, partialTick));
        RenderSystem.defaultBlendFunc();
        drawQuad(overSun, MOON, ECLIPSE_SIZE, visibility);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }

    private static void drawCorona(Matrix4f overSun, float glow, float time) {
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        drawQuad(spun(overSun, time * RAYS_DEGREES_PER_TICK), RAYS, RAYS_SIZE, glow);
        drawQuad(spun(overSun, INNER_RAYS_OFFSET_DEGREES + time * INNER_RAYS_DEGREES_PER_TICK), RAYS, INNER_RAYS_SIZE, glow * INNER_RAYS_ALPHA);
        drawQuad(overSun, CORONA, ECLIPSE_SIZE, glow * pulse(time));
    }

    private static float animationTime(ClientLevel level, float partialTick) {
        return ClientConfig.REDUCED_MOTION.get() ? 0f : level.getGameTime() % SPIN_PERIOD_TICKS + partialTick;
    }

    private static float pulse(float time) {
        return 1f - PULSE_DEPTH * (0.5f + 0.5f * Mth.sin(time * PULSE_SPEED));
    }

    private static Matrix4f overSun(Matrix4f modelView, float angleDegrees) {
        PoseStack pose = new PoseStack();
        pose.mulPose(modelView);
        pose.mulPose(Axis.YP.rotationDegrees(-90f));
        pose.mulPose(Axis.XP.rotationDegrees(angleDegrees));
        return pose.last().pose();
    }

    private static Matrix4f spun(Matrix4f overSun, float degrees) {
        return new Matrix4f(overSun).rotateY(degrees * Mth.DEG_TO_RAD);
    }

    private static void drawQuad(Matrix4f matrix, ResourceLocation texture, float size, float alpha) {
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(matrix, -size, SKY_DISTANCE, -size).setUv(0f, 0f);
        buffer.addVertex(matrix, size, SKY_DISTANCE, -size).setUv(1f, 0f);
        buffer.addVertex(matrix, size, SKY_DISTANCE, size).setUv(1f, 1f);
        buffer.addVertex(matrix, -size, SKY_DISTANCE, size).setUv(0f, 1f);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }
}
