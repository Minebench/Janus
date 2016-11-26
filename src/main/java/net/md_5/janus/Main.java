package net.md_5.janus;


import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.google.common.cache.Cache;
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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class Main extends JavaPlugin implements Listener {

    private static final Material FRAME = Material.OBSIDIAN;
    private static final Material SIGN = Material.WALL_SIGN;
    private boolean portalTurnPlayer = false;
    private int portalDistance = 3;
    private boolean blockMessages = false;
    private int cooldown = 10;
    private String noPermission = "You don't have permission to use Server Portals!";
    private String noServerPermission = "You don't have permission to use the %server% portal!";
    private String signIdentifier = "server";

	private Cache<UUID, Long> cooldownCache;
    private LoadingCache<Location, Set<Block>> portalCache;

    @Override
    public void onEnable() {
        cooldownCache = CacheBuilder.newBuilder().expireAfterWrite(cooldown, TimeUnit.SECONDS).build();
        portalCache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build(new PortalCacheLoader());
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getPluginManager().registerEvents(this, this);
        getConfig().addDefault("portalTurnPlayer", portalTurnPlayer);
        getConfig().addDefault("portalDistance", portalDistance);
        getConfig().addDefault("cooldown", cooldown);
        getConfig().addDefault("blockMessages", blockMessages);
        getConfig().addDefault("lang.noPermission", noPermission);
        getConfig().addDefault("lang.noServerPermission", noServerPermission);
        getConfig().addDefault("signIdentifier", signIdentifier);
        getConfig().options().copyDefaults(true);
        saveConfig();
        portalTurnPlayer = getConfig().getBoolean("portalTurnPlayer");
        portalDistance = getConfig().getInt("portalDistance");
        if(portalDistance < 1) portalDistance = 1;
        cooldown = getConfig().getInt("cooldown");
        blockMessages = getConfig().getBoolean("blockMessages");
        noPermission = ChatColor.translateAlternateColorCodes('&', getConfig().getString("lang.noPermission"));
        noServerPermission = ChatColor.translateAlternateColorCodes('&', getConfig().getString("lang.noServerPermission"));
        signIdentifier = getConfig().getString("signIdentifier").toLowerCase();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (blockMessages) {
            event.setJoinMessage(null);
        }
        cooldownCache.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        if (event.getPlayer().getLocation().getBlock().getType() == Material.PORTAL) {
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

    @EventHandler
    public void onPlayerPortalEnter(EntityPortalEnterEvent event) {
    	if(!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();
        if (cooldown > 0
                && !player.hasPermission("janus.bypass")
                && cooldownCache.getIfPresent(player.getUniqueId()) != null
                && cooldownCache.getIfPresent(player.getUniqueId()) + cooldown * 1000 > System.currentTimeMillis()) {
            return;
        }

        for (Block block : getPortalNear(event.getLocation())) {
            for (BlockFace bf : BlockFace.values()) {
                Block relative = block.getRelative(bf);
                if (relative.getType() == SIGN) {
                    Sign sign = (Sign) relative.getState();
                    if (sign.getLine(0).toLowerCase().equals("[" + signIdentifier + "]")) {
                        cooldownCache.put(player.getUniqueId(), System.currentTimeMillis());
                        if(!player.hasPermission("janus.use")) {
                            player.sendMessage(ChatColor.RED + noPermission);
                            break;
                        }

                        teleportOutOfPortal(player);

                        String serverName = sign.getLine(1);
                        if (!player.hasPermission("janus.use." + serverName.toLowerCase())) {
                            player.sendMessage(ChatColor.RED + noServerPermission.replace("%server%", serverName));
                            break;
                        }

                        ByteArrayOutputStream b = new ByteArrayOutputStream();
                        DataOutputStream out = new DataOutputStream(b);
                        try {
                            out.writeUTF("Connect");
                            out.writeUTF(serverName);
                        } catch (IOException ex) {
                            // Impossible
                        }
                        player.sendPluginMessage(this, "BungeeCord", b.toByteArray());
                        break;
                        //
                    }
                }
            }
        }
    }

    private Set<Block> getPortalNear(Location location) {
        Set<Block> blocks = null;
        try {
            blocks = portalCache.get(location.getBlock().getLocation());
        } catch (ExecutionException e) {
            getLogger().log(Level.SEVERE, "Error while getting the portal near " + location, e);
        }
        if (blocks == null) {
            return new HashSet<>();
        }
        return blocks;
    }

    private void teleportOutOfPortal(Player player) {
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

    private class PortalCacheLoader extends CacheLoader<Location, Set<Block>> {
        @Override
        public Set<Block> load(Location loc) throws Exception {
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
    }
}
