package org.vansama.buildffa;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;
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
import org.bukkit.util.Vector;

public class FireballFix implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, Long> cooldown = new HashMap<UUID, Long>();
    private static final long COOLDOWN_MS = 500L;

    public FireballFix(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
    }

    // ============================================================
    //  LAUNCH FIREBALL (Default MC Speed + Slightly Faster)
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
        if (cooldown.containsKey(player.getUniqueId())) {
            long last = cooldown.get(player.getUniqueId());
            if (now - last < COOLDOWN_MS) {
                return;
            }
        }
        cooldown.put(player.getUniqueId(), now);

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.setItemInHand(null);
        }
        player.updateInventory();

        Fireball fireball = player.launchProjectile(Fireball.class);

        // تنظیم سرعت مشابه ماینکرفت اما کمی تندتر (1.5 برابر)
        double speed = this.plugin.getConfig().getDouble("fireball.speed", 1.5D);
        Vector dir = player.getLocation().getDirection();
        
        fireball.setVelocity(dir.clone().multiply(speed));
        // ضریب 0.1 برای شتاب‌دهنده انجین ماینکرفت جهت حفظ ثبات گلوله در هوا
        fireball.setDirection(dir.clone().multiply(speed * 0.1D)); 

        double yield = this.plugin.getConfig().getDouble("fireball.yield", 1.0D);
        fireball.setYield((float) yield);

        // Throw effects
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

        try {
            player.playSound(player.getLocation(), Sound.GHAST_FIREBALL, 1.0F, 1.0F);
        } catch (Throwable ignored) {}
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
        // در BedWars1058 نیروی افقی در 1- ضرب می‌شود تا بردار برعکس شود
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
                y = fireballVertical * 1.5; // kb for not jumping
            } else {
                y = y * fireballVertical * 1.5; // kb for jumping
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
    //  BEDWARS1058 FIRE SETTINGS
    // ============================================================
    @EventHandler
    public void fireballPrime(ExplosionPrimeEvent e) {
        if (!(e.getEntity() instanceof Fireball)) return;
        
        e.setFire(false);
    }
}