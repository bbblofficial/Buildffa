package org.vansama.buildffa;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class KillListener implements Listener {
  private Map<UUID, Integer> killCounts = new HashMap<UUID, Integer>();
  private Map<UUID, Long> lastKillTimes = new ConcurrentHashMap<UUID, Long>();
  private Map<UUID, Long> lastVictimTimes = new ConcurrentHashMap<UUID, Long>();
  private JavaPlugin plugin;
  private FileConfiguration config;
  private String nmsVersion;

  public KillListener(JavaPlugin plugin) {
    this.plugin = plugin;
    this.config = plugin.getConfig();
    plugin.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);

    String packageName = plugin.getServer().getClass().getPackage().getName();
    this.nmsVersion = packageName.substring(packageName.lastIndexOf('.') + 1);
  }

  // ============================================================
  //  COMBAT MODE — track every hit between players
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

    CombatManager.registerHit(attacker.getUniqueId(), victim.getUniqueId());
  }
  // ============================================================

  @EventHandler
  public void onPlayerKill(PlayerDeathEvent event) {
    Player deathPlayer = event.getEntity();
    Player killer = deathPlayer.getKiller();
    if (killer == null) return;

    long currentTime = System.currentTimeMillis();

    long lastVictimTime = this.lastVictimTimes.getOrDefault(deathPlayer.getUniqueId(), Long.valueOf(0L)).longValue();
    if (currentTime - lastVictimTime < 3000L) {
      return;
    }
    this.lastVictimTimes.put(deathPlayer.getUniqueId(), Long.valueOf(currentTime));

    long lastKillTime = this.lastKillTimes.getOrDefault(killer.getUniqueId(), Long.valueOf(0L)).longValue();
    if (currentTime - lastKillTime < 80L) {
      return;
    }
    this.lastKillTimes.put(killer.getUniqueId(), Long.valueOf(currentTime));

    UUID killerId = killer.getUniqueId();
    int kills = this.killCounts.getOrDefault(killerId, Integer.valueOf(0)).intValue() + 1;
    this.killCounts.put(killerId, Integer.valueOf(kills));

    boolean enableTitle = this.config.getBoolean("kill-screen.enable-title", true);

    if (enableTitle) {
        String title = this.config.getString("kill-screen.title", "&e+%killcount% &7Kill");
        String subtitle = this.config.getString("kill-screen.subtitle", "&e+1 Kill");

        title = title.replace("%killcount%", String.valueOf(kills));
        subtitle = subtitle.replace("%killcount%", String.valueOf(kills));

        sendTitle(killer, title, subtitle, kills);
    }

    killer.playSound(killer.getLocation(), Sound.LEVEL_UP, 1.0F, 1.0F);

    // Clear combat between these two — kill resolved the fight
    CombatManager.clearPlayer(killer.getUniqueId());
    CombatManager.clearPlayer(deathPlayer.getUniqueId());
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private void sendTitle(Player player, String title, String subtitle, int kills) {
    try {
      title = colorize(title);
      subtitle = colorize(subtitle);

      Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + nmsVersion + ".entity.CraftPlayer");
      Object craftPlayer = craftPlayerClass.cast(player);
      Object entityPlayer = craftPlayerClass.getMethod("getHandle").invoke(craftPlayer);
      Object playerConnection = entityPlayer.getClass().getField("playerConnection").get(entityPlayer);

      Class<?> chatComponentClass = Class.forName("net.minecraft.server." + nmsVersion + ".ChatComponentText");
      Object titleComponent = chatComponentClass.getConstructor(String.class).newInstance(title);
      Object subtitleComponent = chatComponentClass.getConstructor(String.class).newInstance(subtitle);

      Class<?> packetTitleClass = Class.forName("net.minecraft.server." + nmsVersion + ".PacketPlayOutTitle");
      Class<?> enumTitleActionClass = Class.forName("net.minecraft.server." + nmsVersion + ".PacketPlayOutTitle$EnumTitleAction");

      Object actionTitle = Enum.valueOf((Class<Enum>) enumTitleActionClass, "TITLE");
      Object actionSubtitle = Enum.valueOf((Class<Enum>) enumTitleActionClass, "SUBTITLE");

      Constructor<?> titleConstructor = packetTitleClass.getConstructor(enumTitleActionClass, chatComponentClass);
      Object packetTitle = titleConstructor.newInstance(actionTitle, titleComponent);
      Object packetSubtitle = titleConstructor.newInstance(actionSubtitle, subtitleComponent);

      Class<?> packetClass = Class.forName("net.minecraft.server." + nmsVersion + ".Packet");
      Method sendPacketMethod = playerConnection.getClass().getMethod("sendPacket", packetClass);

      sendPacketMethod.invoke(playerConnection, packetTitle);
      sendPacketMethod.invoke(playerConnection, packetSubtitle);

      Constructor<?> timingConstructor = packetTitleClass.getConstructor(int.class, int.class, int.class);
      Object packetTiming = timingConstructor.newInstance(Integer.valueOf(0), Integer.valueOf(40), Integer.valueOf(0));
      sendPacketMethod.invoke(playerConnection, packetTiming);

    } catch (Exception e) {
      if (this.config.getBoolean("kill-screen.enable-chat-fallback", false)) {
          String fallback = this.config.getString("kill-screen.chat-message", "&a+1 Kill!");
          player.sendMessage(colorize(fallback.replace("%killcount%", String.valueOf(kills))));
      }
    }
  }

  // ============================================================
  //  QUIT — handle combat-log punishment + killstreak reset
  // ============================================================
  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();

    // If the player was in combat, reward the opponent
    UUID partnerId = CombatManager.getCombatPartner(player.getUniqueId());
    if (partnerId != null) {
      Player partner = Bukkit.getPlayer(partnerId);
      if (partner != null && partner.isOnline()) {
        if (CombatManager.areInCombat(player.getUniqueId(), partner.getUniqueId())) {
          // Check partner also has combat against player (both hit each other)
          // Full heal the partner
          partner.setHealth(partner.getMaxHealth());
          partner.setFoodLevel(20);
          partner.setSaturation(20.0F);

          String msg = this.config.getString("combat.opponent-left-message",
              "&a%player% &7left the server during combat. You were healed.");
          partner.sendMessage(colorize(msg.replace("%player%", player.getName())));
        }
      }
    }

    // Killstreak reset on quit if in combat
    DatabaseManager db = ((BuildFFA) this.plugin).getDatabaseManager();
    if (db != null && partnerId != null) {
      PlayerData data = db.getPlayer(player.getUniqueId());
      if (data != null) {
        data.resetKillstreak();
        db.savePlayer(data);
      }
    }

    CombatManager.clearPlayer(player.getUniqueId());
    resetKillCount(player);
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    resetKillCount(event.getPlayer());
  }

  private void resetKillCount(Player player) {
    this.killCounts.remove(player.getUniqueId());
    this.lastKillTimes.remove(player.getUniqueId());
    this.lastVictimTimes.remove(player.getUniqueId());
  }

  public int getKillCount(Player player) {
    return this.killCounts.getOrDefault(player.getUniqueId(), Integer.valueOf(0)).intValue();
  }

  public void updateKillCount(Player player, int increment) {
    UUID playerId = player.getUniqueId();
    int currentKills = this.killCounts.getOrDefault(playerId, Integer.valueOf(0)).intValue();
    this.killCounts.put(playerId, Integer.valueOf(currentKills + increment));
  }

  private String colorize(String message) {
    return ChatColor.translateAlternateColorCodes('&', message);
  }
}