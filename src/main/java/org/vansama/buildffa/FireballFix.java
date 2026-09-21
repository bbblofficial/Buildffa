package org.vansama.buildffa;

import java.util.ArrayList;
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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
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

    private final ArrayList<Player> coolDownPlayers = new ArrayList<Player>();

    public FireballFix(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
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
        if (item == null) return;

        if (item.getType() != Material.FIREBALL) return;

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

        double speed = this.plugin.getConfig().getDouble("fireball.speed", 2.0D);
        Vector direction = player.getLocation().getDirection().multiply(speed);
        fireball.setDirection(direction);
        fireball.setVelocity(direction);

        fireball.setIsIncendiary(false);

        double yield = this.plugin.getConfig().getDouble("fireball.yield", 1.0D);
        fireball.setYield((float) yield);

        // Throw effects (BedWars-style)
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
    //  EXPLODE → KNOCKBACK + DAMAGE (BedWars style)
    // ============================================================
    @EventHandler
    public void onExplode(EntityExplodeEvent event) {
        Location l = event.getLocation();
        double radius = this.plugin.getConfig().getDouble("fireball.knockback.radius", 4.0D);

        // ✅ Collection (نه List)
        Collection<Entity> nearby = l.getWorld().getNearbyEntities(l, radius, radius, radius);

        if (event.getEntityType() == EntityType.FIREBALL
                && this.plugin.getConfig().getBoolean("fireball.knockback.enabled", true)) {

            double hf = this.plugin.getConfig().getDouble("fireball.knockback.height-force", 1.5D) / 2.0D;
            double rf = this.plugin.getConfig().getDouble("fireball.knockback.radius-force", 2.0D) / 2.0D;

            for (Entity entity : nearby) {
                if (entity instanceof Player) {
                    pushAway((LivingEntity) entity, l, hf, rf, event);
                }
            }
        }
    }

    // ============================================================
    //  PREVENT VANILLA EXPLOSION DAMAGE
    // ============================================================
    @EventHandler
    public void fireballDirectHit(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) return;

        // Cancel vanilla — our pushAway() handles damage
        event.setCancelled(true);
    }

    // ============================================================
    //  PUSH + DAMAGE (BedWars algorithm)
    // ============================================================
    void pushAway(LivingEntity player, Location l, double hf, double rf, EntityExplodeEvent event) {
        Location loc = player.getLocation();
        double damage = this.plugin.getConfig().getDouble("fireball.knockback.damage", 1.0D);

        double distance = (event.getYield() * 16.0F);
        distance *= 1.0D;

        double hf1 = Math.max(-4.0D, Math.min(4.0D, hf));
        double rf1 = Math.max(-4.0D, Math.min(4.0D, -1.0D * rf));

        // Knockback direction (away from explosion)
        player.setVelocity(l.toVector()
                .subtract(loc.toVector())
                .normalize()
                .multiply(rf1)
                .setY(hf1));

        // Custom damage
        double finalDamage = distance - loc.distance(player.getLocation()) + damage;
        if (finalDamage < 0) finalDamage = 0;

        EntityDamageEvent damageEvent = new EntityDamageEvent(
                player,
                EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,
                finalDamage);

        Bukkit.getPluginManager().callEvent(damageEvent);

        if (!damageEvent.isCancelled()) {
            player.damage(damageEvent.getFinalDamage());
        }
    }
}