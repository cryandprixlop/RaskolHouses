package ru.raskol.houses.hook;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import ru.raskol.houses.RaskolHouses;

/** Economy через Vault: списание с игрока, депозиты, создание оффлайн-аккаунтов. */
public final class VaultHook {

    private final RaskolHouses plugin;
    private Economy economy;

    public VaultHook(RaskolHouses plugin) {
        this.plugin = plugin;
        RegisteredServiceProvider<Economy> rsp = plugin.getServer()
                .getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            this.economy = rsp.getProvider();
        }
    }

    public boolean isAvailable() { return economy != null; }

    public double balance(Player player) {
        if (!isAvailable()) return 0.0;
        return economy.getBalance(player);
    }

    public String format(double amount) {
        if (!isAvailable()) return String.valueOf(amount);
        return economy.format(amount);
    }

    public boolean has(Player player, double amount) {
        if (!isAvailable()) return false;
        return economy.has(player, amount);
    }

    public boolean withdraw(Player player, double amount) {
        if (!isAvailable()) return false;
        EconomyResponse r = economy.withdrawPlayer(player, amount);
        return r.transactionSuccess();
    }

    public boolean depositPlayer(Player player, double amount) {
        if (!isAvailable()) return false;
        EconomyResponse r = economy.depositPlayer(player, amount);
        return r.transactionSuccess();
    }

    /** Создать оффлайн-аккаунт (например, казну town-<город>), если его нет. */
    public boolean createAccount(String accountName) {
        if (!isAvailable()) return false;
        try {
            if (economy.hasAccount(accountName)) return true;
            return economy.createPlayerAccount(accountName);
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean depositAccount(String accountName, double amount) {
        if (!isAvailable()) return false;
        EconomyResponse r = economy.depositPlayer(accountName, amount);
        return r.transactionSuccess();
    }

    public boolean depositAccount(String accountName, String worldName, double amount) {
        if (!isAvailable()) return false;
        EconomyResponse r = economy.depositPlayer(accountName, worldName, amount);
        return r.transactionSuccess();
    }
}
