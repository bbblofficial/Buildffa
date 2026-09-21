package org.vansama.buildffa;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class PotionFix implements Listener {

    private final JavaPlugin plugin;
    private final Set<UUID> pendingCheck = new HashSet<UUID>();

    public PotionFix(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(final PlayerItemConsumeEvent event) {
        final Player player = event.getPlayer();
        final ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.POTION) return;

        pendingCheck.add(player.getUniqueId());

        Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
            @Override
            public void run() {
                pendingCheck.remove(player.getUniqueId());
                if (!player.isOnline()) return;

                ItemStack[] contents = player.getInventory().getContents();
                boolean changed = false;
                for (int i = 0; i < contents.length; i++) {
                    ItemStack s = contents[i];
                    if (s == null) continue;
                    if (s.getType() == Material.GLASS_BOTTLE) {
                        player.getInventory().setItem(i, null);
                        changed = true;
                    }
                }
                if (changed) player.updateInventory();
            }
        }, 1L);
    }
}