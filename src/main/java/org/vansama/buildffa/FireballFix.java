package org.vansama.buildffa;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
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
================================================================
BEDWARS / MINEMEN-CLUB STYLE FIREBALL (FULLY FIXED)
================================================================
*/
public class FireballFix implements Listener {
    private final JavaPlugin plugin;
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private static final long COOLDOWN_MS = 500L;

    // Base speed for the slider. 
    // Terminal velocity in blocks/tick will be roughly 9.9 * (BASE_SPEED * multiplier).
    // We use 0.5 so that a multiplier of 1.0 gives ~5 blocks/tick (100 blocks/sec), 
    // which is a fast but visible BedWars-style fireball, NOT a hitscan laser.
    private static final double BASE_SPEED = 0.5D;

    public static final double SLIDER_MIN = -10.0D;
    public static final double SLIDER_MAX = 10.0D;

    // Reflection handles for NMS EntityFireball fields
    private static Field dirXField;
    private static Field dirYField;
    private static Field dirZField;
    private static Method craftFireballGetHandle;
    private static boolean reflectionReady = false;

    static {
        try {
            String craftBukkitPackage = Bukkit.getServer().getClass().getPackage().getName();
            String version = craftBukkitPackage.substring(craftBukkitPackage.lastIndexOf('.') + 1);
            
            Class<?> nmsFireballClass = Class.forName("net.minecraft.server." + version + ".EntityFireball");
            Class<?> craftFireballClass = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftFireball");
            
            dirXField = nmsFireballClass.getDeclaredField("dirX");
            dirYField = nmsFireballClass.getDeclaredField("dirY");
            dirZField = nmsFireballClass.getDeclaredField("dirZ");
            dirXField.setAccessible(true);
            dirYField.setAccessible(true);
            dirZField.setAccessible(true);
            
            craftFireballGetHandle = craftFireballClass.getDeclaredMethod("getHandle");
            craftFireballGetHandle.setAccessible(true);
            reflectionReady = true;
        } catch (Throwable t) {
            reflectionReady = false;
        }
    }

    public FireballFix(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // ============================================================
    //  SLIDER -> MULTIPLIER
    // ============================================================
    public static double sliderToMultiplier(double slider) {
        if (slider < SLIDER_MIN) slider = SLIDER_MIN;
        if (slider > SLIDER_MAX) slider = SLIDER_MAX;
        return 1.0D + (slider / 10.0D);
    }

    private double getMultiplier() {
        double slider = this.plugin.getConfig().getDouble("fireball.speed-level", 1.5D);
        return sliderToMultiplier(slider);
    }

    // ============================================================
    //  LAUNCH FIREBALL (FIXED)
    // ============================================================
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
        event.setCancelled(true);

        long now = System.currentTimeMillis();
        Long last = cooldown.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) {
            return;
        }
        cooldown.put(player.getUniqueId(), now);

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.setItemInHand(null);
        }
        player.updateInventory();

        // Get normalized direction
        Vector dir = player.getEyeLocation().getDirection().normalize();
        double multiplier = getMultiplier();
        double speed = BASE_SPEED * multiplier;
        
        // Prevent spawning a fireball with 0 or negative speed
        if (speed <= 0.01D) {
            player.sendMessage(ChatColor.RED + "Fireball speed is too low to launch.");
            return;
        }

        // FIX 1: Spawn the fireball slightly in front of the player (1.5 blocks)
        // This eliminates the spawn "kink" and prevents it from instantly 
        // colliding with the player's own hitbox or the block they are staring at.
        Location spawnLoc = player.getEyeLocation().add(dir.clone().multiply(1.5));
        
        // FIX 2: Use spawnEntity instead of launchProjectile to avoid 1.8.8 Bukkit quirks
        Fireball fireball = (Fireball) player.getWorld().spawnEntity(spawnLoc, EntityType.FIREBALL);
        fireball.setShooter(player);

        // FIX 3: Set the NMS acceleration fields directly.
        // In 1.8.8 NMS, the fireball's velocity is updated each tick by adding dirX/Y/Z, 
        // then applying drag (0.99). The terminal velocity is roughly 99 * dirMagnitude.
        // By setting dir to (direction * speed * 0.1), we get a terminal velocity of ~9.9 * speed.
        Vector nmsAccel = dir.clone().multiply(speed * 0.1D);
        setStraightAcceleration(fireball, nmsAccel);

        // FIX 4: Set the initial velocity so it doesn't start from a standstill
        fireball.setVelocity(dir.clone().multiply(speed * 5.0D));

        // ============================================================
        //  EXPLOSION / EFFECTS
        // ============================================================
        double yield = this.plugin.getConfig().getDouble("fireball.yield", 1.0D);
        fireball.setYield((float) yield);

        if (this.plugin.getConfig().getBoolean("fireball.throw-effects.enabled", false)) {
            List<String> effects = this.plugin.getConfig().getStringList("fireball.throw-effects.effects");
            if (effects != null) {
                for (String element : effects) {
                    String[] tokens = element.split(":");
                    if (tokens.length < 3) continue;
                    PotionEffectType effect = PotionEffectType.getByName(tokens[0].toUpperCase());
                    if (effect != null) {
                        try {
                            player.addPotionEffect(new PotionEffect(
                                    effect,
                                    Integer.parseInt(tokens[1]),
                                    Integer.parseInt(tokens[2]),
                                    true, false));
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }

        String msg = this.plugin.getConfig().getString("fireball.message", "");
        if (msg != null && !msg.isEmpty()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        }

        try {
            player.playSound(player.getLocation(), Sound.GHAST_FIREBALL, 1.0F, 1.0F);
        } catch (Throwable ignored) {}
    }

    /**
     * Writes the fireball's real NMS acceleration field (dirX/dirY/dirZ) directly.
     */
    private static void setStraightAcceleration(Fireball fireball, Vector accelVector) {
        if (reflectionReady) {
            try {
                Object handle = craftFireballGetHandle.invoke(fireball);
                dirXField.set(handle, accelVector.getX());
                dirYField.set(handle, accelVector.getY());
                dirZField.set(handle, accelVector.getZ());
                return;
            } catch (Throwable ignored) {}
        }
        // Fallback if reflection fails
        fireball.setDirection(accelVector);
    }

    // ============================================================
    //  BEDWARS1058 EXACT KNOCKBACK LOGIC
    // ============================================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fireballHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Fireball)) return;
        if (!this.plugin.getConfig().getBoolean("fireball.knockback.enabled", true)) return;

        Location location = e.getEntity().getLocation();
        if (location.getWorld() == null) return;

        double fireballExplosionSize = this.plugin.getConfig().getDouble("fireball.knockback.radius", 4.0D);
        double fireballHorizontal = this.plugin.getConfig().getDouble("fireball.knockback.radius-force", 1.5D) * -1.0D;
        double fireballVertical = this.plugin.getConfig().getDouble("fireball.knockback.height-force", 0.9D);
        double damage = this.plugin.getConfig().getDouble("fireball.knockback.damage", 2.0D);

        Vector vector = location.toVector();
        Collection<Entity> nearbyEntities = location.getWorld().getNearbyEntities(location, fireballExplosionSize, fireballExplosionSize, fireballExplosionSize);
        
        for (Entity entity : nearbyEntities) {
            if (!(entity instanceof Player)) continue;
            Player player = (Player) entity;
            Vector playerVector = player.getLocation().toVector();
            Vector normalizedVector = vector.clone().subtract(playerVector).normalize();
            Vector horizontalVector = normalizedVector.clone().multiply(fireballHorizontal);
            
            double y = normalizedVector.getY();
            if (y < 0) y += 1.5;
            if (y <= 0.5) {
                y = fireballVertical * 1.5;
            } else {
                y = y * fireballVertical * 1.5;
            }
            player.setVelocity(horizontalVector.setY(y));
            if (damage > 0) {
                player.damage(damage);
            }
        }
    }

    // ============================================================
    //  CANCEL DIRECT HIT DAMAGE (Vanilla)
    // ============================================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fireballDirectHit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Fireball && e.getEntity() instanceof Player) {
            e.setCancelled(true);
        }
    }

    // ============================================================
    //  FIRE SETTINGS
    // ============================================================
    @EventHandler
    public void fireballPrime(ExplosionPrimeEvent e) {
        if (!(e.getEntity() instanceof Fireball)) return;
        e.setFire(false);
    }

    // ============================================================
    //  NO TERRAIN DAMAGE
    // ============================================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fireballExplode(EntityExplodeEvent e) {
        if (!(e.getEntity() instanceof Fireball)) return;
        if (!this.plugin.getConfig().getBoolean("fireball.break-blocks", false)) {
            e.blockList().clear();
        }
    }
}