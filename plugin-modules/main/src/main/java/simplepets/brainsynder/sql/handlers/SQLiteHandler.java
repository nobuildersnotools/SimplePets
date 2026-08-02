package simplepets.brainsynder.sql.handlers;

import org.bsdevelopment.nbt.StorageTagCompound;
import org.bsdevelopment.pluginutils.storage.optional.BiOptional;
import org.bsdevelopment.pluginutils.utilities.Triple;
import simplepets.brainsynder.PetCore;
import simplepets.brainsynder.sql.SQLData;
import simplepets.brainsynder.sql.SQLHandler;
import simplepets.brainsynder.utils.Utilities;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SQLiteHandler implements SQLHandler {
    private Connection sqliteConnection;
    private final Object connectionLock = new Object();

    @Override
    public synchronized Connection implementConnection() {
        if (sqliteConnection != null) return sqliteConnection;
        return sqliteConnection = loadSqlite();
    }

    private Connection loadSqlite() {
        // Load JDBC
        try {
            Class.forName("org.sqlite.JDBC");
            File file = new File(PetCore.getInstance().getDataFolder(), "storage.db");
            if (!file.exists()) file.createNewFile();
            return DriverManager.getConnection("jdbc:sqlite:" + file);
        } catch (ClassNotFoundException | SQLException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void initiateDatabase() {
        try {
            Connection connection = implementConnection();
            PreparedStatement statement = connection.prepareStatement(CREATE_TABLE);
            statement.executeUpdate();

            statement.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CompletableFuture<Boolean> sendPlayerData(UUID uuid, String name, StorageTagCompound compound) {
        return CompletableFuture
            .supplyAsync(() -> sendPlayerDataSync(uuid, name, compound), PetCore.getInstance().async);
    }

    @Override
    public boolean sendPlayerDataSync(UUID uuid, String name, StorageTagCompound compound) {
        synchronized (connectionLock) {
        try {
            Connection connection = implementConnection();
            // Checks if the uuid is in the MySQL database
            boolean hasEntry;
            {
                PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM `" + SQLData.TABLE_PREFIX + "_players` WHERE uuid = '" + uuid.toString() + "' LIMIT 1");
                ResultSet result = statement.executeQuery();
                hasEntry = result.next();

                statement.close();
            }

            PreparedStatement statement;
            if (hasEntry) {
                statement = connection.prepareStatement("UPDATE `" + SQLData.TABLE_PREFIX + "_players` SET " +
                    "name=?, UnlockedPets=?, PetName=?, NeedsRespawn=?, SavedPets=? WHERE uuid = ?");
                statement.setString(1, name);

                statement.setString(2, Utilities.encodeBase64(compound.getTag("owned_pets").toString()));
                statement.setString(3, Utilities.encodeBase64(compound.getTag("pet_names").toString()));
                statement.setString(4, Utilities.encodeBase64(compound.getTag("spawned_pets").toString()));
                statement.setString(5, Utilities.encodeBase64(compound.getTag("saved_pets").toString()));
                statement.setString(6, uuid.toString());
            } else {
                statement = connection.prepareStatement("INSERT INTO `" + SQLData.TABLE_PREFIX + "_players` " +
                    "(`uuid`, `name`, `UnlockedPets`, `PetName`, `NeedsRespawn`, `SavedPets`) VALUES (?, ?, ?, ?, ?, ?)");
                statement.setString(1, uuid.toString());
                statement.setString(2, name);

                statement.setString(3, Utilities.encodeBase64(compound.getTag("owned_pets").toString()));
                statement.setString(4, Utilities.encodeBase64(compound.getTag("pet_names").toString()));
                statement.setString(5, Utilities.encodeBase64(compound.getTag("spawned_pets").toString()));
                statement.setString(6, Utilities.encodeBase64(compound.getTag("saved_pets").toString()));
            }
            statement.executeUpdate();

            statement.close();
            return true;
        } catch (SQLException exception) {
            return false;
        }
        }
    }

    @Override
    public CompletableFuture<StorageTagCompound> fetchData(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (connectionLock) {
            StorageTagCompound compound = new StorageTagCompound();

            try {
                Connection connection = implementConnection();

                PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + SQLData.TABLE_PREFIX + "_players WHERE uuid = ?");
                statement.setString(1, uuid.toString());
                ResultSet results = statement.executeQuery();

                if (results.next()) {
                    try {
                        compound = rowToCompound(uuid, results, false);
                    } catch (NullPointerException | IllegalArgumentException ignored) {
                    }
                }

                results.close();
                statement.close();
            } catch (SQLException ignored) {
            }

            return compound;
            }
        }, PetCore.getInstance().async);
    }

    @Override
    public CompletableFuture<Integer> getRowCount() {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (connectionLock) {
            int count = 0;

            try {
                Connection connection = implementConnection();

                PreparedStatement statement = connection.prepareStatement("SELECT * FROM `" + SQLData.TABLE_PREFIX + "_players`");
                ResultSet result = statement.executeQuery();

                int size = 0;
                while (result.next()) size++;

                count = size;

                statement.close();
            } catch (SQLException ignored) {
            }

            return count;
            }
        }, PetCore.getInstance().async);
    }

    @Override
    public CompletableFuture<BiOptional<Integer, Integer>> removeDuplicates() {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (connectionLock) {
            int rawCount = 0;
            int totalCount = 0;

            try {
                Connection connection = implementConnection();
                connection.setAutoCommit(false);

                List<String> list = new ArrayList<>();

                try (PreparedStatement statement = connection.prepareStatement("SELECT `uuid`, COUNT(`uuid`) AS `count` FROM `" + SQLData.TABLE_PREFIX + "_players` " + "GROUP BY `uuid` HAVING COUNT(`uuid`) > 1;")) {
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            ++rawCount;
                            list.add(result.getString("uuid"));
                        }
                    }
                }

                if (list.isEmpty()) {
                    connection.rollback();
                    connection.setAutoCommit(true);
                    return BiOptional.of(0, 0);
                }

                PreparedStatement fetch = connection.prepareStatement(
                        "SELECT `uuid`,`name`,`UnlockedPets`,`PetName`,`NeedsRespawn`,`SavedPets` " + "FROM `" + SQLData.TABLE_PREFIX + "_players` WHERE `uuid`=?;"
                );
                PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM `" + SQLData.TABLE_PREFIX + "_players` WHERE `uuid`=?;"
                );
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO `" + SQLData.TABLE_PREFIX + "_players` " + "(`uuid`, `name`, `UnlockedPets`, `PetName`, `NeedsRespawn`, `SavedPets`) " + "VALUES (?, ?, ?, ?, ?, ?);"
                );
                PreparedStatement countRows = connection.prepareStatement(
                        "SELECT COUNT(*) AS `c` FROM `" + SQLData.TABLE_PREFIX + "_players` WHERE `uuid`=?;"
                );

                for (String uuid : list) {
                    int rows = 0;
                    countRows.setString(1, uuid);
                    try (ResultSet c = countRows.executeQuery()) {
                        if (c.next()) rows = c.getInt("c");
                    }

                    if (rows <= 1) continue;

                    String keepUuid = null;
                    String keepName = null;
                    String keepUnlocked = null;
                    String keepPetName = null;
                    String keepNeedsRespawn = null;
                    String keepSavedPets = null;
                    int bestScore = -1;

                    fetch.setString(1, uuid);
                    try (ResultSet resultSet = fetch.executeQuery()) {
                        while (resultSet.next()) {
                            String storedUUID = resultSet.getString("uuid");
                            String storeName = resultSet.getString("name");
                            String storeUnlockedPets = resultSet.getString("UnlockedPets");
                            String storePetName = resultSet.getString("PetName");
                            String storeRespawn = resultSet.getString("NeedsRespawn");
                            String storeSavedPets = resultSet.getString("SavedPets");

                            int score = 0;
                            if (storeUnlockedPets != null) score += storeUnlockedPets.length();
                            if (storePetName != null) score += storePetName.length();
                            if (storeRespawn != null) score += storeRespawn.length();
                            if (storeSavedPets != null) score += storeSavedPets.length();

                            if (score <= bestScore) continue;

                            bestScore = score;
                            keepUuid = storedUUID;
                            keepName = storeName;
                            keepUnlocked = storeUnlockedPets;
                            keepPetName = storePetName;
                            keepNeedsRespawn = storeRespawn;
                            keepSavedPets = storeSavedPets;
                        }
                    }

                    if (keepUuid == null) continue;

                    delete.setString(1, uuid);
                    delete.executeUpdate();

                    insert.setString(1, keepUuid);
                    insert.setString(2, keepName);
                    insert.setString(3, keepUnlocked);
                    insert.setString(4, keepPetName);
                    insert.setString(5, keepNeedsRespawn);
                    insert.setString(6, keepSavedPets);
                    insert.executeUpdate();

                    totalCount += (rows - 1);
                }

                countRows.close();
                fetch.close();
                delete.close();
                insert.close();

                connection.commit();
                connection.setAutoCommit(true);
            } catch (SQLException exception) {
                exception.printStackTrace();
            }

            return BiOptional.of(rawCount, totalCount);
            }
        }, PetCore.getInstance().async);
    }

    @Override
    public CompletableFuture<BiOptional<Integer, Integer>> removeNPCs() {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (connectionLock) {
            int rawCount = 0;
            int totalCount = 0;

            try {
                Connection connection = implementConnection();

                PreparedStatement statement = connection.prepareStatement("SELECT * FROM `" + SQLData.TABLE_PREFIX + "_players` WHERE `uuid` LIKE '________-____-2___-____-____________';");

                List<String> list = new ArrayList<>();
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        ++rawCount;
                        list.add(result.getString("uuid"));
                    }
                }

                statement = connection.prepareStatement("DELETE FROM `" + SQLData.TABLE_PREFIX + "_players` WHERE `uuid`=?");

                for (String string : list) {
                    statement.setString(1, string);
                    statement.addBatch();
                }
                totalCount = statement.executeBatch().length;

                statement.close();
            } catch (SQLException exception) {
                exception.printStackTrace();
            }

            return BiOptional.of(rawCount, totalCount);
            }
        }, PetCore.getInstance().async);
    }

    @Override
    public CompletableFuture<List<Triple<UUID, String, Integer>>> findDuplicates() {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (connectionLock) {
            List<Triple<UUID, String, Integer>> list = new ArrayList<>();
            try {
                Connection connection = implementConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT `uuid`,`name`, COUNT(`uuid`) FROM `" + SQLData.TABLE_PREFIX + "_players` GROUP BY `uuid` HAVING COUNT(`uuid`) > 1;");

                ResultSet result = statement.executeQuery();
                while (result.next()) {
                    list.add(Triple.of(
                        UUID.fromString(result.getString(1)),
                        result.getString(2),
                        result.getInt(3)
                    ));
                }

                statement.close();
            } catch (SQLException ignored) {
            }
            return list;
            }
        }, PetCore.getInstance().async);
    }
}
