package com.ddd.guardvillagerstaczsupport.mixin;

import com.tacz.guns.api.item.IGun;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Pillager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IllagerModel.class)
public class IllagerModelMixin {
    @Shadow
    @Final
    private ModelPart arms;

    @Shadow
    @Final
    private ModelPart rightArm;

    @Shadow
    @Final
    private ModelPart leftArm;

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void guardvillagerstaczsupport$useExtendedGunPoseForTaczPillagers(AbstractIllager entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Pillager) || IGun.getIGunOrNull(entity.getMainHandItem()) == null) {
            return;
        }

        this.arms.visible = false;
        this.leftArm.visible = true;
        this.rightArm.visible = true;

        float headYaw = netHeadYaw * (float)(Math.PI / 180.0D);
        float headPitchRadians = headPitch * (float)(Math.PI / 180.0D);

        this.rightArm.xRot = headPitchRadians - 1.45F;
        this.rightArm.yRot = headYaw - 0.18F;
        this.rightArm.zRot = 0.0F;

        this.leftArm.xRot = headPitchRadians - 1.45F;
        this.leftArm.yRot = headYaw + 0.18F;
        this.leftArm.zRot = 0.0F;
    }
}
