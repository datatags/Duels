package me.realized.duels.arena;

import com.google.gson.reflect.TypeToken;
import me.realized.duels.Core;
import me.realized.duels.configuration.Config;
import me.realized.duels.data.ArenaData;
import me.realized.duels.data.DataManager;
import me.realized.duels.utilities.PlayerUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class ArenaManager {

    private final Core instance;
    private final Config config;
    private final DataManager dataManager;
    private final File base;
    private final Random random = new Random();

    private List<Arena> arenas = new ArrayList<>();

    public ArenaManager(Core instance) {
        this.instance = instance;
        this.config = instance.getConfiguration();
        this.dataManager = instance.getDataManager();

        base = new File(instance.getDataFolder(), "arenas.json");

        try {
            boolean generated = base.createNewFile();

            if (generated) {
                instance.info("Generated arena file.");
            }

        } catch (IOException e) {
            instance.warn("Failed to generate arena file! (" + e.getMessage() + ")");
        }
    }

    public void load() {
        arenas.clear();

        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(base))) {
            List<ArenaData> loaded = instance.getGson().fromJson(reader, new TypeToken<List<ArenaData>>() {}.getType());

            if (loaded != null && !loaded.isEmpty()) {
                for (ArenaData data : loaded) {
                    arenas.add(data.toArena());
                }
            }
        } catch (IOException ex) {
            instance.warn("Failed to load arenas from the file! (" + ex.getMessage() + ")");
        }

        instance.info("Loaded " + arenas.size() + " arena(s).");
    }

    public void save() {
        List<ArenaData> saved = new ArrayList<>();
        Location toTeleport = dataManager.getLobby() != null ? dataManager.getLobby() : Bukkit.getWorlds().get(0).getSpawnLocation();

        if (!arenas.isEmpty()) {
            for (Arena arena : arenas) {
                saved.add(new ArenaData(arena));

                if (arena.isUsed()) {
                    Arena.Match match = arena.getCurrentMatch();

                    for (UUID uuid : arena.getPlayers()) {
                        Player player = Bukkit.getPlayer(uuid);

                        if (player == null || player.isDead()) {
                            continue;
                        }

                        if (config.getBoolean("teleport-to-latest-location")) {
                            toTeleport = match.getLocation(uuid);
                        }

                        PlayerUtil.pm("&c&l[Duels] Plugin is disabling, matches are ended by default.", player);
                        PlayerUtil.reset(player, false);

                        Arena.InventoryData data = match.getInventories(uuid);

                        if (!PlayerUtil.canTeleportTo(player, toTeleport)) {
                            player.setHealth(0.0D);
                        } else {
                            player.teleport(toTeleport);
                            PlayerUtil.setInventory(player, data.getInventoryContents(), data.getArmorContents(), false);
                        }
                    }
                }
            }
        }

        try {
            boolean generated = base.createNewFile();

            if (generated) {
                instance.info("Generated arena file!");
            }

            Writer writer = new OutputStreamWriter(new FileOutputStream(base));
            instance.getGson().toJson(saved, writer);
            writer.flush();
            writer.close();
        } catch (IOException ex) {
            instance.warn("Failed to save arenas! (" + ex.getMessage() + ")");
        }
    }

    public Arena getArena(String name) {
        for (Arena arena : arenas) {
            if (arena.getName().equals(name)) {
                return arena;
            }
        }

        return null;
    }

    public Arena getArena(Player player) {
        for (Arena arena : arenas) {
            if (arena.getPlayers().contains(player.getUniqueId())) {
                return arena;
            }
        }

        return null;
    }

    public Arena getAvailableArena() {
        List<Arena> available = new ArrayList<>();

        for (Arena arena : arenas) {
            if (!arena.isDisabled()  && arena.isValid() && !arena.isUsed()) {
                available.add(arena);
            }
        }

        if (available.isEmpty()) {
            return null;
        }

        return available.get(random.nextInt(available.size()));
    }

    public boolean isInMatch(Player player) {
        for (Arena arena : arenas) {
            if (arena.getPlayers().contains(player.getUniqueId())) {
                return true;
            }
        }

        return false;
    }

    public void createArena(String name) {
        arenas.add(new Arena(name, false));
    }

    public void removeArena(Arena arena) {
        arenas.remove(arena);
    }

    public List<String> getArenas() {
        List<String> result = new ArrayList<>();

        if (arenas.isEmpty()) {
            result.add("No arenas are currently loaded.");
            return result;
        }

        for (Arena arena : arenas) {
            if (arena.isDisabled()) {
                result.add(ChatColor.DARK_RED + arena.getName());
                continue;
            }

            if (!arena.isValid()) {
                result.add(ChatColor.BLUE + arena.getName());
                continue;
            }

            result.add((arena.isUsed() ? ChatColor.RED : ChatColor.GREEN) + arena.getName());
        }

        return result;
    }
}
