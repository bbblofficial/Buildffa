package org.vansama.buildffa;

import java.io.File;
import java.util.ArrayList;
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
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

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

  // 1.8.8 sidebar supports up to 15 lines
  private static final int MAX_LINES = 15;

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
    for (Player player : Bukkit.getOnlinePlayers()) {
      createScoreboard(player);
    }
  }

  // ============================================================
  //  Update Task
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
    int delay = this.scoreboardConfig.getInt("join-delay", 5);
    Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
      @Override
      public void run() {
        if (player.isOnline()) {
          createScoreboard(player);
        }
      }
    }, delay);
  }

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    UUID id = event.getPlayer().getUniqueId();
    this.playerBoards.remove(id);
    this.hiddenPlayers.remove(id);
  }

  // ============================================================
  //  Scoreboard Creation
  // ============================================================

  public void createScoreboard(Player player) {
    if (!this.scoreboardConfig.getBoolean("enabled", true)) return;
    if (this.hiddenPlayers.contains(player.getUniqueId())) return;

    Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
    Objective objective = board.registerNewObjective("buildffa", "dummy");
    objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    objective.setDisplayName(colorize(getTitle()));

    // Create 15 teams (one per possible line)
    for (int i = 0; i < MAX_LINES; i++) {
      Team team = board.registerNewTeam("line_" + i);
      // Each team has an invisible unique entry so the number hides
      String entry = getUniqueEntry(i);
      team.addEntry(entry);
    }

    this.playerBoards.put(player.getUniqueId(), board);
    player.setScoreboard(board);
    updateScoreboard(player);
  }

  // ============================================================
  //  Scoreboard Update
  // ============================================================

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

    // Update title (animated or static)
    objective.setDisplayName(colorize(getTitle()));

    // Get lines for this player
    List<String> lines = getLinesForPlayer(player);
    if (lines == null) lines = new ArrayList<String>();

    // Limit to 15 lines
    if (lines.size() > MAX_LINES) {
      lines = lines.subList(0, MAX_LINES);
    }

    // Apply placeholders to all lines first
    List<String> processed = new ArrayList<String>();
    for (String raw : lines) {
      processed.add(applyPlaceholders(player, raw));
    }

    // Update each team
    for (int i = 0; i < MAX_LINES; i++) {
      Team team = board.getTeam("line_" + i);
      if (team == null) {
        team = board.registerNewTeam("line_" + i);
        team.addEntry(getUniqueEntry(i));
      }

      if (i < processed.size()) {
        String line = processed.get(i);
        // splitLine handles 16-char limit and color codes properly
        String[] parts = splitLine(line);
        team.setPrefix(parts[0]);
        team.setSuffix(parts[1]);
      } else {
        // Empty line — hide it by setting empty prefix/suffix
        team.setPrefix("");
        team.setSuffix("");
      }
    }

    // Add all entries to the objective with descending scores
    // but ONLY if we haven't already added them (avoids flicker)
    for (int i = 0; i < MAX_LINES; i++) {
      String entry = getUniqueEntry(i);
      if (!objective.getScore(entry).isScoreSet()) {
        // Score from 15 (top) down to 1 (bottom)
        objective.getScore(entry).setScore(MAX_LINES - i);
      }
    }
  }

  // ============================================================
  //  Title
  // ============================================================

  private String getTitle() {
    if (this.scoreboardConfig.getBoolean("title.animated", true)) {
      List<String> frames = this.scoreboardConfig.getStringList("title.frames");
      if (frames == null || frames.isEmpty()) {
        return "&6&lBuildFFA";
      }
      return frames.get(this.animationFrame % frames.size());
    }
    return this.scoreboardConfig.getString("title.static", "&6&lBuildFFA");
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
  //  Line Splitting (for 1.8.8's 16-char prefix limit)
  // ============================================================

  /**
   * Splits a line into prefix (max 16 chars) and suffix (max 16 chars).
   * Handles color codes correctly so colors carry over.
   */
  private String[] splitLine(String line) {
    if (line == null) return new String[]{"", ""};
    if (line.isEmpty()) return new String[]{"", ""};

    // If the whole line fits in 16 visible chars, no split needed
    if (line.length() <= 16) {
      return new String[]{line, ""};
    }

    // Find the split point (16 chars, but don't split mid-color-code)
    int splitAt = 16;

    // If character at splitAt-1 is '§' (section sign), back up one
    if (line.length() > 0 && line.charAt(splitAt - 1) == ChatColor.COLOR_CHAR) {
      splitAt--;
    }

    String prefix = line.substring(0, splitAt);
    String suffix = line.substring(splitAt);

    // Get last color code from prefix and prepend to suffix
    String lastColors = ChatColor.getLastColors(prefix);
    if (lastColors != null && !lastColors.isEmpty()) {
      suffix = lastColors + suffix;
    }

    // Trim suffix to 16 chars max (1.8.8 limit)
    if (suffix.length() > 16) {
      // Make sure we don't cut in the middle of a color code
      int end = 16;
      if (suffix.length() > 0 && suffix.charAt(end - 1) == ChatColor.COLOR_CHAR) {
        end--;
      }
      suffix = suffix.substring(0, end);
    }

    return new String[]{prefix, suffix};
  }

  /**
   * Returns a unique invisible entry for a given line index.
   * Uses section-sign + color code so the entry is invisible.
   * Every team needs a unique entry so 1.8.8 doesn't merge them.
   */
  private String getUniqueEntry(int index) {
    ChatColor[] colors = ChatColor.values();
    ChatColor c1 = colors[index % colors.length];
    ChatColor c2 = colors[(index / colors.length) % colors.length];
    return c1.toString() + c2.toString() + ChatColor.RESET;
  }

  // ============================================================
  //  Placeholders
  // ============================================================

  private String applyPlaceholders(Player player, String line) {
    if (line == null) return "";

    int kills = this.killListener.getKillCount(player);
    int deaths = this.playerDeaths.containsKey(player.getUniqueId())
        ? this.playerDeaths.get(player.getUniqueId()).intValue() : 0;
    int online = Bukkit.getOnlinePlayers().size();
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
  //  Toggle
  // ============================================================

  public boolean isHidden(Player player) {
    return this.hiddenPlayers.contains(player.getUniqueId());
  }

  public boolean toggleScoreboard(Player player) {
    UUID id = player.getUniqueId();
    if (this.hiddenPlayers.contains(id)) {
      this.hiddenPlayers.remove(id);
      createScoreboard(player);
      return true;
    } else {
      this.hiddenPlayers.add(id);
      this.playerBoards.remove(id);
      player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
      return false;
    }
  }

  // ============================================================
  //  Utilities
  // ============================================================

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