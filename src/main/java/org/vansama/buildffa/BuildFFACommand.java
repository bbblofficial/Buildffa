package org.vansama.buildffa;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
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
  
  // ذخیره وضعیت Build Mode بازیکنان
  private final Set<UUID> buildModePlayers = new HashSet<UUID>();
  
  public BuildFFACommand(JavaPlugin plugin, KitEditor kitEditor) {
    this.plugin = plugin;
    this.kitEditor = kitEditor;
  }
  
  private String getPerm(String action, String defaultPerm) {
    return this.plugin.getConfig().getString("permissions." + action, defaultPerm);
  }
  
  private void sendNoPerm(CommandSender sender) {
    String msg = this.plugin.getConfig().getString("messages.no-permission", "&cYou do not have permission to do this.");
    sender.sendMessage(colorize(msg));
  }
  
  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (args.length == 0) {
      sendHelp(sender);
      return true;
    }
    
    String sub = args[0].toLowerCase();
    
    if (sub.equals("kiteditor")) {
      return handleKitEditor(sender, args);
    }
    if (sub.equals("setvoid")) {
      return handleSetVoid(sender, args);
    }
    if (sub.equals("sethighlimit")) {
      return handleSetHighLimit(sender, args);
    }
    if (sub.equals("setspawn")) {
      return handleSetSpawn(sender);
    }
    if (sub.equals("buildmode")) {
      return handleBuildMode(sender);
    }
    if (sub.equals("creator")) {
      return handleCreator(sender);
    }
    if (sub.equals("reload")) {
      return handleReload(sender);
    }
    if (sub.equals("help")) {
      sendHelp(sender);
      return true;
    }
    sender.sendMessage(colorize("&cUnknown subcommand. Use /buildffa help"));
    return true;
  }

  // ==========================================
  // Toggle Build Mode (Decorative)
  // ==========================================
  private boolean handleBuildMode(CommandSender sender) {
    if (!(sender instanceof Player)) {
      sender.sendMessage(colorize("&cOnly players can use this command."));
      return true;
    }
    if (!sender.hasPermission(getPerm("buildmode", "buildffa.buildmode"))) {
      sendNoPerm(sender);
      return true;
    }
    
    Player player = (Player) sender;
    UUID uuid = player.getUniqueId();
    
    if (this.buildModePlayers.contains(uuid)) {
      // خاموش کردن
      this.buildModePlayers.remove(uuid);
      sendActionBar(player, "&fYou are currently &aNORMAL MODE");
    } else {
      // روشن کردن
      this.buildModePlayers.add(uuid);
      sendActionBar(player, "&fYou are currently &cBUILD MODE");
    }
    return true;
  }

  // ==========================================
  // متد ارسال پیام اکشن بار با Reflection برای 1.8.x
  // ==========================================
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
      
      // موقعیت 2 در پکت چت یعنی Action Bar
      Object packet = packetChatClass.getConstructor(iChatBaseClass, byte.class).newInstance(chatComponent, (byte) 2);
      
      Class<?> packetClass = Class.forName("net.minecraft.server." + nmsVersion + ".Packet");
      Method sendPacketMethod = playerConnection.getClass().getMethod("sendPacket", packetClass);
      
      sendPacketMethod.invoke(playerConnection, packet);
    } catch (Exception e) {
      // اگر سرور پلاگین اکشن‌بار را ساپورت نکرد، پیام را در چت می‌فرستد
      player.sendMessage(colorize(message));
    }
  }

  private boolean handleKitEditor(CommandSender sender, String[] args) {
    if (!(sender instanceof Player)) {
      sender.sendMessage(colorize("&cOnly players can use the Kit Editor."));
      return true;
    }
    if (!sender.hasPermission(getPerm("kiteditor", "buildffa.kiteditor"))) {
      sendNoPerm(sender);
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
  
  private boolean handleSetVoid(CommandSender sender, String[] args) {
    if (!sender.hasPermission(getPerm("setvoid", "buildffa.setvoid"))) {
      sendNoPerm(sender);
      return true;
    }
    
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
  
  private boolean handleSetHighLimit(CommandSender sender, String[] args) {
    if (!sender.hasPermission(getPerm("sethighlimit", "buildffa.sethighlimit"))) {
      sendNoPerm(sender);
      return true;
    }
    
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
  
  private boolean handleSetSpawn(CommandSender sender) {
    if (!(sender instanceof Player)) {
      sender.sendMessage(colorize("&cOnly players can use setspawn."));
      return true;
    }
    if (!sender.hasPermission(getPerm("setspawn", "buildffa.setspawn"))) {
      sendNoPerm(sender);
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
  
  private void reloadListeners() {
    ArrayList<RegisteredListener> listeners = HandlerList.getRegisteredListeners(this.plugin);
    for (RegisteredListener rl : listeners) {
      Listener l = rl.getListener();
      if (l instanceof Void) {
        ((Void) l).reloadConfig();
      }
      if (l instanceof High) {
        ((High) l).reloadConfig();
      }
    }
  }
  
  private boolean handleCreator(CommandSender sender) {
    sender.sendMessage(colorize("&8&m----------------------------------"));
    sender.sendMessage(colorize("&6&lBuildFFA &7- &fCreated by &bMuvixo"));
    sender.sendMessage(colorize("&7Plugin author: &fVanSaMa"));
    sender.sendMessage(colorize("&7Version: &f4.0 (1.8.8)"));
    sender.sendMessage(colorize("&7Made by &fPixelValley"));
    sender.sendMessage(colorize("&8&m----------------------------------"));
    return true;
  }
  
  private boolean handleReload(CommandSender sender) {
    if (!sender.hasPermission(getPerm("reload", "buildffa.reload"))) {
      sendNoPerm(sender);
      return true;
    }
    this.plugin.reloadConfig();
    reloadListeners();
    sender.sendMessage(colorize("&aBuildFFA configuration reloaded."));
    return true;
  }
  
  private void sendHelp(CommandSender sender) {
    sender.sendMessage(colorize("&8&m----------------------------------"));
    sender.sendMessage(colorize("&6&lBuildFFA &7- &fCommands"));
    sender.sendMessage(colorize("&e/buildffa kiteditor &7- Open the Kit Editor GUI"));
    sender.sendMessage(colorize("&e/buildffa kiteditor reset &7- Reset your kit"));
    sender.sendMessage(colorize("&e/buildffa buildmode &7- Toggle Build Mode"));
    sender.sendMessage(colorize("&e/buildffa setvoid &7- Set void Y to your current Y"));
    sender.sendMessage(colorize("&e/buildffa setvoid [y] &7- Set void Y to a specific value"));
    sender.sendMessage(colorize("&e/buildffa sethighlimit &7- Set high limit to your current Y"));
    sender.sendMessage(colorize("&e/buildffa sethighlimit [y] &7- Set high limit to a value"));
    sender.sendMessage(colorize("&e/buildffa setspawn &7- Set respawn point to your location"));
    sender.sendMessage(colorize("&e/buildffa creator &7- Show plugin credits"));
    sender.sendMessage(colorize("&e/buildffa reload &7- Reload configuration"));
    sender.sendMessage(colorize("&8&m----------------------------------"));
  }
  
  private String colorize(String message) {
    return ChatColor.translateAlternateColorCodes('&', message);
  }
}