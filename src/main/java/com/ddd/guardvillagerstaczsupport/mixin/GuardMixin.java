package com.ddd.guardvillagerstaczsupport.mixin;

import com.ddd.guardvillagerstaczsupport.TaczGunAttackGoal;
import com.ddd.guardvillagerstaczsupport.TaczTargetAssistGoal;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tallestegg.guardvillagers.common.entities.Guard;

@Mixin(Guard.class)
public abstract class GuardMixin {
    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void guardvillagerstaczsupport$addTaczGunGoal(CallbackInfo ci) {
        Guard guard = (Guard)(Object)this;
        ((Mob)(Object)this).goalSelector.addGoal(2, new TaczGunAttackGoal(guard));
        ((Mob)(Object)this).targetSelector.addGoal(1, new TaczTargetAssistGoal(guard));
    }
}
