package simplepets.brainsynder.menu.items.list;

import org.bsdevelopment.pluginutils.inventory.ItemBuilder;
import simplepets.brainsynder.api.Namespace;
import simplepets.brainsynder.api.entity.IEntityPet;
import simplepets.brainsynder.api.inventory.CustomInventory;
import simplepets.brainsynder.api.inventory.Item;
import simplepets.brainsynder.api.plugin.config.ConfigOption;
import simplepets.brainsynder.api.user.PetUser;
import simplepets.brainsynder.managers.InventoryManager;
import simplepets.brainsynder.menu.inventory.DataMenu;
import simplepets.brainsynder.menu.inventory.PetSelectorMenu;
import simplepets.brainsynder.utils.Scheduler;

import java.io.File;

@Namespace(namespace = "remove")
public class Remove extends Item {

    public Remove(File file) {
        super(file);
    }

    @Override
    public ItemBuilder getDefaultItem() {
        return ItemBuilder.playerSkull("http://textures.minecraft.net/texture/beb588b21a6f98ad1ff4e085c552dcb050efc9cab427f46048f18fc803475f7")
            .withName("&#b35349&lRemove Pets").addLore("&#d1c9c9Click Here to remove this pet", "&#d1c9c9... or all pets if you have the selector screen open.", "&#d1c9c9Shift+Click to select a pet");
    }

    @Override
    public void onClick(PetUser user, CustomInventory inventory, IEntityPet pet) {
        if (!user.hasPets()) return;
        if (!ConfigOption.MISC_TOGGLES_REMOVE_ALL_PETS.get()) {
            onShiftClick(user, inventory, pet);
            return;
        }
        if (pet != null) {
            user.removePet(pet.getPetType());
            if (inventory instanceof DataMenu) user.updateDataMenu();
            return;
        }
        user.removePets();
        if (inventory instanceof DataMenu) user.updateDataMenu();
    }

    @Override
    public void onShiftClick(PetUser masterUser, CustomInventory inventory) {
        if (!masterUser.hasPets()) return;
        if (masterUser.getPetEntities().size() == 1) {
            if (ConfigOption.MISC_TOGGLES_AUTO_CLOSE_REMOVE.get())
                masterUser.getPlayer().closeInventory();
            Scheduler.runEntityLater(masterUser.getPlayer(), () ->
                masterUser.getPetEntities().stream().findFirst().ifPresent(iEntityPet -> {
                    masterUser.removePet(iEntityPet.getPetType());
                    if (inventory instanceof DataMenu) masterUser.updateDataMenu();
                }), 2);
            return;
        }
        PetSelectorMenu menu = InventoryManager.SELECTOR;
        menu.setTask(masterUser.getPlayer().getName(), (user, type) -> {
            if (ConfigOption.MISC_TOGGLES_AUTO_CLOSE_REMOVE.get())
                user.getPlayer().closeInventory();
            Scheduler.runEntityLater(masterUser.getPlayer(), () -> {
                user.removePet(type);
                if (inventory instanceof DataMenu) user.updateDataMenu();
            }, 2);
        });
        menu.open(masterUser, 1, inventory.getTitle());
    }
}
