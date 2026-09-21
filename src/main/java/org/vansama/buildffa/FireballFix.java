package org.vansama.buildffa;

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
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class FireballFix implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, Long> cooldown = new HashMap<UUID, Long>();
    private static final long COOLDOWN_MS = 500L;

    // ============================================================
    //  REAL ROOT CAUSE OF THE "ROCKET" BUG (read before touching this file)
    //
    //  Bukkit's own Fireball javadoc says, verbatim:
    //      "Fireballs fly straight and do not take setVelocity(...) well."
    //
    //  On a Fireball, setDirection(Vector) is the ONLY call you should ever
    //  make. On 1.8.8 it sets BOTH the entity's real velocity (motX/Y/Z)
    //  AND its internal "power" field (dirX/Y/Z) FROM THE SAME VECTOR, so
    //  the two stay consistent, and the vector's magnitude IS the speed.
    //
    //  The old code called setDirection(dir * speed * 0.1) and then
    //  immediately called setVelocity(dir * speed) on top of it. That
    //  second call is exactly what the javadoc warns against: it silently
    //  overwrote the real velocity with a value 10x larger than what
    //  setDirection had just configured, while leaving the "power" field
    //  at the smaller value. The entity was left in an inconsistent state
    //  (real speed way higher than its own power field), which is why it
    //  shot out like a rocket and why turning the speed-level slider up
    //  or down barely changed anything - most of the visible speed was
    //  coming from the stray setVelocity(), not from the slider math.
    //
    //  FIX: never call setVelocity() on the fireball. Only setDirection().
    //  On top of that, we actively re-clamp the fireball's speed every
    //  tick to the configured value (see startSpeedLock below), because
    //  vanilla fireballs re-add their "power" to their velocity every
    //  tick with no drag - meaning ANY nonzero power will make a fireball
    //  keep speeding up the longer it flies. The re-clamp is what makes
    //  the speed-level slider actually mean something: whatever value you
    //  configure is the fireball's constant real speed for its whole
    //  flight, not just its speed in the first tick.
    // ============================================================

    // Magnitude of the direction vector that reproduces vanilla-feeling
    // fireball speed (i.e. what "0 -> vanilla exact / 1.00x" in the
    // config means). This is the length of a normalized look vector,
    // matching how Bukkit hands you a fireball by default.
    private static final double VANILLA_BASE = 1.0D;

    public static final double SLIDER_MIN = -10.0D;
    public static final double SLIDER_MAX = 10.0D;

    public FireballFix(JavaPlugin plugin) {
        this.plugin = plugin;
        // NOTE: do NOT register events here. BuildFFA.java already does
        // getServer().getPluginManager().registerEvents(this.fireballFix, ...)
        // in onEnable(). Registering it a second time here made every
        // right-click event fire this listener's methods TWICE (once per
        // registration), which doubled fireball launches / knockback /
        // damage in some cases and made the speed feel even more chaotic
        // and inconsistent than the setVelocity() bug already did.
    }

    // ============================================================
    //  SLIDER -> MULTIPLIER
    //  -10 -> 0.00x  (stopped)
    //    0 -> 1.00x  (vanilla exact)
    //  +10 -> 2.00x  (double acceleration)
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
    //  LAUNCH FIREBALL - FIXED
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

        // ---- compute direction & the ONE speed value we will ever set ----
        final Vector dir = player.getLocation().getDirection().normalize();
        final double multiplier = getMultiplier();
        final double power = VANILLA_BASE * multiplier;

        final Fireball fireball = player.launchProjectile(Fireball.class);

        // The only call that should ever touch this fireball's motion.
        // Magnitude of this vector == real speed. No setVelocity(), ever.
        fireball.setDirection(dir.clone().multiply(power));

        // Keep the speed CONSTANT for the whole flight. Vanilla fireballs
        // add their "power" to their velocity every tick with no drag, so
        // without this a fireball keeps accelerating forever (this is
        // also why raising/lowering speed-level used to feel like it did
        // almost nothing once the projectile had traveled a few blocks).
        if (power > 0.0D) {
            startSpeedLock(fireball, power);
        }

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

    // ============================================================
    //  SPEED LOCK
    //  Runs every tick for as long as the fireball is alive and pins its
    //  velocity magnitude back to "power", using whatever direction it is
    //  currently traveling in (so it still flies straight, it just never
    //  speeds up or slows down). This is what makes the config slider a
    //  real, constant speed instead of just an initial-tick nudge.
    // ============================================================
    private void startSpeedLock(final Fireball fireball, final double power) {
        new BukkitRunnable() {
            private int ticksAlive = 0;

            @Override
            public void run() {
                ticksAlive++;
                if (fireball == null || fireball.isDead() || !fireball.isValid() || ticksAlive > 200) {
                    cancel();
                    return;
                }

                Vector velocity = fireball.getVelocity();
                if (velocity.lengthSquared() < 1.0E-6) {
                    cancel();
                    return;
                }

                Vector normalized = velocity.normalize();
                fireball.setDirection(normalized.multiply(power));
            }
        }.runTaskTimer(this.plugin, 1L, 1L);
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
}