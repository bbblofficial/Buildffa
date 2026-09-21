package org.vansama.buildffa;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
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
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

public class FireballFix implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, Long> cooldown = new HashMap<UUID, Long>();
    private static final long COOLDOWN_MS = 1000L;

    // Cached config values (reloaded on every fireball for safety)
    private double explosionSize;
    private boolean makeFire;
    private double knockbackHorizontal;
    private double knockbackVertical;
    private double damageSelf;
    private double damageEnemy;

    public FireballFix(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
    }

    public void loadConfig() {
        this.explosionSize        = plugin.getConfig().getDouble("fireball.explosion-size", 3.0D);
        this.makeFire             = plugin.getConfig().getBoolean("fireball.make-fire", false);
        this.knockbackHorizontal  = plugin.getConfig().getDouble("fireball.knockback-horizontal", 1.2D) * -1;
        this.knockbackVertical    = plugin.getConfig().getDouble("fireball.knockback-vertical", 0.9D);
        this.damageSelf           = plugin.getConfig().getDouble("fireball.damage-self", 3.0D);
        this.damageEnemy          = plugin.getConfig().getDouble("fireball.damage-enemy", 4.0D);
    }

    // ============================================================
    //  RIGHT-CLICK FIRE CHARGE → SHOOT FIREBALL
    // ============================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getItemInHand();
        if (item == null || item.getType() != Material.FIREBALL) return;

        long now = System.currentTimeMillis();
        if (cooldown.containsKey(player.getUniqueId())) {
            long last = cooldown.get(player.getUniqueId()).longValue();
            if (now - last < COOLDOWN_MS) {
                event.setCancelled(true);
                return;
            }
        }
        cooldown.put(player.getUniqueId(), Long.valueOf(now));

        event.setCancelled(true);

        // Consume one fire charge
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.setItemInHand(null);
        }
        player.updateInventory();

        // Launch fireball
        Fireball fireball = player.launchProjectile(Fireball.class);
        fireball.setIsIncendiary(makeFire);
        fireball.setYield((float) explosionSize);

        try {
            player.playSound(player.getLocation(), Sound.GHAST_FIREBALL, 1.0F, 1.0F);
        } catch (Throwable ignored) {}

        String msg = this.plugin.getConfig().getString("fireball.message", "");
        if (msg != null && !msg.isEmpty()) {
            player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', msg));
        }
    }

    // ============================================================
    //  FIREBALL HIT → APPLY CUSTOM KNOCKBACK + DAMAGE
    // ============================================================
    @EventHandler
    public void fireballHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Fireball)) return;

        Location location = event.getEntity().getLocation();
        ProjectileSource source = ((Fireball) event.getEntity()).getShooter();
        if (!(source instanceof Player)) return;
        Player shooter = (Player) source;

        Vector vector = location.toVector();
        World world = location.getWorld();
        if (world == null) return;

        Collection<Entity> nearby = world.getNearbyEntities(
                location, explosionSize, explosionSize, explosionSize);

        for (Entity entity : nearby) {
            if (!(entity instanceof Player)) continue;
            Player target = (Player) entity;

            // Knockback
            Vector targetVec = target.getLocation().toVector();
            Vector normalized = vector.subtract(targetVec).normalize();
            Vector horizontal = normalized.multiply(knockbackHorizontal);

            double y = normalized.getY();
            if (y < 0) y += 1.5;
            if (y <= 0.5) {
                y = knockbackVertical * 1.5;
            } else {
                y = y * knockbackVertical * 1.5;
            }

            target.setVelocity(horizontal.setY(y));

            // Damage
            if (target.equals(shooter)) {
                if (damageSelf > 0) target.damage(damageSelf);
            } else {
                if (damageEnemy > 0) target.damage(damageEnemy);
            }
        }
    }

    // ============================================================
    //  PREVENT VANILLA FIREBALL DAMAGE (we handle it ourselves)
    // ============================================================
    @EventHandler
    public void fireballDirectHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Fireball)) return;
        if (!(event.getEntity() instanceof Player)) return;
        event.setCancelled(true);
    }

    // ============================================================
    //  EXPLOSION PRIME → CONTROL FIRE
    // ============================================================
    @EventHandler
    public void fireballPrime(ExplosionPrimeEvent event) {
        if (!(event.getEntity() instanceof Fireball)) return;
        event.setFire(makeFire);
    }
}