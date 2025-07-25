package dg.spigot;

import dg.DGSession;
import dg.enums.DGChannel;
import dg.enums.DGState;
import dg.reflect.ReflectUtils;
import dg.spigot.command.MapCommand;
import dg.spigot.command.SicCommand;
import dg.spigot.command.WavCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class SpigotMain extends JavaPlugin implements Listener {
    public static final String itemName = "郊狼绑定二维码";

    private static boolean freeze = false;
    private static boolean multiply = false;
    public static int a = 0;
    public static int b = 0;
    private static int punishInteract = 0;
    private static int punishHurt = 0;
    private static int punishDie = 0;

    public static void clearMaps(Player p) {
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack item = p.getInventory().getItem(i);
            if (item != null && item.getType() == ReflectUtils.mapMaterial) {
                MapMeta meta = (MapMeta) item.getItemMeta();
                if (meta != null && itemName.equals(meta.getDisplayName())) {
                    p.getInventory().setItem(i, null);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        DGSession session = sessions.get(event.getPlayer().getName());
        if (session != null && session.getState() == DGState.PLAYING) {
            if (punishInteract != 0) {
                if (session.isPlaying()) {
                    return;
                }
                session.sendStrength(a / 2, DGChannel.A);
                session.sendStrength(b / 2, DGChannel.B);
                session.sendWave(punishInteract, DGChannel.BOTH);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHurt(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player)) {
            return; // 仅处理玩家受伤事件
        }
        Player player = (Player) entity;
        DGSession session = sessions.get(player.getName());
        if (session != null && session.getState() == DGState.PLAYING) {
            if (punishDie != 0 && event.getCause() != EntityDamageEvent.DamageCause.VOID && event.getDamage() > 0.6 + player.getHealth()) {
                event.setCancelled(true);
                player.setHealth(1);
                if (session.getStrengthA() == a && session.getStrengthB() == b && session.getExpectedEndTime() >= punishDie - 1) {
                    return;
                }
                session.sendStrength(a, DGChannel.A);
                session.sendStrength(b, DGChannel.B);
                session.sendWave(punishDie, DGChannel.BOTH);
            } else if (punishHurt != 0) {
                if (session.getStrengthA() == a && session.getStrengthB() == b && session.getExpectedEndTime() >= punishHurt - 1) {
                    return;
                }
                session.sendStrength(a, DGChannel.A);
                session.sendStrength(b, DGChannel.B);
                session.sendWave(punishHurt, DGChannel.BOTH);
            }
        } else {
            if (multiply) {
                event.setDamage(event.getDamage() * 2);
                player.sendMessage("请使用/dgmap命令完成郊狼绑定，避免受到双倍伤害！");
            }
        }
    }

    private static final Map<String, Long> lastNagTime = new HashMap<>();

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        DGSession session = sessions.get(event.getPlayer().getName());
        if (session == null || session.getState() != DGState.PLAYING) {
            if (freeze) {
                if (Math.abs(event.getTo().getX() - event.getFrom().getX()) > 0.01 ||
                        Math.abs(event.getTo().getZ() - event.getFrom().getZ()) > 0.01) {
                    event.setCancelled(true);
                    long lastNag = lastNagTime.getOrDefault(event.getPlayer().getName(), 0L);
                    long now = System.currentTimeMillis();
                    if (now - lastNag > 1000) {
                        event.getPlayer().sendMessage("请使用/dgmap命令完成郊狼绑定后方可游玩！");
                        lastNagTime.put(event.getPlayer().getName(), now);
                    }
                }
            }
        }
    }


    public static final Map<String, DGSession> sessions = new HashMap<>();

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        clearMaps(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        DGSession session = sessions.remove(player.getName());
        if (session != null) {
            session.close();
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (item != null && item.getType() == ReflectUtils.mapMaterial) {
            MapMeta meta = (MapMeta) item.getItemMeta();
            if (meta != null && itemName.equals(meta.getDisplayName())) {
                event.setCancelled(true);
                item.setAmount(0);
            }
        }
    }

    @EventHandler
    public void onDrop(ItemSpawnEvent event) {
        ItemStack item = event.getEntity().getItemStack();
        if (item.getType() == ReflectUtils.mapMaterial) {
            MapMeta meta = (MapMeta) item.getItemMeta();
            if (meta != null && itemName.equals(meta.getDisplayName())) {
                event.setCancelled(true);
            }
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        DGSession.endpoint = getConfig().getString("server");
        String punish = getConfig().getString("punishment");
        if ("freeze".equalsIgnoreCase(punish)) {
            freeze = true;
        } else if ("double".equalsIgnoreCase(punish)) {
            multiply = true;
        } else if (!"none".equalsIgnoreCase(punish)) {
            getLogger().warning("未知的惩罚方式 '" + punish + "'，请检查配置文件。");
            getLogger().warning("使用默认惩罚方式 'none'。");
        }
        a = getConfig().getInt("A");
        b = getConfig().getInt("B");
        if (a <= 0 || b <= 0 || a > 200 || b > 200) {
            getLogger().warning("A 和 B 的值必须在 1 到 200 之间，请检查配置文件。");
            getLogger().warning("使用默认值 A=100, B=50。");
            a = 100;
            b = 50;
        }
        punishInteract = getConfig().getInt("shock_interact", 0);
        punishHurt = getConfig().getInt("shock_duration", 0);
        punishDie = getConfig().getInt("die_duration", 0);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (label.toLowerCase()) {
            case "dgmap":
                MapCommand.handle(sender);
                break;
            case "sic":
                SicCommand.handle(sender, args);
                break;
            case "wav":
                WavCommand.handle(sender, args);
                break;
            default:
                throw new IllegalStateException();
        }
        return true;
    }
}
