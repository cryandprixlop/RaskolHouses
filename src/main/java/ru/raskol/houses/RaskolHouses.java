package ru.raskol.houses;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.raskol.houses.command.HouseCommand;
import ru.raskol.houses.hook.RaskolHousesExpansion;
import ru.raskol.houses.hook.SelectionHook;
import ru.raskol.houses.hook.TownyHook;
import ru.raskol.houses.hook.VaultHook;
import ru.raskol.houses.listener.InteractionListener;
import ru.raskol.houses.listener.ProtectionListener;
import ru.raskol.houses.listener.SignListener;
import ru.raskol.houses.service.HouseService;
import ru.raskol.houses.store.HouseStore;

public final class RaskolHouses extends JavaPlugin {

    private HouseStore houseStore;
    private SelectionHook selectionHook;
    private TownyHook townyHook;
    private VaultHook vaultHook;
    private HouseService houseService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        houseStore = new HouseStore(this);
        houseStore.load();
        selectionHook = new SelectionHook(this);
        townyHook = new TownyHook(this);
        vaultHook = new VaultHook(this);
        houseService = new HouseService(this, houseStore, vaultHook);

        PluginCommand cmd = getCommand("rhouse");
        if (cmd != null) {
            HouseCommand executor = new HouseCommand(this, houseStore,
                    selectionHook, townyHook, houseService);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getServer().getPluginManager().registerEvents(
                new ProtectionListener(this, houseStore), this);
        getServer().getPluginManager().registerEvents(
                new InteractionListener(this, houseStore), this);
        getServer().getPluginManager().registerEvents(
                new SignListener(this, houseStore, houseService), this);

        boolean papi = getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
        if (papi) {
            new RaskolHousesExpansion(this, houseStore).register();
        }

        getLogger().info("RaskolHouses v" + getDescription().getVersion()
                + " включён. Домов: " + houseStore.all().size()
                + ". WE: " + (selectionHook.isAvailable() ? "да" : "нет")
                + ". Towny: " + (townyHook.isAvailable() ? "да" : "нет")
                + ". Vault: " + (vaultHook.isAvailable() ? "да" : "нет")
                + ". PAPI: " + (papi ? "да" : "нет"));
    }

    @Override
    public void onDisable() {
        if (houseStore != null) houseStore.save();
        getLogger().info("RaskolHouses выключен.");
    }

    public HouseStore getHouseStore() { return houseStore; }
    public TownyHook getTownyHook() { return townyHook; }
    public VaultHook getVaultHook() { return vaultHook; }
    public HouseService getHouseService() { return houseService; }
}
