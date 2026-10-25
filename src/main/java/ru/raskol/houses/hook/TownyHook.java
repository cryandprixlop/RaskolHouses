package ru.raskol.houses.hook;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import ru.raskol.houses.RaskolHouses;

import java.lang.reflect.Method;

/** Towny через рефлексию: город, нация, банки города и королевства. */
public final class TownyHook {

    private final RaskolHouses plugin;

    public TownyHook(RaskolHouses plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        return plugin.getServer().getPluginManager().getPlugin("Towny") != null;
    }

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

    /** Нация, в которой состоит игрок (через его город). null если нет. */
    public String residentNationName(Player player) {
        try {
            Class<?> utilClass = Class.forName("com.palmergames.bukkit.towny.TownyUniverse");
            Object universe = utilClass.getMethod("getInstance").invoke(null);
            Object resident = utilClass.getMethod("getResident", java.util.UUID.class)
                    .invoke(universe, player.getUniqueId());
            if (resident == null) return null;
            Object town = callFirst(resident, "getTownOrNull", "getTown");
            if (town == null) return null;
            Object nation = callFirst(town, "getNationOrNull", "getNation");
            if (nation == null) return null;
            return (String) nation.getClass().getMethod("getName").invoke(nation);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Нация города, в котором стоит дом. null если город вне нации. */
    public String nationOfTown(String townName) {
        try {
            Class<?> apiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object town = apiClass.getMethod("getTown", String.class).invoke(api, townName);
            if (town == null) return null;
            Object nation = callFirst(town, "getNationOrNull", "getNation");
            if (nation == null) return null;
            return (String) nation.getClass().getMethod("getName").invoke(nation);
        } catch (Throwable t) {
            return null;
        }
    }

    public boolean depositTownBank(String townName, double amount, String reason) {
        try {
            Object account = townAccount(townName);
            if (account == null) return false;
            Method m = account.getClass().getMethod("deposit", double.class, String.class);
            Object out = m.invoke(account, amount, reason);
            return !(out instanceof Boolean b) || b;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean withdrawTownBank(String townName, double amount, String reason) {
        try {
            Object account = townAccount(townName);
            if (account == null) return false;
            Method m = account.getClass().getMethod("withdraw", double.class, String.class);
            Object out = m.invoke(account, amount, reason);
            return !(out instanceof Boolean b) || b;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Депозит в казну королевства (нации). */
    public boolean depositNationBank(String nationName, double amount, String reason) {
        try {
            Class<?> apiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object nation = apiClass.getMethod("getNation", String.class).invoke(api, nationName);
            if (nation == null) return false;
            Object account = nation.getClass().getMethod("getAccount").invoke(nation);
            if (account == null) return false;
            Method m = account.getClass().getMethod("deposit", double.class, String.class);
            Object out = m.invoke(account, amount, reason);
            return !(out instanceof Boolean b) || b;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean withdrawNationBank(String nationName, double amount, String reason) {
        try {
            Class<?> apiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object nation = apiClass.getMethod("getNation", String.class).invoke(api, nationName);
            if (nation == null) return false;
            Object account = nation.getClass().getMethod("getAccount").invoke(nation);
            if (account == null) return false;
            Method m = account.getClass().getMethod("withdraw", double.class, String.class);
            Object out = m.invoke(account, amount, reason);
            return !(out instanceof Boolean b) || b;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean isKing(Player player) {
        try {
            Class<?> utilClass = Class.forName("com.palmergames.bukkit.towny.TownyUniverse");
            Object universe = utilClass.getMethod("getInstance").invoke(null);
            Object resident = utilClass.getMethod("getResident", java.util.UUID.class)
                    .invoke(universe, player.getUniqueId());
            if (resident == null) return false;
            Boolean k = (Boolean) resident.getClass().getMethod("isKing").invoke(resident);
            return k != null && k;
        } catch (Throwable t) {
            return false;
        }
    }

    private Object townAccount(String townName) throws Exception {
        Class<?> apiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
        Object api = apiClass.getMethod("getInstance").invoke(null);
        Object town = apiClass.getMethod("getTown", String.class).invoke(api, townName);
        if (town == null) return null;
        return town.getClass().getMethod("getAccount").invoke(town);
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
