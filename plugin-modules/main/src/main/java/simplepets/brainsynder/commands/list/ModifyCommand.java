package simplepets.brainsynder.commands.list;

import org.bsdevelopment.nbt.StorageTagCompound;
import org.bsdevelopment.nbt.io.StorageStringParser;
import org.bsdevelopment.pluginutils.command.CommandBuilder;
import org.bsdevelopment.pluginutils.command.arguments.PlayerArgument;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import simplepets.brainsynder.PetCore;
import simplepets.brainsynder.api.entity.misc.IEntityControllerPet;
import simplepets.brainsynder.api.pet.PetType;
import simplepets.brainsynder.api.plugin.SimplePets;
import simplepets.brainsynder.api.plugin.config.MessageOption;
import simplepets.brainsynder.commands.PetCommandClass;
import simplepets.brainsynder.utils.Scheduler;

public class ModifyCommand implements PetCommandClass {

    @Override
    public CommandBuilder build() {
        return CommandBuilder.create("modify")
                .withPermission("pet.commands.modify")
                .withDescription("Modifies the NBT of the player's active pet")
                .withArguments(ACCESSIBLE_PET_TYPES)
                .withArguments(PET_NBT)
                .withSubcommand(buildTargetCommand())
                .executesPlayer((player, args) -> {
                    PetType type = args.get("type");
                    StorageTagCompound nbtArg = args.get("nbt");

                    SimplePets.getUserManager().getPetUser(player).ifPresent(user -> {
                        user.getPetEntity(type).ifPresent(entityPet -> modify(player, entityPet, type, nbtArg));
                    });
                });
    }

    public CommandBuilder buildTargetCommand() {
        return CommandBuilder.create("target")
                .withPermission("pet.commands.modify.other")
                .withDescription("Modifies the NBT of another player's active pet")
                .withArguments(new PlayerArgument("player"))
                .withArguments(ALL_PET_TYPES)
                .withArguments(PET_NBT)
                .executes((sender, args) -> {
                    Player target = args.get("player");
                    PetType type = args.get("type");
                    StorageTagCompound nbtArg = args.get("nbt");

                    if (!SimplePets.getSpawnUtil().isRegistered(type)) {
                        sender.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.PET_NOT_REGISTERED).replace("{type}", type.getName()));
                        return;
                    }

                    SimplePets.getUserManager().getPetUser(target).ifPresent(user -> {
                        user.getPetEntity(type).ifPresent(entityPet -> modify(sender, entityPet, type, nbtArg));
                    });
                });
    }

    private void modify(CommandSender sender, simplepets.brainsynder.api.entity.IEntityPet entityPet,
                        PetType type, StorageTagCompound nbtArg) {
        simplepets.brainsynder.api.entity.IEntityPet target = entityPet;
        if (target instanceof IEntityControllerPet controller) target = controller.getVisibleEntity();
        simplepets.brainsynder.api.entity.IEntityPet finalTarget = target;
        Scheduler.runEntity(finalTarget.getEntity(), () -> {
            try {
                StorageTagCompound compound = StorageStringParser.getTagFromJson(nbtArg.toString());
                finalTarget.applyCompound(compound);
                Scheduler.run(sender, () -> {
                    String message = PetCore.getInstance().getMessageFile().getTranslation(MessageOption.MODIFY_COMPOUND)
                        .replace("{compound}", compound.toString());
                    if (!message.isEmpty()) sender.sendMessage(message.replaceAll("(?i):0b", ":false").replaceAll("(?i):1b", ":true"));
                    sender.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.MODIFY_APPLIED)
                        .replace("{type}", type.getName()));
                });
            } catch (Exception exception) {
                Scheduler.run(sender, () -> {
                    sender.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.INVALID_NBT));
                    String errorMessage = PetCore.getInstance().getMessageFile().getTranslation(MessageOption.INVALID_NBT_MESSAGE)
                        .replace("{message}", String.valueOf(exception.getMessage())
                            .replaceAll("(?i):0b", ":false").replaceAll("(?i):1b", ":true"));
                    if (!errorMessage.isEmpty()) sender.sendMessage(errorMessage);
                });
            }
        });
    }
}
