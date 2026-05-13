package com.ddd.guardvillagerstaczsupport;

import com.tacz.guns.api.item.IGun;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import tallestegg.guardvillagers.common.entities.Guard;

import java.util.Comparator;
import java.util.EnumSet;

public class TaczTargetAssistGoal extends Goal {
    private static final double TARGET_SCAN_RANGE = 32.0D;

    private final Guard guard;
    private LivingEntity target;

    public TaczTargetAssistGoal(Guard guard) {
        this.guard = guard;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (IGun.getIGunOrNull(this.guard.getMainHandItem()) == null) {
            return false;
        }

        LivingEntity currentTarget = this.guard.getTarget();
        if (this.isValidTarget(currentTarget) && this.guard.getSensing().hasLineOfSight(currentTarget)) {
            return false;
        }

        this.target = this.findNearestVisibleHostile();
        return this.target != null;
    }

    @Override
    public void start() {
        this.guard.setTarget(this.target);
    }

    private LivingEntity findNearestVisibleHostile() {
        return this.guard.level()
                .getEntitiesOfClass(LivingEntity.class, this.guard.getBoundingBox().inflate(TARGET_SCAN_RANGE), this::isVisibleHostile)
                .stream()
                .min(Comparator.comparingDouble(this.guard::distanceToSqr))
                .orElse(null);
    }

    private boolean isVisibleHostile(LivingEntity entity) {
        return this.isValidTarget(entity) && this.guard.getSensing().hasLineOfSight(entity);
    }

    private boolean isValidTarget(LivingEntity entity) {
        return entity != null
                && entity != this.guard
                && entity.isAlive()
                && !entity.isInvisible()
                && entity instanceof Enemy
                && this.guard.canAttack(entity);
    }
}
