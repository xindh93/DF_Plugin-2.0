package cjs.DF_Plugin.command;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.events.game.GameStartManager;
import org.bukkit.command.CommandSender;

public class StartGameCommand {
    private final GameStartManager gameStartManager;

    public StartGameCommand(DF_Main plugin) {
        this.gameStartManager = plugin.getGameStartManager();
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("§c이 명령어를 사용할 권한이 없습니다.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("stop")) {
            sender.sendMessage("§e게임을 중지합니다...");
            gameStartManager.stopGame();
        } else {
            sender.sendMessage("§e게임을 시작합니다...");
            gameStartManager.startGame();
        }

        return true;
    }
}