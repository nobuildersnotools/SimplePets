package simplepets.brainsynder.commands.list;

import org.bsdevelopment.pluginutils.chat.TellrawMessage;
import org.bsdevelopment.pluginutils.chat.decoration.NamedTextColor;
import org.bsdevelopment.pluginutils.command.CommandBuilder;
import org.bsdevelopment.pluginutils.text.Colorize;
import org.bsdevelopment.pluginutils.utilities.PasteClient;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import simplepets.brainsynder.PetCore;
import simplepets.brainsynder.api.plugin.config.MessageOption;
import simplepets.brainsynder.commands.PetCommandClass;
import simplepets.brainsynder.sql.SQLData;
import simplepets.brainsynder.utils.Scheduler;

public class DatabaseCommand implements PetCommandClass {
    @Override
    public CommandBuilder build() {
        return CommandBuilder.create("database")
                .withPermission("pet.commands.database")
                .withDescription("Shows information about the database")
                .withSubcommand(buildReloadCommand())
                .withSubcommand(buildInstallCommand())
                .withSubcommand(buildUpdateCommand())
                .executes((sender, args) -> {
                    PetCore.getInstance().getSqlHandler().getRowCount().whenComplete((playerDataCount, throwable) -> {
                        Scheduler.run(sender, () -> {
                        if (failed(sender, throwable) || playerDataCount == null) return;
                        sender.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.PREFIX) + Colorize.translateBungeeHex(" &#d1c9c9Player Data SQL &#b35349======&#de9790-------"));
                        sender.sendMessage(Colorize.translateBungeeHex(" &#e1eb5b- &#d1c9c9Type: &#e3c79a" + (SQLData.USE_SQLITE ? "SQLite" : "MySQL")));
                        TellrawMessage raw = TellrawMessage.of("&#e1eb5b - &#d1c9c9Status: ");
                        if (SQLData.USE_SQLITE) {
                            raw.then("CONNECTED").color(NamedTextColor.GREEN).tooltip("&7SQLite connections are kept connected");
                        } else {
                            raw.then("IDLE").color("#e3aa4f").tooltip("&7MySQL connections are kept closed until they are needed", "&7That's what the IDLE state is");
                        }
                        raw.send(sender);
                        sender.sendMessage(Colorize.translateBungeeHex(" &#e1eb5b- &#d1c9c9Players In Database: &#e3c79a" + playerDataCount));
                        });
                    });
                });
    }


    // Command: /pet database removenpcs
    public CommandBuilder buildInstallCommand() {
        return CommandBuilder.create("removenpcs")
                .withPermission("pet.commands.database.removenpcs")
                .withDescription("Clears the database of any NPC/offline UUIDs")
                .executes((sender, args) -> {
                    PetCore.getInstance().getSqlHandler().removeNPCs().whenComplete((biOptional, throwable) -> {
                        Scheduler.run(sender, () -> {
                        if (failed(sender, throwable) || biOptional == null) return;
                        int rawCount = biOptional.first().orElse(0);
                        int totalCount = biOptional.second().orElse(0);

                        if (rawCount == 0) {
                            // No duplicates...
                            sender.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.PREFIX) + ChatColor.GRAY + " No NPC account entries found that needed to be deleted");
                            return;
                        }

                        sender.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.PREFIX) + ChatColor.GRAY + " Number of NPC accounts found: " + rawCount);
                        sender.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.PREFIX) + ChatColor.GRAY + " Number of accounts actually deleted: " + totalCount);
                        });
                    });
                });
    }


    // Command: /pet database removeduplicates
    public CommandBuilder buildUpdateCommand() {
        return CommandBuilder.create("removeduplicates")
                .withPermission("pet.commands.database.removeduplicates")
                .withDescription("Clears the database of all duplicate players")
                .executes((sender, args) -> {
                    PetCore.getInstance().getSqlHandler().removeDuplicates().whenComplete((biOptional, throwable) -> {
                        Scheduler.run(sender, () -> {
                        if (failed(sender, throwable) || biOptional == null) return;
                        int rawCount = biOptional.first().orElse(0);
                        int totalCount = biOptional.second().orElse(0);

                        if (rawCount == 0) {
                            // No duplicates...
                            sender.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.PREFIX) + ChatColor.GRAY + " No duplicate entries found that needed to be deleted");
                            return;
                        }

                        sender.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.PREFIX) + ChatColor.GRAY + " Number of duplicate accounts found: " + rawCount);
                        sender.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.PREFIX) + ChatColor.GRAY + " Number of duplicate entries actually deleted: " + totalCount);
                        });
                    });
                });
    }


    // Command: /pet database findduplicates
    public CommandBuilder buildReloadCommand() {
        return CommandBuilder.create("findduplicates")
                .withPermission("pet.commands.database.findduplicates")
                .withDescription("Fetches a list of all duplicate players in the database")
                .executes((sender, args) -> {
                    sender.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.PREFIX) + ChatColor.GRAY + " Finding any duplicates in the database...");
                    PetCore.getInstance().getSqlHandler().findDuplicates().whenComplete((triples, throwable) -> {
                        Scheduler.run(sender, () -> {
                        if (failed(sender, throwable) || triples == null) return;
                        if (triples.isEmpty()) {
                            sender.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.PREFIX) + ChatColor.GRAY + " No duplicate players were found in the database.");
                            return;
                        }

                        StringBuilder builder = new StringBuilder();

                        triples.forEach(triple -> {
                            builder.append("[Count: ").append(triple.right).append("]   '").append(triple.middle).append("'    (").append(triple.left.toString()).append(")").append("\n");
                        });

                        Scheduler.runAsync(() -> {
                            try {
                                String pasteUrl = PasteClient.pasteUrl(PasteClient.upload(builder.toString(), "plain"));
                                Scheduler.run(sender, () -> {
                                    sender.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.PREFIX) + ChatColor.GRAY + " Here is a list of duplicated players: ");
                                    sender.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.PREFIX) + ChatColor.GRAY + " " + pasteUrl);
                                });
                            } catch (Exception e) {
                                Scheduler.run(sender, () -> sender.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.PREFIX) + ChatColor.RED + " Failed to upload paste: " + e.getMessage()));
                            }
                        });
                        });
                    });
                });
    }

    private static boolean failed(CommandSender sender, Throwable throwable) {
        if (throwable == null) return false;
        sender.sendMessage(PetCore.getInstance().getMessageFile().getTranslation(MessageOption.PREFIX)
            + ChatColor.RED + " Database operation failed: " + throwable.getMessage());
        return true;
    }
}
