package ru.raskol.houses.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import ru.raskol.houses.RaskolHouses;
import ru.raskol.houses.model.House;
import ru.raskol.houses.store.HouseStore;

import java.util.List;

/** Плейсхолдеры %raskolhouses_*%. */
public final class RaskolHousesExpansion extends PlaceholderExpansion {

    private final RaskolHouses plugin;
    private final HouseStore store;

    public RaskolHousesExpansion(RaskolHouses plugin, HouseStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    @Override
    public String getIdentifier() { return "raskolhouses"; }

    @Override
    public String getAuthor() { return "Baltimor"; }

    @Override
    public String getVersion() { return plugin.getDescription().getVersion(); }

    @Override
    public boolean persist() { return true; }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) return "";
        switch (params) {
            case "house": {
                House h = store.houseAt(player.getLocation());
                return h == null ? "" : h.title();
            }
            case "owner": {
                House h = store.houseAt(player.getLocation());
                return h == null ? "" : (h.isOwned() ? h.ownerName : "свободен");
            }
            case "price": {
                House h = store.houseAt(player.getLocation());
                return h == null ? "" : String.valueOf((int) h.price);
            }
            case "status": {
                House h = store.houseAt(player.getLocation());
                return h == null ? "" : (h.isOwned() ? "продан" : "свободен");
            }
            case "owned":
                return String.valueOf(store.ownedCount(player.getUniqueId().toString()));
            case "home": {
                List<House> owned = store.ownedBy(player.getUniqueId().toString());
                return owned.isEmpty() ? "" : owned.get(0).title();
            }
            default:
                return null;
        }
    }
}
