package simplepets.brainsynder.nms.pathfinder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import org.bsdevelopment.pluginutils.utilities.MathUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import simplepets.brainsynder.api.event.entity.movment.PetTeleportEvent;
import simplepets.brainsynder.api.other.ParticleHandler;
import simplepets.brainsynder.api.pet.CommandReason;
import simplepets.brainsynder.api.plugin.SimplePets;
import simplepets.brainsynder.api.plugin.config.ConfigOption;
import simplepets.brainsynder.api.user.PetUser;
import simplepets.brainsynder.nms.entity.EntityPet;
import simplepets.brainsynder.nms.helper.VersionHelper;
import simplepets.brainsynder.utils.Scheduler;

import java.util.EnumSet;

public class PathfinderFollowPlayer extends Goal {
    private final EntityPet entity;
    private final PathNavigation navigation;
    private final float maxRange;
    private final double minRange;
    private final int teleportDistance;

    private PetUser user;
    private Player player;
    private BlockPos targetPos;

    public PathfinderFollowPlayer(EntityPet entity) {
        this.entity = entity;
        this.maxRange = ConfigOption.PATHFINDING_MAX_DISTANCE.get();
        this.minRange = ConfigOption.PATHFINDING_MIN_DISTANCE.get();
        this.teleportDistance = ConfigOption.PATHFINDING_TELEPORT_DISTANCE.get();

        navigation = entity.getNavigation();

        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        VersionHelper.setFollowAttributes(entity, maxRange);
    }

    @Override // Runs per tick
    public boolean canUse() {
        // Simple Check in case user is null
        if (user == null) {
            this.user = entity.getPetUser();
        }

        if (user == null) return false; // Failed: no user found
        if (user.getPlayer() == null) return false; // Failed: no player found
        if (entity == null) return false; // Failed: entity is missing ?!?
        if (!Scheduler.isOwnedByCurrentRegion(user.getPlayer())) {
            entity.teleportToOwner();
            return false;
        }
        this.player = VersionHelper.getEntityHandle(user.getPlayer());

        if (!user.getPlayer().isOnline()) return false; // Failed: player is not online

        // Failed: player is riding a mob, and config denies pet from following player
        if (user.getPlayer().isInsideVehicle()
                && !ConfigOption.PATHFINDING_FOLLOW_WHEN_RIDING.get()) return false;
        if (!user.hasPets()) return false; // Failed: player has no pets

        // Failed: pet and player are in different worlds
        if (!VersionHelper.getEntityLevel(player).getWorld().getName()
                .equals(VersionHelper.getEntityLevel(entity).getWorld().getName())) return false;

        if (entity.isFlightEnabled()) {
            int hoverOffset = Math.max(2, (int) entity.getBoundingBox().getYsize());
            targetPos = new BlockPos(randomize(player.getX()), player.getBlockY() + hoverOffset, randomize(player.getZ()));
        } else {
            targetPos = new BlockPos(randomize(player.getX()), player.getBlockY(), randomize(player.getZ()));
        }
        return true;
    }

    @Override // called after start() and if canUse() == true
    public boolean canContinueToUse() {
        if (!Scheduler.isOwnedByCurrentRegion(user.getPlayer())) return false;
        return !navigation.isInProgress() && (entity.distanceToSqr(player) < (double) (this.maxRange * this.maxRange));
    }

    @Override
    public void tick() {
        if (!Scheduler.isOwnedByCurrentRegion(user.getPlayer())) {
            entity.teleportToOwner();
            return;
        }
        if (entity.distanceToSqr(this.player) >= teleportDistance) { // Will teleport the pet if the player is more than 155 blocks away
            if (entity.distanceTo(this.player) >= 80) { // Will teleport the pet if the player is more than 144 blocks away
                entity.teleportToOwner(); // Will ignore all checks and just teleport to the player
            } else {
                this.tryTeleport();
            }
        }
    }

    @Override // Set navigation here
    public void start() {
        // Failed: pet is in the air and is not a flying pet
        if ( (!entity.isFlightEnabled()) && (!entity.onGround())) return;

        Path path = navigation.createPath(targetPos, 1);
        navigation.moveTo(path, entity.getAttribute(Attributes.MOVEMENT_SPEED).getValue());
    }

    @Override
    public void stop() {
        navigation.stop();
    }

    private void tryTeleport() {
        BlockPos blockPosition = player.blockPosition();
        double distance = this.minRange;

        for (int i = 0; i < 15; ++i) {
            double x = this.getRandomInt(-distance, distance);
            double y = this.getRandomInt(-1, 1);
            double z = this.getRandomInt(-distance, distance);
            boolean flag = this.tryTeleportTo(blockPosition.getX() + x, blockPosition.getY() + y, blockPosition.getZ() + z);
            if (flag) return;
        }

        entity.teleportToOwner();
    }

    private boolean tryTeleportTo(double x, double y, double z) {
        if (Math.abs(x - player.getX()) < 2.0D && Math.abs(z - player.getZ()) < 2.0D) return false;

        PetTeleportEvent event = new PetTeleportEvent(entity, new Location(user.getPlayer().getWorld(), x, y, z));
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        if (!this.canTeleportTo(new BlockPos((int) x, (int) y, (int) z))) return false;
        VersionHelper.moveTo(entity, x + 0.5D, y, z + 0.5D, this.entity.getYRot(), this.entity.getXRot());
        SimplePets.getPetUtilities().runPetCommands(CommandReason.TELEPORT, user, entity.getPetType());
        SimplePets.getParticleHandler().sendParticle(ParticleHandler.Reason.TELEPORT, user.getPlayer(), entity.getEntity().getLocation());
        this.navigation.stop();
        return true;
    }

    private boolean canTeleportTo(BlockPos blockposition) {
        if (!VersionHelper.isWalkable(entity, blockposition.mutable())) return false;

        BlockPos position = blockposition.subtract(this.entity.blockPosition());
        return VersionHelper.getEntityLevel(entity).noCollision(this.entity, this.entity.getBoundingBox().move(position));
    }

    private int randomize (double value) {
        return (int) (value + ( MathUtil.randomInt((int) -this.minRange, (int) this.minRange) ));
    }

    private double getRandomInt(double min, double max) {
        return MathUtil.randomFloat((float) min, (float) max);
    }
}
