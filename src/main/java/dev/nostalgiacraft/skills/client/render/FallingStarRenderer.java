package dev.nostalgiacraft.skills.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.entity.FallingStar;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class FallingStarRenderer extends EntityRenderer<FallingStar> {
    private static final ResourceLocation HEAD = texture("falling_star");
    private static final ResourceLocation STREAK = texture("star_streak");
    private static final RenderType HEAD_TYPE = RenderType.entityTranslucentEmissive(HEAD);
    private static final RenderType STREAK_TYPE = RenderType.entityTranslucentEmissive(STREAK);
    private static final float HEAD_SIZE = 1.5f;
    private static final double STREAK_LENGTH = 7.0;
    private static final double STREAK_HALF_WIDTH = 0.25;
    private static final double MIN_DIRECTION_SQR = 1.0e-6;

    public FallingStarRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(NostalgiaSkills.MODID, "textures/entity/" + name + ".png");
    }

    @Override
    public void render(FallingStar star, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight) {
        Vec3 behind = star.getDeltaMovement().reverse();
        Vec3 toCamera = entityRenderDispatcher.camera.getPosition().subtract(star.getPosition(partialTick));
        Vec3 across = behind.cross(toCamera);
        if (across.lengthSqr() > MIN_DIRECTION_SQR) {
            renderStreak(pose.last(), buffers.getBuffer(STREAK_TYPE), behind.normalize().scale(STREAK_LENGTH), across.normalize());
        }
        renderHead(pose, buffers.getBuffer(HEAD_TYPE));
    }

    private void renderStreak(PoseStack.Pose pose, VertexConsumer consumer, Vec3 tail, Vec3 across) {
        Vec3 edge = across.scale(STREAK_HALF_WIDTH);
        Vec3[] corners = {edge, edge.reverse(), tail.subtract(edge), tail.add(edge)};
        float[][] uvs = {{0f, 0f}, {1f, 0f}, {1f, 1f}, {0f, 1f}};
        for (int i = 0; i < corners.length; i++) {
            vertex(consumer, pose, corners[i], uvs[i]);
        }
    }

    private void renderHead(PoseStack pose, VertexConsumer consumer) {
        pose.pushPose();
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        pose.scale(HEAD_SIZE, HEAD_SIZE, HEAD_SIZE);
        PoseStack.Pose last = pose.last();
        vertex(consumer, last, new Vec3(-0.5, -0.5, 0), new float[]{0f, 1f});
        vertex(consumer, last, new Vec3(0.5, -0.5, 0), new float[]{1f, 1f});
        vertex(consumer, last, new Vec3(0.5, 0.5, 0), new float[]{1f, 0f});
        vertex(consumer, last, new Vec3(-0.5, 0.5, 0), new float[]{0f, 0f});
        pose.popPose();
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 position, float[] uv) {
        consumer.addVertex(pose, (float) position.x, (float) position.y, (float) position.z)
                .setColor(-1)
                .setUv(uv[0], uv[1])
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0f, 1f, 0f);
    }

    @Override
    public boolean shouldRender(FallingStar star, Frustum frustum, double cameraX, double cameraY, double cameraZ) {
        return frustum.isVisible(star.getBoundingBox().inflate(STREAK_LENGTH + HEAD_SIZE));
    }

    @Override
    public ResourceLocation getTextureLocation(FallingStar star) {
        return HEAD;
    }
}
