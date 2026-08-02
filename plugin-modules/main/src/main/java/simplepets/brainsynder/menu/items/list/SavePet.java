package simplepets.brainsynder.menu.items.list;

import org.bsdevelopment.nbt.StorageTagCompound;
import org.bsdevelopment.pluginutils.inventory.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import simplepets.brainsynder.PetCore;
import simplepets.brainsynder.api.Namespace;
import simplepets.brainsynder.api.entity.IEntityPet;
import simplepets.brainsynder.api.inventory.CustomInventory;
import simplepets.brainsynder.api.inventory.Item;
import simplepets.brainsynder.api.pet.PetType;
import simplepets.brainsynder.api.plugin.config.ConfigOption;
import simplepets.brainsynder.api.plugin.config.MessageOption;
import simplepets.brainsynder.api.user.PetUser;
import simplepets.brainsynder.managers.InventoryManager;
import simplepets.brainsynder.menu.inventory.PetSelectorMenu;
import simplepets.brainsynder.utils.Scheduler;
import simplepets.brainsynder.utils.Utilities;

import java.io.File;

@Namespace(namespace = "savepet")
public class SavePet extends Item {
    public SavePet(File file) {
        super(file);
    }

    @Override
    public ItemBuilder getDefaultItem() {
        return ItemBuilder.of(Material.CHAIN_COMMAND_BLOCK)
            .withName("&#e3c79a&lSave Pet")
            .addLore("&7Click here to save your current", "&7pet for you to spawn later");
    }

    @Override
    public boolean addItemToInv(PetUser user, CustomInventory inventory) {
        return user.canSaveMorePets();
    }

    @Override
    public void onClick(PetUser masterUser, CustomInventory inventory, IEntityPet pet) {
        if (!masterUser.hasPets()) return;

        if (pet != null) {
            savePet(masterUser, inventory, pet);
            return;
        }

        if (masterUser.getPetEntities().size() == 1) {
            masterUser.getPetEntities().stream().findFirst().ifPresent(iEntityPet -> {
                savePet(masterUser, inventory, iEntityPet);
            });
            return;
        }
        PetSelectorMenu menu = InventoryManager.SELECTOR;
        menu.setTask(masterUser.getPlayer().getName(), (user, type) -> {
            user.getPetEntity(type).ifPresent(entity -> {
                savePet(user, inventory, entity);
            });
        });
        menu.open(masterUser, 1, inventory.getTitle());
    }

    private boolean canSavePet(PetUser user, IEntityPet entityPet) {
        if (!ConfigOption.PET_SAVES_ENABLED.get()) return false;
        Player player = user.getPlayer();

        if (!user.canSaveMorePets()) {
            player.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.PET_SAVES_LIMIT_REACHED));
            return false;
        }

        if (player.hasPermission("pet.saves." + entityPet.getPetType().getName() + ".bypass")) return true;

        int saveLimit = Utilities.parseTypeSaveLimit(entityPet.getPetType());
        if (saveLimit < 0) return true;

        int typeCount = 0;
        for (PetUser.Entry<PetType, StorageTagCompound> entry : user.getSavedPets()) {
            if (entry.getKey() == entityPet.getPetType()) typeCount++;
        }

        if (typeCount < Utilities.getPermissionAmount(player, saveLimit, "pet.saves." + entityPet.getPetType().getName() + ".")) {
            return true;
        }
        player.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.PET_SAVES_LIMIT_REACHED_TYPE).replace("{type}", entityPet.getPetType().getName()));
        return false;
    }

    private void savePet(PetUser user, CustomInventory inventory, IEntityPet entityPet) {
        if (!canSavePet(user, entityPet)) {
            Scheduler.runEntityLater(user.getPlayer(), () -> inventory.open(user), 1L);
            return;
        }

        Scheduler.callEntity(entityPet.getEntity(), () -> entityPet.asCompound())
            .whenComplete((compound, throwable) -> Scheduler.runEntity(user.getPlayer(), () -> {
                if (throwable == null && compound != null) {
                    if (entityPet.getPetType() == PetType.ARMOR_STAND) compound.setBoolean("restricted", true);
                    user.addPetSave(compound);
                }
                inventory.open(user);
            }));
    }
}
