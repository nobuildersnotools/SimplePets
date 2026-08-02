package simplepets.brainsynder.commands.list;

import org.bsdevelopment.pluginutils.command.CommandBuilder;
import org.bsdevelopment.pluginutils.command.CommandPermission;
import org.bsdevelopment.pluginutils.command.arguments.PlayerArgument;
import org.bukkit.entity.Player;
import simplepets.brainsynder.PetCore;
import simplepets.brainsynder.api.plugin.SimplePets;
import simplepets.brainsynder.api.plugin.config.MessageOption;
import simplepets.brainsynder.commands.PetCommandClass;
import simplepets.brainsynder.menu.inventory.SelectionMenu;
import simplepets.brainsynder.utils.Scheduler;

public class GUICommand implements PetCommandClass {

    @Override
    public CommandBuilder build() {
        return CommandBuilder.create("gui")
                .withPermission("pet.commands.gui")
                .withDescription("Opens the pet gui")
                .withRequirement(sender -> sender instanceof Player)
                .withArguments(new PlayerArgument("player")
                        .setOptional(true)
                        .withPermission(CommandPermission.of("pet.commands.gui.other")))
                .executesPlayer((player, args) -> {
                    if (args.has("player")) {
                        if (!player.hasPermission("pet.commands.gui.other")) {
                            player.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.NO_PERMISSION));
                            return;
                        }
                        Player target = args.get("player");
                        Scheduler.runEntity(target, () -> SimplePets.getUserManager().getPetUser(target).ifPresent(user -> {
                            SimplePets.getGUIHandler().getInventory(SelectionMenu.class).ifPresent(selectionMenu -> selectionMenu.open(user));
                        }));
                        return;
                    }
                    SimplePets.getUserManager().getPetUser(player).ifPresent(user -> {
                        SimplePets.getGUIHandler().getInventory(SelectionMenu.class).ifPresent(selectionMenu -> selectionMenu.open(user));
                    });
                });
    }
}
