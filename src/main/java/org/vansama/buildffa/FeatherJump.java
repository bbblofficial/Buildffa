package org.vansama.buildffa;

import java.util.HashMap;
import java.util.Map;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class FeatherJump implements Listener {

    private final JavaPlugin plugin;

    // 3-second cooldown
    private final Map<UUID, Long> cooldown = new HashMap<UUID, Long>();
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

        // Must be in the air
        if (player.isOnGround()) return;

        // Cooldown
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
        double forwardBoost = this.plugin.getConfig().getDouble("feather-jump.forward-boost", 0.0D);

        Vector velocity = player.getVelocity();
        velocity.setY(boost);

        if (forwardBoost > 0.0D) {
            Vector direction = player.getLocation().getDirection().setY(0).normalize();
            velocity.add(direction.multiply(forwardBoost));
        }

        player.setVelocity(velocity);
        // ============================================================

        // Visual effects
        Location loc = player.getLocation();
        try {
            player.getWorld().playEffect(loc, Effect.CLOUD, 1);
            player.getWorld().playEffect(loc, Effect.SMOKE, 4);
        } catch (Throwable ignored) {}

        try {
            player.playSound(loc, Sound.BAT_TAKEOFF, 0.7F, 1.5F);
        } catch (Throwable ignored) {}

        String msg = this.plugin.getConfig().getString("feather-jump.message", "&b✦ &fDouble Jump!");
        if (msg != null && !msg.isEmpty()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        }
    }
}