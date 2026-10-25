package ru.raskol.houses.hook;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import ru.raskol.houses.RaskolHouses;

public final class VaultHook {

    private final RaskolHouses plugin;
    private Economy economy;

    public VaultHook(RaskolHouses plugin) {
        this.plugin = plugin;
        RegisteredServiceProvider<Economy> rsp = plugin.getServer()
                .getServicesManager().getRegistration(Economy.class);
        if (rsp != null) this.economy = rsp.getProvider();
    }

    public boolean isAvailable() { return economy != null; }

    public double balance(Player player) {
        return isAvailable() ? economy.getBalance(player) : 0.0;
    }

    public String format(double amount) {
        return isAvailable() ? economy.format(amount) : String.valueOf(amount);
    }

    public boolean has(Player player, double amount) {
        return isAvailable() && economy.has(player, amount);
    }

    public boolean withdraw(Player player, double amount) {
        if (!isAvailable()) return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean depositPlayer(Player player, double amount) {
        if (!isAvailable()) return false;
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    public boolean createAccount(String accountName) {
        if (!isAvailable()) return false;
        try {
            if (economy.hasAccount(accountName)) return true;
            return economy.createPlayerAccount(accountName);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Зачисление оффлайн-аккаунту или игроку по имени. */
    public boolean depositAccount(String accountName, double amount) {
        if (!isAvailable()) return false;
        return economy.depositPlayer(accountName, amount).transactionSuccess();
    }

    public boolean depositAccount(String accountName, String worldName, double amount) {
        if (!isAvailable()) return false;
        return economy.depositPlayer(accountName, worldName, amount).transactionSuccess();
    }

    public boolean withdrawAccount(String accountName, double amount) {
        if (!isAvailable()) return false;
        return economy.withdrawPlayer(accountName, amount).transactionSuccess();
    }
}
