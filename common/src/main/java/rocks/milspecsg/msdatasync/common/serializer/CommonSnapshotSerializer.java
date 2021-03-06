/*
 *     MSDataSync - MilSpecSG
 *     Copyright (C) 2019 Cableguy20
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package rocks.milspecsg.msdatasync.common.serializer;

import com.google.inject.Inject;
import rocks.milspecsg.msdatasync.api.model.snapshot.Snapshot;
import rocks.milspecsg.msdatasync.api.serializer.ExperienceSerializer;
import rocks.milspecsg.msdatasync.api.serializer.GameModeSerializer;
import rocks.milspecsg.msdatasync.api.serializer.HealthSerializer;
import rocks.milspecsg.msdatasync.api.serializer.HungerSerializer;
import rocks.milspecsg.msdatasync.api.serializer.InventorySerializer;
import rocks.milspecsg.msdatasync.api.serializer.Serializer;
import rocks.milspecsg.msdatasync.api.serializer.SnapshotSerializer;
import rocks.milspecsg.msdatasync.common.data.key.MSDataSyncKeys;
import rocks.milspecsg.msrepository.api.data.registry.Registry;
import rocks.milspecsg.msrepository.api.util.UserService;

import java.util.ArrayList;
import java.util.List;

public abstract class CommonSnapshotSerializer<
    TSnapshot extends Snapshot<?>,
    TDataKey,
    TUser,
    TPlayer,
    TInventory,
    TItemStackSnapshot>
    extends CommonSerializer<TSnapshot, TDataKey, TUser>
    implements SnapshotSerializer<TSnapshot, TUser> {

    @Override
    public String getName() {
        return "msdatasync:snapshot";
    }

    private List<Serializer<TSnapshot, TUser>> serializers;

    private List<Serializer<TSnapshot, TUser>> externalSerializers;

    @Inject
    private ExperienceSerializer<TSnapshot, TUser> experienceSerializer;

    @Inject
    private GameModeSerializer<TSnapshot, TUser> gameModeSerializer;

    @Inject
    private HealthSerializer<TSnapshot, TUser> healthSerializer;

    @Inject
    private HungerSerializer<TSnapshot, TUser> hungerSerializer;

    @Inject
    private InventorySerializer<TSnapshot, TUser, TInventory, TItemStackSnapshot> inventorySerializer;

    @Inject
    private UserService<TUser, TPlayer> userService;

    protected Registry registry;

    protected CommonSnapshotSerializer(Registry registry) {
        this.registry = registry;
        registry.addRegistryLoadedListener(this::registryLoaded);
        externalSerializers = new ArrayList<>();
    }

    @Override
    public boolean isSerializerEnabled(String name) {
        return serializers.stream().anyMatch(serializer -> serializer.getName().equals(name));
    }

    @Override
    public void registerSerializer(Serializer<TSnapshot, TUser> serializer) {
        externalSerializers.add(serializer);
    }

    private void registryLoaded() {

        postLoadedEvent();

        verifyExternalSerializers();

        List<String> enabledSerializers = new ArrayList<>(registry.getOrDefault(MSDataSyncKeys.SERIALIZE_ENABLED_SERIALIZERS));
        serializers = new ArrayList<>();

        if (enabledSerializers.remove("msdatasync:experience")) {
            announceEnabled("msdatasync:experience");
            serializers.add(experienceSerializer);
        }

        if (enabledSerializers.remove("msdatasync:gameMode")) {
            announceEnabled("msdatasync:gameMode");
            serializers.add(gameModeSerializer);
        }

        if (enabledSerializers.remove("msdatasync:health")) {
            announceEnabled("msdatasync:health");
            serializers.add(healthSerializer);
        }

        if (enabledSerializers.remove("msdatasync:hunger")) {
            announceEnabled("msdatasync:hunger");
            serializers.add(hungerSerializer);
        }

        if (enabledSerializers.remove("msdatasync:inventory")) {
            announceEnabled("msdatasync:inventory");
            serializers.add(inventorySerializer);
        }

        externalSerializers.stream()
            .filter(serializer -> enabledSerializers.remove(serializer.getName()))
            .forEach(serializer -> serializers.add(serializer));
    }

    protected abstract void postLoadedEvent();

    private void verifyExternalSerializers() {
        List<Serializer<TSnapshot, TUser>> externalSerializersToRemove = new ArrayList<>();
        externalSerializers.forEach(serializer -> {
            String name = serializer.getName();
            if (name.startsWith("msdatasync") || name.split(":").length != 2) {
                System.err.println("[MSDataSync] External serialization module name \"" + serializer.getName() + "\" is invalid. Ignoring!");
                externalSerializersToRemove.add(serializer);
            }
        });

        externalSerializersToRemove.forEach(externalSerializers::remove);
    }

    protected abstract void announceEnabled(String name);

    @Override
    public boolean serialize(TSnapshot snapshot, TUser user) {
        if (serializers.isEmpty()) {
            System.err.println("[MSDataSync] No enabled serializers");
            return false;
        }
        boolean success = true;

        for (Serializer<TSnapshot, TUser> serializer : serializers) {
            // will still try to keep going even if one module fails
            try {
                snapshot.getModulesUsed().add(serializer.getName());
                if (!serializer.serialize(snapshot, user)) {
                    System.err.println("[MSDataSync] Serialization module \"" + serializer.getName() + "\" failed for " + userService.getUserName(user) + "! All valid data was still uploaded!");
                    success = false;
                    snapshot.getModulesFailed().add(serializer.getName());
                }
            } catch (RuntimeException e) {
                success = false;
            }
        }
        return success;
    }

    @Override
    public boolean deserialize(TSnapshot snapshot, TUser user) {
        if (serializers.isEmpty()) {
            System.err.println("[MSDataSync] No enabled deserializers");
            return false;
        }
        boolean success = true;
        List<String> serializersToUse = new ArrayList<>(snapshot.getModulesUsed());
        for (Serializer<TSnapshot, TUser> serializer : serializers) {
            // will still try to keep going even if one module fails
            try {
                if (serializersToUse.remove(serializer.getName())) {
                    // only use modules that were used to upload
                    if (!serializer.deserialize(snapshot, user)) {
                        System.err.println("[MSDataSync] Deserialization module \"" + serializer.getName() + "\" failed for snapshot " + snapshot.getId() + " for " + userService.getUserName(user));
                        success = false;
                    }
                } else {
                    System.err.println("[MSDataSync] Deserialization module \"" + serializer.getName() + "\" was not used in snapshot " + snapshot.getId() + " for " + userService.getUserName(user) + " but it is enabled in the config, skipping!");
                }
            } catch (Exception e) {
                success = false;
            }
        }

        serializersToUse.forEach(moduleName -> System.err.println(
            "[MSDataSync] Deserialization module \""
                + moduleName +
                "\" was used in snapshot "
                + snapshot.getId() +
                " but it is not enabled in the config! Not all data from snapshot could be added to user "
                + userService.getUserName(user)
        ));
        return success;
    }
}
