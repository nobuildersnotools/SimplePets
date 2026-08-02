package simplepets.brainsynder.commands.list;

import org.bsdevelopment.pluginutils.command.CommandBuilder;
import org.bsdevelopment.pluginutils.command.CommandPermission;
import org.bsdevelopment.pluginutils.command.arguments.PlayerArgument;
import org.bukkit.entity.Player;
import simplepets.brainsynder.PetCore;
import simplepets.brainsynder.api.pet.PetType;
import simplepets.brainsynder.api.plugin.SimplePets;
import simplepets.brainsynder.api.plugin.config.MessageOption;
import simplepets.brainsynder.commands.PetCommandClass;
import simplepets.brainsynder.managers.InventoryManager;
import simplepets.brainsynder.utils.Scheduler;

public class DataCommand implements PetCommandClass {

    @Override // Command: /pet data <type>
    public CommandBuilder build() {
        return CommandBuilder.create("data")
                .withPermission("pet.commands.data")
                .withDescription("Opens the pet data GUI for the selected pet type")
                .withRequirement(sender -> sender instanceof Player)
                .withSubcommand(buildTargetCommand())
                .withArguments(ALL_PET_TYPES)
                .executesPlayer((player, args) -> {
                    PetType type = args.get("type");

                    SimplePets.getUserManager().getPetUser(player).ifPresent(user -> {
                        if (!SimplePets.getSpawnUtil().isRegistered(type)) {
                            player.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.PET_NOT_REGISTERED).replace("{type}", type.getName()));
                            return;
                        }

                        InventoryManager.PET_DATA.setType(player, type);
                        InventoryManager.PET_DATA.open(user);
                    });
                });
    }


    // Command: /pet data target [player] <type>
    public CommandBuilder buildTargetCommand() {
        return CommandBuilder.create("target")
                .withPermission("pet.commands.data.target")
                .withDescription("")
                .withRequirement(sender -> sender instanceof Player)
                .withArguments(new PlayerArgument("player")
                        .setOptional(true)
                        .withPermission(CommandPermission.of("pet.commands.data.other")))
                .withArguments(ALL_PET_TYPES)
                .executesPlayer((player, args) -> {
                    Player target = args.getOrDefault("player", player);
                    PetType type = args.get("type");

                    if (!SimplePets.getSpawnUtil().isRegistered(type)) {
                        player.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.PET_NOT_REGISTERED).replace("{type}", type.getName()));
                        return;
                    }
                    Scheduler.runEntity(target, () -> SimplePets.getUserManager().getPetUser(target).ifPresent(user -> {
                        InventoryManager.PET_DATA.setType(target, type);
                        InventoryManager.PET_DATA.open(user);
                    }));
                });
    }
}
