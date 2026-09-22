package org.vansama.buildffa;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Shows the enemy's HP in the ActionBar when you hit them.
 *
 * Format (default):  [EnemyName] -- [18/20]
 *
 * - On every hit → shows the target's HP
 * - After N seconds (config: enemy-healthbar.timeout-seconds) without a new hit,
 *   the bar disappears automatically.
 * - YOUR OWN HP is NOT shown anymore.
 *
 * Config: enemy-healthbar.*
 */
public class EnemyHealthBar implements Listener {

    private final JavaPlugin plugin;

    private final Map<UUID, UUID> lastTarget = new ConcurrentHashMap<UUID, UUID>();
    private final Map<UUID, Long> lastHitTime = new ConcurrentHashMap<UUID, Long>();

    private int taskId = -1;

    private String nmsVersion;
    private Class<?> craftPlayerClass;
    private Class<?> chatComponentClass;
    private Class<?> packetChatClass;
    private Class<?> iChatBaseClass;
    private Class<?> packetClass;
    private Constructor<?> packetChatConstructor;
    private Method getHandleMethod;
    private boolean nmsReady = false;

    private static final long DEFAULT_TIMEOUT_SECONDS = 5L;

    public EnemyHealthBar(JavaPlugin plugin) {
        this.plugin = plugin;
        setupNMS();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startTask();
    }

    // ============================================================
    //  NMS setup for ActionBar
    // ============================================================
    private void setupNMS() {
        try {
            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            this.nmsVersion = packageName.substring(packageName.lastIndexOf('.') + 1);

            this.craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + nmsVersion + ".entity.CraftPlayer");
            this.chatComponentClass = Class.forName("net.minecraft.server." + nmsVersion + ".ChatComponentText");
            this.packetChatClass = Class.forName("net.minecraft.server." + nmsVersion + ".PacketPlayOutChat");
            this.iChatBaseClass = Class.forName("net.minecraft.server." + nmsVersion + ".IChatBaseComponent");
            this.packetClass = Class.forName("net.minecraft.server." + nmsVersion + ".Packet");

            this.packetChatConstructor = packetChatClass.getConstructor(iChatBaseClass, byte.class);
            this.getHandleMethod = craftPlayerClass.getMethod("getHandle");

            this.nmsReady = true;
        } catch (Throwable t) {
            this.nmsReady = false;
            plugin.getLogger().warning("EnemyHealthBar NMS setup failed: " + t.getMessage());
        }
    }

    // ============================================================
    //  DETECT HIT — record attacker -> victim
    // ============================================================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player victim = (Player) event.getEntity();
        Player attacker = null;

        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile) {
            Projectile proj = (Projectile) event.getDamager();
            if (proj.getShooter() instanceof Player) {
                attacker = (Player) proj.getShooter();
            }
        }

        if (attacker == null) return;
        if (attacker.getUniqueId().equals(victim.getUniqueId())) return;

        if (victim.isDead() || !victim.isOnline()) return;

        lastTarget.put(attacker.getUniqueId(), victim.getUniqueId());
        lastHitTime.put(attacker.getUniqueId(), System.currentTimeMillis());
    }

    // ============================================================
    //  UPDATE TASK
    // ============================================================
    private void startTask() {
        if (this.taskId != -1) {
            Bukkit.getScheduler().cancelTask(this.taskId);
        }

        int interval = plugin.getConfig().getInt("enemy-healthbar.update-interval", 5);
        if (interval < 1) interval = 5;

        this.taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!plugin.getConfig().getBoolean("enemy-healthbar.enabled", true)) return;

                long now = System.currentTimeMillis();
                long timeoutMs = getTimeoutMs();

                for (Player attacker : Bukkit.getOnlinePlayers()) {
                    UUID targetId = lastTarget.get(attacker.getUniqueId());
                    if (targetId == null) continue;

                    Long lastHit = lastHitTime.get(attacker.getUniqueId());
                    if (lastHit == null) continue;

                    // Timeout → clear + hide
                    if (now - lastHit > timeoutMs) {
                        lastTarget.remove(attacker.getUniqueId());
                        lastHitTime.remove(attacker.getUniqueId());
                        sendActionBar(attacker, "");
                        continue;
                    }

                    Player target = Bukkit.getPlayer(targetId);
                    if (target == null || !target.isOnline() || target.isDead()) {
                        lastTarget.remove(attacker.getUniqueId());
                        lastHitTime.remove(attacker.getUniqueId());
                        sendActionBar(attacker, "");
                        continue;
                    }

                    String format = plugin.getConfig().getString("enemy-healthbar.format",
                            "&c[%name%] &8-- &f[%hp%&7/&f%max%&f]");

                    String out = format
                            .replace("%name%", target.getName())
                            .replace("%hp%", formatNumber(target.getHealth()))
                            .replace("%max%", formatNumber(target.getMaxHealth()));

                    sendActionBar(attacker, colorize(out));
                }
            }
        }, interval, interval);
    }

    private long getTimeoutMs() {
        long seconds = plugin.getConfig().getLong("enemy-healthbar.timeout-seconds", DEFAULT_TIMEOUT_SECONDS);
        if (seconds < 1) seconds = DEFAULT_TIMEOUT_SECONDS;
        return seconds * 1000L;
    }

    // ============================================================
    //  HELPERS
    // ============================================================
    private String formatNumber(double d) {
        if (d == Math.floor(d)) return String.valueOf((int) d);
        return String.format("%.1f", d);
    }

    private void sendActionBar(Player player, String message) {
        if (player == null || !player.isOnline()) return;

        // Spigot API first
        try {
            Method m = player.getClass().getMethod("sendActionBar", String.class);
            m.invoke(player, message);
            return;
        } catch (Throwable ignored) {}

        if (!this.nmsReady) return;

        try {
            Object craftPlayer = craftPlayerClass.cast(player);
            Object entityPlayer = getHandleMethod.invoke(craftPlayer);
            Object playerConnection = entityPlayer.getClass()
                    .getField("playerConnection").get(entityPlayer);

            Object chatComponent = chatComponentClass
                    .getConstructor(String.class).newInstance(message);

            Object packet = packetChatConstructor.newInstance(chatComponent, (byte) 2);

            Method sendPacketMethod = playerConnection.getClass()
                    .getMethod("sendPacket", packetClass);

            sendPacketMethod.invoke(playerConnection, packet);
        } catch (Throwable ignored) {}
    }

    // ============================================================
    //  RELOAD / SHUTDOWN
    // ============================================================
    public void reloadConfig() {
        if (this.taskId != -1) {
            Bukkit.getScheduler().cancelTask(this.taskId);
            this.taskId = -1;
        }
        this.lastTarget.clear();
        this.lastHitTime.clear();
        startTask();
    }

    public void shutdown() {
        if (this.taskId != -1) {
            Bukkit.getScheduler().cancelTask(this.taskId);
            this.taskId = -1;
        }
        this.lastTarget.clear();
        this.lastHitTime.clear();
    }

    private String colorize(String msg) {
        if (msg == null) return "";
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}