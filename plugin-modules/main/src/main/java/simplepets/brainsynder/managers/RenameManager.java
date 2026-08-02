package simplepets.brainsynder.managers;

import org.bsdevelopment.pluginutils.inventory.AnvilGUI;
import org.bsdevelopment.pluginutils.inventory.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.StringPrompt;
import org.bukkit.plugin.Plugin;
import simplepets.brainsynder.PetCore;
import simplepets.brainsynder.api.event.user.PetRenameEvent;
import simplepets.brainsynder.api.pet.PetType;
import simplepets.brainsynder.api.plugin.config.ConfigOption;
import simplepets.brainsynder.api.plugin.config.MessageOption;
import simplepets.brainsynder.api.user.PetUser;
import simplepets.brainsynder.dialog.RenameDialog;
import simplepets.brainsynder.utils.RenameType;
import simplepets.brainsynder.utils.Scheduler;

import java.util.Arrays;
import java.util.Collections;

public class RenameManager {
    private final PetCore plugin;

    public RenameManager(PetCore plugin) {
        this.plugin = plugin;
        RenameDialog.register();
    }

    public void renameViaAnvil(PetUser user, PetType type) {
        AnvilGUI.Builder builder = new AnvilGUI.Builder().plugin(PetCore.getInstance());
        builder.itemLeft(ItemBuilder.of(Material.NAME_TAG).withName(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.RENAME_ANVIL_TAG)).build());
        builder.onClick((slot, stateSnapshot) -> {
            if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

            String name = stateSnapshot.text();

            if (name.equalsIgnoreCase("reset")) name = null;
            PetRenameEvent renameEvent = new PetRenameEvent(user, type, name);
            Bukkit.getPluginManager().callEvent(renameEvent);

            if (!renameEvent.isCancelled()) user.setPetName(renameEvent.getName(), type);
            return Arrays.asList(AnvilGUI.ResponseAction.close());
        });
        builder.title(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.RENAME_ANVIL_TITLE));
        builder.open(user.getPlayer());

    }

    public void renameViaChat(PetUser user, PetType type) {
        ConversationFactory factory = new ConversationFactory(PetCore.getInstance());
        factory.withLocalEcho(false)
            .withFirstPrompt(new PetRenamePrompt())
            .addConversationAbandonedListener(event -> {
                if (event.gracefulExit()) {
                    Scheduler.runEntity(user.getPlayer(), () -> {
                        String name = event.getContext().getSessionData("name").toString(); // It's a string prompt for a reason
                        if (name.equalsIgnoreCase("cancel")) {
                            user.getPlayer().sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.RENAME_VIA_CHAT_CANCEL));
                            return;
                        }
                        if (name.equalsIgnoreCase("reset")) name = null;
                        PetRenameEvent renameEvent = new PetRenameEvent(user, type, name);
                        Bukkit.getPluginManager().callEvent(renameEvent);

                        if (!renameEvent.isCancelled()) user.setPetName(renameEvent.getName(), type);
                    });
                }
            });
        factory.buildConversation(user.getPlayer()).begin();
    }

    public void renameViaDialog(PetUser user, PetType type) {
        RenameDialog.open(user, type);
    }

    public void renameViaSign(PetUser user, PetType type) {
        Plugin protocol = plugin.getServer().getPluginManager().getPlugin("ProtocolLib");
        if ((protocol == null) || (!protocol.isEnabled())) {
            if (RenameType.getType(ConfigOption.RENAME_TYPE.get(), RenameType.ANVIL) == RenameType.SIGN)
                ConfigOption.RENAME_TYPE.set(RenameType.ANVIL.name(), true);
            return;
        }
        simplepets.brainsynder.hooks.ProtocolHook.renameViaSign(user, type);
    }

    private static class PetRenamePrompt extends StringPrompt {
        @Override
        public String getPromptText(ConversationContext context) {
            return PetCore.getInstance().getMessageFile().getTranslation(MessageOption.RENAME_VIA_CHAT);
        }

        @Override
        public Prompt acceptInput(ConversationContext context, String answer) {
            context.setSessionData("name", answer);
            return END_OF_CONVERSATION;
        }
    }
}
