package org.vansama.buildffa;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class Infinite implements Listener {

    private final JavaPlugin plugin;

    public Infinite(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
    }

    // ============================================================
    // Infinite hunger bar
    // ============================================================
    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (event.getFoodLevel() < 20) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20.0F);
            player.setExhaustion(0.0F);
        }
    }

    // ============================================================
    // Golden apple heals to full — but is CONSUMED normally
    // ============================================================
    @EventHandler
    public void onItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        Material type = item.getType();

        if (type == Material.GOLDEN_APPLE
                || type == Material.GOLDEN_CARROT
                || type == Material.COOKED_BEEF
                || type == Material.BREAD) {
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.setSaturation(20.0F);
            player.setExhaustion(0.0F);
        }
    }

    // ============================================================
    // Infinite blocks — placed blocks are refilled EVERY TIME
    // The stack amount is restored IMMEDIATELY on the next tick.
    // Works even when spamming (repeating task checks every tick
    // for a short window).
    // ============================================================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent event) {
        final Player player = event.getPlayer();

        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (!event.canBuild()) return;

        final ItemStack itemInHand = player.getItemInHand();
        if (itemInHand == null || itemInHand.getType() == Material.AIR) return;

        final Material type = itemInHand.getType();
        final short data = itemInHand.getDurability();
        final int amountBeforePlace = itemInHand.getAmount();

        // Restore the amount on the very next tick — but do it
        // multiple times over a few ticks to survive spam-placing.
        for (int delay = 1; delay <= 3; delay++) {
            this.plugin.getServer().getScheduler().scheduleSyncDelayedTask(
                this.plugin,
                new Runnable() {
                    @Override
                    public void run() {
                        restoreAmount(player, type, data, amountBeforePlace);
                    }
                },
                delay
            );
        }
    }

    /**
     * Finds the player's held item (or any matching stack in their
     * inventory) and restores its amount to `amountBeforePlace`.
     */
    private void restoreAmount(Player player, Material type, short data, int amountBeforePlace) {
        if (player == null || !player.isOnline()) return;

        // 1) Check the item currently in hand
        ItemStack inHand = player.getItemInHand();
        if (inHand != null
                && inHand.getType() == type
                && inHand.getDurability() == data) {
            if (inHand.getAmount() < amountBeforePlace) {
                inHand.setAmount(amountBeforePlace);
                player.updateInventory();
                return;
            }
        }

        // 2) Check the whole inventory (in case the player swapped slots)
        ItemStack[] contents = player.getInventory().getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack slot = contents[i];
            if (slot == null) continue;
            if (slot.getType() != type) continue;
            if (slot.getDurability() != data) continue;
            if (slot.getAmount() < amountBeforePlace) {
                slot.setAmount(amountBeforePlace);
                changed = true;
            }
        }
        if (changed) {
            player.updateInventory();
        }
    }
}