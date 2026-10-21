package ru.raskol.houses.task;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.raskol.houses.RaskolHouses;
import ru.raskol.houses.model.House;
import ru.raskol.houses.service.HouseService;
import ru.raskol.houses.store.HouseStore;

import java.time.LocalDate;

/**
 * Раз в сутки (первый тик после полуночи) списывает 1 день предоплаты налога.
 * paidDays <= 0 — просрочка; paidDays <= -grace-days — конфискация в пользу города.
 */
public final class TaxTask implements Runnable {

    private final RaskolHouses plugin;
    private final HouseStore store;
    private final HouseService service;

    public TaxTask(RaskolHouses plugin, HouseStore store, HouseService service) {
        this.plugin = plugin;
        this.store = store;
        this.service = service;
    }

    @Override
    public void run() {
        String today = LocalDate.now().toString();
        if (today.equals(store.getLastTaxDate())) return;
        store.setLastTaxDate(today);

        boolean enabled = plugin.getConfig().getBoolean("tax.enabled", true);
        int grace = plugin.getConfig().getInt("tax.grace-days", 3);
        double perDay = plugin.getConfig().getDouble("tax.per-day", 200.0);

        for (House h : store.all()) {
            if (!h.isOwned()) continue;
            if (enabled) h.paidDays--;

            if (h.paidDays <= -grace) {
                String name = h.ownerName;
                h.owner = "";
                h.ownerName = "";
                h.paidDays = 0;
                service.updateSign(h);
                plugin.getLogger().info("[Houses] CONFISCATED: " + h.id() + " (ex-owner " + name + ")");
                Player online = Bukkit.getPlayerExact(name);
                if (online != null) {
                    online.sendMessage("§c✖ " + h.title() + " конфискован в пользу города за неуплату налога.");
                }
            } else if (h.paidDays <= 0) {
                warn(h, "§c✖ Предоплата налога кончилась! Дом в просрочке ("
                        + (-h.paidDays) + "/" + grace + " дн.). Заплати: /rhouse pay <дни>");
            } else if (h.paidDays <= 3) {
                warn(h, "§e⚠ " + h.title() + ": оплачено всего " + h.paidDays
                        + " дн. налога (" + (int) perDay + "/день). Продли: /rhouse pay <дни>");
            }
        }
        store.save();
    }

    private void warn(House h, String message) {
        Player online = Bukkit.getPlayerExact(h.ownerName);
        if (online != null) online.sendMessage(message);
        plugin.getLogger().info("[Houses] tax: " + h.id() + " paidDays=" + h.paidDays);
    }
}
