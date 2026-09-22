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
    //  INFINITE FOOD BAR (anti-hunger)
    //
    //  Config:  infinite.food: true
    //
    //  رفتار:
    //  - food و saturation همیشه 20 میمونه
    //  - پلیر اصلاً گشنه نمیشه
    //  - ❌ ولی HP رو پر نمیکنه (نه از این event)
    // ============================================================
    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        if (!this.plugin.getConfig().getBoolean("infinite.food", true)) return;

        Player player = (Player) event.getEntity();
        if (player.getGameMode() == GameMode.CREATIVE) return;

        // Cancel any decrease, keep food full
        if (event.getFoodLevel() < 20) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20.0F);
            player.setExhaustion(0.0F);
        }
    }

    // ============================================================
    //  ON CONSUME — food gets eaten, hunger reset, ❌ NO HEAL
    //
    //  Config:  infinite.food: true
    //
    //  رفتار:
    //  - food و saturation بعد از خوردن 20 میشن
    //  - ❌ HP پر نمیشه — پلیر باید خودش gapple بخوره یا بمیره
    // ============================================================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        if (!this.plugin.getConfig().getBoolean("infinite.food", true)) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;

        ItemStack item = event.getItem();
        Material type = item.getType();

        // ✅ فقط food رو refill کن، HP رو دست نزن
        if (type.isEdible()) {
            // schedule 1 tick later so vanilla effects apply first
            this.plugin.getServer().getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        player.setFoodLevel(20);
                        player.setSaturation(20.0F);
                        player.setExhaustion(0.0F);
                    }
                }
            }, 1L);
        }
    }

    // ============================================================
    //  INFINITE BLOCKS
    // ============================================================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent event) {
        if (!this.plugin.getConfig().getBoolean("infinite.blocks", true)) return;

        final Player player = event.getPlayer();

        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (!event.canBuild()) return;

        final ItemStack itemInHand = player.getItemInHand();
        if (itemInHand == null || itemInHand.getType() == Material.AIR) return;

        final Material type = itemInHand.getType();
        final short data = itemInHand.getDurability();
        final int amountBeforePlace = itemInHand.getAmount();

        // Restore the amount over the next few ticks
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

        // 2) Check the whole inventory
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