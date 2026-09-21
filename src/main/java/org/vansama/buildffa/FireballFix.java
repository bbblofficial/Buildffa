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
    //  RIGHT-CLICK FIRE CHARGE → SHOOT BEDWARS FIREBALL
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

        // مصرف یک عدد فایربال
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.setItemInHand(null);
        }
        player.updateInventory();

        // پرتاب از سطح چشم پلیر برای جلوگیری از گیر کردن به بلاک زیر پا
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection().normalize();

        Fireball fireball = player.getWorld().spawn(eyeLoc.add(direction.clone().multiply(1.2D)), Fireball.class);
        fireball.setShooter(player);


        double speed = this.plugin.getConfig().getDouble("fireball.speed", 1.25D);

        Vector velocity = direction.clone().multiply(speed);
        fireball.setVelocity(velocity);
        fireball.setDirection(velocity.clone().multiply(0.1D));

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
    //  PROJECTILE HIT → HYPIXEL BEDWARS KNOCKBACK & JUMP
    // ============================================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fireballHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Fireball)) return;

        Fireball fireball = (Fireball) event.getEntity();
        if (!this.plugin.getConfig().getBoolean("fireball.knockback.enabled", true)) return;

        Location hitLoc = fireball.getLocation();
        if (hitLoc.getWorld() == null) return;

        double radius = this.plugin.getConfig().getDouble("fireball.knockback.radius", 4.0D);
        double horizontalForce = this.plugin.getConfig().getDouble("fireball.knockback.radius-force", 1.5D);
        double verticalForce = this.plugin.getConfig().getDouble("fireball.knockback.height-force", 0.95D);
        double damage = this.plugin.getConfig().getDouble("fireball.knockback.damage", 1.0D);

        Collection<Entity> nearbyEntities = hitLoc.getWorld().getNearbyEntities(hitLoc, radius, radius, radius);

        for (Entity entity : nearbyEntities) {
            if (!(entity instanceof Player)) continue;
            Player target = (Player) entity;

            Location targetLoc = target.getLocation();
            double distance = hitLoc.distance(targetLoc);
            if (distance > radius) continue;

            // محاسبه ضریب فاصله (هرچه نزدیک‌تر، پرتاب قوی‌تر)
            double distanceFactor = 1.0D - (distance / radius);
            if (distanceFactor < 0.2D) distanceFactor = 0.2D;

            // بردار دافعه به سمت بیرون
            Vector knockbackDir = targetLoc.toVector().subtract(hitLoc.toVector());
            knockbackDir.setY(0); // جداسازی مؤلفه افقی

            if (knockbackDir.lengthSquared() > 0.0001D) {
                knockbackDir.normalize();
            } else {
                // اگر دقیقاً روی فایربال بود، به سمت عقب پلیر هل داده شود
                knockbackDir = target.getLocation().getDirection().multiply(-1).setY(0).normalize();
            }

            // اعمال نیروی افقی متناسب با فاصله
            Vector finalVelocity = knockbackDir.multiply(horizontalForce * distanceFactor);

            // اعمال پرش عمودی به سبک Fireball Jump بدوارز
            finalVelocity.setY(verticalForce);

            target.setVelocity(finalVelocity);

            if (damage > 0) {
                target.damage(damage);
            }
        }
    }

    // ============================================================
    //  CANCEL DIRECT FIREBALL EXPLOSION DAMAGE OVERRIDE
    // ============================================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fireballDirectHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Fireball && event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }
}