package org.vansama.buildffa;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;
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

    private final Map<UUID, Long> cooldown = new HashMap<UUID, Long>();
    private static final long COOLDOWN_MS = 3000L;

    public FeatherJump(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
    }

    // ⚠️ ignoreCancelled = false تا همیشه صدا زده شه
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onRightClick(PlayerInteractEvent event) {
        // فقط راست‌کلیک
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getItemInHand();

        // چک: Feather توی دست
        if (item == null || item.getType() != Material.FEATHER) return;

        // ============ DEBUG ============
        plugin.getLogger().info("[FeatherJump] " + player.getName()
                + " | onGround=" + player.isOnGround()
                + " | item=FEATHER x" + item.getAmount());
        // ===============================

        // چک: توی هوا باشه
        if (player.isOnGround()) {
            plugin.getLogger().info("[FeatherJump] " + player.getName() + " rejected: on ground");
            return;
        }

        // Cooldown
        long now = System.currentTimeMillis();
        if (cooldown.containsKey(player.getUniqueId())) {
            long last = cooldown.get(player.getUniqueId()).longValue();
            if (now - last < COOLDOWN_MS) {
                long remaining = (COOLDOWN_MS - (now - last)) / 1000;
                player.sendMessage(ChatColor.RED + "Double Jump cooldown: " + remaining + "s");
                return;
            }
        }
        cooldown.put(player.getUniqueId(), Long.valueOf(now));

        // مصرف 1 پر
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.setItemInHand(null);
        }
        player.updateInventory();

        // Boost
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

        plugin.getLogger().info("[FeatherJump] " + player.getName() + " DOUBLE JUMPED!");
    }
}