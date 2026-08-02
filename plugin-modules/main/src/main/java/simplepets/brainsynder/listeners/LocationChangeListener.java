package simplepets.brainsynder.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import simplepets.brainsynder.api.plugin.SimplePets;
import simplepets.brainsynder.api.plugin.config.ConfigOption;
import simplepets.brainsynder.impl.PetOwner;
import simplepets.brainsynder.utils.Scheduler;

public class LocationChangeListener implements Listener {

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        SimplePets.getUserManager().getPetUser(event.getPlayer()).ifPresent(user ->
            ((PetOwner) user).updateLocation(event.getTo()));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!ConfigOption.REMOVE_PET_ON_WORLD_CHANGE.get()) return;

        SimplePets.getUserManager().getPetUser(event.getPlayer()).ifPresent(user -> {
            ((PetOwner) user).updateLocation(event.getPlayer().getLocation());
            user.cacheAndRemove();
            Scheduler.runEntityLater(event.getPlayer(), user::summonCachedPets, 40);
        });
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.UNKNOWN) return;

        SimplePets.getUserManager().getPetUser(event.getPlayer()).ifPresent(user -> {
            ((PetOwner) user).updateLocation(event.getTo());
            user.cacheAndRemove();
            Scheduler.runEntityLater(event.getPlayer(), user::summonCachedPets, 40);
        });
    }

}
