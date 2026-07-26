package com.ddd.guardvillagerstaczsupport;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.ArrayList;
import java.util.List;

public class PillagerTaczHandler {
    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.getEntity().getType() != EntityType.PILLAGER || !(event.getEntity() instanceof Pillager pillager)) {
            return;
        }

        pillager.goalSelector.addGoal(2, new PillagerTaczGunAttackGoal(pillager));

        if (event.loadedFromDisk() || pillager.getRandom().nextDouble() >= Config.PILLAGER_TACZ_WEAPON_SPAWN_CHANCE.get()) {
            return;
        }

        ItemStack gunStack = this.createRandomConfiguredGun(pillager);
        if (gunStack.isEmpty() || IGun.getIGunOrNull(gunStack) == null) {
            return;
        }

        pillager.setItemSlot(EquipmentSlot.MAINHAND, gunStack);
        pillager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity().level().isClientSide() || event.getEntity().getType() != EntityType.PILLAGER || !(event.getEntity() instanceof Pillager pillager)) {
            return;
        }

        ItemStack gunStack = pillager.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null || pillager.getRandom().nextDouble() >= Config.PILLAGER_TACZ_WEAPON_DROP_CHANCE.get()) {
            return;
        }

        this.addDrop(event, gunStack.copyWithCount(1));

        ItemStack ammoStack = this.createAmmoDrop(gun.getGunId(gunStack), pillager);
        if (!ammoStack.isEmpty()) {
            this.addDrop(event, ammoStack);
        }
    }

    private ItemStack createRandomConfiguredGun(Pillager pillager) {
        List<ResourceLocation> gunIds = this.getConfiguredGunIds();
        if (gunIds.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ResourceLocation gunId = gunIds.get(pillager.getRandom().nextInt(gunIds.size()));
        return GunItemBuilder.create()
                .setId(gunId)
                .setCount(1)
                .setAmmoCount(0)
                .setAmmoInBarrel(false)
                .build(pillager.registryAccess());
    }

    private List<ResourceLocation> getConfiguredGunIds() {
        List<ResourceLocation> gunIds = new ArrayList<>();
        for (String entry : Config.PILLAGER_TACZ_WEAPON_IDS.get()) {
            try {
                gunIds.add(ResourceLocation.parse(entry));
            } catch (Exception ignored) {
                GuardVillagersTACZSupport.LOGGER.warn("Ignoring invalid TACZ pillager weapon id '{}'", entry);
            }
        }
        return gunIds;
    }

    private ItemStack createAmmoDrop(ResourceLocation gunId, Pillager pillager) {
        if (gunId == null) {
            return ItemStack.EMPTY;
        }

        int min = Config.PILLAGER_TACZ_AMMO_DROP_MIN.get();
        int max = Math.max(min, Config.PILLAGER_TACZ_AMMO_DROP_MAX.get());
        int count = min + pillager.getRandom().nextInt(max - min + 1);
        if (count <= 0) {
            return ItemStack.EMPTY;
        }

        CommonGunIndex gunIndex;
        try {
            gunIndex = CommonAssetsManager.get().getGunIndex(gunId);
        } catch (Exception exception) {
            GuardVillagersTACZSupport.LOGGER.warn("Could not resolve TACZ ammo for pillager weapon '{}'", gunId, exception);
            return ItemStack.EMPTY;
        }

        if (gunIndex == null || gunIndex.getGunData() == null || gunIndex.getGunData().getAmmoId() == null) {
            return ItemStack.EMPTY;
        }

        return AmmoItemBuilder.create()
                .setId(gunIndex.getGunData().getAmmoId())
                .setCount(count)
                .build();
    }

    private void addDrop(LivingDropsEvent event, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        event.getDrops().add(new ItemEntity(
                event.getEntity().level(),
                event.getEntity().getX(),
                event.getEntity().getY(),
                event.getEntity().getZ(),
                stack));
    }
}
