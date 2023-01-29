package crazypants.enderzoo.entity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILeapAtTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import crazypants.enderzoo.config.Config;
import crazypants.enderzoo.entity.ai.EntityAIAttackOnCollideAggressive;
import crazypants.enderzoo.entity.ai.EntityAINearestAttackableTargetBounded;

public class EntityDireWolf extends EntityMob implements IEnderZooMob {

    public static final String NAME = "enderzoo.DireWolf";
    public static final int EGG_BG_COL = 0x606060;
    public static final int EGG_FG_COL = 0xA0A0A0;

    private static final String SND_HURT = "enderzoo:direwolf.hurt";
    private static final String SND_HOWL = "enderzoo:direwolf.howl";
    private static final String SND_GROWL = "enderzoo:direwolf.growl";
    private static final String SND_DEATH = "enderzoo:direwolf.death";

    private static final int ANGRY_INDEX = 12;

    private EntityLivingBase previsousAttackTarget;

    private static int packHowl = 0;
    private static long lastHowl = 0;

    public EntityDireWolf(World world) {
        super(world);
        setSize(0.8F, 1.2F);
        getNavigator().setAvoidsWater(true);
        tasks.addTask(1, new EntityAISwimming(this));
        tasks.addTask(3, new EntityAILeapAtTarget(this, 0.4F));
        tasks.addTask(4, new EntityAIAttackOnCollideAggressive(this, 1.1D, true).setAttackFrequency(20));
        tasks.addTask(7, new EntityAIWander(this, 0.5D));
        tasks.addTask(9, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0F));
        tasks.addTask(9, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, true));
        if (Config.direWolfAggresiveRange > 0) {
            EntityAINearestAttackableTargetBounded nearTarg = new EntityAINearestAttackableTargetBounded(
                    this,
                    EntityPlayer.class,
                    0,
                    true);
            nearTarg.setMaxDistanceToTarget(Config.direWolfAggresiveRange);
            targetTasks.addTask(2, nearTarg);
        }
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        getDataWatcher().addObject(ANGRY_INDEX, (byte) 0);
        updateAngry();
    }

    @Override
    protected boolean isAIEnabled() {
        return true;
    }

    @Override
    public float getBlockPathWeight(int x, int y, int z) {
        // Impl from EntityAnimal
        return this.worldObj.getBlock(x, y - 1, z) == Blocks.grass ? 10f
                : this.worldObj.getLightBrightness(x, y, z) - 0.5F;
    }

    @Override
    public boolean getCanSpawnHere() {
        // Impl from EntityAnimal
        int i = MathHelper.floor_double(this.posX);
        int j = MathHelper.floor_double(this.boundingBox.minY);
        int k = MathHelper.floor_double(this.posZ);
        // Only allow spawning if on top of a snow block or a snow layer
        /*
         * if (this.worldObj.getBlock(i, j - 1, k) == Blocks.snow) {
         * System.out.println("Dire Wolf spawning on snow block at " + i + " " + j + " " + k ); } if
         * (this.worldObj.getBlock(i, j, k) == Blocks.snow_layer) {
         * System.out.println("Dire Wolf spawning on snow layer at " + i + " " + j + " " + k); }
         */
        return (this.worldObj.getBlock(i, j - 1, k) == Blocks.snow
                || this.worldObj.getBlock(i, j, k) == Blocks.snow_layer)
                && this.worldObj.getFullBlockLightValue(i, j, k) > 8
                && super.getCanSpawnHere();
    }

    public boolean isAngry() {
        return getDataWatcher().getWatchableObjectByte(ANGRY_INDEX) == 1;
    }

    @Override
    protected boolean isValidLightLevel() {
        return true;
    }

    @Override
    public int getMaxSpawnedInChunk() {
        return 6;
    }

    // @Override
    // public boolean isCreatureType(EnumCreatureType type, boolean forSpawnCount) {
    // System.out.println("EntityDireWolf.isCreatureType: " + type);
    // return type.getCreatureClass().isAssignableFrom(this.getClass());
    // }

    private void updateAngry() {
        getDataWatcher().updateObject(ANGRY_INDEX, getAttackTarget() != null ? (byte) 1 : (byte) 0);
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.5D);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(40.0D);
        MobInfo.DIRE_WOLF.applyAttributes(this);
    }

    @Override
    protected void func_145780_a(int p_145780_1_, int p_145780_2_, int p_145780_3_, Block p_145780_4_) {
        playSound("mob.wolf.step", 0.15F, 1.0F);
    }

    // static long nextprinttime=0;

    @Override
    protected String getLivingSound() {
        if (isAngry()) {
            return SND_GROWL;
        }
        if (EntityUtil.isPlayerWithinRange(this, 18)) {
            return SND_GROWL;
        }
        boolean howl = false;
        boolean isNight = (worldObj.getWorldTime() > 15000) && (worldObj.getWorldTime() < 21000);
        boolean isFullMoon = (worldObj.getCurrentMoonPhaseFactor() == 1.0)
                || (worldObj.getCurrentMoonPhaseFactor() == 0.75);

        /*
         * if( worldObj.getTotalWorldTime() > nextprinttime) { System.out.println("isNight: " + isNight +
         * "  isFullMoon: " + isFullMoon + " worldTime: " + worldObj.getTotalWorldTime() + " lastHowl: " + lastHowl +
         * " packHowl: " + packHowl); nextprinttime = worldObj.getTotalWorldTime() + 200; }
         */

        if ((packHowl > 0) && worldObj.getTotalWorldTime() > (lastHowl + 10)) {
            howl = true;
        } else {
            // Not a pack howl, delay based on config
            if (worldObj.getTotalWorldTime() > (Config.direWolfHowlDelay + lastHowl)
                    && rand.nextFloat() <= (Config.direWolfHowlChance * ((isNight) ? ((isFullMoon) ? 4 : 2) : 1))) {
                howl = true;
            }
        }
        if (howl) {
            if (packHowl <= 0
                    && rand.nextFloat() <= (Config.direWolfPackHowlChance * ((isNight) ? ((isFullMoon) ? 4 : 1) : 1))) {
                packHowl = rand.nextInt(Config.direWolfPackHowlAmount * ((isFullMoon) ? 2 : 1) + 1);
            }
            lastHowl = worldObj.getTotalWorldTime();
            packHowl = Math.max(packHowl - 1, 0);
            return SND_HOWL;
        } else {
            return SND_GROWL;
        }
    }

    @Override
    public void playSound(String name, float volume, float pitch) {
        if (SND_HOWL.equals(name)) {
            volume *= (float) Config.direWolfHowlVolumeMult;
            pitch *= 0.8f;
        }
        worldObj.playSoundAtEntity(this, name, volume, pitch);
    }

    @Override
    protected String getHurtSound() {
        return SND_HURT;
    }

    @Override
    protected String getDeathSound() {
        return SND_DEATH;
    }

    @Override
    public float getEyeHeight() {
        return height * 0.8F;
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F;
    }

    @Override
    protected Item getDropItem() {
        return Items.bone;
    }

    /// Called when this entity is killed.
    @Override
    protected void dropFewItems(boolean recentlyHit, int looting) {
        if (recentlyHit && (this.rand.nextInt(3) == 0 || this.rand.nextInt(1 + looting) > 0)) {
            for (int i = this.rand.nextInt(3 + looting); i-- > 0;) {
                this.dropItem(Items.bone, 1);
            }
        }
    }

    public static ArrayList<String> collarNames = new ArrayList<String>();
    {
        int count;

        // Read collar names count
        count = Integer.parseInt(StatCollector.translateToLocal("entity.enderzoo.DireWolf.tags.count"));
        // Loop through filling up with collar names
        for (; count > 0; count--) {
            collarNames.add(
                    new String(
                            StatCollector
                                    .translateToLocal("entity.enderzoo.DireWolf.tags." + Integer.toString(count))));
        }
    }

    /// Called 2.5% of the time when this entity is killed. 20% chance that superRare == 1, otherwise superRare == 0.
    @Override
    protected void dropRareDrop(int superRare) {
        ItemStack collar = new ItemStack(Items.name_tag, 1);
        int messageId = rand.nextInt(collarNames.size());
        collar.setStackDisplayName(collarNames.get(messageId));
        this.entityDropItem(collar, 1.0F);
    }

    public float getTailRotation() {
        if (isAngry()) {
            return (float) Math.PI / 2;
        }
        return (float) Math.PI / 4;
    }

    @Override
    public void setPosition(double x, double y, double z) {
        posX = x;
        posY = y;
        posZ = z;
        // Correct misalignment of bounding box
        double hw = width / 2.0F;
        double hd = hw * 2.25;
        float f1 = height;
        boundingBox.setBounds(x - hw, y - yOffset + ySize, z - hd, x + hw, y - yOffset + ySize + f1, z + hd);
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        EntityLivingBase curTarget = getAttackTarget();
        if (curTarget != previsousAttackTarget) {
            if (curTarget != null) {
                doGroupArgo(curTarget);
            }
            previsousAttackTarget = getAttackTarget();
            updateAngry();
        }
    }

    @SuppressWarnings("unchecked")
    private void doGroupArgo(EntityLivingBase curTarget) {
        if (!Config.direWolfPackAttackEnabled) {
            return;
        }
        int range = 16;
        AxisAlignedBB bb = AxisAlignedBB
                .getBoundingBox(posX - range, posY - range, posZ - range, posX + range, posY + range, posZ + range);
        List<EntityDireWolf> pack = worldObj.getEntitiesWithinAABB(EntityDireWolf.class, bb);
        if (pack != null && !pack.isEmpty()) {
            for (EntityDireWolf wolf : pack) {
                if (wolf.getAttackTarget() == null) {
                    EntityUtil.cancelCurrentTasks(wolf);
                    wolf.setAttackTarget(curTarget);
                }
            }
        }
    }

}
