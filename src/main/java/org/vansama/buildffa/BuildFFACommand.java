package org.vansama.buildffa;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;

public class BuildFFACommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final KitEditor kitEditor;
    private final ScoreboardManager scoreboardManager;
    private final DatabaseManager database;

    public BuildFFACommand(JavaPlugin plugin, KitEditor kitEditor, ScoreboardManager scoreboardManager, DatabaseManager database) {
        this.plugin = plugin;
        this.kitEditor = kitEditor;
        this.scoreboardManager = scoreboardManager;
        this.database = database;
    }

    private String getPerm(String action, String defaultPerm) {
        return this.plugin.getConfig().getString("permissions." + action, defaultPerm);
    }

    private void sendNoPerm(CommandSender sender) {
        String msg = this.plugin.getConfig().getString("messages.no-permission", "&cYou do not have permission to do this.");
        sender.sendMessage(colorize(msg));
    }

    // ============================================================
    //  onCommand — permission-aware dispatcher
    // ============================================================
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        // ============================================================
        //  ADMIN COMMANDS (permission required)
        // ============================================================

        if (sub.equals("setvoid")) {
            if (!sender.hasPermission(getPerm("setvoid", "buildffa.setvoid"))) {
                sendNoPerm(sender);
                return true;
            }
            return handleSetVoid(sender, args);
        }

        if (sub.equals("sethighlimit")) {
            if (!sender.hasPermission(getPerm("sethighlimit", "buildffa.sethighlimit"))) {
                sendNoPerm(sender);
                return true;
            }
            return handleSetHighLimit(sender, args);
        }

        if (sub.equals("setspawn")) {
            if (!sender.hasPermission(getPerm("setspawn", "buildffa.setspawn"))) {
                sendNoPerm(sender);
                return true;
            }
            return handleSetSpawn(sender);
        }

        if (sub.equals("buildmode")) {
            if (!sender.hasPermission(getPerm("buildmode", "buildffa.buildmode"))) {
                sendNoPerm(sender);
                return true;
            }
            return handleBuildMode(sender, args);
        }

        if (sub.equals("ypvp")) {
            if (!sender.hasPermission(getPerm("ypvp", "buildffa.ypvp"))) {
                sendNoPerm(sender);
                return true;
            }
            return handleYPvP(sender, args);
        }

        if (sub.equals("ypearl")) {
            if (!sender.hasPermission(getPerm("ypearl", "buildffa.ypearl"))) {
                sendNoPerm(sender);
                return true;
            }
            return handleYPearl(sender, args);
        }

        if (sub.equals("resetstats")) {
            if (!sender.hasPermission(getPerm("resetstats", "buildffa.resetstats"))) {
                sendNoPerm(sender);
                return true;
            }
            return handleResetStats(sender, args);
        }

        if (sub.equals("forceksreward")) {
            if (!sender.hasPermission(getPerm("resetstats", "buildffa.resetstats"))) {
                sendNoPerm(sender);
                return true;
            }
            return handleForceKsReward(sender, args);
        }

        if (sub.equals("connectioncheck") || sub.equals("cc")) {
            if (!sender.hasPermission(getPerm("connectioncheck", "buildffa.connection"))) {
                sendNoPerm(sender);
                return true;
            }
            return handleConnection(sender, args);
        }

        if (sub.equals("reload")) {
            if (!sender.hasPermission(getPerm("reload", "buildffa.reload"))) {
                sendNoPerm(sender);
                return true;
            }
            return handleReload(sender);
        }

        // ============================================================
        //  PLAYER COMMANDS
        // ============================================================

        if (sub.equals("kiteditor")) {
            if (!sender.hasPermission(getPerm("kiteditor", "buildffa.kiteditor"))) {
                sendNoPerm(sender);
                return true;
            }
            return handleKitEditor(sender, args);
        }

        if (sub.equals("scoreboard") || sub.equals("sb")) {
            // "sb reload" needs the reload permission
            if (args.length >= 2 && args[1].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission(getPerm("reload", "buildffa.reload"))) {
                    sendNoPerm(sender);
                    return true;
                }
                return handleScoreboard(sender, args);
            }
            // Regular toggle needs scoreboard-toggle permission
            if (!sender.hasPermission(getPerm("scoreboard-toggle", "buildffa.scoreboard.toggle"))) {
                sendNoPerm(sender);
                return true;
            }
            return handleScoreboard(sender, args);
        }

        if (sub.equals("stats")) {
            // "stats add" and "stats reset" need resetstats permission
            if (args.length >= 2) {
                String sub2 = args[1].toLowerCase();
                if (sub2.equals("add") || sub2.equals("reset")) {
                    if (!sender.hasPermission(getPerm("resetstats", "buildffa.resetstats"))) {
                        sendNoPerm(sender);
                        return true;
                    }
                }
            }
            return handleStats(sender, args);
        }

        if (sub.equals("top")) {
            return handleTop(sender, args);
        }

        if (sub.equals("creator")) {
            return handleCreator(sender);
        }

        if (sub.equals("help")) {
            sendHelp(sender);
            return true;
        }

        sender.sendMessage(colorize("&cUnknown subcommand. Use /buildffa help"));
        return true;
    }

    // ==========================================
    // Connection Check Command
    // ==========================================
    private boolean handleConnection(CommandSender sender, String[] args) {
        Connection conn = getConnectionListener();
        if (conn == null) {
            sender.sendMessage(colorize("&cError: Connection listener not found. Try /buildffa reload"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(colorize("&8&m----------------------------------"));
            sender.sendMessage(colorize("&6&lConnection Check Status"));
            sender.sendMessage(colorize("&7Enabled: " + (conn.isEnabled() ? "&aYES" : "&cNO")));
            sender.sendMessage(colorize("&7Ping Threshold: &e" + conn.getPingThreshold() + "ms"));
            sender.sendMessage(colorize("&7Usage: &e/buildffa cc on|off|toggle"));
            sender.sendMessage(colorize("&7Usage: &e/buildffa cc bypass <player>"));
            sender.sendMessage(colorize("&7Usage: &e/buildffa cc unbypass <player>"));
            sender.sendMessage(colorize("&7Usage: &e/buildffa cc forceaddping <player> <ping>"));
            sender.sendMessage(colorize("&7Usage: &e/buildffa cc ping <player> default"));
            sender.sendMessage(colorize("&8&m----------------------------------"));
            return true;
        }

        String arg = args[1].toLowerCase();

        if (arg.equals("on")) {
            conn.setEnabled(true);
            sender.sendMessage(colorize("&aConnection check &lENABLED&a."));
            return true;
        }

        if (arg.equals("off")) {
            conn.setEnabled(false);
            sender.sendMessage(colorize("&cConnection check &lDISABLED&c."));
            return true;
        }

        if (arg.equals("toggle")) {
            boolean state = !conn.isEnabled();
            conn.setEnabled(state);
            sender.sendMessage(colorize(state
                    ? "&aConnection check &lENABLED&a."
                    : "&cConnection check &lDISABLED&c."));
            return true;
        }

        if (arg.equals("bypass")) {
            if (args.length < 3) {
                sender.sendMessage(colorize("&cUsage: /buildffa cc bypass <player>"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(colorize("&cPlayer not found: &e" + args[2]));
                return true;
            }
            conn.addBypass(target.getUniqueId());
            sender.sendMessage(colorize("&a" + target.getName() + " is now bypassing connection check."));
            return true;
        }

        if (arg.equals("unbypass")) {
            if (args.length < 3) {
                sender.sendMessage(colorize("&cUsage: /buildffa cc unbypass <player>"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(colorize("&cPlayer not found: &e" + args[2]));
                return true;
            }
            conn.removeBypass(target.getUniqueId());
            sender.sendMessage(colorize("&a" + target.getName() + " is no longer bypassing."));
            return true;
        }

        // ==========================================
        //  /buildffa cc forceaddping <player> <ping>
        // ==========================================
        if (arg.equals("forceaddping")) {
            if (args.length < 4) {
                sender.sendMessage(colorize("&cUsage: /buildffa cc forceaddping <player> <ping>"));
                return true;
            }

            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(colorize("&cPlayer not found: &e" + args[2]));
                return true;
            }

            int pingAmount;
            try {
                pingAmount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(colorize("&cInvalid ping amount: &e" + args[3]));
                return true;
            }

            if (pingAmount < 0) {
                sender.sendMessage(colorize("&cPing amount cannot be negative."));
                return true;
            }

            int realPing = getRealPing(target);
            if (pingAmount <= realPing) {
                sender.sendMessage(colorize("&cThe forced ping must be higher than the player's current ping."));
                sender.sendMessage(colorize("&7" + target.getName() + "'s current ping: &e" + realPing + "ms"));
                return true;
            }

            if (pingAmount < conn.getPingThreshold()) {
                sender.sendMessage(colorize("&cThe forced ping must be at least the threshold (&e" + conn.getPingThreshold() + "ms&c)."));
                return true;
            }

            conn.setForcedPing(target.getUniqueId(), pingAmount);
            sender.sendMessage(colorize("&aForced ping for &e" + target.getName() + " &aset to &e" + pingAmount + "ms&a."));
            sender.sendMessage(colorize("&7Real ping: &e" + realPing + "ms &7| Threshold: &e" + conn.getPingThreshold() + "ms"));
            return true;
        }

        // ==========================================
        //  /buildffa cc ping <player> default
        // ==========================================
        if (arg.equals("ping")) {
            if (args.length < 4) {
                sender.sendMessage(colorize("&cUsage: /buildffa cc ping <player> default"));
                return true;
            }

            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(colorize("&cPlayer not found: &e" + args[2]));
                return true;
            }

            String mode = args[3].toLowerCase();
            if (!mode.equals("default")) {
                sender.sendMessage(colorize("&cUsage: /buildffa cc ping <player> default"));
                return true;
            }

            if (!conn.hasForcedPing(target.getUniqueId())) {
                sender.sendMessage(colorize("&e" + target.getName() + " &7does not have a forced ping."));
                return true;
            }

            conn.clearForcedPing(target.getUniqueId());
            conn.removeBypass(target.getUniqueId());
            sender.sendMessage(colorize("&aForced ping removed for &e" + target.getName() + "&a. Using real ping now."));
            return true;
        }

        sender.sendMessage(colorize("&cUnknown argument. Use /buildffa cc"));
        return true;
    }

    private int getRealPing(Player player) {
        try {
            Object craftPlayer = player.getClass().getMethod("getHandle").invoke(player);
            return ((Integer) craftPlayer.getClass().getField("ping").get(craftPlayer)).intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private Connection getConnectionListener() {
        ArrayList<RegisteredListener> listeners = HandlerList.getRegisteredListeners(this.plugin);
        for (RegisteredListener rl : listeners) {
            Listener l = rl.getListener();
            if (l instanceof Connection) {
                return (Connection) l;
            }
        }
        return null;
    }

    // ==========================================
    // Stats Command (with add / reset subcommands)
    // ==========================================
    private boolean handleStats(CommandSender sender, String[] args) {
        // ==========================================
        //  /buildffa stats add <kill|kdr|ks|death> <player> <amount>
        // ==========================================
        if (args.length >= 2 && args[1].equalsIgnoreCase("add")) {
            if (args.length < 5) {
                sender.sendMessage(colorize("&cUsage: /buildffa stats add <kill|kdr|ks|death> <player> <amount>"));
                return true;
            }

            String stat = args[2].toLowerCase();
            Player target = Bukkit.getPlayer(args[3]);
            if (target == null) {
                sender.sendMessage(colorize("&cPlayer not found: &e" + args[3]));
                return true;
            }

            int amount;
            try {
                amount = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                sender.sendMessage(colorize("&cInvalid amount: &e" + args[4]));
                return true;
            }

            PlayerData data = this.database.getPlayer(target.getUniqueId());
            if (data == null) {
                data = this.database.loadPlayer(target.getUniqueId());
            }
            if (data == null) {
                sender.sendMessage(colorize("&cNo data found for &e" + target.getName()));
                return true;
            }

            String statName;
            if (stat.equals("kill") || stat.equals("kills")) {
                data.setKills(data.getKills() + amount);
                statName = "kills";
            } else if (stat.equals("death") || stat.equals("deaths")) {
                data.setDeaths(data.getDeaths() + amount);
                statName = "deaths";
            } else if (stat.equals("ks") || stat.equals("killstreak") || stat.equals("streak")) {
                data.setKillstreak(data.getKillstreak() + amount);
                if (data.getKillstreak() > data.getBestKillstreak()) {
                    data.setBestKillstreak(data.getKillstreak());
                }
                statName = "killstreak";
            } else if (stat.equals("kdr")) {
                data.setKills(data.getKills() + amount);
                statName = "kdr (via kills)";
            } else {
                sender.sendMessage(colorize("&cInvalid stat: &e" + stat));
                sender.sendMessage(colorize("&7Valid stats: &ekill&7, &ekdr&7, &eks&7, &edeath"));
                return true;
            }

            this.database.savePlayer(data);
            sender.sendMessage(colorize("&aAdded &e" + amount + " &ato &e" + target.getName() + "'s &e" + statName + "&a."));
            sender.sendMessage(colorize("&7Kills: &a" + data.getKills()
                    + " &7| Deaths: &c" + data.getDeaths()
                    + " &7| KS: &b" + data.getKillstreak()
                    + " &7| KDR: &e" + String.format("%.2f", data.getKDR())));
            return true;
        }

        // ==========================================
        //  /buildffa stats reset <kill|ks|death> <player>
        // ==========================================
        if (args.length >= 2 && args[1].equalsIgnoreCase("reset")) {
            if (args.length < 4) {
                sender.sendMessage(colorize("&cUsage: /buildffa stats reset <kill|ks|death> <player>"));
                return true;
            }

            String stat = args[2].toLowerCase();
            Player target = Bukkit.getPlayer(args[3]);
            if (target == null) {
                sender.sendMessage(colorize("&cPlayer not found: &e" + args[3]));
                return true;
            }

            PlayerData data = this.database.getPlayer(target.getUniqueId());
            if (data == null) {
                data = this.database.loadPlayer(target.getUniqueId());
            }
            if (data == null) {
                sender.sendMessage(colorize("&cNo data found for &e" + target.getName()));
                return true;
            }

            String statName;
            if (stat.equals("kill") || stat.equals("kills")) {
                data.setKills(0);
                statName = "kills";
            } else if (stat.equals("death") || stat.equals("deaths")) {
                data.setDeaths(0);
                statName = "deaths";
            } else if (stat.equals("ks") || stat.equals("killstreak") || stat.equals("streak")) {
                data.setKillstreak(0);
                data.setBestKillstreak(0);
                statName = "killstreak & best killstreak";
            } else {
                sender.sendMessage(colorize("&cInvalid stat: &e" + stat));
                sender.sendMessage(colorize("&7Valid stats: &ekill&7, &eks&7, &edeath"));
                return true;
            }

            this.database.savePlayer(data);
            sender.sendMessage(colorize("&aReset &e" + statName + " &afor &e" + target.getName() + "&a."));
            return true;
        }

        // ---- Default /stats <player> ----
        Player target;

        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(colorize("&cPlayer not found: &e" + args[1]));
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(colorize("&cUsage from console: /buildffa stats <player>"));
                return true;
            }
            target = (Player) sender;
        }

        PlayerData data = this.database.getPlayer(target.getUniqueId());
        if (data == null) {
            sender.sendMessage(colorize("&cNo data found for &e" + target.getName()));
            return true;
        }

        sender.sendMessage(colorize("&8&m----------------------------------"));
        sender.sendMessage(colorize("&6&lStats &7- &f" + data.getName()));
        sender.sendMessage(colorize("&7Kills: &a" + data.getKills()));
        sender.sendMessage(colorize("&7Deaths: &c" + data.getDeaths()));
        sender.sendMessage(colorize("&7KDR: &e" + String.format("%.2f", data.getKDR())));
        sender.sendMessage(colorize("&7Killstreak: &b" + data.getKillstreak()));
        sender.sendMessage(colorize("&7Best Killstreak: &b" + data.getBestKillstreak()));
        sender.sendMessage(colorize("&8&m----------------------------------"));
        return true;
    }

    // ==========================================
    // Force Killstreak Reward
    // /buildffa forceksreward <ks>
    // Gives the reward bundle for that killstreak level
    // to the command sender (must be a player).
    // ==========================================
    private boolean handleForceKsReward(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(colorize("&cOnly players can use this command."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(colorize("&cUsage: /buildffa forceksreward <ks>"));
            sender.sendMessage(colorize("&7Example: &e/buildffa forceksreward 10"));
            return true;
        }

        int ks;
        try {
            ks = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(colorize("&cInvalid killstreak value: &e" + args[1]));
            return true;
        }

        if (ks < 1) {
            sender.sendMessage(colorize("&cKillstreak must be at least 1."));
            return true;
        }

        Player player = (Player) sender;

        // Check if the reward is enabled
        if (!this.plugin.getConfig().getBoolean("killstreak-rewards.enabled", true)) {
            sender.sendMessage(colorize("&cKillstreak rewards are disabled in config.yml."));
            return true;
        }

        // Resolve the level (supports repeat-from-12 wrap)
        boolean repeatFrom12 = this.plugin.getConfig().getBoolean("killstreak-rewards.repeat-from-12", true);
        int level = resolveRewardLevel(ks, repeatFrom12);

        if (level == -1) {
            sender.sendMessage(colorize("&cNo reward found for killstreak &e" + ks + "&c."));
            return true;
        }

        String rewardString = this.plugin.getConfig()
                .getString("killstreak-rewards.rewards." + level, "");

        if (rewardString == null || rewardString.isEmpty()) {
            sender.sendMessage(colorize("&cNo reward configured for killstreak &e" + level + "&c."));
            return true;
        }

        // Give the reward
        boolean gaveAny = giveRewardBundle(player, rewardString);

        if (!gaveAny) {
            sender.sendMessage(colorize("&cFailed to give reward for killstreak &e" + ks + "&c."));
            return true;
        }

        sender.sendMessage(colorize("&a&l★ &aKillstreak &e" + ks + " &a— reward received!"));
        try {
            player.playSound(player.getLocation(), org.bukkit.Sound.LEVEL_UP, 1.0F, 1.5F);
        } catch (Throwable ignored) {}

        return true;
    }

    // ==========================================
    // Resolve which reward level to use
    // (handles repeat-from-12 wrapping)
    // ==========================================
    private int resolveRewardLevel(int streak, boolean repeatFrom12) {
        // Exact match first
        if (this.plugin.getConfig().contains("killstreak-rewards.rewards." + streak)) {
            return streak;
        }

        // Wrap around 12 if enabled
        if (repeatFrom12 && streak > 12) {
            int wrapped = ((streak - 1) % 12) + 1;
            if (this.plugin.getConfig().contains("killstreak-rewards.rewards." + wrapped)) {
                return wrapped;
            }
        }

        // Fall back to the highest configured level <= streak
        for (int i = streak - 1; i >= 1; i--) {
            if (this.plugin.getConfig().contains("killstreak-rewards.rewards." + i)) {
                return i;
            }
        }

        return -1;
    }

    // ==========================================
    // Give a reward bundle (string like "gapple:1 fb:1 speed:2")
    // Returns true if at least one item was given.
    // ==========================================
    private boolean giveRewardBundle(Player player, String rewardString) {
        String[] parts = rewardString.split(" ");
        boolean gaveAny = false;

        for (String part : parts) {
            if (part.isEmpty()) continue;

            String[] split = part.split(":");
            if (split.length != 2) continue;

            String itemName = split[0].toLowerCase();
            int amount;
            try {
                amount = Integer.parseInt(split[1]);
            } catch (NumberFormatException e) {
                continue;
            }
            if (amount <= 0) continue;

            org.bukkit.inventory.ItemStack item = buildRewardItem(itemName, amount);
            if (item != null) {
                player.getInventory().addItem(item);
                gaveAny = true;
            }
        }

        return gaveAny;
    }

    // ==========================================
    // Build a reward item from its config name
    // ==========================================
    private org.bukkit.inventory.ItemStack buildRewardItem(String name, int amount) {
        if (name.equals("gapple") || name.equals("golden_apple") || name.equals("gap")) {
            return new org.bukkit.inventory.ItemStack(org.bukkit.Material.GOLDEN_APPLE, amount);
        }
        if (name.equals("fb") || name.equals("fireball") || name.equals("fire_charge")) {
            return new org.bukkit.inventory.ItemStack(org.bukkit.Material.FIREBALL, amount);
        }
        if (name.equals("perl") || name.equals("pearl") || name.equals("ender_pearl")) {
            return new org.bukkit.inventory.ItemStack(org.bukkit.Material.ENDER_PEARL, amount);
        }
        if (name.equals("feather")) {
            return new org.bukkit.inventory.ItemStack(org.bukkit.Material.FEATHER, amount);
        }
        if (name.equals("speed")) {
            return makeRewardPotion(1, amount);
        }
        if (name.equals("jump")) {
            return makeRewardPotion(2, amount);
        }
        return null;
    }

    /**
     * Build ONE potion. kind: 1 = Speed, 2 = Jump
     * amount is used as the level (not the count) to match
     * the existing Kill.java behavior.
     */
    private org.bukkit.inventory.ItemStack makeRewardPotion(int kind, int level) {
        org.bukkit.inventory.ItemStack potion = new org.bukkit.inventory.ItemStack(
                org.bukkit.Material.POTION, 1);

        short data;
        if (kind == 1) {
            if (level <= 1) data = 8194;      // Speed I
            else data = 8226;                 // Speed II
        } else {
            if (level <= 1) data = 8203;      // Jump I
            else if (level == 2) data = 8235; // Jump II
            else if (level == 3) data = 8267; // Jump III
            else if (level == 4) data = 8299; // Jump IV
            else data = 8331;                 // Jump V
        }

        potion.setDurability(data);
        return potion;
    }

    // ==========================================
    // Top Command
    // ==========================================
    private boolean handleTop(CommandSender sender, String[] args) {
        String type = "kills";
        int limit = 10;

        if (args.length >= 2) type = args[1].toLowerCase();
        if (args.length >= 3) {
            try {
                limit = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                limit = 10;
            }
        }
        if (limit < 1) limit = 1;
        if (limit > 20) limit = 20;

        List<PlayerData> top;

        if (type.equals("deaths")) {
            top = this.database.getTopDeaths(limit);
        } else if (type.equals("kdr")) {
            top = this.database.getTopKDR(limit);
        } else if (type.equals("killstreak") || type.equals("streak")) {
            top = this.database.getTopKillstreak(limit);
        } else {
            top = this.database.getTopKills(limit);
            type = "kills";
        }

        sender.sendMessage(colorize("&8&m----------------------------------"));
        sender.sendMessage(colorize("&6&lTop " + limit + " &7- &f" + type.toUpperCase()));
        sender.sendMessage(colorize("&8&m----------------------------------"));

        if (top.isEmpty()) {
            sender.sendMessage(colorize("&7No data yet."));
        } else {
            int rank = 1;
            for (PlayerData data : top) {
                String prefix;
                if (rank == 1) prefix = "&6&l#1 ";
                else if (rank == 2) prefix = "&7&l#2 ";
                else if (rank == 3) prefix = "&c&l#3 ";
                else prefix = "&7#" + rank + " ";

                String value;
                if (type.equals("deaths")) {
                    value = "&c" + data.getDeaths() + " deaths";
                } else if (type.equals("kdr")) {
                    value = "&e" + String.format("%.2f", data.getKDR()) + " KDR";
                } else if (type.equals("killstreak")) {
                    value = "&b" + data.getBestKillstreak() + " KS";
                } else {
                    value = "&a" + data.getKills() + " kills";
                }

                sender.sendMessage(colorize(prefix + "&f" + data.getName() + " &8- " + value));
                rank++;
            }
        }

        sender.sendMessage(colorize("&8&m----------------------------------"));
        return true;
    }

    // ==========================================
    // Reset Stats Command
    // ==========================================
    private boolean handleResetStats(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(colorize("&cUsage: /buildffa resetstats <player>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(colorize("&cPlayer not found: &e" + args[1]));
            return true;
        }

        PlayerData data = this.database.getPlayer(target.getUniqueId());
        if (data == null) {
            sender.sendMessage(colorize("&cNo data found."));
            return true;
        }

        data.setKills(0);
        data.setDeaths(0);
        data.setKillstreak(0);
        data.setBestKillstreak(0);
        this.database.savePlayer(data);

        sender.sendMessage(colorize("&aReset stats for &e" + target.getName()));
        target.sendMessage(colorize("&cYour stats have been reset by an admin."));
        return true;
    }

    // ==========================================
    // Scoreboard Command
    // ==========================================
    private boolean handleScoreboard(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(colorize("&cOnly players can use the scoreboard command."));
            return true;
        }
        Player player = (Player) sender;

        if (args.length >= 2 && args[1].equalsIgnoreCase("reload")) {
            if (this.scoreboardManager == null) {
                player.sendMessage(colorize("&cError: ScoreboardManager not found."));
                return true;
            }
            this.scoreboardManager.reloadConfig();
            player.sendMessage(colorize("&aScoreboard configuration reloaded."));
            return true;
        }

        if (this.scoreboardManager == null) {
            player.sendMessage(colorize("&cError: ScoreboardManager not found."));
            return true;
        }

        boolean nowVisible = this.scoreboardManager.toggleScoreboard(player);
        if (nowVisible) {
            player.sendMessage(colorize("&aScoreboard &lENABLED&a."));
        } else {
            player.sendMessage(colorize("&cScoreboard &lDISABLED&c."));
        }
        return true;
    }

    // ==========================================
    // YPvP Command
    // ==========================================
    private boolean handleYPvP(CommandSender sender, String[] args) {
        YPvP ypvp = getYPvPListener();
        if (ypvp == null) {
            sender.sendMessage(colorize("&cError: YPvP listener not found. Try /buildffa reload"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(colorize("&8&m----------------------------------"));
            sender.sendMessage(colorize("&6&lYPvP Status"));
            sender.sendMessage(colorize("&7Enabled: " + (ypvp.isEnabled() ? "&aYES" : "&cNO")));
            sender.sendMessage(colorize("&7Y-Level: &e" + ypvp.getYPvPLimit()));
            sender.sendMessage(colorize("&7Usage: &e/buildffa ypvp [y] &7- Set Y level"));
            sender.sendMessage(colorize("&7Usage: &e/buildffa ypvp toggle &7- Enable/Disable"));
            sender.sendMessage(colorize("&7Usage: &e/buildffa ypvp off &7- Disable"));
            sender.sendMessage(colorize("&8&m----------------------------------"));
            return true;
        }

        String arg = args[1].toLowerCase();

        if (arg.equals("toggle")) {
            boolean newState = !ypvp.isEnabled();
            ypvp.setEnabled(newState);
            sender.sendMessage(colorize(newState
                    ? "&aYPvP has been &lENABLED &aat Y >= &e" + ypvp.getYPvPLimit()
                    : "&cYPvP has been &lDISABLED"));
            return true;
        }

        if (arg.equals("off")) {
            ypvp.setEnabled(false);
            sender.sendMessage(colorize("&cYPvP has been &lDISABLED"));
            return true;
        }

        if (arg.equals("on")) {
            ypvp.setEnabled(true);
            sender.sendMessage(colorize("&aYPvP has been &lENABLED &aat Y >= &e" + ypvp.getYPvPLimit()));
            return true;
        }

        double y;
        try {
            y = Double.parseDouble(arg);
        } catch (NumberFormatException e) {
            sender.sendMessage(colorize("&cInvalid number: &e" + args[1]));
            sender.sendMessage(colorize("&7Usage: &e/buildffa ypvp [y]"));
            return true;
        }

        ypvp.setYPvPLimit(y);
        ypvp.setEnabled(true);

        sender.sendMessage(colorize("&aYPvP Y-level set to &e" + y + " &aand &lENABLED&a."));
        sender.sendMessage(colorize("&7Players at Y >= &e" + y + " &7cannot hit or be hit."));
        return true;
    }

    private YPvP getYPvPListener() {
        ArrayList<RegisteredListener> listeners = HandlerList.getRegisteredListeners(this.plugin);
        for (RegisteredListener rl : listeners) {
            Listener l = rl.getListener();
            if (l instanceof YPvP) {
                return (YPvP) l;
            }
        }
        return null;
    }

    // ==========================================
    // YPearl Command
    // ==========================================
    private boolean handleYPearl(CommandSender sender, String[] args) {
        YPearl ypearl = getYPearlListener();
        if (ypearl == null) {
            sender.sendMessage(colorize("&cError: YPearl listener not found. Try /buildffa reload"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(colorize("&8&m----------------------------------"));
            sender.sendMessage(colorize("&6&lYPearl Status"));
            sender.sendMessage(colorize("&7Enabled: " + (ypearl.isEnabled() ? "&aYES" : "&cNO")));
            sender.sendMessage(colorize("&7Y-Level: &e" + ypearl.getYPearlLimit()));
            sender.sendMessage(colorize("&7Usage: &e/buildffa ypearl [y] &7- Set Y level"));
            sender.sendMessage(colorize("&7Usage: &e/buildffa ypearl toggle &7- Enable/Disable"));
            sender.sendMessage(colorize("&7Usage: &e/buildffa ypearl off &7- Disable"));
            sender.sendMessage(colorize("&8&m----------------------------------"));
            return true;
        }

        String arg = args[1].toLowerCase();

        if (arg.equals("toggle")) {
            boolean newState = !ypearl.isEnabled();
            ypearl.setEnabled(newState);
            sender.sendMessage(colorize(newState
                    ? "&aYPearl has been &lENABLED &aat Y >= &e" + ypearl.getYPearlLimit()
                    : "&cYPearl has been &lDISABLED"));
            return true;
        }

        if (arg.equals("off")) {
            ypearl.setEnabled(false);
            sender.sendMessage(colorize("&cYPearl has been &lDISABLED"));
            return true;
        }

        if (arg.equals("on")) {
            ypearl.setEnabled(true);
            sender.sendMessage(colorize("&aYPearl has been &lENABLED &aat Y >= &e" + ypearl.getYPearlLimit()));
            return true;
        }

        double y;
        try {
            y = Double.parseDouble(arg);
        } catch (NumberFormatException e) {
            sender.sendMessage(colorize("&cInvalid number: &e" + args[1]));
            sender.sendMessage(colorize("&7Usage: &e/buildffa ypearl [y]"));
            return true;
        }

        ypearl.setYPearlLimit(y);
        ypearl.setEnabled(true);

        sender.sendMessage(colorize("&aYPearl Y-level set to &e" + y + " &aand &lENABLED&a."));
        sender.sendMessage(colorize("&7Players at Y >= &e" + y + " &7cannot throw Ender Pearls."));
        return true;
    }

    private YPearl getYPearlListener() {
        ArrayList<RegisteredListener> listeners = HandlerList.getRegisteredListeners(this.plugin);
        for (RegisteredListener rl : listeners) {
            Listener l = rl.getListener();
            if (l instanceof YPearl) {
                return (YPearl) l;
            }
        }
        return null;
    }

    // ==========================================
    // Build Mode
    // ==========================================
    private boolean handleBuildMode(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(colorize("&cOnly players can use this command."));
            return true;
        }

        Player player = (Player) sender;

        String action = "toggle";
        if (args.length >= 2) {
            action = args[1].toLowerCase();
        }

        if (action.equals("on")) {
            BuildModeManager.setBuildMode(player, true);
            sendActionBar(player, "&fYou are currently &aBUILD MODE &7(high-limit bypassed)");
            player.sendMessage(colorize("&aBuild Mode &lENABLED&a. You can now build above the high limit."));
            return true;
        }

        if (action.equals("off")) {
            BuildModeManager.setBuildMode(player, false);
            sendActionBar(player, "&fYou are currently &cNORMAL MODE");
            player.sendMessage(colorize("&cBuild Mode &lDISABLED&c. High limit is now enforced."));
            return true;
        }

        if (action.equals("toggle")) {
            boolean nowEnabled = BuildModeManager.toggleBuildMode(player);
            if (nowEnabled) {
                sendActionBar(player, "&fYou are currently &aBUILD MODE &7(high-limit bypassed)");
                player.sendMessage(colorize("&aBuild Mode &lENABLED&a."));
            } else {
                sendActionBar(player, "&fYou are currently &cNORMAL MODE");
                player.sendMessage(colorize("&cBuild Mode &lDISABLED&c."));
            }
            return true;
        }

        player.sendMessage(colorize("&cUsage: /buildffa buildmode <on|off|toggle>"));
        return true;
    }

    private void sendActionBar(Player player, String message) {
        try {
            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            String nmsVersion = packageName.substring(packageName.lastIndexOf('.') + 1);

            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + nmsVersion + ".entity.CraftPlayer");
            Object craftPlayer = craftPlayerClass.cast(player);
            Object entityPlayer = craftPlayerClass.getMethod("getHandle").invoke(craftPlayer);
            Object playerConnection = entityPlayer.getClass().getField("playerConnection").get(entityPlayer);

            Class<?> chatComponentClass = Class.forName("net.minecraft.server." + nmsVersion + ".ChatComponentText");
            Object chatComponent = chatComponentClass.getConstructor(String.class).newInstance(colorize(message));

            Class<?> packetChatClass = Class.forName("net.minecraft.server." + nmsVersion + ".PacketPlayOutChat");
            Class<?> iChatBaseClass = Class.forName("net.minecraft.server." + nmsVersion + ".IChatBaseComponent");

            Object packet = packetChatClass.getConstructor(iChatBaseClass, byte.class).newInstance(chatComponent, (byte) 2);

            Class<?> packetClass = Class.forName("net.minecraft.server." + nmsVersion + ".Packet");
            Method sendPacketMethod = playerConnection.getClass().getMethod("sendPacket", packetClass);

            sendPacketMethod.invoke(playerConnection, packet);
        } catch (Exception e) {
            player.sendMessage(colorize(message));
        }
    }

    // ==========================================
    // Kit Editor
    // ==========================================
    private boolean handleKitEditor(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(colorize("&cOnly players can use the Kit Editor."));
            return true;
        }
        Player player = (Player) sender;

        if (args.length >= 2 && args[1].equalsIgnoreCase("reset")) {
            this.kitEditor.resetKit(player);
            player.sendMessage(colorize("&aYour kit has been reset to the default kit."));
            return true;
        }

        this.kitEditor.openKitEditorGUI(player);
        return true;
    }

    // ==========================================
    // Set Void
    // ==========================================
    private boolean handleSetVoid(CommandSender sender, String[] args) {
        double y;
        if (args.length >= 2) {
            try {
                y = Double.parseDouble(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(colorize("&cInvalid number: &e" + args[1]));
                sender.sendMessage(colorize("&7Usage: &e/buildffa setvoid [y]"));
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(colorize("&cYou must be a player to use setvoid without a value."));
                sender.sendMessage(colorize("&7From console: &e/buildffa setvoid [y]"));
                return true;
            }
            Player player = (Player) sender;
            y = player.getLocation().getY();
        }

        FileConfiguration config = this.plugin.getConfig();
        config.set("kill-height", Double.valueOf(y));
        this.plugin.saveConfig();

        reloadListeners();

        sender.sendMessage(colorize("&aVoid kill height set to &e" + y + " &a(Y level)."));
        return true;
    }

    // ==========================================
    // Set High Limit
    // ==========================================
    private boolean handleSetHighLimit(CommandSender sender, String[] args) {
        double y;
        if (args.length >= 2) {
            try {
                y = Double.parseDouble(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(colorize("&cInvalid number: &e" + args[1]));
                sender.sendMessage(colorize("&7Usage: &e/buildffa sethighlimit [y]"));
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(colorize("&cYou must be a player to use sethighlimit without a value."));
                sender.sendMessage(colorize("&7From console: &e/buildffa sethighlimit [y]"));
                return true;
            }
            Player player = (Player) sender;
            y = player.getLocation().getY();
        }

        FileConfiguration config = this.plugin.getConfig();
        config.set("high-limit", Double.valueOf(y));
        this.plugin.saveConfig();

        reloadListeners();

        sender.sendMessage(colorize("&aHigh limit set to &e" + y + " &a(Y level)."));
        return true;
    }

    // ==========================================
    // Set Spawn
    // ==========================================
    private boolean handleSetSpawn(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(colorize("&cOnly players can use setspawn."));
            return true;
        }

        Player player = (Player) sender;
        Location loc = player.getLocation();

        SpawnManager spawnManager = new SpawnManager(this.plugin);
        spawnManager.setSpawn(loc);

        String world = loc.getWorld().getName();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        player.sendMessage(colorize("&aRespawn point set to &e" + world + " " + x + " " + y + " " + z + "&a."));
        return true;
    }

    // ==========================================
    // Reload Listeners
    // ==========================================
    private void reloadListeners() {
        ArrayList<RegisteredListener> listeners = HandlerList.getRegisteredListeners(this.plugin);
        for (RegisteredListener rl : listeners) {
            Listener l = rl.getListener();
            if (l instanceof Void) ((Void) l).reloadConfig();
            if (l instanceof High) ((High) l).reloadConfig();
            if (l instanceof YPvP) ((YPvP) l).reloadConfig();
            if (l instanceof YPearl) ((YPearl) l).reloadConfig();
            if (l instanceof Connection) ((Connection) l).reloadConfig();
            if (l instanceof Blocks) ((Blocks) l).reloadConfig();
        }
    }

    // ==========================================
    // Creator
    // ==========================================
    private boolean handleCreator(CommandSender sender) {
        sender.sendMessage(colorize("&8&m----------------------------------"));
        sender.sendMessage(colorize("&6&lBuildFFA &7- &fCreated by &bMuvixo"));
        sender.sendMessage(colorize("&7Plugin author: &fVanSaMa"));
        sender.sendMessage(colorize("&7Version: &f4.0 (1.8.8)"));
        sender.sendMessage(colorize("&7Made by &fPixelValley"));
        sender.sendMessage(colorize("&8&m----------------------------------"));
        return true;
    }

    // ==========================================
    // Reload
    // ==========================================
    private boolean handleReload(CommandSender sender) {
        this.plugin.reloadConfig();
        if (this.scoreboardManager != null) {
            this.scoreboardManager.reloadConfig();
        }
        reloadListeners();
        sender.sendMessage(colorize("&aBuildFFA configuration reloaded."));
        return true;
    }

    // ==========================================
    // Help (permission-aware)
    // ==========================================
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(colorize("&8&m----------------------------------"));
        sender.sendMessage(colorize("&6&lBuildFFA &7- &fCommands"));
        sender.sendMessage(colorize("&8&m----------------------------------"));

        // ============================================================
        //  PLAYER COMMANDS
        // ============================================================

        if (sender.hasPermission(getPerm("kiteditor", "buildffa.kiteditor"))) {
            sender.sendMessage(colorize("&e/buildffa kiteditor &7- Open the Kit Editor GUI"));
            sender.sendMessage(colorize("&e/buildffa kiteditor reset &7- Reset your kit"));
        }

        if (sender.hasPermission(getPerm("scoreboard-toggle", "buildffa.scoreboard.toggle"))) {
            sender.sendMessage(colorize("&e/buildffa sb &7- Toggle scoreboard visibility"));
        }

        // Stats (view) — available to everyone
        sender.sendMessage(colorize("&e/buildffa stats [player] &7- Show player stats"));
        sender.sendMessage(colorize("&e/buildffa top [kills|deaths|kdr|streak] [limit] &7- Show top players"));
        sender.sendMessage(colorize("&e/buildffa creator &7- Show plugin credits"));

        // ============================================================
        //  ADMIN COMMANDS
        // ============================================================

        boolean isAdmin = sender.hasPermission(getPerm("setvoid", "buildffa.setvoid"))
                || sender.hasPermission(getPerm("sethighlimit", "buildffa.sethighlimit"))
                || sender.hasPermission(getPerm("setspawn", "buildffa.setspawn"))
                || sender.hasPermission(getPerm("buildmode", "buildffa.buildmode"))
                || sender.hasPermission(getPerm("ypvp", "buildffa.ypvp"))
                || sender.hasPermission(getPerm("ypearl", "buildffa.ypearl"))
                || sender.hasPermission(getPerm("resetstats", "buildffa.resetstats"))
                || sender.hasPermission(getPerm("connectioncheck", "buildffa.connection"))
                || sender.hasPermission(getPerm("reload", "buildffa.reload"));

        if (isAdmin) {
            sender.sendMessage(colorize("&8&m----------------------------------"));
            sender.sendMessage(colorize("&c&lAdmin Commands"));
            sender.sendMessage(colorize("&8&m----------------------------------"));

            if (sender.hasPermission(getPerm("buildmode", "buildffa.buildmode"))) {
                sender.sendMessage(colorize("&e/buildffa buildmode &7- Toggle Build Mode"));
                sender.sendMessage(colorize("&e/buildffa buildmode on|off &7- Set Build Mode"));
            }

            if (sender.hasPermission(getPerm("setvoid", "buildffa.setvoid"))) {
                sender.sendMessage(colorize("&e/buildffa setvoid &7- Set void Y to your current Y"));
                sender.sendMessage(colorize("&e/buildffa setvoid [y] &7- Set void Y to a specific value"));
            }

            if (sender.hasPermission(getPerm("sethighlimit", "buildffa.sethighlimit"))) {
                sender.sendMessage(colorize("&e/buildffa sethighlimit &7- Set high limit to your current Y"));
                sender.sendMessage(colorize("&e/buildffa sethighlimit [y] &7- Set high limit to a value"));
            }

            if (sender.hasPermission(getPerm("ypvp", "buildffa.ypvp"))) {
                sender.sendMessage(colorize("&e/buildffa ypvp [y] &7- Set YPvP Y level & enable"));
                sender.sendMessage(colorize("&e/buildffa ypvp toggle &7- Enable/Disable YPvP"));
                sender.sendMessage(colorize("&e/buildffa ypvp off &7- Disable YPvP"));
            }

            if (sender.hasPermission(getPerm("ypearl", "buildffa.ypearl"))) {
                sender.sendMessage(colorize("&e/buildffa ypearl [y] &7- Set YPearl Y level & enable"));
                sender.sendMessage(colorize("&e/buildffa ypearl toggle &7- Enable/Disable YPearl"));
                sender.sendMessage(colorize("&e/buildffa ypearl off &7- Disable YPearl"));
            }

            if (sender.hasPermission(getPerm("setspawn", "buildffa.setspawn"))) {
                sender.sendMessage(colorize("&e/buildffa setspawn &7- Set respawn point to your location"));
            }

            if (sender.hasPermission(getPerm("resetstats", "buildffa.resetstats"))) {
                sender.sendMessage(colorize("&e/buildffa stats add <kill|kdr|ks|death> <player> <amount> &7- Add to a stat"));
                sender.sendMessage(colorize("&e/buildffa stats reset <kill|ks|death> <player> &7- Reset a stat"));
                sender.sendMessage(colorize("&e/buildffa resetstats <player> &7- Reset all player stats"));
                sender.sendMessage(colorize("&e/buildffa forceksreward <ks> &7- Give yourself the reward for that killstreak"));
            }

            if (sender.hasPermission(getPerm("connectioncheck", "buildffa.connection"))) {
                sender.sendMessage(colorize("&e/buildffa cc &7- Connection check status"));
                sender.sendMessage(colorize("&e/buildffa cc on|off|toggle &7- Toggle connection check"));
                sender.sendMessage(colorize("&e/buildffa cc bypass <player> &7- Bypass a player"));
                sender.sendMessage(colorize("&e/buildffa cc unbypass <player> &7- Remove bypass from a player"));
                sender.sendMessage(colorize("&e/buildffa cc forceaddping <player> <ping> &7- Force a ping value"));
                sender.sendMessage(colorize("&e/buildffa cc ping <player> default &7- Remove forced ping"));
            }

            if (sender.hasPermission(getPerm("reload", "buildffa.reload"))) {
                sender.sendMessage(colorize("&e/buildffa reload &7- Reload configuration"));
                sender.sendMessage(colorize("&e/buildffa sb reload &7- Reload scoreboard.yml"));
            }
        }

        sender.sendMessage(colorize("&8&m----------------------------------"));
    }

    private String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}