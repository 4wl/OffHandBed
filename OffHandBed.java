import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.potion.Potion;
import net.minecraft.util.CombatRules;
import net.minecraft.util.DamageSource;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Explosion;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class SmartOffHand extends Module {
    private Setting<Boolean> gap = register(Settings.b("RightClickGapple", true));
    private Setting<Boolean> crystal = register(Settings.b("OffHandCrystalWhenCa", true));
    private Setting<Boolean> beds = register(Settings.b("OffhandBed", true));
    public Setting<Integer> detectrange = register(Settings.i("Range", 6));
    public Setting<Integer> totemhealth = register(Settings.integerBuilder("TotemHealth").withMinimum(1).withValue(4).withMaximum(36).build());
    public double totemhealths;
    //public Setting<Integer> switchDelay = register(Settings.integerBuilder("SwitchDelay").withMinimum(1).withValue(10).withMaximum(20).build());
    //int delayTot = 0, lastDamage = 0;
    //float lastAbsorption = 0, lastHealth = 0;
    public List<BlockPos> findCrystalBlocks() {
        final NonNullList<BlockPos> positions = NonNullList.create();
        positions.addAll(getSphere(new BlockPos(mc.player.posX, mc.player.posY + 1, mc.player.posZ), detectrange.getValue().floatValue(), detectrange.getValue().intValue(), false, true, 0).stream().filter(this::canPlaceCrystal).collect(Collectors.toList()));
        return positions;
    }

    public List<BlockPos> getSphere(final BlockPos loc, final float r, final int h, final boolean hollow, final boolean sphere, final int plus_y) {
        final List<BlockPos> circleblocks = new ArrayList<>();
        final int cx = loc.getX();
        final int cy = loc.getY();
        final int cz = loc.getZ();
        for (int x = cx - (int) r; x <= cx + r; ++x) {
            for (int z = cz - (int) r; z <= cz + r; ++z) {
                for (int y = sphere ? (cy - (int) r) : cy; y < (sphere ? (cy + r) : ((float) (cy + h))); ++y) {
                    final double dist = (cx - x) * (cx - x) + (cz - z) * (cz - z) + (sphere ? ((cy - y) * (cy - y)) : 0);
                    if (dist < r * r && (!hollow || dist >= (r - 1.0f) * (r - 1.0f))) {
                        final BlockPos l = new BlockPos(x, y + plus_y, z);
                        circleblocks.add(l);
                    }
                }
            }
        }
        return circleblocks;
    }


    public static float calculateDamage(final double posX, final double posY, final double posZ, final Entity entity) {
        final float doubleExplosionSize = 12.0f;
        final double distancedsize = entity.getDistance(posX, posY, posZ) / doubleExplosionSize;
        final Vec3d vec3d = new Vec3d(posX, posY, posZ);
        final double blockDensity = entity.world.getBlockDensity(vec3d, entity.getEntityBoundingBox());
        final double v = (1.0 - distancedsize) * blockDensity;
        final float damage = (float) (int) ((v * v + v) / 2.0 * 7.0 * doubleExplosionSize + 1.0);
        double finald = 1.0;
        if (entity instanceof EntityLivingBase) {
            finald = getBlastReduction((EntityLivingBase) entity, getDamageMultiplied(damage), new Explosion(mc.world, null, posX, posY, posZ, 6.0f, false, true));
        }
        return (float) finald;
    }

    public static float calculateDamage(final EntityEnderCrystal crystal, final Entity entity) {
        return calculateDamage(crystal.posX, crystal.posY, crystal.posZ, entity);
    }

    public static float getDamageMultiplied(final float damage) {
        final int diff = mc.world.getDifficulty().getId();
        return damage * ((diff == 0) ? 0.0f : ((diff == 2) ? 1.0f : ((diff == 1) ? 0.5f : 1.5f)));
    }

    public static float getBlastReduction(final EntityLivingBase entity, float damage, final Explosion explosion) {
        if (entity instanceof EntityPlayer) {
            final EntityPlayer ep = (EntityPlayer) entity;
            final DamageSource ds = DamageSource.causeExplosionDamage(explosion);
            damage = CombatRules.getDamageAfterAbsorb(damage, (float) ep.getTotalArmorValue(), (float) ep.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS).getAttributeValue());
            final int k = EnchantmentHelper.getEnchantmentModifierDamage(ep.getArmorInventoryList(), ds);
            final float f = MathHelper.clamp((float) k, 0.0f, 20.0f);
            damage *= 1.0f - f / 25.0f;
            ResistanceDetector w = new ResistanceDetector();
            if (entity.isPotionActive(Objects.requireNonNull(Potion.getPotionById(11))) || entity.getAbsorptionAmount() >= 9 || w.resistanceList.containsKey(entity.getName())) {
                damage -= damage / 5.0f;
            }
            return damage;
        }
        damage = CombatRules.getDamageAfterAbsorb(damage, (float) entity.getTotalArmorValue(), (float) entity.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS).getAttributeValue());
        return damage;
    }

    public boolean canPlaceCrystal(BlockPos blockPos) {
        final BlockPos boost = blockPos.add(0, 1, 0);
        final BlockPos boost2 = blockPos.add(0, 2, 0);
        return ((mc.world.getBlockState(blockPos).getBlock() == Blocks.BEDROCK || mc.world.getBlockState(blockPos).getBlock() == Blocks.OBSIDIAN) && mc.world.getBlockState(boost).getBlock() == Blocks.AIR && mc.world.getBlockState(boost2).getBlock() == Blocks.AIR && mc.world.getEntitiesWithinAABB(EntityPlayer.class, new AxisAlignedBB(boost)).isEmpty() && mc.world.getEntitiesWithinAABB(EntityPlayer.class, new AxisAlignedBB(boost2)).isEmpty());
    }

    public void onUpdate() {
        if (mc.currentScreen instanceof GuiContainer || mc.player == null || mc.world == null) {
            return;
        }
        int gaps = -1;
        int crystals = -1;
        int totem = -1;
        int bed = -1;
        totemhealths = 0;
        List<BlockPos> uwu = findCrystalBlocks();
        List<EntityEnderCrystal> crystalList = mc.world.loadedEntityList.stream().filter(e -> e instanceof EntityEnderCrystal && e.getDistance(mc.player) <= detectrange.getValue()).map(e -> (EntityEnderCrystal) e).collect(Collectors.toList());
        for (BlockPos awa : uwu) {
            double w = calculateDamage(awa.x + 0.5, awa.y + 1, awa.z + 0.5, mc.player);
            if (w > totemhealths) {
                totemhealths = w;
            }
        }
        for (EntityEnderCrystal crystal : crystalList) {
            double w = calculateDamage(crystal, mc.player);
            if (w > totemhealths) {
                totemhealths = w;
            }
        }
        totemhealths = Math.max(totemhealths, totemhealth.getValue());
        for (int i = 0; i < 44; i++) {
            if (mc.player.inventory.getStackInSlot(i) == mc.player.getHeldItemOffhand()) continue;
            if (mc.player.inventory.getStackInSlot(i).getItem() == Items.END_CRYSTAL) {
                if ((crystals == -1 || mc.player.inventory.getStackInSlot(crystals).getCount() > mc.player.inventory.getStackInSlot(i).getCount()) && (mc.player.getHeldItemOffhand().getItem() != Items.END_CRYSTAL || mc.player.getHeldItemOffhand().getDisplayName().equals(mc.player.inventory.getStackInSlot(i).getDisplayName())))
                    crystals = i;
                continue;
            }
            if (mc.player.inventory.getStackInSlot(i).getItem() == Items.TOTEM_OF_UNDYING) {
                totem = i;
                continue;
            }
            if (mc.player.inventory.getStackInSlot(i).getItem() == Items.GOLDEN_APPLE) {
                gaps = i;
                continue;
            }
            if (mc.player.inventory.getStackInSlot(i).getItem() == Items.BED) {
                bed = i;
            }
        }
        if (mc.player.getHealth() + mc.player.getAbsorptionAmount() > totemhealths || (totem == -1 && mc.player.getHeldItemOffhand().getItem() != Items.TOTEM_OF_UNDYING)) {
            if (mc.player.getHeldItemMainhand().getItem() instanceof ItemSword && mc.gameSettings.keyBindUseItem.isKeyDown() && gap.getValue()) {
                if (mc.player.getHeldItemOffhand().getItem() != Items.GOLDEN_APPLE && gaps != -1) {
                    mc.playerController.windowClick(0, gaps < 9 ? gaps + 36 : gaps, 0, ClickType.PICKUP, mc.player);
                    mc.playerController.windowClick(0, 45, 0, ClickType.PICKUP, mc.player);
                    mc.playerController.windowClick(0, gaps < 9 ? gaps + 36 : gaps, 0, ClickType.PICKUP, mc.player);
                    return;
                }
                return;
            }
            if ((!ModuleManager.getModuleByName("AutoCrystal").isDisabled() || !ModuleManager.getModuleByName("AutoCrystalLite").isDisabled()) && crystal.getValue()) {
                if ((mc.player.getHeldItemOffhand().getItem() != Items.END_CRYSTAL || (mc.player.getHeldItemOffhand().getCount() < 16)) && crystals != -1) {
                    mc.playerController.windowClick(0, crystals < 9 ? crystals + 36 : crystals, 0, ClickType.PICKUP, mc.player);
                    mc.playerController.windowClick(0, 45, 0, ClickType.PICKUP, mc.player);
                    mc.playerController.windowClick(0, crystals < 9 ? crystals + 36 : crystals, 0, ClickType.PICKUP, mc.player);
                    return;
                }
                return;
            }
            if (!ModuleManager.getModuleByName("BedAura").isDisabled() && beds.getValue()) {
                if (mc.player.getHeldItemOffhand().getItem() != Items.BED && bed != -1) {
                    mc.playerController.windowClick(0, bed < 9 ? bed + 36 : bed, 0, ClickType.PICKUP, mc.player);
                    mc.playerController.windowClick(0, 45, 0, ClickType.PICKUP, mc.player);
                    mc.playerController.windowClick(0, bed < 9 ? bed + 36 : bed, 0, ClickType.PICKUP, mc.player);
                    return;
                }
                return;
            }

        }
        if ((totem != -1) && mc.player.getHeldItemOffhand().getItem() != Items.TOTEM_OF_UNDYING) {
            mc.playerController.windowClick(0, totem < 9 ? totem + 36 : totem, 0, ClickType.PICKUP, mc.player);
            mc.playerController.windowClick(0, 45, 0, ClickType.PICKUP, mc.player);
            mc.playerController.windowClick(0, totem < 9 ? totem + 36 : totem, 0, ClickType.PICKUP, mc.player);
            return;
        }

    }

    public String getHudInfo() {
        if (mc.player.getHeldItemOffhand().getItem() == Items.TOTEM_OF_UNDYING)
            return "Totem";
        else if (mc.player.getHeldItemOffhand().getItem() == Items.END_CRYSTAL)
            return "Crystal";
        else if (mc.player.getHeldItemOffhand().getItem() == Items.GOLDEN_APPLE)
            return "Gapple";
        else
            return "None";
    }
}