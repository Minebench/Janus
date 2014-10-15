package net.md_5.janus;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin implements Listener {

    private static final Material FRAME = Material.OBSIDIAN;
    private static final Material PORTAL = Material.PORTAL;
    private static final Material SIGN = Material.WALL_SIGN;
    private static final String IDENTIFIER = "[server]";
    private boolean blockMessages = false;

    @Override
    public void onEnable() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getPluginManager().registerEvents(this, this);
        getConfig().addDefault("blockMessages", blockMessages);
        getConfig().options().copyDefaults(true);
        saveConfig();
        blockMessages = getConfig().getBoolean("blockMessages");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (blockMessages) {
            event.setJoinMessage(null);
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
        if (event.getLine(0).equals(IDENTIFIER) && !event.getPlayer().isOp()) {
            event.getPlayer().sendMessage(ChatColor.RED + "You are not allowed to do that!");
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        World world = to.getWorld();
        if (world.getBlockAt(to).getType() == PORTAL && world.getBlockAt(event.getFrom()).getType() != PORTAL) {
            for (Block block : getPortalNear(world, to.getBlockX(), to.getBlockY(), to.getBlockZ())) {
                for (BlockFace bf : BlockFace.values()) {
                    Block relative = block.getRelative(bf);
                    if (relative.getType() == SIGN) {
                        Sign sign = (Sign) relative.getState();
                        if (sign.getLine(0).equals(IDENTIFIER)) {
                            //
                            event.setCancelled(true);
                            Location location = event.getPlayer().getLocation();
                            float yaw = location.getYaw();
                            if ((yaw += 180) > 360) {
                                yaw -= 360;
                            }
                            location.setYaw(yaw);
                            event.getPlayer().teleport(location);
                            ByteArrayOutputStream b = new ByteArrayOutputStream();
                            DataOutputStream out = new DataOutputStream(b);
                            try {
                                out.writeUTF("Connect");
                                out.writeUTF(sign.getLine(1));
                            } catch (IOException ex) {
                                // Impossible
                            }

                            event.getPlayer().sendPluginMessage(this, "BungeeCord", b.toByteArray());
                            break;
                            //
                        }
                    }
                }
            }
        }
    }

    private Set<Block> getPortalNear(World world, int x, int y, int z) {
        byte b0 = 0;
        byte b1 = 0;
        if (world.getBlockAt(x - 1, y, z).getType() == FRAME || world.getBlockAt(x + 1, y, z).getType() == FRAME) {
            b0 = 1;
        }
        if (world.getBlockAt(x, y, z - 1).getType() == FRAME || world.getBlockAt(x, y, z + 1).getType() == FRAME) {
            b1 = 1;
        }

        Set<Block> blocks = new HashSet<Block>();

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
