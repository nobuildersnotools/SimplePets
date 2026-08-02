package simplepets.brainsynder.commands.list;

import org.bsdevelopment.pluginutils.command.CommandBuilder;
import org.bsdevelopment.pluginutils.command.arguments.CustomArgument;
import org.bsdevelopment.pluginutils.command.arguments.StringArgument;
import org.bsdevelopment.pluginutils.command.arguments.suggestions.ArgumentSuggestions;
import org.bsdevelopment.pluginutils.command.exception.ArgumentParseException;
import org.bsdevelopment.pluginutils.files.JsonFile;
import org.bsdevelopment.pluginutils.libs.json.JsonValue;
import org.bukkit.command.CommandSender;
import simplepets.brainsynder.PetCore;
import simplepets.brainsynder.api.pet.IPetConfig;
import simplepets.brainsynder.api.pet.PetType;
import simplepets.brainsynder.api.plugin.SimplePets;
import simplepets.brainsynder.api.plugin.config.MessageOption;
import simplepets.brainsynder.commands.PetCommandClass;
import simplepets.brainsynder.impl.PetConfiguration;
import simplepets.brainsynder.managers.InventoryManager;
import simplepets.brainsynder.utils.Scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PetConfigCommand implements PetCommandClass {

    @Override
    public CommandBuilder build() {
        return CommandBuilder.create("petconfig")
                .withPermission("pet.commands.petconfig")
                .withDescription("Modify settings for the selected pet type.")
                .withArguments(new CustomArgument<>(new StringArgument("type"), info -> {
                    Optional<PetType> optional = PetType.getPetType(info.input());
                    if (optional.isEmpty())
                        throw ArgumentParseException.fromString("Invalid pet type: " + info.input());
                    return optional.get();
                }).replaceSuggestions(ArgumentSuggestions.of(info -> {
                    List<String> list = new ArrayList<>();
                    for (PetType type : PetType.values()) {
                        if (type == PetType.UNKNOWN) continue;
                        list.add(type.getName());
                    }
                    return list;
                })))
                .withArguments(new CustomArgument<>(new StringArgument("key"), info -> {
                    if (info.previousArgs() == null || !info.previousArgs().has("type")) return info.input();
                    PetType type = info.previousArgs().get("type");
                    Optional<IPetConfig> config = SimplePets.getPetConfigManager().getPetConfig(type);
                    if (config.isEmpty())
                        throw ArgumentParseException.fromString("No config found for: " + type.getName());
                    PetConfiguration.PetConfig petConfig = (PetConfiguration.PetConfig) config.get();
                    JsonFile jsonFile = petConfig.getJSON();
                    if (!jsonFile.hasKey(info.input()))
                        throw ArgumentParseException.fromString("Unknown config key: " + info.input());
                    JsonValue value = jsonFile.getValue(info.input());
                    if (!value.isBoolean() && !value.isNumber() && !value.isString())
                        throw ArgumentParseException.fromString("Key is not modifiable: " + info.input());
                    return info.input();
                }).replaceSuggestions(ArgumentSuggestions.of(info -> {
                    List<String> list = new ArrayList<>();
                    if (info.previousArgs() == null || !info.previousArgs().has("type")) return list;
                    PetType type = info.previousArgs().get("type");
                    SimplePets.getPetConfigManager().getPetConfig(type).ifPresent(iPetConfig -> {
                        PetConfiguration.PetConfig config = (PetConfiguration.PetConfig) iPetConfig;
                        JsonFile jsonFile = config.getJSON();
                        jsonFile.getKeys().forEach(key -> {
                            JsonValue value = jsonFile.getValue(key);
                            if (value.isBoolean() || value.isNumber() || value.isString()) list.add(key);
                        });
                    });
                    return list;
                })))
                .withArguments(new StringArgument("value")
                        .replaceSuggestions(ArgumentSuggestions.of(info -> {
                            List<String> list = new ArrayList<>();
                            list.add("reset");
                            if (info.previousArgs() == null || !info.previousArgs().has("type") || !info.previousArgs().has("key"))
                                return list;
                            PetType type = info.previousArgs().get("type");
                            String key = info.previousArgs().get("key");
                            SimplePets.getPetConfigManager().getPetConfig(type).ifPresent(iPetConfig -> {
                                PetConfiguration.PetConfig config = (PetConfiguration.PetConfig) iPetConfig;
                                JsonFile jsonFile = config.getJSON();
                                String defaultValue = jsonFile.getDefaultValue(key).toString();
                                String current = jsonFile.getValue(key).toString();
                                list.add(defaultValue);
                                if (!defaultValue.equalsIgnoreCase(current)) list.add(current);
                            });
                            return list;
                        })))
                .executes((sender, args) -> Scheduler.runGlobal(() -> {
                    PetType type = args.get("type");
                    String key = args.get("key");
                    String newValue = args.get("value");

                    SimplePets.getPetConfigManager().getPetConfig(type).ifPresent(iPetConfig -> {
                        PetConfiguration.PetConfig config = (PetConfiguration.PetConfig) iPetConfig;
                        JsonFile jsonFile = config.getJSON();

                        if (!jsonFile.hasKey(key)) {
                            message(sender, PetCore.getInstance().getMessageFile().getTranslation(MessageOption.CONFIG_UNKNOWN_KEY)
                                    .replace("{key}", key));
                            return;
                        }

                        JsonValue original = jsonFile.getDefaultValue(key);
                        boolean updated = false, reset = false;

                        if (newValue.equalsIgnoreCase("reset")) {
                            jsonFile.set(key, jsonFile.getDefaultValue(key));
                            updated = true;
                            reset = true;
                        } else if (original.isBoolean()) {
                            if ("true".equalsIgnoreCase(newValue) || "false".equalsIgnoreCase(newValue)) {
                                jsonFile.set(key, Boolean.parseBoolean(newValue));
                                updated = true;
                            } else {
                                message(sender, PetCore.getInstance().getMessageFile().getTranslation(MessageOption.CONFIG_INVALID_BOOLEAN)
                                        .replace("{key}", key).replace("{value}", newValue));
                                return;
                            }
                        } else if (original.isNumber()) {
                            if (original.toString().contains(".")) {
                                try {
                                    jsonFile.set(key, Double.parseDouble(newValue));
                                    updated = true;
                                } catch (NumberFormatException e) {
                                    message(sender, PetCore.getInstance().getMessageFile().getTranslation(MessageOption.CONFIG_INVALID_DOUBLE)
                                            .replace("{key}", key).replace("{value}", newValue));
                                    return;
                                }
                            } else {
                                try {
                                    jsonFile.set(key, Integer.parseInt(newValue));
                                    updated = true;
                                } catch (NumberFormatException e) {
                                    message(sender, PetCore.getInstance().getMessageFile().getTranslation(MessageOption.CONFIG_INVALID_INT)
                                            .replace("{key}", key).replace("{value}", newValue));
                                    return;
                                }
                            }
                        } else if (original.isString()) {
                            jsonFile.set(key, newValue);
                            updated = true;
                        }

                        if (updated) {
                            jsonFile.save();
                            boolean finalReset = reset;
                            Scheduler.runGlobalLater(() -> {
                                ((PetConfiguration) PetCore.getInstance().getPetConfigManager()).reset();
                                InventoryManager.SELECTION.reloadAvailableTypes();

                                Scheduler.run(sender, () -> {
                                if (finalReset) {
                                    sender.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.CONFIG_VALUE_RESET)
                                            .replace("{key}", key)
                                            .replace("{value}", jsonFile.getDefaultValue(key).toString())
                                            .replace("{type}", type.getName()));
                                } else {
                                    sender.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.CONFIG_VALUE_UPDATED)
                                            .replace("{key}", key)
                                            .replace("{value}", newValue)
                                            .replace("{type}", type.getName()));
                                }
                                });
                            }, 5);
                        } else {
                            message(sender, PetCore.getInstance().getMessageFile().getTranslation(MessageOption.CONFIG_UNABLE_TO_UPDATE)
                                    .replace("{key}", key).replace("{value}", newValue));
                        }
                    });
                }));
    }

    private void message(CommandSender sender, String message) {
        Scheduler.run(sender, () -> sender.sendMessage(message));
    }
}
