package simplepets.brainsynder.dialog;

import org.bsdevelopment.pluginutils.dialog.UniDialog;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import simplepets.brainsynder.PetCore;
import simplepets.brainsynder.api.event.user.PetRenameEvent;
import simplepets.brainsynder.api.pet.PetType;
import simplepets.brainsynder.api.plugin.SimplePets;
import simplepets.brainsynder.api.plugin.config.ConfigOption;
import simplepets.brainsynder.api.plugin.config.MessageOption;
import simplepets.brainsynder.api.user.PetUser;
import simplepets.brainsynder.utils.Scheduler;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RenameDialog {
    private static final String ACTION_ID = "simplepets_rename";
    private static final Map<UUID, PetType> pendingRenames = new ConcurrentHashMap<>();

    public static void register() {
        UniDialog.unregisterAction(ACTION_ID);
        UniDialog.registerAction(ACTION_ID, payload -> {
            UUID uuid = payload.owner();
            PetType type = pendingRenames.remove(uuid);
            if (type == null) return;

            Player player = Bukkit.getPlayer(uuid);
            if (player == null) return;

            Scheduler.runEntity(player, () -> SimplePets.getUserManager().getPetUser(player).ifPresent(user -> {
                String name = payload.textValue("name");
                if (name != null && name.isBlank()) name = null;
                if (name != null && name.equalsIgnoreCase("reset")) name = null;

                PetRenameEvent renameEvent = new PetRenameEvent(user, type, name);
                Bukkit.getPluginManager().callEvent(renameEvent);

                if (!renameEvent.isCancelled()) user.setPetName(renameEvent.getName(), type);
            }));
        });
    }

    public static void open(PetUser user, PetType type) {
        Player player = user.getPlayer();
        pendingRenames.put(player.getUniqueId(), type);

        int maxLength = ConfigOption.RENAME_LIMIT_CHARS_ENABLED.get()
                ? ConfigOption.RENAME_LIMIT_CHARS_NUMBER.get()
                : 50;

        UniDialog.notice()
                .title(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.RENAME_DIALOG_TITLE))
                .body(b -> b.text().text(
                        PetCore.getInstance().getMessageFile().getTranslation(MessageOption.RENAME_DIALOG_BODY)
                                .replace("{type}", type.getName())
                ))
                .input("name", i -> i.textInput()
                        .label(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.RENAME_DIALOG_INPUT_LABEL))
                        .maxLength(maxLength)
                )
                .action(a -> a
                        .label(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.RENAME_DIALOG_SUBMIT))
                        .dynamicCustom(ACTION_ID)
                )
                .open(player);
    }
}
