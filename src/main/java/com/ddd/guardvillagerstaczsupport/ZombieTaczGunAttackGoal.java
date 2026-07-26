package com.ddd.guardvillagerstaczsupport;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;

public class ZombieTaczGunAttackGoal extends Goal {
    private static final int FAILED_ATTACK_RETRY_TICKS = 4;

    private final Zombie zombie;
    private int attackTime;
    private int reloadTime;
    private int shotsUntilReload;

    public ZombieTaczGunAttackGoal(Zombie zombie) {
        this.zombie = zombie;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.zombie.getTarget();
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
        IGunOperator.fromLivingEntity(this.zombie).aim(false);
    }

    @Override
    public void tick() {
        LivingEntity target = this.zombie.getTarget();
        if (target == null || !target.isAlive()) {
            IGunOperator.fromLivingEntity(this.zombie).aim(false);
            return;
        }

        ItemStack gunStack = this.zombie.getMainHandItem();
        if (IGun.getIGunOrNull(gunStack) == null) {
            IGunOperator.fromLivingEntity(this.zombie).aim(false);
            return;
        }

        this.zombie.getLookControl().setLookAt(target, 30.0F, 30.0F);
        this.zombie.lookAt(target, 30.0F, 30.0F);

        IGunOperator operator = IGunOperator.fromLivingEntity(this.zombie);
        double attackRangeSqr = this.defaultAttackRangeSqr();
        double distanceSqr = this.zombie.distanceToSqr(target);
        boolean canSee = this.zombie.getSensing().hasLineOfSight(target);
        boolean readyToFire = canSee && distanceSqr <= attackRangeSqr;
        operator.aim(readyToFire);

        if (distanceSqr > attackRangeSqr * 0.75D) {
            this.zombie.getNavigation().moveTo(target, 0.8D);
        } else {
            this.zombie.getNavigation().stop();
            this.zombie.getMoveControl().strafe(0.0F, 0.0F);
        }

        if (this.attackTime > 0) {
            --this.attackTime;
        }

        if (this.reloadTime > 0) {
            --this.reloadTime;
            IGunOperator.fromLivingEntity(this.zombie).aim(false);
            return;
        }

        if (!readyToFire || this.attackTime > 0 || operator.getSynDrawCoolDown() > 0L) {
            return;
        }

        if (!this.isFullAuto(gunStack) && operator.getSynShootCoolDown() > 0L) {
            return;
        }

        this.aimAtWithInaccuracy(target);
        this.prepareInfiniteAmmo(gunStack);
        ShootResult result = operator.shoot(this.zombie::getXRot, this.zombie::getYRot);
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
        return IGun.getIGunOrNull(this.zombie.getMainHandItem()) != null;
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
        IGunOperator.fromLivingEntity(this.zombie).draw(this.zombie::getMainHandItem);
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

    private void aimAtWithInaccuracy(LivingEntity target) {
        double dx = target.getX() - this.zombie.getX();
        double dy = target.getEyeY() - this.zombie.getEyeY();
        double dz = target.getZ() - this.zombie.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float)(-(Mth.atan2(dy, horizontalDistance) * Mth.RAD_TO_DEG));
        float spread = Config.ZOMBIE_TACZ_INACCURACY_DEGREES.get().floatValue();

        yaw += this.randomOffset(spread);
        pitch += this.randomOffset(spread);

        this.zombie.setYRot(yaw);
        this.zombie.setYHeadRot(yaw);
        this.zombie.yBodyRot = yaw;
        this.zombie.setXRot(pitch);
    }

    private float randomOffset(float maxDegrees) {
        if (maxDegrees <= 0.0F) {
            return 0.0F;
        }

        return (this.zombie.getRandom().nextFloat() * 2.0F - 1.0F) * maxDegrees;
    }
}
