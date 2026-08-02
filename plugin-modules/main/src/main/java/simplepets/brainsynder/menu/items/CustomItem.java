package simplepets.brainsynder.menu.items;

import org.bsdevelopment.pluginutils.libs.json.JsonArray;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import simplepets.brainsynder.api.entity.IEntityPet;
import simplepets.brainsynder.api.inventory.CustomInventory;
import simplepets.brainsynder.api.inventory.Item;
import simplepets.brainsynder.api.pet.PetType;
import simplepets.brainsynder.api.plugin.SimplePets;
import simplepets.brainsynder.api.user.PetUser;
import simplepets.brainsynder.debug.DebugLevel;
import simplepets.brainsynder.utils.Scheduler;

import java.io.File;

public abstract class CustomItem extends Item {
    public static final String COMMANDS = "CommandsOnClick";
    public static final String TARGET = "target";
    protected static final String NAMESPACE = "namespace";

    public CustomItem(File file) {
        super(file);
    }

    public void runCommands(PetUser owner) {
        if (!hasKey(COMMANDS)) return;
        JsonArray array = (JsonArray) getValue(COMMANDS);

        String target = null;
        if (hasKey(TARGET)) {
            String string = getString(TARGET);
            switch (string.toLowerCase()) {
                case "player" -> target = "PLAYER";
                case "all" -> target = "ALL";
                default -> {
                    if (PetType.getPetType(string).isPresent()) {
                        target = string;
                    } else {
                        target = "PLAYER";
                        SimplePets.getDebugLogger().debug(DebugLevel.ERROR, "Custom Item '" + getName() + "' has an invalid 'target' please use \"target\": \"player\" or use \"all\" or the actual pet type");
                    }
                }
            }
        }

        if (target == null) {
            SimplePets.getDebugLogger().debug(DebugLevel.ERROR, "Custom Item '" + getName() + "' missing 'target' please add \"target\": \"player\" or use \"all\" or the actual pet type");
            return;
        }

        if (target.equals("ALL")) {
            owner.getPetEntities().forEach(entityPet -> {
                Scheduler.runEntity(entityPet.getEntity(), () -> {
                    Location location = entityPet.getEntity().getLocation();
                    String loc = location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();

                    array.forEach(jsonValue -> dispatch(jsonValue.asString()
                        .replace("{location}", loc)
                        .replace("{name}", owner.getOwnerName())
                        .replace("{type}", entityPet.getPetType().getName())
                    ));
                });
            });
            return;
        }

        if (target.equals("PLAYER")) {
            Player player = owner.getPlayer();
            Location location = player.getLocation();
            String loc = location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();

            array.forEach(jsonValue -> dispatch(jsonValue.asString()
                .replace("{location}", loc)
                .replace("{name}", owner.getOwnerName())
            ));
            return;
        }

        PetType.getPetType(target).ifPresent(type -> {
            owner.getPetEntity(type).ifPresent(entityPet -> {
                Scheduler.runEntity(entityPet.getEntity(), () -> {
                    Location location = entityPet.getEntity().getLocation();
                    String loc = location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();

                    array.forEach(jsonValue -> dispatch(jsonValue.asString()
                        .replace("{location}", loc)
                        .replace("{name}", owner.getOwnerName())
                        .replace("{type}", entityPet.getPetType().getName())
                    ));
                });
            });
        });
    }

    @Override
    public void onClick(PetUser user, CustomInventory inventory, IEntityPet pet) {
        runCommands(user);
    }

    private void dispatch(String command) {
        if (command == null) return;
        command = command.trim();
        if (command.startsWith("/")) command = command.substring(1);
        if (command.isEmpty()) return;
        String finalCommand = command;
        Scheduler.runGlobal(() -> Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), finalCommand));
    }
}
