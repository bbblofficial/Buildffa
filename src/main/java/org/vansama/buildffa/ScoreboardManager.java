package org.vansama.buildffa;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

public class ScoreboardManager implements Listener {

  private final JavaPlugin plugin;
  private final KillListener killListener;

  private final Map<UUID, Scoreboard> playerBoards = new HashMap<UUID, Scoreboard>();
  private final Set<UUID> hiddenPlayers = new HashSet<UUID>();
  private final Map<UUID, Integer> playerDeaths = new HashMap<UUID, Integer>();

  private File scoreboardFile;
  private FileConfiguration scoreboardConfig;

  private int animationFrame = 0;
  private int taskId = -1;

  public ScoreboardManager(JavaPlugin plugin, KillListener killListener) {
    this.plugin = plugin;
    this.killListener = killListener;
    loadScoreboardConfig();
    Bukkit.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
    startUpdateTask();
  }

  // ============================================================
  //  Config Loading
  // ============================================================

  private void loadScoreboardConfig() {
    if (this.scoreboardFile == null) {
      this.scoreboardFile = new File(this.plugin.getDataFolder(), "scoreboard.yml");
    }
    if (!this.scoreboardFile.exists()) {
      this.plugin.saveResource("scoreboard.yml", false);
    }
    this.scoreboardConfig = YamlConfiguration.loadConfiguration(this.scoreboardFile);
  }

  public void reloadConfig() {
    loadScoreboardConfig();
    this.animationFrame = 0;

    // Recreate every player's board so changes take effect immediately
    for (Player player : Bukkit.getOnlinePlayers()) {
      createScoreboard(player);
    }
  }

  // ============================================================
  //  Task
  // ============================================================

  private void startUpdateTask() {
    if (this.taskId != -1) {
      Bukkit.getScheduler().cancelTask(this.taskId);
    }
    int interval = this.scoreboardConfig.getInt("update-interval", 10);
    if (interval < 1) interval = 10;

    this.taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this.plugin, new Runnable() {
      @Override
      public void run() {
        if (!scoreboardConfig.getBoolean("enabled", true)) return;
        animationFrame++;
        for (Player player : Bukkit.getOnlinePlayers()) {
          if (hiddenPlayers.contains(player.getUniqueId())) continue;
          updateScoreboard(player);
        }
      }
    }, interval, interval);
  }

  // ============================================================
  //  Events
  // ============================================================

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    final Player player = event.getPlayer();
    Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
      @Override
      public void run() {
        if (player.isOnline()) {
          createScoreboard(player);
        }
      }
    }, 5L);
  }

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    UUID id = event.getPlayer().getUniqueId();
    this.playerBoards.remove(id);
    this.hiddenPlayers.remove(id);
  }

  // ============================================================
  //  Scoreboard Creation / Update
  // ============================================================

  public void createScoreboard(Player player) {
    if (!this.scoreboardConfig.getBoolean("enabled", true)) return;
    if (this.hiddenPlayers.contains(player.getUniqueId())) return;

    Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
    Objective objective = board.registerNewObjective("buildffa", "dummy");
    objective.setDisplaySlot(DisplaySlot.SIDEBAR);

    this.playerBoards.put(player.getUniqueId(), board);
    player.setScoreboard(board);
    updateScoreboard(player);
  }

  public void updateScoreboard(Player player) {
    if (!this.scoreboardConfig.getBoolean("enabled", true)) {
      Scoreboard existing = this.playerBoards.remove(player.getUniqueId());
      if (existing != null) {
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
      }
      return;
    }

    if (this.hiddenPlayers.contains(player.getUniqueId())) return;

    Scoreboard board = this.playerBoards.get(player.getUniqueId());
    if (board == null) {
      createScoreboard(player);
      return;
    }

    Objective objective = board.getObjective("buildffa");
    if (objective == null) {
      objective = board.registerNewObjective("buildffa", "dummy");
      objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    // ---- Title ----
    String title;
    if (this.scoreboardConfig.getBoolean("title.animated", true)) {
      List<String> frames = this.scoreboardConfig.getStringList("title.frames");
      if (frames == null || frames.isEmpty()) {
        title = "&6&lBuildFFA";
      } else {
        title = frames.get(this.animationFrame % frames.size());
      }
    } else {
      title = this.scoreboardConfig.getString("title.static", "&6&lBuildFFA");
    }
    objective.setDisplayName(colorize(title));

    // ---- Lines ----
    List<String> lines = getLinesForPlayer(player);
    if (lines == null) lines = new java.util.ArrayList<String>();

    // Reset old entries
    for (String entry : board.getEntries()) {
      board.resetScores(entry);
    }

    // In 1.8 the max line length is 16 chars per entry.
    int max = Math.min(lines.size(), 15); // 1.8 sidebar limit is 15 lines

    for (int i = 0; i < max; i++) {
      String raw = lines.get(i);
      String processed = applyPlaceholders(player, raw);

      String entry;
      String prefix;
      String suffix;

      if (processed.length() > 16) {
        prefix = processed.substring(0, 16);
        suffix = processed.substring(16);
        String lastColors = ChatColor.getLastColors(prefix);
        if (lastColors != null && !lastColors.isEmpty()) {
          suffix = lastColors + suffix;
        }
        entry = prefix + uniqueCode(i);
      } else {
        entry = processed + uniqueCode(i);
      }

      Score score = objective.getScore(entry);
      score.setScore(max - i);
    }

    player.setScoreboard(board);
  }

  private List<String> getLinesForPlayer(Player player) {
    if (this.scoreboardConfig.getBoolean("per-world.enabled", false)) {
      String world = player.getWorld().getName();
      List<String> worldLines = this.scoreboardConfig.getStringList("per-world.worlds." + world);
      if (worldLines != null && !worldLines.isEmpty()) {
        return worldLines;
      }
    }
    return this.scoreboardConfig.getStringList("lines");
  }

  // ============================================================
  //  Placeholders
  // ============================================================

  private String applyPlaceholders(Player player, String line) {
    if (line == null) return "";

    int kills = this.killListener.getKillCount(player);
    int deaths = this.playerDeaths.containsKey(player.getUniqueId())
        ? this.playerDeaths.get(player.getUniqueId()).intValue() : 0;
    int online = Bukkit.getOnlinePlayers().size();   // <-- FIXED (was .length)
    int maxOnline = Bukkit.getMaxPlayers();
    String world = player.getWorld().getName();
    int ping = getPing(player);
    int health = (int) Math.ceil(player.getHealth());
    int food = player.getFoodLevel();
    int y = player.getLocation().getBlockY();
    double highLimit = this.plugin.getConfig().getDouble("high-limit", 100.0D);
    double voidKill = this.plugin.getConfig().getDouble("kill-height", 0.0D);

    String out = line;
    out = out.replace("%player%", player.getName());
    out = out.replace("%kills%", String.valueOf(kills));
    out = out.replace("%deaths%", String.valueOf(deaths));
    out = out.replace("%online%", String.valueOf(online));
    out = out.replace("%max_online%", String.valueOf(maxOnline));
    out = out.replace("%world%", world);
    out = out.replace("%ping%", String.valueOf(ping));
    out = out.replace("%health%", String.valueOf(health));
    out = out.replace("%food%", String.valueOf(food));
    out = out.replace("%y%", String.valueOf(y));
    out = out.replace("%highlimit%", String.valueOf((int) highLimit));
    out = out.replace("%void%", String.valueOf((int) voidKill));

    return colorize(out);
  }

  // ============================================================
  //  Death Tracking
  // ============================================================

  public void addDeath(Player player) {
    UUID id = player.getUniqueId();
    int current = this.playerDeaths.containsKey(id) ? this.playerDeaths.get(id).intValue() : 0;
    this.playerDeaths.put(id, Integer.valueOf(current + 1));
  }

  public int getDeaths(Player player) {
    return this.playerDeaths.containsKey(player.getUniqueId())
        ? this.playerDeaths.get(player.getUniqueId()).intValue() : 0;
  }

  // ============================================================
  //  Toggle Visibility
  // ============================================================

  public boolean isHidden(Player player) {
    return this.hiddenPlayers.contains(player.getUniqueId());
  }

  public boolean toggleScoreboard(Player player) {
    UUID id = player.getUniqueId();
    if (this.hiddenPlayers.contains(id)) {
      this.hiddenPlayers.remove(id);
      createScoreboard(player);
      return true; // now visible
    } else {
      this.hiddenPlayers.add(id);
      this.playerBoards.remove(id);
      player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
      return false; // now hidden
    }
  }

  // ============================================================
  //  Utilities
  // ============================================================

  private String uniqueCode(int index) {
    ChatColor[] colors = ChatColor.values();
    ChatColor c1 = colors[index % colors.length];
    ChatColor c2 = colors[(index / colors.length) % colors.length];
    return c1.toString() + c2.toString();
  }

  private int getPing(Player player) {
    try {
      Object craftPlayer = player.getClass().getMethod("getHandle").invoke(player);
      return ((Integer) craftPlayer.getClass().getField("ping").get(craftPlayer)).intValue();
    } catch (Exception e) {
      return 0;
    }
  }

  private String colorize(String message) {
    if (message == null) return "";
    return ChatColor.translateAlternateColorCodes('&', message);
  }

  public void shutdown() {
    if (this.taskId != -1) {
      Bukkit.getScheduler().cancelTask(this.taskId);
      this.taskId = -1;
    }
    this.playerBoards.clear();
    this.hiddenPlayers.clear();
    this.playerDeaths.clear();
  }
}