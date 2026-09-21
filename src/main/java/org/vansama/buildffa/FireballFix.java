package org.vansama.buildffa;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * ================================================================================
 * FIXED FIREBALL PHYSICS ENGINE (BuildFFA v4.0+)
 * ================================================================================
 * 
 * This class fixes the "mouse-click" bug by directly manipulating NMS fields.
 * 
 * KEY CHANGES:
 * 1. Increased BASE_SPEED to 4.0D to ensure visible travel distance.
 * 2. Corrected NMS acceleration scaling (dirX/Y/Z are acceleration, not velocity).
 * 3. Added safety checks for reflection to prevent crashes on non-CraftBukkit servers.
 */
public class FireballFix implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    // Cooldown in milliseconds (0.5 seconds)
    private static final long COOLDOWN_MS = 500L;

    // NMS Scale Factor: In 1.8, dirX/Y/Z are multiplied by 0.1 internally by Minecraft.
    // To get a desired acceleration 'A', we must set the field to A / 0.1.
    private static final double NMS_SCALE_FACTOR = 0.1D;

    // Base Speed: This determines the "standard" BedWars-like speed.
    // 4.0 provides a punchy, fast feel that travels significantly before exploding.
    private static final double BASE_SPEED = 4.0D;

    public static final double SLIDER_MIN = -10.0D;
    public static final double SLIDER_MAX = 10.0D;

    // Reflection Handles
    private static Field dirXField;
    private static Field dirYField;
    private static Field dirZField;
    private static Method getHandleMethod;
    private static boolean reflectionInitialized = false;

    static {
        try {
            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            String version = packageName.substring(packageName.lastIndexOf('.') + 1);
            
            Class<?> nmsFireballClass = Class.forName("net.minecraft.server." + version + ".EntityFireball");
            Class<?> craftFireballClass = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftFireball");

            dirXField = nmsFireballClass.getDeclaredField("dirX");
            dirYField = nmsFireballClass.getDeclaredField("dirY");
            dirZField = nmsFireballClass.getDeclaredField("dirZ");
            
            dirXField.setAccessible(true);
            dirYField.setAccessible(true);
            dirZField.setAccessible(true);

            getHandleMethod = craftFireballClass.getDeclaredMethod("getHandle");
            getHandleMethod.setAccessible(true);

            reflectionInitialized = true;
        } catch (Exception e) {
            // Reflection failed (likely not 1.8 CraftBukkit). Fallback will be used.
            reflectionInitialized = false;
        }
    }

    public FireballFix(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Converts the config slider value (-10 to +10) into a multiplier.
     * -10 = 0x (Stopped)
     *   0 = 1x (Base Speed)
     * +10 = 2x (Double Speed)
     */
    public static double sliderToMultiplier(double slider) {
        if (slider < SLIDER_MIN) slider = SLIDER_MIN;
        if (slider > SLIDER_MAX) slider = SLIDER_MAX;
        return 1.0D + (slider / 10.0D);
    }

    private double getCurrentMultiplier() {
        double slider = plugin.getConfig().getDouble("fireball.speed-level", 1.5D);
        return sliderToMultiplier(slider);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onRightClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getItemInHand();

        if (item == null || item.getType() != Material.FIREBALL) {
            return;
        }

        // Prevent default behavior (consuming item/shooting vanilla fireball)
        event.setCancelled(true);

        // Cooldown Check
        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();
        Long lastClick = cooldowns.get(uuid);
        
        if (lastClick != null && (now - lastClick) < COOLDOWN_MS) {
            return;
        }
        cooldowns.put(uuid, now);

        // Consume Item
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.setItemInHand(null);
        }
        player.updateInventory();

        // Calculate Launch Vector
        Vector direction = player.getEyeLocation().getDirection().normalize();
        double multiplier = getCurrentMultiplier();
        double speed = BASE_SPEED * multiplier;

        // Create the fireball with an initial velocity vector.
        // Note: In 1.8, launchProjectile sets velocity, but we will override it immediately.
        Fireball fireball = player.launchProjectile(Fireball.class, direction.clone().multiply(speed));

        // Apply Custom Physics
        applyCustomPhysics(fireball, direction, speed);

        // Configure Explosion & Effects
        configureFireballProperties(fireball, player);
    }

    /**
     * Directly manipulates NMS fields to ensure straight, fast flight.
     */
    private void applyCustomPhysics(Fireball fireball, Vector direction, double speed) {
        if (!reflectionInitialized) {
            // Fallback: Use Bukkit API if reflection fails.
            fireball.setVelocity(direction.clone().multiply(speed));
            return;
        }

        try {
            Object nmsEntity = getHandleMethod.invoke(fireball);

            // In Minecraft 1.8, the fireball's movement is calculated as:
            // pos += dir * 0.1
            // Therefore, to achieve a specific 'speed' per tick, we set dir = speed / 0.1
            
            double accelX = direction.getX() * speed / NMS_SCALE_FACTOR;
            double accelY = direction.getY() * speed / NMS_SCALE_FACTOR;
            double accelZ = direction.getZ() * speed / NMS_SCALE_FACTOR;

            dirXField.set(nmsEntity, accelX);
            dirYField.set(nmsEntity, accelY);
            dirZField.set(nmsEntity, accelZ);

        } catch (Exception e) {
            e.printStackTrace();
            // Fallback to Bukkit API if reflection fails at runtime
            fireball.setVelocity(direction.clone().multiply(speed));
        }
    }

    private void configureFireballProperties(Fireball fireball, Player shooter) {
        // Yield (Explosion Size)
        float yield = (float) plugin.getConfig().getDouble("fireball.yield", 1.0D);
        fireball.setYield(yield);

        // Throw Effects (Potions on shooter)
        if (plugin.getConfig().getBoolean("fireball.throw-effects.enabled", false)) {
            List<String> effects = plugin.getConfig().getStringList("fireball.throw-effects.effects");
            for (String eff : effects) {
                String[] parts = eff.split(":");
                if (parts.length == 3) {
                    try {
                        PotionEffectType type = PotionEffectType.getByName(parts[0]);
                        int duration = Integer.parseInt(parts[1]);
                        int amplifier = Integer.parseInt(parts[2]);
                        if (type != null) {
                            shooter.addPotionEffect(new PotionEffect(type, duration, amplifier, true, false));
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        // Message
        String msg = plugin.getConfig().getString("fireball.message", "");
        if (!msg.isEmpty()) {
            shooter.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', msg));
        }

        // Sound
        try {
            shooter.playSound(shooter.getLocation(), Sound.GHAST_FIREBALL, 1.0F, 1.0F);
        } catch (Exception ignored) {}
    }

    // ========================================================================
    // HIT & EXPLOSION LOGIC
    // ========================================================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFireballHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Fireball)) return;
        
        Fireball fireball = (Fireball) event.getEntity();
        
        // Only apply custom knockback if enabled in config
        if (!plugin.getConfig().getBoolean("fireball.knockback.enabled", true)) return;

        Location loc = fireball.getLocation();
        if (loc.getWorld() == null) return;

        double radius = plugin.getConfig().getDouble("fireball.knockback.radius", 4.0D);
        double hForce = plugin.getConfig().getDouble("fireball.knockback.radius-force", 1.5D) * -1.0D;
        double vForce = plugin.getConfig().getDouble("fireball.knockback.height-force", 1.0D);
        double damage = plugin.getConfig().getDouble("fireball.knockback.damage", 0.5D);

        Vector center = loc.toVector();
        
        for (Entity entity : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
            if (!(entity instanceof Player)) continue;
            if (entity.equals(fireball.getShooter())) continue; // Don't hit self

            Player victim = (Player) entity;
            Vector victimVec = victim.getLocation().toVector();
            
            // Calculate direction from explosion to player
            Vector diff = victimVec.subtract(center).normalize();
            
            // Apply Horizontal Knockback
            Vector knockback = diff.clone().multiply(hForce);
            
            // Apply Vertical Knockback
            double yDiff = diff.getY();
            double verticalBoost = vForce;
            
            // Adjust vertical force based on relative height to prevent "sticking" to ground
            if (yDiff < 0) {
                verticalBoost = vForce * 1.5;
            } else if (yDiff < 0.5) {
                verticalBoost = vForce * 1.2;
            }
            
            knockback.setY(verticalBoost);
            
            victim.setVelocity(knockback);
            
            if (damage > 0) {
                victim.damage(damage, fireball.getShooter() instanceof Player ? (Player) fireball.getShooter() : null);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDirectHit(EntityDamageByEntityEvent event) {
        // Cancel vanilla direct hit damage because we handle it in ProjectileHitEvent
        if (event.getDamager() instanceof Fireball && event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        if (event.getEntity() instanceof Fireball) {
            event.setFire(false); // Disable fire spread
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof Fireball)) return;
        
        // If break-blocks is false, clear the block list to prevent griefing
        if (!plugin.getConfig().getBoolean("fireball.break-blocks", false)) {
            event.blockList().clear();
        }
    }
}