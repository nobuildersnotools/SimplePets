package simplepets.brainsynder.managers;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import simplepets.brainsynder.PetCore;
import simplepets.brainsynder.api.user.PetUser;
import simplepets.brainsynder.api.user.UserManagement;
import simplepets.brainsynder.impl.PetOwner;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class UserManager implements UserManagement {
    private final Map<UUID, PetUser> userMap;
    private final Map<String, UUID> uuidCache;
    private final PetCore PLUGIN;

    public UserManager(PetCore plugin) {
        this.PLUGIN = plugin;
        this.userMap = new ConcurrentHashMap<>();
        this.uuidCache = new ConcurrentHashMap<>();
    }

    @Override
    public void getPetUser(String username, Consumer<Optional<PetUser>> consumer) {
        if (uuidCache.containsKey(username)) {
            consumer.accept(getPetUser(uuidCache.get(username)));
            return;
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(username);
        if (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline()) {
            UUID uuid = offlinePlayer.getUniqueId();
            uuidCache.put(username, uuid);
            consumer.accept(getPetUser(uuid));
            return;
        }

        consumer.accept(Optional.empty());
    }

    @Override
    public Optional<PetUser> getPetUser(UUID uuid) {
        return getPetUser(Bukkit.getPlayer(uuid));
    }

    @Override
    public Optional<PetUser> getPetUser(Player player) {
        if (player == null) return Optional.empty();

        return Optional.of(userMap.computeIfAbsent(player.getUniqueId(), ignored -> new PetOwner(player)));
    }


    @Override
    public Collection<PetUser> getAllUsers() {
        return userMap.values();
    }

    @Override
    public boolean isUserCached(Player player) {
        return userMap.containsKey(player.getUniqueId());
    }


}
