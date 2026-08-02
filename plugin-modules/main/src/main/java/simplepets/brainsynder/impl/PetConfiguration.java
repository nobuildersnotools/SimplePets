package simplepets.brainsynder.impl;

import com.google.common.collect.Lists;
import org.bsdevelopment.nbt.StorageBase;
import org.bsdevelopment.pluginutils.files.JsonFile;
import org.bsdevelopment.pluginutils.inventory.ItemBuilder;
import org.bsdevelopment.pluginutils.libs.json.Json;
import org.bsdevelopment.pluginutils.libs.json.JsonArray;
import org.bsdevelopment.pluginutils.libs.json.JsonObject;
import org.bsdevelopment.pluginutils.sound.SafeSound;
import org.bsdevelopment.pluginutils.text.WordUtils;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import simplepets.brainsynder.PetCore;
import simplepets.brainsynder.api.pet.*;
import simplepets.brainsynder.api.plugin.SimplePets;
import simplepets.brainsynder.api.plugin.config.ConfigOption;
import simplepets.brainsynder.debug.DebugLevel;
import simplepets.brainsynder.utils.Keys;
import simplepets.brainsynder.utils.Utilities;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class PetConfiguration implements PetConfigManager {
    private final PetCore plugin;
    private final Map<PetType, IPetConfig> configMap;

    public PetConfiguration(PetCore plugin) {
        this.plugin = plugin;
        this.configMap = new ConcurrentHashMap<>();

        for (PetType type : PetType.values()) {
            if (!type.isSupported()) continue;
            if (!SimplePets.getSpawnUtil().isRegistered(type)) continue;
            configMap.put(type, new PetConfig(type));
        }
    }

    public void reset() {
        configMap.clear();

        for (PetType type : PetType.values()) {
            if (!type.isSupported()) continue;
            if (!SimplePets.getSpawnUtil().isRegistered(type)) continue;
            configMap.put(type, new PetConfig(type));
        }
    }

    public void reset(PetType type) {
        configMap.remove(type);

        if (!type.isSupported()) return;
        if (!SimplePets.getSpawnUtil().isRegistered(type)) return;
        configMap.put(type, new PetConfig(type));
    }

    @Override
    public Optional<IPetConfig> getPetConfig(PetType type) {
        if (configMap.containsKey(type)) return Optional.of(configMap.get(type));
        return Optional.empty();
    }

    public class PetConfig implements IPetConfig {
        private final PetType type;
        private final JsonFile JSON;
        private final Map<String, JsonObject> additional;
        private final Map<CommandReason, List<String>> commandMap;

        private PetConfig(PetType type) {
            this.type = type;
            this.additional = new HashMap<>();
            this.commandMap = new HashMap<>();

            this.JSON = new JsonFile(new File(new File(plugin.getDataFolder() + File.separator + "Pets"), type.getName() + ".json"), true) {
                @Override
                public void loadDefaults() {
                    setDefault("enabled", true);
                    setDefault("hat", true);
                    setDefault("mount", true);
                    type.getCustomization().ifPresent(customization -> setDefault("ambient-sound", customization.ambient()));

                    JsonObject reasons = new JsonObject();
                    for (CommandReason reason : CommandReason.values()) reasons.add(reason.name(), new JsonArray());
                    setDefault("commands", reasons);

                    setDefault("ride_speed", ConfigOption.PET_TOGGLES_RIDE_SPEED.get());
                    setDefault("walk_speed", ConfigOption.PET_TOGGLES_WALK_SPEED.get());
                    setDefault("fly_speed", ConfigOption.PET_TOGGLES_FLY_SPEED.get());
                    setDefault("water_speed", ConfigOption.PET_TOGGLES_WATER_SPEED.get());
                    setDefault("fly", canFlyDefault(type));
                    setDefault("float_down", false);

                    setDefault("display_name", "&a&l%player%'s " + WordUtils.capitalize(type.getName().replace("_", " ")) + " Pet");
                    setDefault("item", Json.parse(type.getBuilder().toTag().toJson()));

                    JsonObject dataObject = new JsonObject();
                    type.getPetData().forEach(petData -> {
                        JsonObject data = new JsonObject();
                        data.set("enabled", petData.isEnabledByDefault());

                        setJsonDefaultValue(data, "default", petData.defaultValue());

                        JsonObject values = new JsonObject();
                        petData.getDefaultItems().forEach((value, item) -> {
                            String name = petData.namespace();
                            name = name.replace("_", " ");
                            name = WordUtils.capitalize(name);

                            ItemBuilder builder = (ItemBuilder) item;
                            String raw = builder.getName();
                            raw = raw.replace("{name}", name);
                            builder.withName(raw);

                            values.add(String.valueOf(value), Json.parse(builder.toTag().toJson()));
                        });
                        data.set("values", values);

                        dataObject.add(petData.namespace(), data);
                    });
                    setDefault("data", dataObject);
                }
            };

            if (JSON.hasKey("commands")) {
                JsonObject commands = (JsonObject) JSON.getValue("commands");
                commands.names().forEach(s -> CommandReason.getReason(s).ifPresent(reason -> {
                    List<String> list = commandMap.getOrDefault(reason, Lists.newArrayList());
                    JsonArray array = (JsonArray) commands.get(s);
                    array.forEach(jsonValue -> list.add(jsonValue.asString()));
                    commandMap.put(reason, list);
                }));
            }

            // Ensure PetData sections are present and valid.
            type.getPetData().forEach(this::checkPetData);
        }

        public JsonFile getJSON() {
            return JSON;
        }

        @Override
        public void handleAdditionalStorage(String pluginKey, Function<JsonObject, JsonObject> json) {
            additional.put(pluginKey, json.apply(additional.getOrDefault(pluginKey, new JsonObject())));
        }

        @Override
        public String getDisplayName() {
            return JSON.getString("display_name");
        }

        @Override
        public boolean canHat(Player player) {
            if (ConfigOption.PET_TOGGLES_HAT.get()) return true;
            if (JSON.getBoolean("hat", true)) return Utilities.hasPermission(player, type.getPermission("hat"));
            return false;
        }

        @Override
        public boolean canMount(Player player) {
            if (ConfigOption.PET_TOGGLES_MOUNTABLE.get()) return true;
            if (JSON.getBoolean("mount", true)) return Utilities.hasPermission(player, type.getPermission("mount"));
            return false;
        }

        @Override
        public boolean canFly(Player player) {
            if (ConfigOption.PET_TOGGLES_FLYABLE.get()) return true;
            if (JSON.getBoolean("fly", true)) return Utilities.hasPermission(player, type.getPermission("fly"));
            return false;
        }

        @Override
        public boolean isEnabled() {
            return JSON.getBoolean("enabled");
        }

        @Override
        public boolean canFloat() {
            return JSON.getBoolean("float_down", false);
        }

        @Override
        public double getRideSpeed() {
            return JSON.getDouble("ride_speed", ConfigOption.PET_TOGGLES_RIDE_SPEED.get());
        }

        @Override
        public double getWalkSpeed() {
            return JSON.getDouble("walk_speed", ConfigOption.PET_TOGGLES_WALK_SPEED.get());
        }

        @Override
        public double getFlySpeed() {
            return JSON.getDouble("fly_speed", ConfigOption.PET_TOGGLES_FLY_SPEED.get());
        }

        @Override
        public double getWaterSpeed() {
            return JSON.getDouble("water_speed", ConfigOption.PET_TOGGLES_WATER_SPEED.get());
        }

        @Override
        public Optional<EntityType> getEntityType() {
            return Optional.empty();
        }

        @Override
        public SafeSound getSound() {
            if (!JSON.containsKey("ambient-sound")) return null;
            String sound = JSON.getString("ambient-sound", null);
            if (sound == null || sound.isEmpty()) return null;
            try {
                return SafeSound.of(Sound.valueOf(sound));
            } catch (IllegalArgumentException e) {
                // Version-specific or custom pet sound — not in the Bukkit Sound enum
                return SafeSound.of(sound);
            }
        }

        @Override
        public ItemBuilder getBuilder() {
            try {
                return ItemBuilder.of(StorageBase.fromJson(JSON.getValue("item").toString())).handleMeta(ItemMeta.class, itemMeta -> {
                    itemMeta.getPersistentDataContainer().set(Keys.GUI_ITEM, PersistentDataType.INTEGER, 1);
                    itemMeta.getPersistentDataContainer().set(Keys.PET_TYPE_ITEM, PersistentDataType.STRING, type.getName());
                    return itemMeta;
                });
            } catch (Exception e) {
                SimplePets.getDebugLogger().debug(DebugLevel.ERROR, "Failed to get default item for '" + type.getName() + "'");
                SimplePets.getDebugLogger().debug(DebugLevel.ERROR, "Error: " + e.getMessage());
                return type.getBuilder();
            }
        }

        @Override
        public Optional<ItemBuilder> getDataItem(String namespace, Object value) {
            return getDataItem(namespace, value, null, false);
        }

        @Override
        public Optional<ItemBuilder> getDataItem(String namespace, Object value, ItemBuilder fallback) {
            return getDataItem(namespace, value, fallback, true);
        }

        private Optional<ItemBuilder> getDataItem(String namespace, Object value, ItemBuilder fallback, boolean allowInsertFallback) {
            if (!JSON.containsKey("data")) return Optional.empty();

            JsonObject dataRoot = (JsonObject) JSON.getValue("data");
            if (!dataRoot.names().contains(namespace)) return Optional.empty();

            JsonObject dataSection = (JsonObject) dataRoot.get(namespace);
            JsonObject values = ensureValuesObject(dataRoot, namespace, dataSection, true);

            String key = String.valueOf(value);

            if (values.names().contains(key)) {
                return Optional.of(ItemBuilder.of(StorageBase.fromJson(values.get(key).toString())));
            }

            if (fallback == null) return Optional.empty();

            if (allowInsertFallback) {
                values.add(key, Json.parse(fallback.toTag().toJson()));
                dataSection.set("values", values);
                dataRoot.add(namespace, dataSection);
                JSON.set("data", dataRoot);
                JSON.save(true);
                return Optional.of(fallback);
            }

            return Optional.of(fallback);
        }

        @Override
        public JsonObject getRawData(String namespace) {
            JsonObject data = new JsonObject();
            if (JSON.containsKey("data")) {
                JsonObject dataObject = (JsonObject) JSON.getValue("data");
                if (dataObject.names().contains(namespace)) data = (JsonObject) dataObject.get(namespace);
            }
            return data;
        }

        @Override
        public Map<CommandReason, List<String>> getCommands() {
            return commandMap;
        }

        private boolean checkPetData(PetData<?> petData) {
            String namespace = petData.namespace();

            JsonObject dataRoot = ensureDataRoot(false);
            JsonObject section = dataRoot.names().contains(namespace) ? (JsonObject) dataRoot.get(namespace) : new JsonObject();

            boolean changed = false;

            boolean expectedEnabledDefault = petData.isEnabledByDefault();
            if (!section.names().contains("enabled")) {
                section.set("enabled", expectedEnabledDefault);
                changed = true;
            }

            if (!section.names().contains("default")) {
                setJsonDefaultValue(section, "default", petData.defaultValue());
                changed = true;
            }

            JsonObject values = section.names().contains("values") ? (JsonObject) section.get("values") : new JsonObject();
            if (values.names().isEmpty()) {
                petData.getDefaultItems().forEach((val, item) -> values.add(String.valueOf(val), Json.parse(item.toTag().toJson())));
                changed = true;
            } else {
                for (Map.Entry<String, ItemBuilder> entry : petData.getDefaultItems().entrySet()) {
                    String key = entry.getKey();
                    if (!values.names().contains(key)) {
                        values.add(key, Json.parse(entry.getValue().toTag().toJson()));
                        changed = true;
                    }
                }
            }

            section.set("values", values);
            dataRoot.add(namespace, section);

            if (changed) {
                JSON.set("data", dataRoot);
                JSON.save(true);
                return false;
            }
            return true;
        }

        private JsonObject ensureDataRoot(boolean save) {
            if (!JSON.containsKey("data")) {
                SimplePets.getDebugLogger().debug(DebugLevel.DEBUG, type.getName() + " | Missing 'data' section");
                JsonObject root = new JsonObject();
                JSON.set("data", root);
                if (save) JSON.save(true);
                return root;
            }
            return (JsonObject) JSON.getValue("data");
        }

        private JsonObject ensureValuesObject(JsonObject dataRoot, String namespace, JsonObject section, boolean save) {
            JsonObject values = section.names().contains("values") ? (JsonObject) section.get("values") : new JsonObject();
            if (values.names().isEmpty()) {
                for (PetData<?> petData : type.getPetData()) {
                    if (!petData.namespace().equals(namespace)) continue;

                    petData.getDefaultItems().forEach((val, item) -> values.add(String.valueOf(val), Json.parse(item.toTag().toJson())));

                    if (!section.names().contains("default")) {
                        setJsonDefaultValue(section, "default", petData.defaultValue());
                    }
                    if (!section.names().contains("enabled")) {
                        section.set("enabled", petData.isEnabledByDefault());
                    }
                    break;
                }

                section.set("values", values);
                dataRoot.add(namespace, section);
                JSON.set("data", dataRoot);
                if (save) JSON.save(true);
            }
            return values;
        }

        private void setJsonDefaultValue(JsonObject obj, String key, Object value) {
            if (value instanceof Integer i) {
                obj.set(key, i);
                return;
            }
            if (value instanceof Boolean b) {
                obj.set(key, b);
                return;
            }
            if (value instanceof Double d) {
                obj.set(key, d);
                return;
            }
            if (value instanceof Float f) {
                obj.set(key, f.doubleValue());
                return;
            }
            obj.set(key, String.valueOf(value));
        }

        private boolean canFlyDefault(PetType type) {
            return (type == PetType.BAT)
                || (type == PetType.BEE)
                || (type == PetType.BLAZE)
                || (type == PetType.PHANTOM)
                || (type == PetType.PARROT)
                || (type == PetType.GHAST)
                || (type == PetType.VEX)
                || (type == PetType.WITHER);
        }
    }
}
