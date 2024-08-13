package crazypants.enderzoo.entity;

import java.util.ArrayList;

import javax.annotation.Nonnull;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIBreakDoor;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.kuba6000.mobsinfo.api.IMobInfoProvider;
import com.kuba6000.mobsinfo.api.MobDrop;

import cpw.mods.fml.common.Optional;
import crazypants.enderzoo.config.Config;
import crazypants.enderzoo.entity.ai.EntityAIMountedArrowAttack;
import crazypants.enderzoo.entity.ai.EntityAIMountedAttackOnCollide;

@Optional.Interface(iface = "com.kuba6000.mobsinfo.api.IMobInfoProvider", modid = "mobsinfo")
public class EntityFallenKnight extends EntitySkeleton implements IEnderZooMob, IMobInfoProvider {

    public static final int EGG_FG_COL = 0x365A25;
    public static final int EGG_BG_COL = 0xA0A0A0;

    public static String NAME = "enderzoo.FallenKnight";

    private static final double ATTACK_MOVE_SPEED = Config.fallenKnightChargeSpeed;

    private EntityAIMountedArrowAttack aiArrowAttack;
    private EntityAIMountedAttackOnCollide aiAttackOnCollide;

    private final EntityAIBreakDoor breakDoorAI = new EntityAIBreakDoor(this);
    private boolean canBreakDoors = false;

    private EntityLivingBase lastAttackTarget = null;

    private boolean firstUpdate = true;
    private boolean isMounted = false;

    private boolean spawned = false;

    public EntityFallenKnight(World world) {
        super(world);
        targetTasks.addTask(2, new EntityAINearestAttackableTarget(this, EntityVillager.class, 0, false));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(Config.fallenKnightFollowRange);
        MobInfo.FALLEN_KNIGHT.applyAttributes(this);
    }

    @Override
    protected void attackEntity(Entity target, float distance) {
        if (attackTime <= 0 && distance < getAttackRange()
                && target.boundingBox.maxY > boundingBox.minY
                && target.boundingBox.minY < boundingBox.maxY) {
            attackTime = 20;
            attackEntityAsMob(target);
        }
    }

    private float getAttackRange() {
        if (isRiding()) {
            return 3;
        }
        return 2;
    }

    @Override
    public void setCombatTask() {
        tasks.removeTask(getAiAttackOnCollide());
        tasks.removeTask(getAiArrowAttack());
        if (isRanged()) {
            tasks.addTask(4, getAiArrowAttack());
        } else {
            tasks.addTask(4, getAiAttackOnCollide());
        }
    }

    public EntityAIMountedArrowAttack getAiArrowAttack() {
        if (aiArrowAttack == null) {
            aiArrowAttack = new EntityAIMountedArrowAttack(
                    this,
                    ATTACK_MOVE_SPEED,
                    EntityFallenMount.MOUNTED_ATTACK_MOVE_SPEED,
                    Config.fallenKnightRangedMinAttackPause,
                    Config.fallenKnightRangedMaxAttackPause,
                    Config.fallenKnightRangedMaxRange,
                    Config.fallKnightMountedArchesMaintainDistance);
        }
        return aiArrowAttack;
    }

    public EntityAIMountedAttackOnCollide getAiAttackOnCollide() {
        if (aiAttackOnCollide == null) {
            aiAttackOnCollide = new EntityAIMountedAttackOnCollide(
                    this,
                    EntityPlayer.class,
                    ATTACK_MOVE_SPEED,
                    EntityFallenMount.MOUNTED_ATTACK_MOVE_SPEED,
                    false);
        }
        return aiAttackOnCollide;
    }

    @Override
    protected String getLivingSound() {
        return "mob.zombie.say";
    }

    @Override
    protected String getHurtSound() {
        return "mob.zombie.hurt";
    }

    @Override
    protected String getDeathSound() {
        return "mob.zombie.death";
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();

        if (firstUpdate && !worldObj.isRemote) {
            spawnMount();
        }

        if (isRidingMount()) {
            EntityLiving entLiving = ((EntityLiving) ridingEntity);
            if (lastAttackTarget != getAttackTarget() || firstUpdate) {
                EntityUtil.cancelCurrentTasks(entLiving);
                lastAttackTarget = getAttackTarget();
            }
        }
        firstUpdate = false;

        if (!isMounted == isRidingMount()) {
            getAiAttackOnCollide().resetTask();
            getAiArrowAttack().resetTask();
            getNavigator().clearPathEntity();
            isMounted = isRidingMount();
        }
        if (isBurning() && isRidingMount()) {
            ridingEntity.setFire(8);
        }
        if (Config.fallenKnightArchersSwitchToMelee && (!isMounted || !Config.fallKnightMountedArchesMaintainDistance)
                && getAttackTarget() != null
                && isRanged()
                && getDistanceSqToEntity(getAttackTarget()) < 5) {
            setCurrentItemOrArmor(0, getSwordForLevel(getRandomEquipmentLevel()));
        }
    }

    private boolean isRidingMount() {
        return isRiding() && ridingEntity.getClass() == EntityFallenMount.class;
    }

    @Override
    protected void despawnEntity() {
        Entity mount = ridingEntity;
        super.despawnEntity();
        if (isDead && mount != null) {
            mount.setDead();
        }
    }

    private void spawnMount() {
        if (ridingEntity != null || !spawned) {
            return;
        }

        EntityFallenMount mount = null;
        if (Config.fallenMountEnabled && rand.nextFloat() <= Config.fallenKnightChanceMounted) {
            mount = new EntityFallenMount(worldObj);
            mount.setLocationAndAngles(posX, posY, posZ, rotationYaw, 0.0F);
            mount.onSpawnWithEgg((IEntityLivingData) null);
            // NB: don;t check for entity collisions as we know the knight will collide
            if (!SpawnUtil.isSpaceAvailableForSpawn(worldObj, mount, false)) {
                mount = null;
            }
        }
        if (mount != null) {
            setCanPickUpLoot(false);
            setCanBreakDoors(false);
            worldObj.spawnEntityInWorld(mount);
            mountEntity(mount);
        }
    }

    private boolean isRanged() {
        ItemStack itemstack = getHeldItem();
        return itemstack != null && itemstack.getItem() == Items.bow;
    }

    private int getRandomEquipmentLevel() {
        return getRandomEquipmentLevel(EntityUtil.getDifficultyMultiplierForLocation(worldObj, posX, posY, posZ));
    }

    private int getRandomEquipmentLevel(float occupiedDiffcultyMultiplier) {
        float chanceImprovedArmor = isHardDifficulty() ? Config.fallenKnightChanceArmorUpgradeHard
                : Config.fallenKnightChanceArmorUpgrade;
        chanceImprovedArmor *= (1 + occupiedDiffcultyMultiplier); // If we have the max occupied factor, double the
                                                                  // chance of improved armor

        int armorLevel = rand.nextInt(2);
        for (int i = 0; i < 2; i++) {
            if (rand.nextFloat() <= chanceImprovedArmor) {
                armorLevel++;
            }
        }
        return armorLevel;
        /*
         * 8% 3 8% 2 12% 2 12% 2 12% 1 12% 1 18% 1 18% 0 ===== 8% 3 32% 2 42% 1 18% 0
         */
    }

    protected boolean isHardDifficulty() {
        return EntityUtil.isHardDifficulty(worldObj);
    }

    private ItemStack getSwordForLevel(int swordLevel) {
        //// have a better chance of not getting a wooden or stone sword
        if (swordLevel < 2) {
            swordLevel += rand.nextInt(isHardDifficulty() ? 3 : 2);
            swordLevel = Math.min(swordLevel, 2);
        }
        switch (swordLevel) {
            case 0:
                return new ItemStack(Items.wooden_sword);
            case 1:
                return new ItemStack(Items.stone_sword);
            case 2:
                return new ItemStack(Items.iron_sword);
            case 4:
                return new ItemStack(Items.diamond_sword);
        }
        return new ItemStack(Items.iron_sword);
    }

    @Override
    public IEntityLivingData onSpawnWithEgg(IEntityLivingData livingData) {

        spawned = true;

        // From base entity living class
        getEntityAttribute(SharedMonsterAttributes.followRange)
                .applyModifier(new AttributeModifier("Random spawn bonus", rand.nextGaussian() * 0.05D, 1));

        setSkeletonType(0);
        addRandomArmor();
        enchantEquipment();

        float f = worldObj.func_147462_b(posX, posY, posZ);
        setCanPickUpLoot(rand.nextFloat() < 0.55F * f);
        setCanBreakDoors(rand.nextFloat() < f * 0.1F);

        return livingData;
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound root) {
        super.writeEntityToNBT(root);
        root.setBoolean("canBreakDoors", canBreakDoors);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound root) {
        super.readEntityFromNBT(root);
        setCanBreakDoors(root.getBoolean("canBreakDoors"));
    }

    private void setCanBreakDoors(boolean val) {
        if (canBreakDoors != val) {
            canBreakDoors = val;
            if (canBreakDoors) {
                tasks.addTask(1, breakDoorAI);
            } else {
                tasks.removeTask(breakDoorAI);
            }
        }
    }

    @Override
    protected void addRandomArmor() {
        // ANY CHANGE MADE IN HERE MUST ALSO BE MADE IN provideDropsInformation!
        float occupiedDiffcultyMultiplier = EntityUtil.getDifficultyMultiplierForLocation(worldObj, posX, posY, posZ);

        int equipmentLevel = getRandomEquipmentLevel(occupiedDiffcultyMultiplier);
        int armorLevel = equipmentLevel;
        if (armorLevel == 1) {
            // Skip gold armor, I don't like it
            armorLevel++;
        }

        float chancePerPiece = isHardDifficulty() ? Config.fallenKnightChancePerArmorPieceHard
                : Config.fallenKnightChancePerArmorPiece;
        chancePerPiece *= (1 + occupiedDiffcultyMultiplier); // If we have the max occupied factor, double the chance of
        // improved armor

        for (int slot = 1; slot < 5; slot++) {
            ItemStack itemStack = getEquipmentInSlot(slot);
            if (itemStack == null && rand.nextFloat() <= chancePerPiece) {
                Item item = EntityLiving.getArmorItemForSlot(slot, armorLevel);
                if (item != null) {
                    ItemStack stack = new ItemStack(item);
                    if (armorLevel == 0) {
                        ((ItemArmor) item).func_82813_b(stack, 0);
                    }
                    setCurrentItemOrArmor(slot, stack);
                }
            }
        }
        if (rand.nextFloat() > Config.fallenKnightRangedRatio) {
            setCurrentItemOrArmor(0, getSwordForLevel(equipmentLevel));
        } else {
            setCurrentItemOrArmor(0, new ItemStack(Items.bow));
        }
    }

    @Override
    protected void dropFewItems(boolean hitByPlayer, int lootingLevel) {
        // ANY CHANGE MADE IN HERE MUST ALSO BE MADE IN provideDropsInformation!
        int numDrops = rand.nextInt(3 + lootingLevel);
        for (int i = 0; i < numDrops; ++i) {
            if (rand.nextBoolean()) {
                dropItem(Items.bone, 1);
            } else {
                dropItem(Items.rotten_flesh, 1);
            }
        }
    }

    @Override
    protected void dropRareDrop(int p_70600_1_) {
        // ANY CHANGE MADE IN HERE MUST ALSO BE MADE IN provideDropsInformation!
    }

    @Optional.Method(modid = "mobsinfo")
    @Override
    public void provideDropsInformation(@Nonnull ArrayList<MobDrop> drops) {
        double chance = MobDrop.getChanceBasedOnFromTo(0, 2) * 0.5d;
        drops.add(MobDrop.create(Items.bone).withChance(chance).withLooting());
        drops.add(MobDrop.create(Items.rotten_flesh).withChance(chance).withLooting());

        // armor
        chance = 0.085d * Config.fallenKnightChancePerArmorPieceHard;
        double chanceArmor = Config.fallenKnightChanceArmorUpgradeHard;
        double notChanceArmor = 1d - chanceArmor;
        final double[] chanceForLevel = { 0.5d * notChanceArmor * notChanceArmor, // 0+0+0
                0.5d * chanceArmor * notChanceArmor + 0.5d * notChanceArmor * chanceArmor
                        + 0.5d * notChanceArmor * notChanceArmor, // 0+1+0,0+0+1,1+0+0
                0.5d * chanceArmor * notChanceArmor + 0.5d * notChanceArmor * chanceArmor
                        + 0.5d * chanceArmor * chanceArmor, // 1+1+0,1+0+1,0+1+1
                0.5d * chanceArmor * chanceArmor // 1+1+1
        };

        for (int slot = 1; slot < 5; slot++) {
            Item item = EntityLiving.getArmorItemForSlot(slot, 0);
            ItemStack stack = new ItemStack(item);
            ((ItemArmor) item).func_82813_b(stack, 0);
            double dropChance = chance * chanceForLevel[0];
            drops.add(MobDrop.create(stack).withType(MobDrop.DropType.Additional).withChance(dropChance * 0.5d));
            drops.add(
                    MobDrop.create(stack.copy()).withType(MobDrop.DropType.Additional).withChance(dropChance * 0.5d)
                            .withRandomEnchant(14));
            // gold is skipped
            dropChance = chance * (chanceForLevel[1] + chanceForLevel[2]);
            drops.add(
                    MobDrop.create(EntityLiving.getArmorItemForSlot(slot, 2)).withType(MobDrop.DropType.Additional)
                            .withChance(dropChance * 0.5d));
            drops.add(
                    MobDrop.create(EntityLiving.getArmorItemForSlot(slot, 2)).withType(MobDrop.DropType.Additional)
                            .withChance(dropChance * 0.5d).withRandomEnchant(14));
            dropChance = chance * chanceForLevel[3];
            drops.add(
                    MobDrop.create(EntityLiving.getArmorItemForSlot(slot, 3)).withType(MobDrop.DropType.Additional)
                            .withChance(dropChance * 0.5d));
            drops.add(
                    MobDrop.create(EntityLiving.getArmorItemForSlot(slot, 3)).withType(MobDrop.DropType.Additional)
                            .withChance(dropChance * 0.5d).withRandomEnchant(14));
        }
        chance = 0.085d * (1d - Config.fallenKnightRangedRatio) * chanceForLevel[0];
        drops.add(MobDrop.create(Items.wooden_sword).withType(MobDrop.DropType.Additional).withChance(chance * 0.75d));
        drops.add(
                MobDrop.create(Items.wooden_sword).withType(MobDrop.DropType.Additional).withChance(chance * 0.25d)
                        .withRandomEnchant(14));
        chance = 0.085d * (1d - Config.fallenKnightRangedRatio) * chanceForLevel[1];
        drops.add(MobDrop.create(Items.stone_sword).withType(MobDrop.DropType.Additional).withChance(chance * 0.75d));
        drops.add(
                MobDrop.create(Items.stone_sword).withType(MobDrop.DropType.Additional).withChance(chance * 0.25d)
                        .withRandomEnchant(14));
        chance = 0.085d * (1d - Config.fallenKnightRangedRatio) * (chanceForLevel[2] + chanceForLevel[3]);
        drops.add(MobDrop.create(Items.iron_sword).withType(MobDrop.DropType.Additional).withChance(chance * 0.75d));
        drops.add(
                MobDrop.create(Items.iron_sword).withType(MobDrop.DropType.Additional).withChance(chance * 0.25d)
                        .withRandomEnchant(14));
        chance = 0.085d * Config.fallenKnightRangedRatio;
        drops.add(MobDrop.create(Items.bow).withType(MobDrop.DropType.Additional).withChance(chance * 0.75d));
        drops.add(
                MobDrop.create(Items.bow).withType(MobDrop.DropType.Additional).withChance(chance * 0.25d)
                        .withRandomEnchant(14));

    }

    // public boolean attackEntityAsMob(Entity p_70652_1_)
    // {
    // if (super.attackEntityAsMob(p_70652_1_))
    // {
    // if (this.getSkeletonType() == 1 && p_70652_1_ instanceof EntityLivingBase)
    // {
    // ((EntityLivingBase)p_70652_1_).addPotionEffect(new PotionEffect(Potion.wither.id, 200));
    // }
    //
    // return true;
    // }
    // else
    // {
    // return false;
    // }
    // }

    // public boolean attackEntityAsMob(Entity p_70652_1_)
    // {
    // boolean flag = super.attackEntityAsMob(p_70652_1_);
    //
    // if (flag)
    // {
    // int i = this.worldObj.difficultySetting.getDifficultyId();
    //
    // if (this.getHeldItem() == null && this.isBurning() && this.rand.nextFloat() < (float)i * 0.3F)
    // {
    // p_70652_1_.setFire(2 * i);
    // }
    // }
    //
    // return flag;
    // }
}
