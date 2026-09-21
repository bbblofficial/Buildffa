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
BEDWARS / MINEMEN-CLUB STYLE FIREBALL (NO DRAG FIX)
================================================================
این نسخه مشکل کندی بعد از پرتاب را با حذف کامل setVelocity
و استفاده انحصاری از شتاب NMS حل کرده است.
*/
public class FireballFix implements Listener {
    private final JavaPlugin plugin;
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private static final long COOLDOWN_MS = 500L;

    // BASE_SPEED: سرعت پایه برای ضریب 1.0
    // این مقدار باید طوری باشد که گوی آتشین حس "سریع اما قابل دیدن" داشته باشد.
    private static final double BASE_SPEED = 2.5D; 

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
    //  LAUNCH FIREBALL (FIXED - NO DRAG)
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
        
        if (speed <= 0.01D) {
            player.sendMessage(ChatColor.RED + "Fireball speed is too low to launch.");
            return;
        }

        // Spawn the fireball slightly in front to avoid self-collision
        Location spawnLoc = player.getEyeLocation().add(dir.clone().multiply(1.5));
        Fireball fireball = (Fireball) player.getWorld().spawnEntity(spawnLoc, EntityType.FIREBALL);
        fireball.setShooter(player);

        // FIX: ONLY use NMS acceleration. Do NOT call setVelocity.
        // This prevents the 0.99 drag factor from slowing it down.
        // In 1.8 NMS, dirX/Y/Z are added to velocity every tick.
        // We scale it by 0.1 because Minecraft internally multiplies it by 0.1 again.
        Vector nmsAccel = dir.clone().multiply(speed * 0.1D);
        setStraightAcceleration(fireball, nmsAccel);

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
     * This ensures constant speed without drag.
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
        // Fallback: If reflection fails, we have no choice but to use setDirection.
        // Note: This fallback WILL have drag, but it's better than crashing.
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