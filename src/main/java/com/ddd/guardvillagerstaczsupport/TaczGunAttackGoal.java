package com.ddd.guardvillagerstaczsupport;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.common.entities.Guard;

import java.util.EnumSet;

public class TaczGunAttackGoal extends Goal {
    private static final int FAILED_ATTACK_RETRY_TICKS = 2;
    private static final int AMMO_SEARCH_RADIUS = 64;
    private static final int AMMO_SEARCH_COOLDOWN_TICKS = 100;
    private static final int FOOD_SEARCH_COOLDOWN_TICKS = 100;
    private static final int CONTAINER_REACH_TIMEOUT_TICKS = 400;
    private static final int TIMED_OUT_CONTAINER_SKIP_TICKS = 400;
    private static final int GUARD_OFFHAND_INVENTORY_SLOT = 4;
    private static final float LOW_HEALTH_FOOD_THRESHOLD = 19.0F;
    private static final double AMMO_CONTAINER_REACH_SQR = 4.0D;
    private static final double FRIENDLY_FIRE_LANE_WIDTH = 2.0D;
    private static final int FRIENDLY_FIRE_REPOSITION_RADIUS = 3;
    private static final int BLOCKED_VIEW_REPOSITION_RADIUS = 5;
    private static final int BLOCKED_VIEW_REPOSITION_ATTEMPTS = 16;
    private static final double DEFAULT_ATTACK_RANGE = 32.0D;
    private static final double DEFAULT_ATTACK_RANGE_SQR = DEFAULT_ATTACK_RANGE * DEFAULT_ATTACK_RANGE;

    private final Guard guard;
    private int attackTime;
    private int ammoSearchCooldown;
    private int foodSearchCooldown;
    private int ammoSourceTravelTicks;
    private int foodSourceTravelTicks;
    private int timedOutAmmoContainerSkipTicks;
    private int timedOutFoodContainerSkipTicks;
    private boolean stagedGuardInventoryRound;
    private boolean yieldToDefaultNoAmmoBehavior;
    private BlockPos ammoSourcePos;
    private BlockPos cachedAmmoContainerPos;
    private BlockPos foodSourcePos;
    private BlockPos cachedFoodContainerPos;
    private BlockPos timedOutAmmoContainerPos;
    private BlockPos timedOutFoodContainerPos;
    private int repositionAttemptOffset;

    public TaczGunAttackGoal(Guard guard) {
        this.guard = guard;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.guard.isEating() || this.guard.isBlocking() || !this.isHoldingTaczGun()) {
            this.yieldToDefaultNoAmmoBehavior = false;
            return false;
        }

        ItemStack gunStack = this.guard.getMainHandItem();
        if (this.yieldToDefaultNoAmmoBehavior) {
            if (!this.needsAmmoResupply(gunStack)) {
                this.yieldToDefaultNoAmmoBehavior = false;
            } else if (this.shouldSeekFood(gunStack)) {
                this.yieldToDefaultNoAmmoBehavior = false;
            } else if (this.ammoSearchCooldown > 0) {
                --this.ammoSearchCooldown;
                return false;
            } else {
                this.yieldToDefaultNoAmmoBehavior = false;
            }
        }

        return this.guard.getTarget() != null
                || this.needsAmmoResupply(gunStack)
                || this.shouldSeekFood(gunStack);
    }

    @Override
    public boolean canContinueToUse() {
        return this.ammoSourcePos != null
                || this.foodSourcePos != null
                || (!this.yieldToDefaultNoAmmoBehavior && this.canUse());
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.attackTime = 0;
        this.stagedGuardInventoryRound = false;
        this.drawCurrentGun();
    }

    @Override
    public void stop() {
        this.attackTime = 0;
        this.ammoSourcePos = null;
        this.foodSourcePos = null;
        this.ammoSourceTravelTicks = 0;
        this.foodSourceTravelTicks = 0;
        this.foodSearchCooldown = 0;
        this.stagedGuardInventoryRound = false;
        IGunOperator.fromLivingEntity(this.guard).aim(false);
    }

    @Override
    public void tick() {
        this.tickTimedOutContainerSkips();
        ItemStack gunStack = this.guard.getMainHandItem();
        if (this.foodSourcePos != null) {
            IGunOperator.fromLivingEntity(this.guard).aim(false);
            this.tickFoodResupply();
            return;
        }

        if (this.shouldSeekFood(gunStack)) {
            IGunOperator.fromLivingEntity(this.guard).aim(false);
            if (this.cachedFoodContainerPos != null && this.isCachedFoodContainerUsable()) {
                this.foodSourcePos = this.cachedFoodContainerPos;
                this.foodSourceTravelTicks = 0;
            } else if (this.cachedFoodContainerPos != null) {
                this.cachedFoodContainerPos = null;
                this.foodSearchCooldown = 0;
            }

            if (this.foodSourcePos != null) {
                this.tickFoodResupply();
                return;
            }

            if (this.foodSearchCooldown > 0) {
                --this.foodSearchCooldown;
            } else {
                this.foodSourcePos = this.findNearestVanillaFoodContainer();
                if (this.foodSourcePos != null) {
                    this.cachedFoodContainerPos = this.foodSourcePos;
                    this.foodSourceTravelTicks = 0;
                } else {
                    this.foodSearchCooldown = FOOD_SEARCH_COOLDOWN_TICKS;
                }
            }

            if (this.foodSourcePos != null) {
                this.tickFoodResupply();
                return;
            }

            this.tryStartAmmoResupply(gunStack);
            if (this.ammoSourcePos != null) {
                this.tickAmmoResupply(gunStack);
                return;
            }
        }

        if (this.ammoSourcePos != null) {
            IGunOperator.fromLivingEntity(this.guard).aim(false);
            this.tickAmmoResupply(gunStack);
            return;
        }

        if (this.needsAmmoResupply(gunStack)) {
            IGunOperator.fromLivingEntity(this.guard).aim(false);
            this.tryStartAmmoResupply(gunStack);
            if (this.ammoSourcePos != null) {
                this.tickAmmoResupply(gunStack);
            } else {
                this.enterDefaultNoAmmoBehavior();
            }
            return;
        }

        this.yieldToDefaultNoAmmoBehavior = false;
        this.foodSourcePos = null;
        this.foodSearchCooldown = 0;
        LivingEntity target = this.guard.getTarget();
        if (target == null) {
            IGunOperator.fromLivingEntity(this.guard).aim(false);
            return;
        }

        this.guard.getLookControl().setLookAt(target, 30.0F, 30.0F);
        this.guard.lookAt(target, 30.0F, 30.0F);
        this.aimAt(target);
        IGunOperator operator = IGunOperator.fromLivingEntity(this.guard);

        double distanceSqr = this.guard.distanceToSqr(target);
        boolean canSee = this.guard.getSensing().hasLineOfSight(target);
        boolean readyToFire = canSee && distanceSqr <= DEFAULT_ATTACK_RANGE_SQR;
        operator.aim(readyToFire);

        if (distanceSqr > DEFAULT_ATTACK_RANGE_SQR * 0.75D) {
            this.guard.getNavigation().moveTo(target, 0.8D);
        } else {
            this.guard.getNavigation().stop();
            this.guard.getMoveControl().strafe(0.0F, 0.0F);
        }

        if (this.attackTime > 0) {
            --this.attackTime;
        }

        if (!canSee) {
            if (this.isViewBlockedByBlocks(target)) {
                this.repositionShooterAroundBlockedView(target);
                this.attackTime = FAILED_ATTACK_RETRY_TICKS;
            }
            return;
        }

        if (this.attackTime > 0 || distanceSqr > DEFAULT_ATTACK_RANGE_SQR) {
            return;
        }

        if (this.hasPassiveEntityInLineOfFire(target)) {
            this.repositionShooterAroundLineOfFire(target);
            this.attackTime = FAILED_ATTACK_RETRY_TICKS;
            return;
        }

        if (operator.getSynDrawCoolDown() > 0L) {
            return;
        }

        if (!this.isFullAuto(gunStack) && operator.getSynShootCoolDown() > 0L) {
            return;
        }

        if (!this.prepareGunForSimpleNpcUse(gunStack)) {
            this.tryStartAmmoResupply(gunStack);
            if (this.ammoSourcePos == null) {
                this.enterDefaultNoAmmoBehavior();
            }
            return;
        }

        ShootResult result = operator.shoot(this.guard::getXRot, this.guard::getYRot);
        this.finishStagedGuardInventoryRound(gunStack, result);
        if (result == ShootResult.NOT_DRAW || result == ShootResult.IS_DRAWING) {
            this.drawCurrentGun();
        } else if (result == ShootResult.NEED_BOLT) {
            operator.bolt();
        } else if (result == ShootResult.NO_AMMO) {
            this.clearLoadedAmmoState(gunStack);
            this.tryStartAmmoResupply(gunStack);
            if (this.ammoSourcePos == null) {
                this.enterDefaultNoAmmoBehavior();
            }
        }

        this.attackTime = result == ShootResult.SUCCESS ? this.getPostShotDelayTicks(gunStack) : 0;
    }

    private boolean isHoldingTaczGun() {
        return IGun.getIGunOrNull(this.guard.getMainHandItem()) != null;
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

    private void drawCurrentGun() {
        IGunOperator.fromLivingEntity(this.guard).draw(this.guard::getMainHandItem);
    }

    private boolean needsAmmoResupply(ItemStack gunStack) {
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) {
            return false;
        }

        boolean hasLoadedRound = this.hasLoadedAmmo(gun, gunStack);
        return !hasLoadedRound && !this.guardInventoryHasCompatibleAmmo(gunStack);
    }

    private boolean guardInventoryHasCompatibleAmmo(ItemStack gunStack) {
        for (int slot = 0; slot < this.guard.guardInventory.getContainerSize(); ++slot) {
            ItemStack stack = this.guard.guardInventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }

            IAmmo ammo = IAmmo.getIAmmoOrNull(stack);
            if (ammo != null && ammo.isAmmoOfGun(gunStack, stack)) {
                return true;
            }

            IAmmoBox ammoBox = stack.getItem() instanceof IAmmoBox box ? box : null;
            if (ammoBox != null && ammoBox.isAmmoBoxOfGun(gunStack, stack)
                    && (ammoBox.isCreative(stack) || ammoBox.isAllTypeCreative(stack) || ammoBox.getAmmoCount(stack) > 0)) {
                return true;
            }
        }

        return false;
    }

    private boolean prepareGunForSimpleNpcUse(ItemStack gunStack) {
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) {
            return false;
        }

        boolean hasLoadedRound = this.hasLoadedAmmo(gun, gunStack);
        if (hasLoadedRound) {
            return true;
        }

        if (!this.consumeOneCompatibleAmmo(gunStack)) {
            return false;
        }

        this.stagedGuardInventoryRound = true;
        gun.setCurrentAmmoCount(gunStack, 1);
        gun.setBulletInBarrel(gunStack, true);
        return true;
    }

    private boolean hasLoadedAmmo(IGun gun, ItemStack gunStack) {
        return gun.hasBulletInBarrel(gunStack) || gun.getCurrentAmmoCount(gunStack) > 0;
    }

    private void finishStagedGuardInventoryRound(ItemStack gunStack, ShootResult result) {
        if (!this.stagedGuardInventoryRound) {
            return;
        }

        if (result == ShootResult.SUCCESS || result == ShootResult.NO_AMMO) {
            this.stagedGuardInventoryRound = false;
            this.clearLoadedAmmoState(gunStack);
        }
    }

    private void clearLoadedAmmoState(ItemStack gunStack) {
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun != null) {
            gun.setCurrentAmmoCount(gunStack, 0);
            gun.setBulletInBarrel(gunStack, false);
        }
    }

    private boolean consumeOneCompatibleAmmo(ItemStack gunStack) {
        for (int slot = 0; slot < this.guard.guardInventory.getContainerSize(); ++slot) {
            ItemStack stack = this.guard.guardInventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }

            IAmmo ammo = IAmmo.getIAmmoOrNull(stack);
            if (ammo != null && ammo.isAmmoOfGun(gunStack, stack)) {
                this.guard.guardInventory.removeItem(slot, 1);
                this.guard.guardInventory.setChanged();
                return true;
            }

            IAmmoBox ammoBox = stack.getItem() instanceof IAmmoBox box ? box : null;
            if (ammoBox != null && ammoBox.isAmmoBoxOfGun(gunStack, stack)
                    && (ammoBox.isCreative(stack) || ammoBox.isAllTypeCreative(stack) || ammoBox.getAmmoCount(stack) > 0)) {
                if (!ammoBox.isCreative(stack) && !ammoBox.isAllTypeCreative(stack)) {
                    int remainingAmmo = ammoBox.getAmmoCount(stack) - 1;
                    ammoBox.setAmmoCount(stack, remainingAmmo);
                    if (remainingAmmo <= 0) {
                        ammoBox.setAmmoId(stack, DefaultAssets.EMPTY_AMMO_ID);
                    }
                    this.guard.guardInventory.setChanged();
                }
                return true;
            }
        }

        return false;
    }

    private void tryStartAmmoResupply(ItemStack gunStack) {
        if (this.cachedAmmoContainerPos != null) {
            if (this.isCachedAmmoContainerUsable(gunStack)) {
                this.ammoSourcePos = this.cachedAmmoContainerPos;
                this.ammoSourceTravelTicks = 0;
                return;
            }

            this.cachedAmmoContainerPos = null;
            this.ammoSearchCooldown = 0;
        }

        if (this.ammoSearchCooldown > 0) {
            --this.ammoSearchCooldown;
            return;
        }

        this.ammoSourcePos = this.findNearestVanillaAmmoContainer(gunStack);
        if (this.ammoSourcePos != null) {
            this.cachedAmmoContainerPos = this.ammoSourcePos;
            this.ammoSourceTravelTicks = 0;
        }

        if (this.ammoSourcePos == null) {
            this.ammoSearchCooldown = AMMO_SEARCH_COOLDOWN_TICKS;
        }
    }

    private void enterDefaultNoAmmoBehavior() {
        this.yieldToDefaultNoAmmoBehavior = true;
        IGunOperator.fromLivingEntity(this.guard).aim(false);
        this.guard.getNavigation().stop();
    }

    private boolean shouldSeekFood(ItemStack gunStack) {
        return this.guard.getHealth() < LOW_HEALTH_FOOD_THRESHOLD
                && this.guard.getHealth() < this.guard.getMaxHealth()
                && (this.guard.getTarget() == null || !this.guardInventoryHasCompatibleAmmo(gunStack));
    }

    private BlockPos findNearestVanillaFoodContainer() {
        BlockPos guardPos = this.guard.blockPosition();
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.withinManhattan(guardPos, AMMO_SEARCH_RADIUS, AMMO_SEARCH_RADIUS, AMMO_SEARCH_RADIUS)) {
            if (pos.distSqr(guardPos) > AMMO_SEARCH_RADIUS * AMMO_SEARCH_RADIUS) {
                continue;
            }

            BlockEntity blockEntity = this.guard.level().getBlockEntity(pos);
            if (!(blockEntity instanceof Container container) || !this.isVanillaBlockEntity(blockEntity)) {
                continue;
            }

            if (!this.containerHasFood(container)) {
                continue;
            }

            if (this.isTimedOutFoodContainer(pos)) {
                continue;
            }

            double distance = pos.distSqr(guardPos);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = pos.immutable();
            }
        }

        return nearest;
    }

    private boolean isCachedFoodContainerUsable() {
        if (this.cachedFoodContainerPos.distSqr(this.guard.blockPosition()) > AMMO_SEARCH_RADIUS * AMMO_SEARCH_RADIUS) {
            return false;
        }

        BlockEntity blockEntity = this.guard.level().getBlockEntity(this.cachedFoodContainerPos);
        return blockEntity instanceof Container container
                && this.isVanillaBlockEntity(blockEntity)
                && this.containerHasFood(container)
                && !this.isTimedOutFoodContainer(this.cachedFoodContainerPos);
    }

    private boolean containerHasFood(Container container) {
        for (int slot = 0; slot < container.getContainerSize(); ++slot) {
            if (this.isFood(container.getItem(slot))) {
                return true;
            }
        }

        return false;
    }

    private void tickFoodResupply() {
        BlockEntity blockEntity = this.guard.level().getBlockEntity(this.foodSourcePos);
        if (!(blockEntity instanceof Container container)
                || !this.isVanillaBlockEntity(blockEntity)
                || !this.containerHasFood(container)) {
            if (this.foodSourcePos.equals(this.cachedFoodContainerPos)) {
                this.cachedFoodContainerPos = null;
            }
            this.foodSourcePos = null;
            this.foodSourceTravelTicks = 0;
            return;
        }

        if (this.guard.distanceToSqr(this.foodSourcePos.getX() + 0.5D, this.foodSourcePos.getY() + 0.5D, this.foodSourcePos.getZ() + 0.5D) > AMMO_CONTAINER_REACH_SQR) {
            if (++this.foodSourceTravelTicks >= CONTAINER_REACH_TIMEOUT_TICKS) {
                this.markFoodContainerTimedOut(this.foodSourcePos);
                return;
            }

            this.guard.getNavigation().moveTo(this.foodSourcePos.getX() + 0.5D, this.foodSourcePos.getY(), this.foodSourcePos.getZ() + 0.5D, 0.8D);
            return;
        }

        this.foodSourceTravelTicks = 0;
        this.guard.getNavigation().stop();
        this.consumeOneFoodFromContainer(container);
        if (this.containerHasFood(container)) {
            this.cachedFoodContainerPos = this.foodSourcePos;
        } else if (this.foodSourcePos.equals(this.cachedFoodContainerPos)) {
            this.cachedFoodContainerPos = null;
        }
        this.foodSourcePos = null;
    }

    private void consumeOneFoodFromContainer(Container container) {
        for (int slot = 0; slot < container.getContainerSize(); ++slot) {
            ItemStack stack = container.getItem(slot);
            if (!this.isFood(stack)) {
                continue;
            }

            ItemStack food = container.removeItem(slot, 1);
            FoodProperties foodProperties = food.getFoodProperties(this.guard);
            float nutrition = foodProperties != null ? (float)foodProperties.nutrition() : 1.0F;
            this.guard.heal(2.0F * nutrition);
            this.guard.gameEvent(GameEvent.EAT);

            if (foodProperties != null && foodProperties.usingConvertsTo().isPresent()) {
                this.returnToContainer(container, slot, foodProperties.usingConvertsTo().get().copy());
            }

            container.setChanged();
            return;
        }
    }

    private boolean isFood(ItemStack stack) {
        return !stack.isEmpty() && stack.getFoodProperties(this.guard) != null;
    }

    private BlockPos findNearestVanillaAmmoContainer(ItemStack gunStack) {
        BlockPos guardPos = this.guard.blockPosition();
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.withinManhattan(guardPos, AMMO_SEARCH_RADIUS, AMMO_SEARCH_RADIUS, AMMO_SEARCH_RADIUS)) {
            if (pos.distSqr(guardPos) > AMMO_SEARCH_RADIUS * AMMO_SEARCH_RADIUS) {
                continue;
            }

            BlockEntity blockEntity = this.guard.level().getBlockEntity(pos);
            if (!(blockEntity instanceof Container container) || !this.isVanillaBlockEntity(blockEntity)) {
                continue;
            }

            if (!this.containerHasCompatibleAmmo(container, gunStack)) {
                continue;
            }

            if (this.isTimedOutAmmoContainer(pos)) {
                continue;
            }

            double distance = pos.distSqr(guardPos);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = pos.immutable();
            }
        }

        return nearest;
    }

    private boolean isCachedAmmoContainerUsable(ItemStack gunStack) {
        if (this.cachedAmmoContainerPos.distSqr(this.guard.blockPosition()) > AMMO_SEARCH_RADIUS * AMMO_SEARCH_RADIUS) {
            return false;
        }

        BlockEntity blockEntity = this.guard.level().getBlockEntity(this.cachedAmmoContainerPos);
        return blockEntity instanceof Container container
                && this.isVanillaBlockEntity(blockEntity)
                && this.containerHasCompatibleAmmo(container, gunStack)
                && !this.isTimedOutAmmoContainer(this.cachedAmmoContainerPos);
    }

    private boolean isTimedOutAmmoContainer(BlockPos pos) {
        return this.timedOutAmmoContainerSkipTicks > 0
                && this.timedOutAmmoContainerPos != null
                && this.timedOutAmmoContainerPos.equals(pos);
    }

    private boolean isTimedOutFoodContainer(BlockPos pos) {
        return this.timedOutFoodContainerSkipTicks > 0
                && this.timedOutFoodContainerPos != null
                && this.timedOutFoodContainerPos.equals(pos);
    }

    private void markAmmoContainerTimedOut(BlockPos pos) {
        this.timedOutAmmoContainerPos = pos.immutable();
        this.timedOutAmmoContainerSkipTicks = TIMED_OUT_CONTAINER_SKIP_TICKS;
        if (pos.equals(this.cachedAmmoContainerPos)) {
            this.cachedAmmoContainerPos = null;
        }
        this.ammoSourcePos = null;
        this.ammoSourceTravelTicks = 0;
        this.ammoSearchCooldown = 0;
    }

    private void markFoodContainerTimedOut(BlockPos pos) {
        this.timedOutFoodContainerPos = pos.immutable();
        this.timedOutFoodContainerSkipTicks = TIMED_OUT_CONTAINER_SKIP_TICKS;
        if (pos.equals(this.cachedFoodContainerPos)) {
            this.cachedFoodContainerPos = null;
        }
        this.foodSourcePos = null;
        this.foodSourceTravelTicks = 0;
        this.foodSearchCooldown = 0;
    }

    private void tickTimedOutContainerSkips() {
        if (this.timedOutAmmoContainerSkipTicks > 0 && --this.timedOutAmmoContainerSkipTicks == 0) {
            this.timedOutAmmoContainerPos = null;
        }

        if (this.timedOutFoodContainerSkipTicks > 0 && --this.timedOutFoodContainerSkipTicks == 0) {
            this.timedOutFoodContainerPos = null;
        }
    }

    private boolean isVanillaBlockEntity(BlockEntity blockEntity) {
        ResourceLocation id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
        return id != null && "minecraft".equals(id.getNamespace());
    }

    private boolean containerHasCompatibleAmmo(Container container, ItemStack gunStack) {
        for (int slot = 0; slot < container.getContainerSize(); ++slot) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }

            IAmmo ammo = IAmmo.getIAmmoOrNull(stack);
            if (ammo != null && ammo.isAmmoOfGun(gunStack, stack)) {
                return true;
            }

            IAmmoBox ammoBox = stack.getItem() instanceof IAmmoBox box ? box : null;
            if (ammoBox != null && ammoBox.isAmmoBoxOfGun(gunStack, stack)
                    && (ammoBox.isCreative(stack) || ammoBox.isAllTypeCreative(stack) || ammoBox.getAmmoCount(stack) > 0)) {
                return true;
            }
        }

        return false;
    }

    private void tickAmmoResupply(ItemStack gunStack) {
        BlockEntity blockEntity = this.guard.level().getBlockEntity(this.ammoSourcePos);
        if (!(blockEntity instanceof Container container)
                || !this.isVanillaBlockEntity(blockEntity)
                || !this.containerHasCompatibleAmmo(container, gunStack)) {
            if (this.ammoSourcePos.equals(this.cachedAmmoContainerPos)) {
                this.cachedAmmoContainerPos = null;
            }
            this.ammoSourcePos = null;
            this.ammoSourceTravelTicks = 0;
            return;
        }

        if (this.guard.distanceToSqr(this.ammoSourcePos.getX() + 0.5D, this.ammoSourcePos.getY() + 0.5D, this.ammoSourcePos.getZ() + 0.5D) > AMMO_CONTAINER_REACH_SQR) {
            if (++this.ammoSourceTravelTicks >= CONTAINER_REACH_TIMEOUT_TICKS) {
                this.markAmmoContainerTimedOut(this.ammoSourcePos);
                this.tryStartAmmoResupply(gunStack);
                return;
            }

            this.guard.getNavigation().moveTo(this.ammoSourcePos.getX() + 0.5D, this.ammoSourcePos.getY(), this.ammoSourcePos.getZ() + 0.5D, 0.8D);
            return;
        }

        this.ammoSourceTravelTicks = 0;
        this.guard.getNavigation().stop();
        this.extractMaxCompatibleAmmo(container, gunStack);
        if (this.containerHasCompatibleAmmo(container, gunStack)) {
            this.cachedAmmoContainerPos = this.ammoSourcePos;
        } else if (this.ammoSourcePos.equals(this.cachedAmmoContainerPos)) {
            this.cachedAmmoContainerPos = null;
        }
        this.ammoSourcePos = null;
    }

    private void extractMaxCompatibleAmmo(Container container, ItemStack gunStack) {
        int looseAmmoCount = this.countLooseCompatibleAmmo(container, gunStack);
        int looseAmmoToTake = looseAmmoCount;
        if (looseAmmoToTake > 0) {
            this.extractLooseCompatibleAmmo(container, gunStack, looseAmmoToTake);
        }

        this.extractMaxAmmoBoxRounds(container, gunStack);
        container.setChanged();
    }

    private int countLooseCompatibleAmmo(Container container, ItemStack gunStack) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); ++slot) {
            ItemStack stack = container.getItem(slot);
            IAmmo ammo = IAmmo.getIAmmoOrNull(stack);
            if (ammo != null && ammo.isAmmoOfGun(gunStack, stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void extractLooseCompatibleAmmo(Container container, ItemStack gunStack, int amountToTake) {
        int remaining = amountToTake;
        for (int slot = 0; slot < container.getContainerSize() && remaining > 0; ++slot) {
            ItemStack stack = container.getItem(slot);
            IAmmo ammo = IAmmo.getIAmmoOrNull(stack);
            if (ammo == null || !ammo.isAmmoOfGun(gunStack, stack)) {
                continue;
            }

            int count = Math.min(remaining, stack.getCount());
            ItemStack extracted = container.removeItem(slot, count);
            ItemStack leftover = this.insertIntoGuardInventory(extracted);
            if (!leftover.isEmpty()) {
                this.returnToContainer(container, slot, leftover);
                remaining = 0;
            } else {
                remaining -= count;
            }
        }
    }

    private void extractMaxAmmoBoxRounds(Container container, ItemStack gunStack) {
        for (int slot = 0; slot < container.getContainerSize(); ++slot) {
            ItemStack stack = container.getItem(slot);
            IAmmoBox ammoBox = stack.getItem() instanceof IAmmoBox box ? box : null;
            if (ammoBox == null || !ammoBox.isAmmoBoxOfGun(gunStack, stack)) {
                continue;
            }

            if (ammoBox.isCreative(stack) || ammoBox.isAllTypeCreative(stack)) {
                this.insertIntoGuardInventory(stack.copyWithCount(1));
                continue;
            }

            int amountToTake = ammoBox.getAmmoCount(stack);
            if (amountToTake <= 0) {
                continue;
            }

            ItemStack extractedAmmo = AmmoItemBuilder.create()
                    .setId(ammoBox.getAmmoId(stack))
                    .setCount(amountToTake)
                    .build();
            ItemStack leftover = this.insertIntoGuardInventory(extractedAmmo);
            int inserted = amountToTake - leftover.getCount();
            if (inserted > 0) {
                int remainingAmmo = ammoBox.getAmmoCount(stack) - inserted;
                ammoBox.setAmmoCount(stack, remainingAmmo);
                if (remainingAmmo <= 0) {
                    ammoBox.setAmmoId(stack, DefaultAssets.EMPTY_AMMO_ID);
                }
            }
        }
    }

    private ItemStack insertIntoGuardInventory(ItemStack stack) {
        ItemStack remaining = stack.copy();
        return this.insertIntoGuardInventorySlot(GUARD_OFFHAND_INVENTORY_SLOT, remaining);
    }

    private ItemStack insertIntoGuardInventorySlot(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return stack;
        }

        ItemStack existing = this.guard.guardInventory.getItem(slot);
        if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
            int move = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
            if (move > 0) {
                existing.grow(move);
                stack.shrink(move);
                this.guard.guardInventory.setChanged();
            }
            return stack;
        }

        if (existing.isEmpty()) {
            int move = Math.min(stack.getCount(), stack.getMaxStackSize());
            this.guard.guardInventory.setItem(slot, stack.split(move));
            this.guard.guardInventory.setChanged();
        }

        return stack;
    }

    private void returnToContainer(Container container, int preferredSlot, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        ItemStack existing = container.getItem(preferredSlot);
        if (existing.isEmpty()) {
            container.setItem(preferredSlot, stack);
            return;
        }

        if (ItemStack.isSameItemSameComponents(existing, stack)) {
            int move = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
            existing.grow(move);
            stack.shrink(move);
        }

        for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); ++slot) {
            if (container.getItem(slot).isEmpty()) {
                container.setItem(slot, stack);
                return;
            }
        }
    }

    private boolean hasPassiveEntityInLineOfFire(LivingEntity target) {
        Vec3 start = this.guard.getEyePosition();
        Vec3 end = target.getEyePosition();
        Vec3 shot = end.subtract(start);
        double shotLengthSqr = shot.lengthSqr();
        if (shotLengthSqr < 1.0E-4D) {
            return false;
        }

        AABB searchBox = new AABB(start, end).inflate(FRIENDLY_FIRE_LANE_WIDTH);
        for (LivingEntity entity : this.guard.level().getEntitiesOfClass(LivingEntity.class, searchBox)) {
            if (entity == this.guard || entity == target || !entity.isAlive() || entity instanceof Enemy) {
                continue;
            }

            Vec3 entityCenter = entity.getBoundingBox().getCenter();
            double projection = entityCenter.subtract(start).dot(shot) / shotLengthSqr;
            if (projection <= 0.05D || projection >= 0.95D) {
                continue;
            }

            Vec3 closestPoint = start.add(shot.scale(projection));
            double allowedWidth = FRIENDLY_FIRE_LANE_WIDTH + entity.getBbWidth() * 0.5D;
            if (entityCenter.distanceToSqr(closestPoint) <= allowedWidth * allowedWidth) {
                return true;
            }
        }

        return false;
    }

    private void repositionShooterAroundLineOfFire(LivingEntity target) {
        BlockPos currentPos = this.guard.blockPosition();
        BlockPos bestPos = null;
        double bestScore = Double.MAX_VALUE;
        int skipped = 0;

        for (BlockPos candidate : BlockPos.withinManhattan(currentPos, FRIENDLY_FIRE_REPOSITION_RADIUS, 1, FRIENDLY_FIRE_REPOSITION_RADIUS)) {
            if (candidate.equals(currentPos) || candidate.distSqr(currentPos) > FRIENDLY_FIRE_REPOSITION_RADIUS * FRIENDLY_FIRE_REPOSITION_RADIUS) {
                continue;
            }

            if (skipped++ < this.repositionAttemptOffset) {
                continue;
            }

            double x = candidate.getX() + 0.5D;
            double y = candidate.getY();
            double z = candidate.getZ() + 0.5D;
            if (target.distanceToSqr(x, y, z) > DEFAULT_ATTACK_RANGE_SQR) {
                continue;
            }

            if (!this.positionHasClearLineOfFire(candidate, target)) {
                continue;
            }

            Path path = this.guard.getNavigation().createPath(candidate, 0);
            if (path == null || !path.canReach()) {
                continue;
            }

            double score = candidate.distSqr(currentPos) + target.distanceToSqr(x, y, z) * 0.01D;
            if (score < bestScore) {
                bestScore = score;
                bestPos = candidate.immutable();
            }
        }

        this.repositionAttemptOffset = (this.repositionAttemptOffset + 3) % 16;
        if (bestPos != null) {
            this.guard.getNavigation().moveTo(bestPos.getX() + 0.5D, bestPos.getY(), bestPos.getZ() + 0.5D, 0.9D);
        }
    }

    private void repositionShooterAroundBlockedView(LivingEntity target) {
        BlockPos currentPos = this.guard.blockPosition();
        for (int attempt = 0; attempt < BLOCKED_VIEW_REPOSITION_ATTEMPTS; ++attempt) {
            int dx = this.guard.getRandom().nextInt(BLOCKED_VIEW_REPOSITION_RADIUS * 2 + 1) - BLOCKED_VIEW_REPOSITION_RADIUS;
            int dy = this.guard.getRandom().nextInt(3) - 1;
            int dz = this.guard.getRandom().nextInt(BLOCKED_VIEW_REPOSITION_RADIUS * 2 + 1) - BLOCKED_VIEW_REPOSITION_RADIUS;
            BlockPos candidate = currentPos.offset(dx, dy, dz);

            if (candidate.equals(currentPos) || candidate.distSqr(currentPos) > BLOCKED_VIEW_REPOSITION_RADIUS * BLOCKED_VIEW_REPOSITION_RADIUS) {
                continue;
            }

            double x = candidate.getX() + 0.5D;
            double y = candidate.getY();
            double z = candidate.getZ() + 0.5D;
            if (target.distanceToSqr(x, y, z) > DEFAULT_ATTACK_RANGE_SQR) {
                continue;
            }

            if (!this.positionHasUnobstructedBlockView(candidate, target)) {
                continue;
            }

            Path path = this.guard.getNavigation().createPath(candidate, 0);
            if (path == null || !path.canReach()) {
                continue;
            }

            this.guard.getNavigation().moveTo(x, y, z, 0.9D);
            return;
        }
    }

    private boolean positionHasClearLineOfFire(BlockPos candidate, LivingEntity target) {
        Vec3 start = new Vec3(candidate.getX() + 0.5D, this.guard.getEyeY(), candidate.getZ() + 0.5D);
        Vec3 end = target.getEyePosition();
        Vec3 shot = end.subtract(start);
        double shotLengthSqr = shot.lengthSqr();
        if (shotLengthSqr < 1.0E-4D) {
            return false;
        }

        AABB searchBox = new AABB(start, end).inflate(FRIENDLY_FIRE_LANE_WIDTH);
        for (LivingEntity entity : this.guard.level().getEntitiesOfClass(LivingEntity.class, searchBox)) {
            if (entity == this.guard || entity == target || !entity.isAlive() || entity instanceof Enemy) {
                continue;
            }

            Vec3 entityCenter = entity.getBoundingBox().getCenter();
            double projection = entityCenter.subtract(start).dot(shot) / shotLengthSqr;
            if (projection <= 0.05D || projection >= 0.95D) {
                continue;
            }

            Vec3 closestPoint = start.add(shot.scale(projection));
            double allowedWidth = FRIENDLY_FIRE_LANE_WIDTH + entity.getBbWidth() * 0.5D;
            if (entityCenter.distanceToSqr(closestPoint) <= allowedWidth * allowedWidth) {
                return false;
            }
        }

        return true;
    }

    private boolean isViewBlockedByBlocks(LivingEntity target) {
        return !this.hasUnobstructedBlockView(this.guard.getEyePosition(), target.getEyePosition());
    }

    private boolean positionHasUnobstructedBlockView(BlockPos candidate, LivingEntity target) {
        double eyeOffset = this.guard.getEyeY() - this.guard.getY();
        Vec3 start = new Vec3(candidate.getX() + 0.5D, candidate.getY() + eyeOffset, candidate.getZ() + 0.5D);
        return this.hasUnobstructedBlockView(start, target.getEyePosition());
    }

    private boolean hasUnobstructedBlockView(Vec3 start, Vec3 end) {
        HitResult result = this.guard.level().clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this.guard));
        return result.getType() == HitResult.Type.MISS;
    }

    private void aimAt(LivingEntity target) {
        double dx = target.getX() - this.guard.getX();
        double dy = target.getEyeY() - this.guard.getEyeY();
        double dz = target.getZ() - this.guard.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float)(-(Mth.atan2(dy, horizontalDistance) * Mth.RAD_TO_DEG));

        this.guard.setYRot(yaw);
        this.guard.setYHeadRot(yaw);
        this.guard.yBodyRot = yaw;
        this.guard.setXRot(pitch);
    }
}
