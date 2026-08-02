package simplepets.brainsynder.menu.items.list;

import org.bsdevelopment.pluginutils.inventory.ItemBuilder;
import org.bukkit.Material;
import simplepets.brainsynder.api.Namespace;
import simplepets.brainsynder.api.entity.IEntityPet;
import simplepets.brainsynder.api.inventory.CustomInventory;
import simplepets.brainsynder.api.inventory.Item;
import simplepets.brainsynder.api.plugin.config.ConfigOption;
import simplepets.brainsynder.api.user.PetUser;
import simplepets.brainsynder.managers.InventoryManager;
import simplepets.brainsynder.menu.inventory.PetSelectorMenu;
import simplepets.brainsynder.utils.Scheduler;

import java.io.File;

@Namespace(namespace = "name")
public class Name extends Item {
    public Name(File file) {
        super(file);
    }

    @Override
    public ItemBuilder getDefaultItem() {
        return ItemBuilder.of(Material.NAME_TAG).withName("&#99ffac&lName Pet");
    }

    @Override
    public boolean addItemToInv(PetUser owner, CustomInventory inventory) {
        return ConfigOption.RENAME_ENABLED.get();
    }

    @Override
    public void onClick(PetUser masterUser, CustomInventory inventory, IEntityPet pet) {
        if (!masterUser.hasPets()) return;

        if (pet != null) {
            masterUser.getPlayer().closeInventory();
            Scheduler.runEntityLater(masterUser.getPlayer(), () ->
                masterUser.getPlayer().performCommand("pet rename " + pet.getPetType().getName()), 2);
            return;
        }

        PetSelectorMenu menu = InventoryManager.SELECTOR;
        menu.setTask(masterUser.getPlayer().getName(), (user, type) -> {
            user.getPlayer().closeInventory();
            Scheduler.runEntityLater(masterUser.getPlayer(), () ->
                user.getPlayer().performCommand("pet rename " + type.getName()), 2);
        });
        menu.open(masterUser, 1, inventory.getTitle());
    }
}
