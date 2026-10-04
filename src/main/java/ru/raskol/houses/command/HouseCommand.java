package ru.raskol.houses.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.raskol.houses.RaskolHouses;
import ru.raskol.houses.hook.SelectionHook;
import ru.raskol.houses.hook.TownyHook;
import ru.raskol.houses.model.House;
import ru.raskol.houses.model.Region;
import ru.raskol.houses.store.HouseStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class HouseCommand implements CommandExecutor, TabCompleter {

    private final RaskolHouses plugin;
    private final HouseStore store;
    private final SelectionHook selection;
    private final TownyHook towny;

    public HouseCommand(RaskolHouses plugin, HouseStore store,
                        SelectionHook selection, TownyHook towny) {
        this.plugin = plugin;
        this.store = store;
        this.selection = selection;
        this.towny = towny;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "create" -> handleCreate(sender, args);
            case "interior" -> handleZone(sender, "interior");
            case "yard" -> handleZone(sender, "yard");
            case "setprice" -> handleSetPrice(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "info" -> handleInfo(sender);
            case "list" -> handleList(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== RaskolHouses ===");
        sender.sendMessage("§e/rhouse create <номер> <цена> §7— создать дом из WE-выделения");
        sender.sendMessage("§e/rhouse interior §7— зона интерьера из WE-выделения");
        sender.sendMessage("§e/rhouse yard §7— зона двора из WE-выделения");
        sender.sendMessage("§e/rhouse setprice <номер> <цена> §7— сменить цену");
        sender.sendMessage("§e/rhouse delete <номер> §7— удалить дом");
        sender.sendMessage("§e/rhouse info §7— карточка дома, в котором стоишь");
        sender.sendMessage("§e/rhouse list [город] §7— список домов");
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("rhouse.admin")) return true;
        sender.sendMessage("§cНет прав.");
        return false;
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player p) return p;
        sender.sendMessage("§cТолько игроки.");
        return null;
    }

    /* ================= create ================= */

    private void handleCreate(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        Player p = player(sender);
        if (p == null) return;
        if (args.length < 3) {
            p.sendMessage("§cИспользуй: /rhouse create <номер> <цена>");
            return;
        }
        int number;
        double price;
        try {
            number = Integer.parseInt(args[1]);
            price = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            p.sendMessage("§cНомер — целое число, цена — число.");
            return;
        }

        Region sel = selection.getSelection(p);
        if (sel == null) {
            p.sendMessage("§cСначала сделай выделение WorldEdit (//pos1 //pos2 или //wand).");
            return;
        }

        org.bukkit.Location center = new org.bukkit.Location(p.getWorld(),
                (sel.x1 + sel.x2) / 2.0, (sel.y1 + sel.y2) / 2.0, (sel.z1 + sel.z2) / 2.0);
        String town = towny.townAt(center);
        if (town == null) {
            p.sendMessage("§cДома можно создавать только в городах (это дикие земли).");
            return;
        }
        if (store.byTownNumber(town, number) != null) {
            p.sendMessage("§cВ городе " + town + " уже есть дом №" + number + ".");
            return;
        }

        House h = new House();
        h.town = town;
        h.number = number;
        h.price = price;
        h.structure = sel;
        store.add(h);

        p.sendMessage("§aСоздан " + h.title() + " §aза " + price + ". Регион: "
                + sel.x1 + "," + sel.y1 + "," + sel.z1 + " -> " + sel.x2 + "," + sel.y2 + "," + sel.z2);
        p.sendMessage("§7Теперь выдели комнаты внутри и введи /rhouse interior");
        plugin.getLogger().info("[Houses] created: " + h.id() + " price=" + price
                + " by " + p.getName());
    }

    /* ================= interior / yard ================= */

    private void handleZone(CommandSender sender, String zone) {
        if (!admin(sender)) return;
        Player p = player(sender);
        if (p == null) return;

        House house = store.houseAt(p.getLocation());
        if (house == null) {
            p.sendMessage("§cВстань внутрь дома, которому задаёшь зону.");
            return;
        }
        Region sel = selection.getSelection(p);
        if (sel == null) {
            p.sendMessage("§cСначала сделай выделение WorldEdit.");
            return;
        }
        if (!house.structure.contains(sel)) {
            p.sendMessage("§cВыделение должно быть внутри региона дома.");
            return;
        }
        if (zone.equals("interior")) house.interior = sel;
        else house.yard = sel;
        store.save();
        p.sendMessage("§aЗона " + (zone.equals("interior") ? "интерьера" : "двора")
                + " установлена для " + house.title() + ".");
    }

    /* ================= setprice / delete ================= */

    private void handleSetPrice(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        Player p = player(sender);
        if (p == null) return;
        if (args.length < 3) {
            p.sendMessage("§cИспользуй: /rhouse setprice <номер> <цена>");
            return;
        }
        String town = towny.townAt(p.getLocation());
        if (town == null) {
            p.sendMessage("§cТы вне города.");
            return;
        }
        House house = store.byTownNumber(town, Integer.parseInt(args[1]));
        if (house == null) {
            p.sendMessage("§cВ городе " + town + " нет дома №" + args[1] + ".");
            return;
        }
        house.price = Double.parseDouble(args[2]);
        store.save();
        p.sendMessage("§aЦена " + house.title() + " теперь " + house.price + ".");
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        Player p = player(sender);
        if (p == null) return;
        if (args.length < 2) {
            p.sendMessage("§cИспользуй: /rhouse delete <номер>");
            return;
        }
        String town = towny.townAt(p.getLocation());
        if (town == null) {
            p.sendMessage("§cТы вне города.");
            return;
        }
        House house = store.byTownNumber(town, Integer.parseInt(args[1]));
        if (house == null) {
            p.sendMessage("§cВ городе " + town + " нет дома №" + args[1] + ".");
            return;
        }
        store.remove(house.id());
        p.sendMessage("§cДом " + house.title() + " удалён из реестра.");
    }

    /* ================= info / list ================= */

    private void handleInfo(CommandSender sender) {
        Player p = player(sender);
        if (p == null) return;
        House house = store.houseAt(p.getLocation());
        if (house == null) {
            p.sendMessage("§7Ты не внутри дома.");
            return;
        }
        p.sendMessage("§6=== " + house.title() + " ===");
        p.sendMessage("§eЦена: §7" + house.price);
        p.sendMessage("§eСтатус: " + (house.isOwned()
                ? "§aпродан, владелец: " + house.ownerName
                : "§bсвободен"));
        p.sendMessage("§eЗоны: §7interior=" + (house.interior != null ? "да" : "нет")
                + ", yard=" + (house.yard != null ? "да" : "нет"));
    }

    private void handleList(CommandSender sender, String[] args) {
        String town = args.length >= 2 ? String.join(" ", Arrays.asList(args).subList(1, args.length)) : null;
        List<House> list = new ArrayList<>();
        for (House h : store.all()) {
            if (town == null || h.town.equalsIgnoreCase(town)) list.add(h);
        }
        if (list.isEmpty()) {
            sender.sendMessage("§7Домов не найдено.");
            return;
        }
        sender.sendMessage("§6=== Домов в реестре: " + list.size() + " ===");
        for (House h : list) {
            sender.sendMessage("§e" + h.title() + " §7— " + h.price
                    + (h.isOwned() ? " §aвладелец: " + h.ownerName : " §bсвободен"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("create", "interior", "yard", "setprice",
                    "delete", "info", "list"), args[0]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String s : list) {
            if (s.toLowerCase().startsWith(lower)) out.add(s);
        }
        return out;
    }
}
