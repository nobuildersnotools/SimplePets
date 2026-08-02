package simplepets.brainsynder.commands.list;

import org.bsdevelopment.pluginutils.command.CommandBuilder;
import org.bsdevelopment.pluginutils.libs.json.Json;
import org.bsdevelopment.pluginutils.libs.json.JsonObject;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import simplepets.brainsynder.PetCore;
import simplepets.brainsynder.addon.AddonCloudData;
import simplepets.brainsynder.api.plugin.SimplePets;
import simplepets.brainsynder.api.plugin.config.MessageOption;
import simplepets.brainsynder.commands.PetCommandClass;
import simplepets.brainsynder.managers.AddonManager;
import simplepets.brainsynder.menu.inventory.AddonMenu;
import simplepets.brainsynder.utils.Utilities;
import simplepets.brainsynder.utils.Scheduler;

import java.io.File;
import java.util.Optional;

public class AddonCommand implements PetCommandClass {

    @Override
    public CommandBuilder build() {
        return CommandBuilder.create("addon")
                .withPermission("pet.commands.addon")
                .withDescription("Opens a GUI to download/toggle addons for the plugin")
                .withRequirement(sender -> sender instanceof Player)
                .withSubcommand(buildReloadCommand())
                .withSubcommand(buildInstallCommand())
                .withSubcommand(buildUpdateCommand())
                .executesPlayer((player, args) -> {
                    SimplePets.getUserManager().getPetUser(player).ifPresent(user -> {
                        SimplePets.getGUIHandler().getInventory(AddonMenu.class).ifPresent(selectionMenu -> selectionMenu.open(user));
                    });
                });
    }


    // Command: /pet addon install <addon>
    public CommandBuilder buildInstallCommand() {
        return CommandBuilder.create("install")
                .withPermission("pet.commands.addon.install")
                .withDescription("Installs an addon based on the name provided (Must be in our database and not already installed)")
                .withArguments(CLOUD_ADDONS)
                .executes((sender, args) -> {
                    String targetAddon = args.get("addon");
                    AddonManager manager = PetCore.getInstance().getAddonManager();

                    Optional<AddonCloudData> cloudOptional = manager.fetchCloudData(targetAddon);
                    if (cloudOptional.isEmpty()) {
                        sender.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.PREFIX) + " §c" + targetAddon + " is not a valid addon in our database.");
                        return;
                    }
                    AddonCloudData cloudData = cloudOptional.get();

                    if (manager.fetchAddon(cloudData.getName()).isPresent()) {
                        sender.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.PREFIX) + " §c" + targetAddon + " is already installed, Looking to update it try: §7/pet addon update " + targetAddon);
                        return;
                    }

                    sender.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.PREFIX) + " §7Attempting to install: '" + targetAddon + "'");
                    manager.downloadViaName(cloudData.getName(), cloudData.getDownloadURL(), () -> {
                        Scheduler.run(sender, () -> sender.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.PREFIX) + " §7" + targetAddon + " has been successfully installed!"));
                    });
                });
    }


    // Command: /pet addon update <addon>
    public CommandBuilder buildUpdateCommand() {
        return CommandBuilder.create("update")
                .withPermission("pet.commands.addon.update")
                .withDescription("Updates an addon based on the name provided (Must already be installed)")
                .withArguments(LOCAL_ADDONS)
                .executes((sender, args) -> {
                    String targetAddon = args.get("addon");
                    AddonManager manager = PetCore.getInstance().getAddonManager();
                    manager.fetchAddon(targetAddon).ifPresent(localData -> {
                        String name = localData.getName();

                        Utilities.getInputStreamString("https://bsdevelopment.org/addons/addons.json", result -> {
                            JsonObject json = (JsonObject) Json.parse(result);
                            if (!json.names().contains(name)) {
                                Scheduler.run(sender, () -> sender.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.PREFIX) + " §c" + name + " is not in the addon database: https://pluginwiki.us/addons/"));
                                return;
                            }

                            String url = ((JsonObject) json.get(name)).getString("url", null);
                            if (url == null) {
                                Scheduler.run(sender, () -> sender.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.PREFIX) + " §c" + name + " seems to be missing the download URL (Contact brainsynder)"));
                                return;
                            }

                            manager.update(localData, url, () -> {
                                Scheduler.run(sender, () -> sender.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.PREFIX) + " §7" + name + " has been successfully updated!"));
                            });
                        });
                    });
                });
    }


    // Command: /pet addon reload
    public CommandBuilder buildReloadCommand() {
        return CommandBuilder.create("reload")
                .withPermission("pet.commands.addon.reload")
                .withDescription("Reloads all addons by unloading them, then reloading them from the addons folder")
                .executes((sender, args) -> {
                    AddonManager manager = PetCore.getInstance().getAddonManager();
                    manager.cleanup();
                    File folder = manager.getFolder();
                    Scheduler.runGlobalLater(() -> {
                        if (!folder.exists()) return;
                        for (File file : folder.listFiles()) {
                            manager.loadAddon(file);
                        }

                        Scheduler.run(sender, () -> sender.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.PREFIX) + ChatColor.GRAY + "All Addons have been reloaded"));
                    }, 1);
                });
    }
}
