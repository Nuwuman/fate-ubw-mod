package com.nuwuman.fateubw.client;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.rider.PegasusEntity;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.HorseEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Pegaso: el modelo y la textura del caballo blanco vanilla (los carga el juego, no van en el mod), con la brida
 * teñida de oro (Bellerophon) y unas alas propias. En el suelo las alas van plegadas sobre el lomo; en el aire se
 * abren y aletean. Durante la embestida de Bellerophon arrastra una estela de luz como un cometa.
 */
public class PegasusRenderer extends MobEntityRenderer<PegasusEntity, PegasusRenderer.Model> {
    private static final Identifier HORSE = Identifier.ofVanilla("textures/entity/horse/horse_white.png");
    private static final Identifier WINGS = FateUBW.id("textures/entity/pegasus_wings.png");
    // La brida de Bellerophon, de oro: sus piezas se pintan con la textura del bloque de oro del juego
    private static final Identifier GOLD = Identifier.ofVanilla("textures/block/gold_block.png");

    public PegasusRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new Model(TexturedModelData.of(HorseEntityModel.getModelData(Dilation.NONE), 64, 64).createModel()), 0.75F);
        addFeature(new Wings(this));
        addFeature(new Bridle(this));
    }

    @Override
    public Identifier getTexture(PegasusEntity entity) {
        return HORSE;
    }

    @Override
    public void render(PegasusEntity entity, float entityYaw, float partialTick, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        super.render(entity, entityYaw, partialTick, matrices, vertexConsumers, light);
        int charge = entity.getCharge();
        if (charge <= 0) return;

        Vec3d v = entity.getVelocity();
        if (v.lengthSquared() < 1.0E-4) return;
        float fade = Math.min(1.0F, charge / 6.0F);
        float yaw = (float) Math.toDegrees(Math.atan2(v.x, v.z));
        float pitch = (float) Math.toDegrees(Math.atan2(v.y, v.horizontalLength()));

        matrices.push();
        matrices.translate(0.0, entity.getHeight() * 0.6, 0.0);
        // +Y local en la dirección del vuelo; la estela va hacia -Y (detrás)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F - pitch));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((entity.age + partialTick) * 20.0F));
        Matrix4f m = matrices.peek().getPositionMatrix();
        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getLightning());
        float pulse = 1.0F + 0.1F * MathHelper.sin((entity.age + partialTick) * 1.5F);
        ExcaliburBeamRenderer.prism(vc, m, -8.0F, 1.2F * pulse, 0.2F, 255, 255, 240, (int) (200 * fade));
        ExcaliburBeamRenderer.prism(vc, m, -6.0F, 1.8F * pulse, 0.4F, 255, 220, 120, (int) (110 * fade));
        matrices.pop();
    }

    private static boolean flying(PegasusEntity entity) {
        return !entity.isOnGround();
    }

    /** El caballo vanilla: patas que andan como las del caballo, cabeza que cabecea y patas recogidas al volar. */
    public static class Model extends EntityModel<PegasusEntity> {
        private final ModelPart body, headParts, tail;
        private final ModelPart[] legs, bridle, straps, reins;

        Model(ModelPart root) {
            body = root.getChild("body");
            headParts = root.getChild("head_parts");
            tail = body.getChild("tail");
            legs = new ModelPart[]{root.getChild("left_front_leg"), root.getChild("right_front_leg"),
                    root.getChild("left_hind_leg"), root.getChild("right_hind_leg")};
            if (body.hasChild("saddle")) body.getChild("saddle").visible = false;
            bridle = find(headParts, "left_saddle_mouth", "right_saddle_mouth");
            straps = find(headParts, "head_saddle", "mouth_saddle_wrap");
            reins = find(headParts, "left_saddle_line", "right_saddle_line");
        }

        // Las piezas con esos nombres que cuelgan de la cabeza (directas o de "head")
        private static ModelPart[] find(ModelPart parent, String... names) {
            List<ModelPart> out = new ArrayList<>();
            for (String name : names) {
                if (parent.hasChild(name)) out.add(parent.getChild(name));
                else if (parent.hasChild("head") && parent.getChild("head").hasChild(name)) out.add(parent.getChild("head").getChild(name));
            }
            return out.toArray(new ModelPart[0]);
        }

        @Override
        public void setAngles(PegasusEntity entity, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
            boolean air = flying(entity);
            headParts.pitch = MathHelper.PI / 6 + (air ? -0.15F : 0.05F * MathHelper.sin(age * 0.08F));
            headParts.yaw = MathHelper.clamp(headYaw, -30, 30) * MathHelper.RADIANS_PER_DEGREE;
            tail.pitch = air ? 1.2F : MathHelper.PI / 6 + 0.1F * MathHelper.sin(age * 0.1F);
            float swing = MathHelper.cos(limbAngle * 0.6662F) * 1.4F * limbDistance;
            for (int i = 0; i < 4; i++) {
                legs[i].pitch = air ? (i < 2 ? -0.9F : 0.9F) : (i == 0 || i == 3 ? swing : -swing);
            }
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
            // El caballo blanco tal cual; la brida y las riendas las pinta Bridle, en oro
            for (ModelPart p : bridle) p.visible = false;
            for (ModelPart p : straps) p.visible = false;
            for (ModelPart p : reins) p.visible = false;
            body.render(matrices, vertices, light, overlay, color);
            headParts.render(matrices, vertices, light, overlay, color);
            for (ModelPart leg : legs) leg.render(matrices, vertices, light, overlay, color);
        }
    }

    /** Brida y riendas (estas solo con jinete) de oro, siguiendo a la cabeza. */
    static class Bridle extends FeatureRenderer<PegasusEntity, Model> {
        Bridle(FeatureRendererContext<PegasusEntity, Model> context) {
            super(context);
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, PegasusEntity entity,
                           float limbAngle, float limbDistance, float tickDelta, float age, float headYaw, float headPitch) {
            Model model = getContextModel();
            VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(GOLD));
            matrices.push();
            model.headParts.rotate(matrices);
            draw(model.bridle, matrices, vc, light, true, -1);
            draw(model.reins, matrices, vc, light, entity.hasPassengers(), -1);
            // Las correas son casi todo huecos en la textura del caballo: con ella, teñidas de oro
            draw(model.straps, matrices, vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(HORSE)), light, true, 0xFFFFD040);
            matrices.pop();
        }

        private static void draw(ModelPart[] parts, MatrixStack matrices, VertexConsumer vc, int light, boolean show, int color) {
            for (ModelPart p : parts) {
                if (!show) continue;
                p.visible = true;
                p.render(matrices, vc, light, OverlayTexture.DEFAULT_UV, color);
                p.visible = false;
            }
        }
    }

    /** Las alas, con su textura: plegadas hacia atrás sobre el lomo en el suelo, abiertas y aleteando en el aire. */
    static class Wings extends FeatureRenderer<PegasusEntity, Model> {
        private final ModelPart left, right;

        Wings(FeatureRendererContext<PegasusEntity, Model> context) {
            super(context);
            ModelData data = new ModelData();
            ModelPartData root = data.getRoot();
            // Ala izquierda hacia +X desde la cruz: cobertoras (cada vez más estrechas) y remeras largas hacia atrás
            root.addChild("left", ModelPartBuilder.create()
                    .uv(0, 0).cuboid(0, -0.6F, -4, 7, 1.2F, 9)
                    .uv(0, 10).cuboid(7, -0.5F, -3, 6, 1, 8)
                    .uv(0, 19).cuboid(13, -0.4F, -2, 6, 0.8F, 6)
                    .uv(32, 0).cuboid(0, -0.25F, 5, 5, 0.5F, 5)
                    .uv(32, 6).cuboid(5, -0.25F, 5, 5, 0.5F, 7)
                    .uv(32, 14).cuboid(10, -0.25F, 4, 5, 0.5F, 9)
                    .uv(32, 24).cuboid(15, -0.25F, 4, 5, 0.5F, 8), ModelTransform.pivot(4.5F, 3.5F, -6));
            root.addChild("right", ModelPartBuilder.create().mirrored()
                    .uv(0, 0).cuboid(-7, -0.6F, -4, 7, 1.2F, 9)
                    .uv(0, 10).cuboid(-13, -0.5F, -3, 6, 1, 8)
                    .uv(0, 19).cuboid(-19, -0.4F, -2, 6, 0.8F, 6)
                    .uv(32, 0).cuboid(-5, -0.25F, 5, 5, 0.5F, 5)
                    .uv(32, 6).cuboid(-10, -0.25F, 5, 5, 0.5F, 7)
                    .uv(32, 14).cuboid(-15, -0.25F, 4, 5, 0.5F, 9)
                    .uv(32, 24).cuboid(-20, -0.25F, 4, 5, 0.5F, 8), ModelTransform.pivot(-4.5F, 3.5F, -6));
            ModelPart parts = TexturedModelData.of(data, 64, 64).createModel();
            left = parts.getChild("left");
            right = parts.getChild("right");
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, PegasusEntity entity,
                           float limbAngle, float limbDistance, float tickDelta, float age, float headYaw, float headPitch) {
            if (flying(entity)) {
                float flap = MathHelper.sin(age * 0.5F) * 0.7F;
                left.yaw = 0;
                right.yaw = 0;
                left.roll = -0.15F + flap;
                right.roll = 0.15F - flap;
            } else {
                float breathe = 0.05F * MathHelper.sin(age * 0.08F);
                left.yaw = -1.45F;
                right.yaw = 1.45F;
                left.roll = -0.35F - breathe;
                right.roll = 0.35F + breathe;
            }
            VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(WINGS));
            left.render(matrices, vc, light, OverlayTexture.DEFAULT_UV);
            right.render(matrices, vc, light, OverlayTexture.DEFAULT_UV);
        }
    }
}
