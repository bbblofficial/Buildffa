package org.vansama.buildffa;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class FeatherJump implements Listener {

    private final JavaPlugin plugin;

    // Players who already used their double jump since last ground touch
    private final Map<UUID, Boolean> usedDoubleJump = new HashMap<UUID, Boolean>();

    // Cooldown per player (3 seconds)
    private final Map<UUID, Long> cooldown = new HashMap<UUID, Long>();
    private static final long COOLDOWN_MS = 3000L;

    // Track last space-press (jump) time per player
    private final Map<UUID, Long> lastJumpTime = new HashMap<UUID, Long>();

    public FeatherJump(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
    }

    /**
     * Detect when player presses jump while in the air.
     * In vanilla, pressing space mid-air with flight enabled triggers PlayerToggleFlightEvent.
     * We enable allowFlight on the ground and disable it after each jump.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();

        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (player.getGameMode() == GameMode.SPECTATOR) return;

        // Block real flying
        if (player.isFlying()) {
            event.setCancelled(true);
            player.setFlying(false);
            player.setAllowFlight(false);
            return;
        }

        // We want to intercept the toggle and turn it into a double-jump
        event.setCancelled(true);

        // Disable flight immediately to prevent flying
        player.setAllowFlight(false);
        player.setFlying(false);

        // Must be in the air
        if (player.isOnGround()) return;

        // Already used double jump since last ground?
        Boolean used = usedDoubleJump.get(player.getUniqueId());
        if (used != null && used.booleanValue()) return;

        // Cooldown
        long now = System.currentTimeMillis();
        if (cooldown.containsKey(player.getUniqueId())) {
            long last = cooldown.get(player.getUniqueId()).longValue();
            if (now - last < COOLDOWN_MS) return;
        }

        // Must have a feather
        if (!hasFeather(player)) return;

        cooldown.put(player.getUniqueId(), Long.valueOf(now));
        usedDoubleJump.put(player.getUniqueId(), Boolean.valueOf(true));

        // Consume feather
        removeFeather(player);

        // Apply boost
        double boost = this.plugin.getConfig().getDouble("feather-jump.boost", 1.0D);
        Vector v = player.getVelocity();
        v.setY(boost);
        player.setVelocity(v);

        player.setFallDistance(0.0F);

        try {
            player.playSound(player.getLocation(), Sound.BAT_TAKEOFF, 1.0F, 1.2F);
        } catch (Throwable ignored) {}

        String msg = this.plugin.getConfig().getString("feather-jump.message", "&b✦ &fDouble Jump!");
        if (msg != null && !msg.isEmpty()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        }
    }

    /**
     * Fallback detection — if the player pressed space and their Y velocity went up,
     * but PlayerToggleFlightEvent didn't fire, we handle it here.
     * Also reset double-jump flag when the player lands.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (player.getGameMode() == GameMode.SPECTATOR) return;

        boolean onGround = player.isOnGround();

        if (onGround) {
            // Reset double-jump on landing
            usedDoubleJump.remove(player.getUniqueId());

            // Enable flight so they can toggle it mid-air
            if (!player.getAllowFlight() && !player.isFlying()) {
                player.setAllowFlight(true);
            }
        }
    }

    private boolean hasFeather(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.FEATHER) {
                return true;
            }
        }
        return false;
    }

    private void removeFeather(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == Material.FEATHER) {
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().setItem(i, null);
                }
                player.updateInventory();
                return;
            }
        }
    }
}