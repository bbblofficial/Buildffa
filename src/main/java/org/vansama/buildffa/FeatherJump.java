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
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class FeatherJump implements Listener {

    private final JavaPlugin plugin;

    // Players who are allowed to double-jump right now
    private final Map<UUID, Boolean> canDoubleJump = new HashMap<UUID, Boolean>();

    // Cooldown per player
    private final Map<UUID, Long> cooldown = new HashMap<UUID, Long>();
    private static final long COOLDOWN_MS = 500L;

    public FeatherJump(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);

        // Ticker that re-enables flight when the player is on the ground
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    if (player.getGameMode() == GameMode.CREATIVE) continue;
                    if (player.getGameMode() == GameMode.SPECTATOR) continue;
                    if (player.isFlying()) continue;

                    boolean onGround = player.isOnGround();

                    if (onGround) {
                        // Player is on ground → enable flight so they can toggle it mid-air
                        if (!player.getAllowFlight()) {
                            player.setAllowFlight(true);
                        }
                        canDoubleJump.put(player.getUniqueId(), Boolean.valueOf(true));
                    } else {
                        // Player is in the air → keep flight enabled only if they can still double-jump
                        Boolean allowed = canDoubleJump.get(player.getUniqueId());
                        if (allowed == null || !allowed.booleanValue()) {
                            if (player.getAllowFlight() && !player.isFlying()) {
                                player.setAllowFlight(false);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();

        // Ignore creative/spectator
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (player.getGameMode() == GameMode.SPECTATOR) return;

        // Ignore if the player is really trying to fly (not double jump)
        if (player.isFlying()) return;

        event.setCancelled(true);

        // Must be in the air
        if (player.isOnGround()) {
            player.setAllowFlight(false);
            return;
        }

        // Check if they're allowed to double jump
        Boolean allowed = canDoubleJump.get(player.getUniqueId());
        if (allowed == null || !allowed.booleanValue()) {
            player.setAllowFlight(false);
            return;
        }

        // Cooldown
        long now = System.currentTimeMillis();
        if (cooldown.containsKey(player.getUniqueId())) {
            long last = cooldown.get(player.getUniqueId()).longValue();
            if (now - last < COOLDOWN_MS) {
                player.setAllowFlight(false);
                return;
            }
        }
        cooldown.put(player.getUniqueId(), Long.valueOf(now));

        // Require a feather in the inventory
        if (!hasFeather(player)) {
            player.setAllowFlight(false);
            return;
        }

        // Consume 1 feather
        removeFeather(player);

        // Prevent further double jumps until they touch ground
        canDoubleJump.put(player.getUniqueId(), Boolean.valueOf(false));
        player.setAllowFlight(false);

        // Apply boost
        double boost = this.plugin.getConfig().getDouble("feather-jump.boost", 1.0D);
        Vector v = player.getVelocity();
        v.setY(boost);
        player.setVelocity(v);

        player.setFallDistance(0.0F);

        // Sound
        try {
            player.playSound(player.getLocation(), Sound.BAT_TAKEOFF, 1.0F, 1.2F);
        } catch (Throwable ignored) {}

        // Message
        String msg = this.plugin.getConfig().getString("feather-jump.message", "&b✦ &fDouble Jump!");
        if (msg != null && !msg.isEmpty()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
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