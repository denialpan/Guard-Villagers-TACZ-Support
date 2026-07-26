package com.ddd.guardvillagerstaczsupport;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;

public class PillagerTaczGunAttackGoal extends Goal {
    private static final int FAILED_ATTACK_RETRY_TICKS = 2;

    private final Pillager pillager;
    private int attackTime;
    private int reloadTime;
    private int shotsUntilReload;

    public PillagerTaczGunAttackGoal(Pillager pillager) {
        this.pillager = pillager;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.pillager.getTarget();
        return target != null && target.isAlive() && this.isHoldingTaczGun();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.attackTime = 0;
        this.reloadTime = 0;
        this.shotsUntilReload = this.nextMagazineSize();
        this.drawCurrentGun();
    }

    @Override
    public void stop() {
        this.attackTime = 0;
        this.reloadTime = 0;
        IGunOperator.fromLivingEntity(this.pillager).aim(false);
    }

    @Override
    public void tick() {
        LivingEntity target = this.pillager.getTarget();
        if (target == null || !target.isAlive()) {
            IGunOperator.fromLivingEntity(this.pillager).aim(false);
            return;
        }

        ItemStack gunStack = this.pillager.getMainHandItem();
        if (IGun.getIGunOrNull(gunStack) == null) {
            IGunOperator.fromLivingEntity(this.pillager).aim(false);
            return;
        }

        this.pillager.getLookControl().setLookAt(target, 30.0F, 30.0F);
        this.pillager.lookAt(target, 30.0F, 30.0F);
        this.aimAt(target);

        IGunOperator operator = IGunOperator.fromLivingEntity(this.pillager);
        double attackRangeSqr = this.defaultAttackRangeSqr();
        double distanceSqr = this.pillager.distanceToSqr(target);
        boolean canSee = this.pillager.getSensing().hasLineOfSight(target);
        boolean readyToFire = canSee && distanceSqr <= attackRangeSqr;
        operator.aim(readyToFire && this.reloadTime <= 0);

        if (distanceSqr > attackRangeSqr * 0.75D) {
            this.pillager.getNavigation().moveTo(target, 0.8D);
        } else {
            this.pillager.getNavigation().stop();
            this.pillager.getMoveControl().strafe(0.0F, 0.0F);
        }

        if (this.attackTime > 0) {
            --this.attackTime;
        }

        if (this.reloadTime > 0) {
            --this.reloadTime;
            IGunOperator.fromLivingEntity(this.pillager).aim(false);
            return;
        }

        if (!readyToFire || this.attackTime > 0 || operator.getSynDrawCoolDown() > 0L) {
            return;
        }

        if (!this.isFullAuto(gunStack) && operator.getSynShootCoolDown() > 0L) {
            return;
        }

        this.prepareInfiniteAmmo(gunStack);
        ShootResult result = operator.shoot(this.pillager::getXRot, this.pillager::getYRot);
        if (result == ShootResult.NOT_DRAW || result == ShootResult.IS_DRAWING) {
            this.drawCurrentGun();
        } else if (result == ShootResult.NEED_BOLT) {
            operator.bolt();
        } else if (result == ShootResult.NO_AMMO) {
            this.prepareInfiniteAmmo(gunStack);
        }

        if (result == ShootResult.SUCCESS) {
            this.attackTime = this.getPostShotDelayTicks(gunStack);
            if (--this.shotsUntilReload <= 0) {
                this.reloadTime = Config.HOSTILE_TACZ_RELOAD_TICKS.get();
                this.shotsUntilReload = this.nextMagazineSize();
            }
        } else {
            this.attackTime = FAILED_ATTACK_RETRY_TICKS;
        }
    }

    private boolean isHoldingTaczGun() {
        return IGun.getIGunOrNull(this.pillager.getMainHandItem()) != null;
    }

    private boolean isFullAuto(ItemStack gunStack) {
        IGun gun = IGun.getIGunOrNull(gunStack);
        return gun != null && gun.getFireMode(gunStack) == FireMode.AUTO;
    }

    private int getPostShotDelayTicks(ItemStack gunStack) {
        if (this.isFullAuto(gunStack)) {
            return 0;
        }

        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) {
            return FAILED_ATTACK_RETRY_TICKS;
        }

        int rpm = gun.getRPM(gunStack);
        if (rpm <= 0) {
            return FAILED_ATTACK_RETRY_TICKS;
        }

        return Math.max(1, (int)Math.ceil(1200.0D / (double)rpm));
    }

    private double defaultAttackRangeSqr() {
        double attackRange = Config.DEFAULT_ATTACK_RANGE.get();
        return attackRange * attackRange;
    }

    private void drawCurrentGun() {
        IGunOperator.fromLivingEntity(this.pillager).draw(this.pillager::getMainHandItem);
    }

    private int nextMagazineSize() {
        return Math.max(1, Config.HOSTILE_TACZ_MAGAZINE_SIZE.get());
    }

    private void prepareInfiniteAmmo(ItemStack gunStack) {
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun != null && !gun.hasBulletInBarrel(gunStack) && gun.getCurrentAmmoCount(gunStack) <= 0) {
            gun.setCurrentAmmoCount(gunStack, 1);
            gun.setBulletInBarrel(gunStack, true);
        }
    }

    private void aimAt(LivingEntity target) {
        double dx = target.getX() - this.pillager.getX();
        double dy = target.getEyeY() - this.pillager.getEyeY();
        double dz = target.getZ() - this.pillager.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float)(-(Mth.atan2(dy, horizontalDistance) * Mth.RAD_TO_DEG));

        this.pillager.setYRot(yaw);
        this.pillager.setYHeadRot(yaw);
        this.pillager.yBodyRot = yaw;
        this.pillager.setXRot(pitch);
    }
}
