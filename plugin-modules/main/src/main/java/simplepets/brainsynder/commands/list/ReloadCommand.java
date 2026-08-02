package simplepets.brainsynder.commands.list;

import org.bsdevelopment.pluginutils.command.CommandBuilder;
import org.bukkit.command.CommandSender;
import simplepets.brainsynder.PetCore;
import simplepets.brainsynder.api.plugin.config.MessageOption;
import simplepets.brainsynder.api.plugin.config.internal.ConfigEntry;
import simplepets.brainsynder.commands.PetCommandClass;
import simplepets.brainsynder.managers.InventoryManager;
import simplepets.brainsynder.managers.ItemManager;
import simplepets.brainsynder.utils.Scheduler;

public class ReloadCommand implements PetCommandClass {

    @Override
    public CommandBuilder build() {
        return CommandBuilder.create("reload")
                .withPermission("pet.commands.reload")
                .withDescription("Reloads a selected file/folder")
                .withSubcommand(buildConfigCommand())
                .withSubcommand(buildMessagesCommand())
                .withSubcommand(buildInventoriesCommand())
                .withSubcommand(buildParticlesCommand())
                .withSubcommand(buildPetsCommand())
                .executes((sender, args) -> reload(sender, () -> {
                    PetCore plugin = PetCore.getInstance();
                    plugin.getConfiguration().reload();
                    plugin.getConfiguration().initValues();
                    plugin.getMessageFile().reload();
                    plugin.getParticleHandler().reload(plugin);
                    plugin.reloadPetConfigManager();
                    ((InventoryManager) plugin.getGUIHandler()).initiate();
                    ((ItemManager) plugin.getItemHandler()).initiate();
                }, MessageOption.ALL_RELOADED));
    }

    public CommandBuilder buildConfigCommand() {
        return CommandBuilder.create("config")
                .withPermission("pet.commands.reload")
                .withDescription("Reloads the config file")
                .executes((sender, args) -> reload(sender, () -> {
                    PetCore plugin = PetCore.getInstance();
                    plugin.getConfiguration().reload();
                    plugin.getConfiguration().initValues();
                }, MessageOption.CONFIG_RELOADED));
    }

    public CommandBuilder buildMessagesCommand() {
        return CommandBuilder.create("messages")
                .withPermission("pet.commands.reload")
                .withDescription("Reloads the messages file")
                .executes((sender, args) -> reload(sender,
                    () -> PetCore.getInstance().getMessageFile().reload(), MessageOption.MESSAGES_RELOADED));
    }

    public CommandBuilder buildInventoriesCommand() {
        return CommandBuilder.create("inventories")
                .withPermission("pet.commands.reload")
                .withDescription("Reloads inventory and item files")
                .executes((sender, args) -> reload(sender, () -> {
                    PetCore plugin = PetCore.getInstance();
                    ((InventoryManager) plugin.getGUIHandler()).initiate();
                    ((ItemManager) plugin.getItemHandler()).initiate();
                }, MessageOption.INVENTORIES_RELOADED));
    }

    public CommandBuilder buildParticlesCommand() {
        return CommandBuilder.create("particles")
                .withPermission("pet.commands.reload")
                .withDescription("Reloads the particle files")
                .executes((sender, args) -> reload(sender, () -> {
                    PetCore plugin = PetCore.getInstance();
                    plugin.getParticleHandler().reload(plugin);
                }, MessageOption.PARTICLES_RELOADED));
    }

    public CommandBuilder buildPetsCommand() {
        return CommandBuilder.create("pets")
                .withPermission("pet.commands.reload")
                .withDescription("Reloads the pet config files")
                .executes((sender, args) -> reload(sender, () -> {
                    PetCore plugin = PetCore.getInstance();
                    plugin.reloadPetConfigManager();
                    InventoryManager.SELECTION.reloadAvailableTypes();
                }, MessageOption.PETS_RELOADED));
    }

    private void reload(CommandSender sender, Runnable task, ConfigEntry<String> message) {
        Scheduler.runGlobal(() -> {
            task.run();
            Scheduler.run(sender, () -> sender.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(message)));
        });
    }
}
