package ru.raskol.houses;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.raskol.houses.command.HouseCommand;
import ru.raskol.houses.hook.SelectionHook;
import ru.raskol.houses.hook.TownyHook;
import ru.raskol.houses.listener.ProtectionListener;
import ru.raskol.houses.store.HouseStore;

public final class RaskolHouses extends JavaPlugin {

    private HouseStore houseStore;
    private SelectionHook selectionHook;
    private TownyHook townyHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        houseStore = new HouseStore(this);
        houseStore.load();
        selectionHook = new SelectionHook(this);
        townyHook = new TownyHook(this);

        PluginCommand cmd = getCommand("rhouse");
        if (cmd != null) {
            HouseCommand executor = new HouseCommand(this, houseStore, selectionHook, townyHook);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getServer().getPluginManager().registerEvents(
                new ProtectionListener(this, houseStore), this);

        getLogger().info("RaskolHouses v" + getDescription().getVersion()
                + " включён. Домов в реестре: " + houseStore.all().size()
                + ". WorldEdit: " + (selectionHook.isAvailable() ? "да" : "нет")
                + ". Towny: " + (townyHook.isAvailable() ? "да" : "нет"));
    }

    @Override
    public void onDisable() {
        if (houseStore != null) houseStore.save();
        getLogger().info("RaskolHouses выключен.");
    }

    public HouseStore getHouseStore() { return houseStore; }
    public TownyHook getTownyHook() { return townyHook; }
}
