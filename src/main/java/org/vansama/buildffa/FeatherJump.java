package org.vansama.buildffa;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class FeatherJump implements Listener {

    private final JavaPlugin plugin;

    // 3-second cooldown
    private final Map<UUID, Long> cooldown = new HashMap<UUID, Long>();
    
    // Tracks players who shouldn't take fall damage on their next landing
    private final Set<UUID> noFallDamage = new HashSet<UUID>();
    
    private static final long COOLDOWN_MS = 3000L;

    public FeatherJump(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getItemInHand();
        if (item == null || item.getType() != Material.FEATHER) return;

        // Cooldown check
        long now = System.currentTimeMillis();
        if (cooldown.containsKey(player.getUniqueId())) {
            long last = cooldown.get(player.getUniqueId()).longValue();
            if (now - last < COOLDOWN_MS) return;
        }
        cooldown.put(player.getUniqueId(), Long.valueOf(now));

        event.setCancelled(true);

        // Consume 1 feather
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.setItemInHand(null);
        }
        player.updateInventory();

        // ============================================================
        //  SMOOTH DOUBLE JUMP
        // ============================================================
        double boost = this.plugin.getConfig().getDouble("feather-jump.boost", 0.9D);
        double forwardBoost = this.plugin.getConfig().getDouble("feather-jump.forward-boost", 1.5D);

        // Calculate direction player is looking, remove Y so it's strictly horizontal, then add custom Y boost
        Vector velocity = player.getLocation().getDirection().setY(0).normalize().multiply(forwardBoost);
        velocity.setY(boost);

        player.setVelocity(velocity);
        
        // Add player to the immunity list so they don't take fall damage
        noFallDamage.add(player.getUniqueId());
        // ============================================================

        // Visual effects
        Location loc = player.getLocation();
        try {
            player.getWorld().playEffect(loc, Effect.CLOUD, 1);
            player.getWorld().playEffect(loc, Effect.SMOKE, 4);
            player.playSound(loc, Sound.BAT_TAKEOFF, 0.7F, 1.5F);
        } catch (Throwable ignored) {}

        String msg = this.plugin.getConfig().getString("feather-jump.message", "&b✦ &fDouble Jump!");
        if (msg != null && !msg.isEmpty()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        }
    }

    // ============================================================
    //  FEATHER FALL IMMUNITY HANDLER
    // ============================================================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() == DamageCause.FALL && event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            
            // If the player used a feather, cancel their fall damage and remove them from the list
            if (noFallDamage.contains(player.getUniqueId())) {
                event.setCancelled(true);
                noFallDamage.remove(player.getUniqueId());
            }
        }
    }
}