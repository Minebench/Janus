package net.md_5.janus;


import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class Main extends JavaPlugin implements Listener {

    private static final Material FRAME = Material.OBSIDIAN;
    private static final Material SIGN = Material.WALL_SIGN;
    private static final Material PORTAL = Material.PORTAL;
    private boolean portalTurnPlayer = false;
    private int portalDistance = 3;
    private boolean blockMessages = false;
    private String noPermission = "You don't have permission to use Server Portals!";
    private String noServerPermission = "You don't have permission to use the %server% portal!";
    private String signIdentifier = "server";

    private LoadingCache<Location, String> portalCache;

    @Override
    public void onEnable() {
        load();
    }

    private void load() {
        portalCache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build(new PortalCacheLoader());
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getPluginManager().registerEvents(this, this);
        getConfig().addDefault("portalTurnPlayer", portalTurnPlayer);
        getConfig().addDefault("portalDistance", portalDistance);
        getConfig().addDefault("blockMessages", blockMessages);
        getConfig().addDefault("lang.noPermission", noPermission);
        getConfig().addDefault("lang.noServerPermission", noServerPermission);
        getConfig().addDefault("signIdentifier", signIdentifier);
        getConfig().options().copyDefaults(true);
        saveConfig();
        reloadConfig();
        portalTurnPlayer = getConfig().getBoolean("portalTurnPlayer");
        portalDistance = getConfig().getInt("portalDistance");
        if(portalDistance < 0) portalDistance = 0;
        blockMessages = getConfig().getBoolean("blockMessages");
        noPermission = ChatColor.translateAlternateColorCodes('&', getConfig().getString("lang.noPermission"));
        noServerPermission = ChatColor.translateAlternateColorCodes('&', getConfig().getString("lang.noServerPermission"));
        signIdentifier = getConfig().getString("signIdentifier").toLowerCase();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.AQUA + getName() + " v" + getDescription().getVersion());
            return true;
        } else if ("reload".equalsIgnoreCase(args[0])) {
            load();
            sender.sendMessage(ChatColor.YELLOW + getName() + " reloaded!");
            return true;
        } else if ("info".equalsIgnoreCase(args[0])) {
            sender.sendMessage(new String[] {
                    ChatColor.YELLOW + "Info:",
                    ChatColor.AQUA + " portalCache size: " + ChatColor.YELLOW + portalCache.size(),
                    ChatColor.YELLOW + "Config:",
                    ChatColor.AQUA + " portalTurnPlayer: " + ChatColor.YELLOW + portalTurnPlayer,
                    ChatColor.AQUA + " portalDistance: " + ChatColor.YELLOW + portalDistance,
                    ChatColor.AQUA + " blockMessages: " + ChatColor.YELLOW + blockMessages,
                    ChatColor.AQUA + " noPermission: " + ChatColor.YELLOW + noPermission,
                    ChatColor.AQUA + " noServerPermission: " + ChatColor.YELLOW + noServerPermission,
                    ChatColor.AQUA + " signIdentifier: " + ChatColor.YELLOW + signIdentifier
            });
            return true;
        }
        return false;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (blockMessages) {
            event.setJoinMessage(null);
        }
        if (event.getPlayer().getLocation().getBlock().getType() == PORTAL) {
            teleportOutOfPortal(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (blockMessages) {
            event.setQuitMessage(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (event.getLine(0).toLowerCase().equals("[" + signIdentifier + "]") && !event.getPlayer().hasPermission("janus.sign")) {
            event.getPlayer().sendMessage(ChatColor.RED + "You are not allowed to do that!");
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerPortalEnter(PlayerMoveEvent event) {
        if (event.getFrom().getBlock() == event.getTo().getBlock()) {
            // Player didn't switch block
            return;
        }

        if (event.getTo().getBlock().getType() != PORTAL) {
            // Player didn't move into portal
            return;
        }

        String serverName = getPortalServerNear(event.getTo());
        if (serverName != null) {
            Player player = event.getPlayer();

            if(!player.hasPermission("janus.use")) {
                player.sendMessage(ChatColor.RED + noPermission);
                return;
            }

            if (!player.hasPermission("janus.use." + serverName.toLowerCase())) {
                player.sendMessage(ChatColor.RED + noServerPermission.replace("%server%", serverName));
                return;
            }

            teleportOutOfPortal(player);

            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            try {
                out.writeUTF("Connect");
                out.writeUTF(serverName);
            } catch (IOException ex) {
                // Impossible
            }
            player.sendPluginMessage(this, "BungeeCord", b.toByteArray());
        }

    }

    private String getPortalServerNear(Location location) {
        try {
            return portalCache.get(location.getBlock().getLocation());
        } catch (ExecutionException e) {
            getLogger().log(Level.SEVERE, "Error while getting the portal near " + location, e);
        }
        return null;
    }

    private Set<Block> getPortalNear(Location loc) {
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        byte b0 = 0;
        byte b1 = 0;
        if (world.getBlockAt(x - 1, y, z).getType() == FRAME || world.getBlockAt(x + 1, y, z).getType() == FRAME) {
            b0 = 1;
        }
        if (world.getBlockAt(x, y, z - 1).getType() == FRAME || world.getBlockAt(x, y, z + 1).getType() == FRAME) {
            b1 = 1;
        }

        Set<Block> blocks = new HashSet<>();

        if (world.getBlockAt(x - b0, y, z - b1).getType() == Material.AIR) {
            x -= b0;
            z -= b1;
        }

        for (byte i = -1; i <= 2; ++i) {
            for (byte j = -1; j <= 3; ++j) {
                boolean flag = i == -1 || i == 2 || j == -1 || j == 3;

                if (i != -1 && i != 2 || j != -1 && j != 3) {
                    if (flag) {
                        blocks.add(world.getBlockAt(x + b0 * i, y + j, z + b1 * i));
                    }
                }
            }
        }
        return blocks;
    }

    private void teleportOutOfPortal(Player player) {
        if (portalDistance == 0) {
            return;
        }
        Location location = player.getLocation();
        float originalPitch = location.getPitch();
        location.setPitch(0);
        Vector vec = location.getDirection().multiply(portalDistance);
        location = location.add(vec.multiply(-1));
        location.setPitch(originalPitch);
        if(portalTurnPlayer) {
            float yaw = location.getYaw();
            if ((yaw += 180) > 360) {
                yaw -= 360;
            }
            location.setYaw(yaw);
        }
        player.teleport(location, TeleportCause.PLUGIN);
    }

    private class PortalCacheLoader extends CacheLoader<Location, String> {
        @Override
        public String load(Location loc) throws Exception {
            for (Block block : getPortalNear(loc)) {
                for (BlockFace bf : BlockFace.values()) {
                    Block relative = block.getRelative(bf);
                    if (relative.getType() == SIGN) {
                        Sign sign = (Sign) relative.getState();
                        if (sign.getLine(0).toLowerCase().equals("[" + signIdentifier + "]")) {
                            return sign.getLine(1);
                        }
                    }
                }
            }
            throw new Exception("No Portal found near " + loc);
        }
    }
}
