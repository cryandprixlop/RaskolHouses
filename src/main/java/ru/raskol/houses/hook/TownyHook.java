package ru.raskol.houses.hook;

import org.bukkit.Location;
import ru.raskol.houses.RaskolHouses;

import java.lang.reflect.Method;

/** Towny через рефлексию: имя города по локации. */
public final class TownyHook {

    private final RaskolHouses plugin;

    public TownyHook(RaskolHouses plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        return plugin.getServer().getPluginManager().getPlugin("Towny") != null;
    }

    /** Имя города, в котором находится локация; null если дикие земли. */
    public String townAt(Location loc) {
        try {
            Class<?> apiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object townBlock = apiClass.getMethod("getTownBlock", Location.class).invoke(api, loc);
            if (townBlock == null) return null;
            Object town = callFirst(townBlock, "getTownOrNull", "getTown");
            if (town == null) return null;
            return (String) town.getClass().getMethod("getName").invoke(town);
        } catch (Throwable t) {
            return null;
        }
    }

    private Object callFirst(Object target, String... methods) {
        for (String name : methods) {
            try {
                Method m = target.getClass().getMethod(name);
                Object out = m.invoke(target);
                if (out != null) return out;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
