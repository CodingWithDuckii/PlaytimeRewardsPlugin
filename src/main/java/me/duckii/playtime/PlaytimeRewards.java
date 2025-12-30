package me.duckii.playtime;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class PlaytimeRewards extends JavaPlugin implements Listener {

    private final Map<UUID, Integer> playtime = new HashMap<>();
    private final Map<UUID, Set<Integer>> claimed = new HashMap<>();

    private final String GUI_TITLE = ChatColor.DARK_GREEN + "Playtime Rewards Editor";

    private final Map<Integer, Integer> rewardSlots = Map.of(
            5, 11,
            10, 13,
            15, 15,
            30, 29,
            60, 33
    );

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    playtime.put(uuid, playtime.getOrDefault(uuid, 0) + 1);
                    int minutes = playtime.get(uuid);

                    for (String key : getConfig().getConfigurationSection("rewards").getKeys(false)) {
                        int rewardMinute = Integer.parseInt(key);
                        if (minutes == rewardMinute && !hasClaimed(uuid, rewardMinute)) {
                            giveReward(player, rewardMinute);
                        }
                    }

                    if (minutes >= 60) {
                        playtime.put(uuid, 0);
                        claimed.remove(uuid);
                        player.sendMessage(color("&aYou reached 1 hour online! Rewards have reset."));
                    }
                }
            }
        }.runTaskTimer(this, 20L * 60, 20L * 60);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player player)) return true;

        if (!cmd.getName().equalsIgnoreCase("playtime")) return true;

        // OP opens GUI
        if (player.isOp()) {
            openEditor(player);
            return true;
        }

        // Normal player info
        UUID uuid = player.getUniqueId();
        int time = playtime.getOrDefault(uuid, 0);

        player.sendMessage(color("&7&m------------------------"));
        player.sendMessage(color("&aPlaytime Rewards"));

        int[] minutes = {5, 10, 15, 30, 60};
        for (int m : minutes) {
            if (hasClaimed(uuid, m)) {
                player.sendMessage(color("&a✔ " + m + " minutes"));
            } else {
                player.sendMessage(color("&c✘ " + m + " minutes"));
            }
        }

        int next = 0;
        for (int m : minutes) {
            if (time < m) {
                next = m;
                break;
            }
        }

        if (next > 0) {
            player.sendMessage(color("&eNext reward in &6" + (next - time) + " &eminutes."));
        }

        player.sendMessage(color("&7&m------------------------"));
        return true;
    }

    /* ================= GUI ================= */

    private void openEditor(Player player) {
        Inventory inv = Bukkit.createInventory(null, 45, GUI_TITLE);

        for (int minute : rewardSlots.keySet()) {
            inv.setItem(rewardSlots.get(minute), createRewardItem(minute));
        }

        player.openInventory(inv);
    }

    private ItemStack createRewardItem(int minute) {
        FileConfiguration cfg = getConfig();
        String path = "rewards." + minute;

        Material mat = Material.valueOf(cfg.getString(path + ".material"));
        int amount = cfg.getInt(path + ".amount");

        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(color("&a" + minute + " Minute Reward"));
        meta.setLore(List.of(
                color("&7Current: &f" + mat.name()),
                color("&eClick to set to item in your hand")
        ));

        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals(GUI_TITLE)) return;
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player player)) return;

        int slot = e.getSlot();
        Integer minute = rewardSlots.entrySet()
                .stream()
                .filter(entry -> entry.getValue() == slot)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        if (minute == null) return;

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            player.sendMessage(color("&cHold the item you want as the reward."));
            return;
        }

        getConfig().set("rewards." + minute + ".material", hand.getType().name());
        getConfig().set("rewards." + minute + ".amount", hand.getAmount());
        saveConfig();

        e.getInventory().setItem(slot, createRewardItem(minute));
        player.sendMessage(color("&aUpdated " + minute + " minute reward."));
    }

    /* ================= UTILS ================= */

    private void giveReward(Player player, int minute) {
        FileConfiguration cfg = getConfig();
        String path = "rewards." + minute;

        Material mat = Material.valueOf(cfg.getString(path + ".material"));
        int amount = cfg.getInt(path + ".amount");

        player.getInventory().addItem(new ItemStack(mat, amount));
        markClaimed(player.getUniqueId(), minute);

        player.sendMessage(color("&aYou received the " + minute + " minute reward!"));
    }

    private boolean hasClaimed(UUID uuid, int minute) {
        return claimed.getOrDefault(uuid, new HashSet<>()).contains(minute);
    }

    private void markClaimed(UUID uuid, int minute) {
        claimed.computeIfAbsent(uuid, k -> new HashSet<>()).add(minute);
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
