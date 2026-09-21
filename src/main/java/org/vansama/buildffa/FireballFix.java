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
    //  RIGHT-CLICK FIRE CHARGE → SHOOT FIREBALL
    // ============================================================
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onRightClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getItemInHand();
        if (item == null) return;

        if (item.getType() != Material.FIREBALL) return;

        event.setCancelled(true);

        long now = System.currentTimeMillis();
        if (cooldown.containsKey(player.getUniqueId())) {
            long last = cooldown.get(player.getUniqueId()).longValue();
            if (now - last < COOLDOWN_MS) {
                return;
            }
        }
        cooldown.put(player.getUniqueId(), Long.valueOf(now));

        // Consume one fire charge
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.setItemInHand(null);
        }
        player.updateInventory();

        // Launch fireball
        Fireball fireball = player.launchProjectile(Fireball.class);

        // ============================================================
        //  SPEED HANDLING (Fixed)
        //  حذف شرط > 0 برای پشتیبانی از مقادیر منفی، صدم و دهم.
        //  مقدار وارد شده در کانفیگ دقیقاً به عنوان ضریب سرعت تنظیم می‌شود.
        // ============================================================
        double speed = this.plugin.getConfig().getDouble("fireball.speed", 2.0D);
        
        Vector direction = player.getLocation().getDirection().normalize();
        Vector velocity = direction.multiply(speed);

        fireball.setDirection(velocity);
        fireball.setVelocity(velocity);
        // ============================================================

        fireball.setIsIncendiary(false);

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

        String msg = this.plugin.getConfig().getString("fireball.message", "");
        if (msg != null && !msg.isEmpty()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        }
    }

    // ============================================================
    //  PROJECTILE HIT → BEDWARS KNOCKBACK + DAMAGE
    // ============================================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fireballHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Fireball)) return;

        if (!this.plugin.getConfig().getBoolean("fireball.knockback.enabled", true)) return;

        Location location = event.getEntity().getLocation();

        double radius = this.plugin.getConfig().getDouble("fireball.knockback.radius", 4.0D);
        
        // برداشته شدن ضربدر منفی ۱ برای استاندارد شدن ناک‌بک
        double horizontalForce = this.plugin.getConfig().getDouble("fireball.knockback.radius-force", 1.5D); 
        double verticalForce = this.plugin.getConfig().getDouble("fireball.knockback.height-force", 1.0D);
        double damage = this.plugin.getConfig().getDouble("fireball.knockback.damage", 0.5D);

        if (location.getWorld() == null) return;

        Vector fireballVector = location.toVector();
        Collection<Entity> nearbyEntities = location.getWorld().getNearbyEntities(location, radius, radius, radius);

        for (Entity entity : nearbyEntities) {
            if (!(entity instanceof Player)) continue;
            Player player = (Player) entity;

            Vector playerVector = player.getLocation().toVector();

            // فیکس بزرگ: کسر مکان فایربال از پلیر برای پرتاب کردن پلیر به سمت بیرون
            Vector normalizedVector = playerVector.clone().subtract(fireballVector).normalize();
            
            // ضرب کردن جهت در نیروی افقی تنظیم شده در کانفیگ
            Vector knockback = normalizedVector.multiply(horizontalForce);

            // اعمال ارتفاع استاندارد به سبک بدوارز
            knockback.setY(verticalForce);

            player.setVelocity(knockback);

            if (damage > 0) {
                player.damage(damage);
            }
        }
    }

    // ============================================================
    //  CANCEL DIRECT FIREBALL DAMAGE
    // ============================================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fireballDirectHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Fireball) {
            if (event.getEntity() instanceof Player) {
                event.setCancelled(true);
            }
        }
    }
}