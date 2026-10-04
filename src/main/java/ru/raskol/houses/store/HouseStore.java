package ru.raskol.houses.store;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.raskol.houses.RaskolHouses;
import ru.raskol.houses.model.House;
import ru.raskol.houses.model.Region;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Реестр домов: houses.yml в папке плагина. */
public final class HouseStore {

    private final RaskolHouses plugin;
    private final Map<String, House> houses = new LinkedHashMap<>();

    public HouseStore(RaskolHouses plugin) {
        this.plugin = plugin;
    }

    public void load() {
        houses.clear();
        File file = new File(plugin.getDataFolder(), "houses.yml");
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("houses");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            House h = new House();
            h.town = s.getString("town", "");
            h.number = s.getInt("number", 0);
            h.price = s.getDouble("price", 0.0);
            h.owner = s.getString("owner", "");
            h.ownerName = s.getString("owner-name", "");
            h.structure = readRegion(s.getConfigurationSection("structure"));
            h.interior = readRegion(s.getConfigurationSection("interior"));
            h.yard = readRegion(s.getConfigurationSection("yard"));
            if (h.structure != null) houses.put(h.id(), h);
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (House h : houses.values()) {
            ConfigurationSection s = cfg.createSection("houses." + h.id());
            s.set("town", h.town);
            s.set("number", h.number);
            s.set("price", h.price);
            s.set("owner", h.owner);
            s.set("owner-name", h.ownerName);
            writeRegion(s.createSection("structure"), h.structure);
            if (h.interior != null) writeRegion(s.createSection("interior"), h.interior);
            if (h.yard != null) writeRegion(s.createSection("yard"), h.yard);
        }
        try {
            cfg.save(new File(plugin.getDataFolder(), "houses.yml"));
        } catch (Exception e) {
            plugin.getLogger().severe("[Houses] не удалось сохранить houses.yml: " + e.getMessage());
        }
    }

    private Region readRegion(ConfigurationSection s) {
        if (s == null) return null;
        Region r = new Region();
        r.world = s.getString("world", "world");
        r.x1 = s.getInt("x1"); r.y1 = s.getInt("y1"); r.z1 = s.getInt("z1");
        r.x2 = s.getInt("x2"); r.y2 = s.getInt("y2"); r.z2 = s.getInt("z2");
        return r;
    }

    private void writeRegion(ConfigurationSection s, Region r) {
        s.set("world", r.world);
        s.set("x1", r.x1); s.set("y1", r.y1); s.set("z1", r.z1);
        s.set("x2", r.x2); s.set("y2", r.y2); s.set("z2", r.z2);
    }

    /* ================= Запросы ================= */

    public List<House> all() {
        return new ArrayList<>(houses.values());
    }

    public House houseAt(Location loc) {
        for (House h : houses.values()) {
            if (h.structure != null && h.structure.contains(loc)) return h;
        }
        return null;
    }

    public House byTownNumber(String town, int number) {
        return houses.get(town + "-" + number);
    }

    public int ownedCount(String uuid) {
        int count = 0;
        for (House h : houses.values()) {
            if (uuid.equals(h.owner)) count++;
        }
        return count;
    }

    public void add(House h) {
        houses.put(h.id(), h);
        save();
    }

    public void remove(String id) {
        houses.remove(id);
        save();
    }
}
