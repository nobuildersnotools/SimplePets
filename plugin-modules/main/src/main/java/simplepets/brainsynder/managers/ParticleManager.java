package simplepets.brainsynder.managers;

import org.bsdevelopment.pluginutils.particle.ParticleConfig;
import org.bsdevelopment.pluginutils.particle.ParticlePayload;
import org.bsdevelopment.pluginutils.particle.ParticleTypeWrapper;
import org.bsdevelopment.pluginutils.particle.ParticleUtils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import simplepets.brainsynder.PetCore;
import simplepets.brainsynder.api.other.ParticleHandler;
import simplepets.brainsynder.api.plugin.config.ConfigOption;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ParticleManager implements ParticleHandler {
    private final Map<Reason, ParticleConfig> particles = new ConcurrentHashMap<>();
    private File folder;

    public ParticleManager(PetCore plugin) {
        reload(plugin);
    }

    private void sendToPlayer(ParticleConfig config, Location location) {
        Particle particle = config.handle().resolve();
        if (particle == null) return;

        try {
            config.toRequest(location.getWorld(), location).spawn();
        } catch (IllegalArgumentException ignored) {
        }
    }

    private ParticleConfig defaultInstantEffect() {
        return new ParticleConfig(ParticleTypeWrapper.of(Particle.INSTANT_EFFECT), 15, 1.3, 1.3, 1.3, 0.0, false, new ParticlePayload.Spell(Color.WHITE, 1.0f));
    }

    private ParticleConfig defaultConfig(Particle type, int count, double offset) {
        return new ParticleConfig(ParticleTypeWrapper.of(type), count, offset, offset, offset, 0.0, false, null);
    }

    private ParticleConfig teleportDefault() {
        return new ParticleConfig(ParticleTypeWrapper.of(Particle.PORTAL), 20, 1.0, 1.0, 1.0, 0.3, false, null);
    }

    public void reload(PetCore plugin) {
        folder = new File(plugin.getDataFolder() + File.separator + "Particles");
        if (!folder.exists()) {
            folder.mkdirs();
            writeReadme();
        }

        particles.put(Reason.SPAWN, loadOrDefault(defaultInstantEffect(), "SpawnParticle"));
        particles.put(Reason.FAILED, loadOrDefault(defaultConfig(Particle.ASH, 10, 1.3), "FailedSpawnParticle"));
        particles.put(Reason.TASK_FAILED, loadOrDefault(defaultConfig(Particle.SMOKE, 10, 1.3), "FailedTaskParticle"));
        particles.put(Reason.RENAME, loadOrDefault(defaultConfig(Particle.HAPPY_VILLAGER, 10, 1.3), "RenameParticle"));
        particles.put(Reason.REMOVE, loadOrDefault(defaultConfig(Particle.LAVA, 20, 1.0), "RemoveParticle"));
        particles.put(Reason.TELEPORT, loadOrDefault(teleportDefault(), "TeleportParticle"));
    }

    private void writeReadme() {
        File readme = new File(folder, "README.txt");
        if (readme.exists()) return;

        try (PrintWriter writer = new PrintWriter(readme)) {
            writer.println("Each .xml file in this folder controls one of SimplePets' particle effects.");
            writer.println();
            writer.println("For a full list of particle types, payload options (dust, block, trail, etc.)");
            writer.println("and all available attributes, see the wiki:");
            writer.println();
            writer.println("  https://github.com/brainsynder-Dev/BSPluginUtils/wiki/Particle-API#payload-child-elements");
        } catch (IOException ignored) {
        }
    }

    private void archiveOldJson(File jsonFile) {
        File backupFolder = new File(folder, "old_files");
        if (!backupFolder.exists()) backupFolder.mkdirs();
        jsonFile.renameTo(new File(backupFolder, jsonFile.getName()));
    }

    private ParticleConfig loadOrDefault(ParticleConfig defaultConfig, String name) {
        File xmlFile  = new File(folder, name + ".xml");
        File jsonFile = new File(folder, name + ".json");

        if (jsonFile.exists()) {
            if (!xmlFile.exists()) {
                writeReadme();
                try {
                    ParticleUtils.saveConfig(defaultConfig, xmlFile.toPath());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            archiveOldJson(jsonFile);
        }

        try {
            if (!xmlFile.exists()) {
                ParticleUtils.saveConfig(defaultConfig, xmlFile.toPath());
                return defaultConfig;
            }
            return ParticleUtils.loadConfig(xmlFile.toPath());
        } catch (Exception e) {
            return defaultConfig;
        }
    }

    @Override
    public void sendParticle(Reason reason, Player player, Location location) {
        if (player == null) return;
        switch (reason) {
            case SPAWN:
                if (ConfigOption.PARTICLES_SUMMON_TOGGLE.get()) sendToPlayer(particles.get(Reason.SPAWN), location);
                break;
            case FAILED:
                if (ConfigOption.PARTICLES_FAILED_TOGGLE.get()) sendToPlayer(particles.get(Reason.FAILED), location);
                break;
            case RENAME:
                if (ConfigOption.PARTICLES_RENAME_TOGGLE.get()) sendToPlayer(particles.get(Reason.RENAME), location);
                break;
            case REMOVE:
                if (ConfigOption.PARTICLES_REMOVE_TOGGLE.get()) sendToPlayer(particles.get(Reason.REMOVE), location);
                break;
            case TELEPORT:
                if (ConfigOption.PARTICLES_TELEPORT_TOGGLE.get())
                    sendToPlayer(particles.get(Reason.TELEPORT), location);
                break;
            case TASK_FAILED:
                if (ConfigOption.PARTICLES_FAILED_TASK_TOGGLE.get())
                    sendToPlayer(particles.get(Reason.TASK_FAILED), location);
                break;
        }
    }
}
