package org.vansama.buildffa;

import java.util.ArrayList;
import org.bukkit.ChatColor;
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
  
  public BuildFFACommand(JavaPlugin plugin, KitEditor kitEditor) {
    this.plugin = plugin;
    this.kitEditor = kitEditor;
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
  
  private boolean handleKitEditor(CommandSender sender, String[] args) {
    if (!(sender instanceof Player)) {
      sender.sendMessage(colorize("&cOnly players can use the Kit Editor."));
      return true;
    }
    if (!sender.hasPermission("buildffa.kiteditor")) {
      sender.sendMessage(colorize("&cYou do not have permission to use the Kit Editor."));
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
  
  /**
   * /buildffa setvoid          → uses current Y
   * /buildffa setvoid [y]      → uses provided Y value
   */
  private boolean handleSetVoid(CommandSender sender, String[] args) {
    if (!sender.hasPermission("buildffa.setvoid")) {
      sender.sendMessage(colorize("&cYou do not have permission to use this command."));
      return true;
    }
    
    double y;
    
    if (args.length >= 2) {
      // Parse the provided Y value
      try {
        y = Double.parseDouble(args[1]);
      } catch (NumberFormatException e) {
        sender.sendMessage(colorize("&cInvalid number: &e" + args[1]));
        sender.sendMessage(colorize("&7Usage: &e/buildffa setvoid [y]"));
        return true;
      }
    } else {
      // No argument — fall back to player's current Y
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
  
  /**
   * /buildffa sethighlimit          → uses current Y
   * /buildffa sethighlimit [y]      → uses provided Y value
   */
  private boolean handleSetHighLimit(CommandSender sender, String[] args) {
    if (!sender.hasPermission("buildffa.sethighlimit")) {
      sender.sendMessage(colorize("&cYou do not have permission to use this command."));
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
    if (!sender.hasPermission("buildffa.reload")) {
      sender.sendMessage(colorize("&cYou do not have permission to use this command."));
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
    sender.sendMessage(colorize("&e/buildffa setvoid &7- Set void Y to your current Y"));
    sender.sendMessage(colorize("&e/buildffa setvoid [y] &7- Set void Y to a specific value"));
    sender.sendMessage(colorize("&e/buildffa sethighlimit &7- Set high limit to your current Y"));
    sender.sendMessage(colorize("&e/buildffa sethighlimit [y] &7- Set high limit to a value"));
    sender.sendMessage(colorize("&e/buildffa creator &7- Show plugin credits"));
    sender.sendMessage(colorize("&e/buildffa reload &7- Reload configuration"));
    sender.sendMessage(colorize("&8&m----------------------------------"));
  }
  
  private String colorize(String message) {
    return ChatColor.translateAlternateColorCodes('&', message);
  }
}