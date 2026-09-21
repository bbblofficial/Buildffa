package org.vansama.buildffa;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class FeatherHit implements Listener {

    private final JavaPlugin plugin;

    public FeatherHit(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile proj = event.getEntity();
        if (!(proj instanceof Snowball)) return;
        if (!proj.hasMetadata("buildffa_feather")) return;

        if (!(proj.getShooter() instanceof Player)) return;
        Player shooter = (Player) proj.getShooter();

        Location loc = proj.getLocation();

        if (event.getEntity() != null && event.getEntity() instanceof Player) {
            Player target = (Player) event.getEntity();

            double knockbackHorizontal = plugin.getConfig()
                    .getDouble("feather-jump.knockback-horizontal", 1.5D);
            double knockbackVertical = plugin.getConfig()
                    .getDouble("feather-jump.knockback-vertical", 0.9D);
            double damage = plugin.getConfig()
                    .getDouble("feather-jump.damage", 2.0D);

            Vector direction = target.getLocation().toVector()
                    .subtract(loc.toVector())
                    .normalize()
                    .multiply(knockbackHorizontal)
                    .setY(knockbackVertical);

            target.setVelocity(direction);

            if (damage > 0) {
                target.damage(damage, shooter);
            }
        }
    }
}