package org.vansama.buildffa;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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
  
  public KillListener(JavaPlugin plugin) {
    this.plugin = plugin;
    this.config = plugin.getConfig();
    plugin.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }
  
  @EventHandler
  public void onPlayerKill(PlayerDeathEvent event) {
    Player deathPlayer = event.getEntity();
    Player killer = deathPlayer.getKiller();
    if (killer == null) return;
    
    long currentTime = System.currentTimeMillis();
    
    // Victim cooldown: no repeated kill credit for the same victim within 3s
    long lastVictimTime = ((Long) this.lastVictimTimes.getOrDefault(deathPlayer.getUniqueId(), Long.valueOf(0L))).longValue();
    if (currentTime - lastVictimTime < 3000L) {
      return;
    }
    this.lastVictimTimes.put(deathPlayer.getUniqueId(), Long.valueOf(currentTime));
    
    // Killer cooldown: 80ms between kills
    long lastKillTime = ((Long) this.lastKillTimes.getOrDefault(killer.getUniqueId(), Long.valueOf(0L))).longValue();
    if (currentTime - lastKillTime < 80L) {
      return;
    }
    this.lastKillTimes.put(killer.getUniqueId(), Long.valueOf(currentTime));
    
    UUID killerId = killer.getUniqueId();
    int kills = ((Integer) this.killCounts.getOrDefault(killerId, Integer.valueOf(0))).intValue() + 1;
    this.killCounts.put(killerId, Integer.valueOf(kills));
    
    String titleSuffix = this.config.getString("Title-Suffix", " &7Kill");
    String subTitleKill = this.config.getString("SubTitle-kill", "&e+1 Kill");
    
    sendTitle(killer, "+" + kills + titleSuffix, subTitleKill);
    killer.playSound(killer.getLocation(), Sound.LEVEL_UP, 1.0F, 1.0F);
  }
  
  @SuppressWarnings({ "unchecked", "rawtypes" })
  private void sendTitle(Player player, String title, String subtitle) {
    try {
      title = colorize(title);
      subtitle = colorize(subtitle);
      
      Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer");
      Object craftPlayer = craftPlayerClass.cast(player);
      Object entityPlayer = craftPlayerClass.getMethod("getHandle").invoke(craftPlayer);
      Object playerConnection = entityPlayer.getClass().getField("playerConnection").get(entityPlayer);
      
      Class<?> chatComponentClass = Class.forName("net.minecraft.server.v1_8_R3.ChatComponentText");
      Object titleComponent = chatComponentClass.getConstructor(String.class).newInstance(title);
      Object subtitleComponent = chatComponentClass.getConstructor(String.class).newInstance(subtitle);
      
      Class<?> packetTitleClass = Class.forName("net.minecraft.server.v1_8_R3.PacketPlayOutTitle");
      Class<?> enumTitleActionClass = Class.forName("net.minecraft.server.v1_8_R3.PacketPlayOutTitle$EnumTitleAction");
      
      Object actionTitle = Enum.valueOf((Class<Enum>) enumTitleActionClass, "TITLE");
      Object actionSubtitle = Enum.valueOf((Class<Enum>) enumTitleActionClass, "SUBTITLE");
      
      Constructor<?> titleConstructor = packetTitleClass.getConstructor(enumTitleActionClass, chatComponentClass);
      Object packetTitle = titleConstructor.newInstance(actionTitle, titleComponent);
      Object packetSubtitle = titleConstructor.newInstance(actionSubtitle, subtitleComponent);
      
      Class<?> packetClass = Class.forName("net.minecraft.server.v1_8_R3.Packet");
      Method sendPacketMethod = playerConnection.getClass().getMethod("sendPacket", packetClass);
      
      sendPacketMethod.invoke(playerConnection, packetTitle);
      sendPacketMethod.invoke(playerConnection, packetSubtitle);
      
      Constructor<?> timingConstructor = packetTitleClass.getConstructor(int.class, int.class, int.class);
      Object packetTiming = timingConstructor.newInstance(Integer.valueOf(0), Integer.valueOf(40), Integer.valueOf(0));
      sendPacketMethod.invoke(playerConnection, packetTiming);
    } catch (Exception e) {
      player.sendMessage(title + " " + subtitle);
    }
  }
  
  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    resetKillCount(event.getPlayer());
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
    return ((Integer) this.killCounts.getOrDefault(player.getUniqueId(), Integer.valueOf(0))).intValue();
  }
  
  public void updateKillCount(Player player, int increment) {
    UUID playerId = player.getUniqueId();
    int currentKills = ((Integer) this.killCounts.getOrDefault(playerId, Integer.valueOf(0))).intValue();
    this.killCounts.put(playerId, Integer.valueOf(currentKills + increment));
  }
  
  private String colorize(String message) {
    return ChatColor.translateAlternateColorCodes('&', message);
  }
}