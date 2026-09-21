package org.vansama.buildffa;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
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
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * ================================================================
 *  BEDWARS / MINEMEN-CLUB STYLE FIREBALL
 * ================================================================
 * Hypixel BedWars and Minemen Club are both closed-source, so this
 * is not a copy of their code - nobody outside those studios has
 * that. What follows is the well-documented, publicly shared
 * community technique that plugins such as "BedWars1058" and the
 * standalone "BedWars1058 Fireball Fix" addon are built on, which is
 * what most servers use to reproduce that exact straight-flying,
 * fast, no-block-damage fireball feel. Sources used:
 *
 *  - Bukkit's own Fireball javadoc:
 *      "Fireballs fly straight and do not take setVelocity(...)
 *       well." -> never call setVelocity() on a Fireball.
 *
 *  - Hypixel Forums, "Shooting Fireballs without Offset" (2018):
 *    explains that Bukkit's high-level Fireball#setDirection(...)
 *    can still introduce a small positional "kink" right as the
 *    fireball spawns. The reliable fix (and what the BedWars1058
 *    Fireball Fix addon itself credits as its source) is to reach
 *    into the NMS entity with reflection and set the raw dirX/dirY/
 *    dirZ fields directly - that field IS the fireball's per-tick
 *    acceleration ("power"), and on 1.8.x it is the direction vector
 *    scaled by 0.10. Doing it this way never touches velocity at
 *    all, so there is nothing left to conflict with it.
 * ================================================================
 */
public class FireballFix implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, Long> cooldown = new HashMap<UUID, Long>();
    private static final long COOLDOWN_MS = 500L;

    // Internal NMS scale between a "speed" value and the fireball's
    // actual per-tick acceleration field. This is fixed by Minecraft
    // itself (confirmed by the reflection technique above), not
    // something to tune.
    private static final double NMS_ACCEL_SCALE = 0.10D;

    // Reference speed for slider level 0. This is NOT vanilla ghast
    // speed (vanilla fireballs drift along around 10x slower than
    // this) - it's tuned to the fast, punchy, straight-line feel
    // BedWars/Minemen-style fireballs are known for. Adjust freely;
    // it is just the baseline the -10..+10 slider multiplies.
    private static final double BASE_SPEED = 2.0D;

    public static final double SLIDER_MIN = -10.0D;
    public static final double SLIDER_MAX = 10.0D;

    // ------------------------------------------------------------
    //  Reflection handles for the NMS EntityFireball fields, resolved
    //  ONCE at class-load time. If anything about this server's build
    //  doesn't match what we expect (e.g. it isn't 1.8.x CraftBukkit),
    //  we quietly fall back to the plain Bukkit API instead of
    //  breaking the plugin.
    // ------------------------------------------------------------
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
            // Not on the expected 1.8.x CraftBukkit layout (or a server
            // fork renamed something). We fall back gracefully below.
            reflectionReady = false;
        }
    }

    public FireballFix(JavaPlugin plugin) {
        this.plugin = plugin;
        // Events are registered by BuildFFA.java's onEnable(). Do NOT
        // register again here - doing so used to fire onRightClick()
        // twice per click.
    }

    // ============================================================
    //  SLIDER -> MULTIPLIER
    //  -10 -> 0.00x  (stopped)
    //    0 -> 1.00x  (tuned BedWars/Minemen-style reference speed)
    //  +10 -> 2.00x  (double)
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
    //  LAUNCH FIREBALL
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

        // ---- direction & speed ----
        Vector dir = player.getEyeLocation().getDirection().normalize();
        double multiplier = getMultiplier();
        double speed = BASE_SPEED * multiplier;
        Vector launchVector = dir.clone().multiply(speed);

        // Spawn with the correct initial velocity in ONE atomic call.
        // This is the only place velocity is ever touched - we never
        // call setVelocity() on it afterwards.
        Fireball fireball = player.launchProjectile(Fireball.class, launchVector);

        // Now make it fly perfectly straight: write the fireball's
        // real per-tick acceleration field directly via reflection,
        // using the SAME vector (scaled by the fixed NMS_ACCEL_SCALE)
        // so it stays consistent with the velocity we just set. This
        // is what removes the little "kink"/offset Bukkit's own
        // setDirection() can introduce right at spawn.
        setStraightAcceleration(fireball, launchVector);

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
            player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', msg));
        }

        try {
            player.playSound(player.getLocation(), Sound.GHAST_FIREBALL, 1.0F, 1.0F);
        } catch (Throwable ignored) {}
    }

    /**
     * Writes the fireball's real NMS acceleration field (dirX/dirY/dirZ)
     * directly, bypassing Bukkit's Fireball#setDirection(Vector) and
     * setVelocity(Vector) entirely. "velocityVector" should be the SAME
     * vector the fireball was launched with (magnitude == desired speed);
     * this method applies the fixed 0.10 NMS scale itself.
     *
     * Falls back to the plain Bukkit API if reflection isn't available
     * on this server build, so the plugin never breaks outright.
     */
    private static void setStraightAcceleration(Fireball fireball, Vector velocityVector) {
        if (reflectionReady) {
            try {
                Object handle = craftFireballGetHandle.invoke(fireball);
                dirXField.set(handle, velocityVector.getX() * NMS_ACCEL_SCALE);
                dirYField.set(handle, velocityVector.getY() * NMS_ACCEL_SCALE);
                dirZField.set(handle, velocityVector.getZ() * NMS_ACCEL_SCALE);
                return;
            } catch (Throwable ignored) {
                // fall through to the Bukkit-API fallback below
            }
        }
        // Fallback (older/forked server, or reflection failed): still
        // never call setVelocity() here, only setDirection().
        fireball.setDirection(velocityVector);
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
    //  NO TERRAIN DAMAGE (authentic BedWars/Minemen behaviour)
    //  Fireballs there knock players around but never break blocks
    //  or grief the map. Set fireball.break-blocks: true in config
    //  if you actually want them to destroy terrain.
    // ============================================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fireballExplode(EntityExplodeEvent e) {
        if (!(e.getEntity() instanceof Fireball)) return;
        if (!this.plugin.getConfig().getBoolean("fireball.break-blocks", false)) {
            e.blockList().clear();
        }
    }
}