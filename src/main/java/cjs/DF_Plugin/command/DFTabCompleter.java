package cjs.DF_Plugin.command;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DFTabCompleter implements TabCompleter {

    private final DF_Main plugin;

    public DFTabCompleter(DF_Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            suggestions.addAll(Arrays.asList("createclan", "deleteclan"));
            if (sender.hasPermission("df.admin")) {
                suggestions.add("admin");
            }
        } else if (args.length >= 2) {
            String subCommand = args[0].toLowerCase();
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

            switch (subCommand) {
                case "admin":
                    handleAdminTabComplete(sender, subArgs, suggestions);
                    break;
                case "createclan":
                    handleCreateClanTabComplete(sender, subArgs, suggestions);
                    break;
                case "deleteclan":
                    handleDeleteClanTabComplete(sender, subArgs, suggestions);
                    break;
            }
        }

        StringUtil.copyPartialMatches(args[args.length - 1], suggestions, completions);
        Collections.sort(completions);
        return completions;
    }

    private void handleCreateClanTabComplete(CommandSender sender, String[] subArgs, List<String> suggestions) {
        if (subArgs.length == 1) {
            suggestions.addAll(Arrays.asList("setname", "color", "confirm"));
        } else if (subArgs.length == 2 && subArgs[0].equalsIgnoreCase("color")) {
            suggestions.addAll(Arrays.asList("prev", "next"));
        }
    }

    private void handleDeleteClanTabComplete(CommandSender sender, String[] subArgs, List<String> suggestions) {
        if (subArgs.length == 1) {
            // 1단계 확인, 2단계 확인
            suggestions.addAll(Arrays.asList("confirm", "finalconfirm"));
        }
    }

    private void handleGameModeTabComplete(String[] subArgs, List<String> suggestions) {
        if (subArgs.length == 1) {
            suggestions.addAll(Arrays.asList("darkforest", "pylon", "upgrade"));
        }
    }

    private void handleSettingsTabComplete(String[] subArgs, List<String> suggestions) {
        if (subArgs.length == 1) {
            suggestions.addAll(Arrays.asList("death", "pylon", "worldborder", "utility", "openchant", "bossmobstrength", "detailsettings", "resetsettings"));
        } else if (subArgs.length == 2) {
            String category = subArgs[0].toLowerCase();
            switch (category) {
                case "openchant":
                    suggestions.addAll(Arrays.asList("breach", "thorns"));
                    break;
                case "bossmobstrength":
                    suggestions.addAll(Arrays.asList("ender_dragon", "wither"));
                    break;
            }
        } else if (subArgs.length == 3) {
            String category = subArgs[0].toLowerCase();
            if (category.equals("openchant")) {
                suggestions.addAll(Arrays.asList("true", "false"));
            }
        }
    }

    private void handleAdminTabComplete(CommandSender sender, String[] subArgs, List<String> suggestions) {
        if (!sender.hasPermission("df.admin")) return;

        if (subArgs.length == 1) { // 어드민 명령어 1단계
            suggestions.addAll(Arrays.asList(
                    "gamemode", "settings", "set", "getweapon", "clan",
                    "register", "controlender", "unban", "game", "getitem", "supplydrop"
            ));
        } else if (subArgs.length >= 2) { // 어드민 명령어 2단계 이상
            String adminSubCommand = subArgs[0].toLowerCase();
            String[] adminSubArgs = Arrays.copyOfRange(subArgs, 1, subArgs.length);
            switch (adminSubCommand) {
                case "gamemode":
                    handleGameModeTabComplete(adminSubArgs, suggestions);
                    break;
                case "settings":
                    handleSettingsTabComplete(adminSubArgs, suggestions);
                    break;
                case "getweapon":
                    handleAdminGetWeaponTabComplete(adminSubArgs, suggestions);
                    break;
                case "clan":
                    handleAdminClanTabComplete(adminSubArgs, suggestions);
                    break;
                case "controlender":
                    if (adminSubArgs.length == 1) {
                        suggestions.addAll(Arrays.asList("open", "openafter", "close"));
                    }
                    break;
                case "game":
                    if (adminSubArgs.length == 1) {
                        suggestions.addAll(Arrays.asList("start", "stop"));
                    }
                    break;
                case "unban": {
                    plugin.getPlayerDeathManager().getDeadPlayers().keySet().forEach(uuid -> {
                        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                        if (player.getName() != null) {
                            suggestions.add(player.getName());
                        }
                    });
                    break;
                }
                case "getitem":
                    handleAdminGetItemTabComplete(adminSubArgs, suggestions);
                    break;
                case "supplydrop":
                    if (adminSubArgs.length == 1) {
                        suggestions.add("start");
                    }
                    break;
            }
        }
    }

    private void handleAdminGetItemTabComplete(String[] subArgs, List<String> suggestions) {
        if (subArgs.length == 1) {
            suggestions.addAll(Arrays.asList("main_core", "aux_core", "master_compass", "upgrade_stone", "magic_stone", "return_scroll"));
        }
    }

    private void handleAdminGetWeaponTabComplete(String[] subArgs, List<String> suggestions) {
        if (subArgs.length == 1) {
            // 강화 가능한 아이템 목록 제안 (최신 ProfileRegistry 사용)
            suggestions.addAll(
                    Arrays.stream(Material.values())
                            .filter(m -> plugin.getUpgradeManager().getProfileRegistry().getProfile(m) != null)
                            .map(m -> m.name().toLowerCase())
                            .collect(Collectors.toList())
            );
        } else if (subArgs.length == 2) {
            suggestions.addAll(Arrays.asList("1", "5", "10"));
        }
    }

    private void handleAdminClanTabComplete(String[] subArgs, List<String> suggestions) {
        if (subArgs.length == 1) { // add, remove
            suggestions.addAll(Arrays.asList("add", "remove"));
        } else if (subArgs.length == 2) { // <플레이어>
            // 온라인 플레이어 이름 제안
            suggestions.addAll(
                    Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .collect(Collectors.toList())
            );
        } else if (subArgs.length == 3 && "add".equalsIgnoreCase(subArgs[0])) { // add <플레이어> <클랜>
            suggestions.addAll(plugin.getClanManager().getClanNames());
        }
    }

}