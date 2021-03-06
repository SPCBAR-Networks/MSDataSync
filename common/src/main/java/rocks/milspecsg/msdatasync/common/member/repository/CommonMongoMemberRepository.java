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

package rocks.milspecsg.msdatasync.common.member.repository;

import com.google.inject.Inject;
import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.query.Query;
import rocks.milspecsg.msdatasync.api.member.repository.MongoMemberRepository;
import rocks.milspecsg.msdatasync.api.model.member.Member;
import rocks.milspecsg.msdatasync.api.model.snapshot.Snapshot;
import rocks.milspecsg.msrepository.api.datastore.DataStoreContext;
import rocks.milspecsg.msrepository.common.repository.CommonMongoRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class CommonMongoMemberRepository<
    TMember extends Member<ObjectId>,
    TSnapshot extends Snapshot<ObjectId>,
    TUser,
    TDataKey>
    extends CommonMemberRepository<ObjectId, TMember, TSnapshot, TUser, TDataKey, Datastore>
    implements CommonMongoRepository<TMember>,
    MongoMemberRepository<TMember, TSnapshot, TUser> {

    @Inject
    public CommonMongoMemberRepository(DataStoreContext<ObjectId, Datastore> dataStoreContext) {
        super(dataStoreContext);
    }

    @Override
    public CompletableFuture<Optional<TMember>> getOneForUser(UUID userUUID) {
        return CompletableFuture.supplyAsync(() -> Optional.ofNullable(asQuery(userUUID).get()));
    }

    @Override
    public Query<TMember> asQuery(UUID userUUID) {
        return asQuery().field("userUUID").equal(userUUID);
    }

    @Override
    public CompletableFuture<List<ObjectId>> getSnapshotIds(Query<TMember> query) {
        return CompletableFuture.supplyAsync(() -> {
            Member<ObjectId> member = query.project("snapshotIds", true).get();
            if (member == null) {
                return Collections.emptyList();
            }
            return member.getSnapshotIds();
        });
    }

    @Override
    public CompletableFuture<List<ObjectId>> getSnapshotIds(ObjectId id) {
        return getSnapshotIds(asQuery(id));
    }

    @Override
    public CompletableFuture<List<Instant>> getSnapshotCreationTimes(Query<TMember> query) {
        return getSnapshotIds(query).thenApplyAsync(objectIds -> objectIds.stream().map(o -> Instant.ofEpochSecond(o.getTimestamp())).collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<List<Instant>> getSnapshotCreationTimes(ObjectId id) {
        return getSnapshotCreationTimes(asQuery(id));
    }

    @Override
    public CompletableFuture<List<Instant>> getSnapshotCreationTimesForUser(UUID userUUID) {
        return getSnapshotCreationTimes(asQuery(userUUID));
    }

    @Override
    public CompletableFuture<Boolean> deleteSnapshot(Query<TMember> query, ObjectId snapshotId) {
        return CompletableFuture.supplyAsync(() -> removeSnapshotId(query, snapshotId) && snapshotRepository.deleteOne(snapshotId).join());
    }

    @Override
    public CompletableFuture<Boolean> deleteSnapshot(Query<TMember> query, Instant createdUtc) {
        return getSnapshotIds(query)
            .thenApplyAsync(objectIds -> objectIds.stream()
                .filter(objectId -> Instant.ofEpochSecond(objectId.getTimestamp()).equals(createdUtc))
                .findFirst()
                .map(snapshotId -> removeSnapshotId(query, snapshotId) && snapshotRepository.deleteOne(snapshotId).join())
                .orElse(false)
            );
    }

    private boolean removeSnapshotId(Query<TMember> query, ObjectId snapshotId) {
        return getDataStoreContext().getDataStore().update(query, createUpdateOperations().removeAll("snapshotIds", snapshotId)).getUpdatedCount() > 0;
    }

    @Override
    public CompletableFuture<Boolean> deleteSnapshot(ObjectId id, ObjectId snapshotId) {
        return deleteSnapshot(asQuery(id), snapshotId);
    }

    @Override
    public CompletableFuture<Boolean> deleteSnapshot(ObjectId id, Instant createdUtc) {
        return deleteSnapshot(asQuery(id), createdUtc);
    }

    @Override
    public CompletableFuture<Boolean> deleteSnapshotForUser(UUID userUUID, ObjectId snapshotId) {
        return deleteSnapshot(asQuery(userUUID), snapshotId);
    }

    @Override
    public CompletableFuture<Boolean> deleteSnapshotForUser(UUID userUUID, Instant createdUtc) {
        return deleteSnapshot(asQuery(userUUID), createdUtc);
    }

    @Override
    public CompletableFuture<Boolean> addSnapshot(Query<TMember> query, ObjectId snapshotId) {
        return update(query, createUpdateOperations().addToSet("snapshotIds", snapshotId));
    }

    @Override
    public CompletableFuture<Boolean> addSnapshot(ObjectId id, ObjectId snapshotId) {
        return addSnapshot(asQuery(id), snapshotId);
    }

    @Override
    public CompletableFuture<Boolean> addSnapshotForUser(UUID userUUID, ObjectId snapshotId) {
        return getOneOrGenerateForUser(userUUID).thenApplyAsync(optionalMember -> {
            if (!optionalMember.isPresent()) {
                return false;
            }
            return addSnapshot(asQuery(userUUID), snapshotId).join();
        });
    }

    @Override
    public CompletableFuture<Optional<TSnapshot>> getSnapshot(Query<TMember> query, Instant createdUtc) {
        return getSnapshotIds(query)
            .thenApplyAsync(objectIds -> objectIds.stream()
                .filter(objectId -> Instant.ofEpochSecond(objectId.getTimestamp()).equals(createdUtc))
                .findFirst()
                .flatMap(objectId -> snapshotRepository.getOne(objectId).join())
            );
    }

    @Override
    public CompletableFuture<Optional<TSnapshot>> getSnapshot(ObjectId id, Instant createdUtc) {
        return getSnapshot(asQuery(id), createdUtc);
    }

    @Override
    public CompletableFuture<Optional<TSnapshot>> getSnapshotForUser(UUID userUUID, Instant createdUtc) {
        return getSnapshot(asQuery(userUUID), createdUtc);
    }

    @Override
    public CompletableFuture<List<ObjectId>> getClosestSnapshots(Query<TMember> query, Instant createdUtc) {
        final long seconds = createdUtc.getEpochSecond();
        return getSnapshotIds(query).thenApplyAsync(objectIds -> {
            Optional<ObjectId> closestBefore = Optional.empty();
            Optional<ObjectId> closestAfter = Optional.empty();
            Optional<ObjectId> same = Optional.empty();

            for (ObjectId objectId : objectIds) {
                long toTest = objectId.getTimestamp();

                if (toTest == seconds) {
                    same = Optional.of(objectId);
                } else if (toTest < seconds && (!closestBefore.isPresent() || toTest > closestBefore.get().getTimestamp())) {
                    closestBefore = Optional.of(objectId);
                } else if (toTest > seconds && (!closestAfter.isPresent() || toTest < closestAfter.get().getTimestamp())) {
                    closestAfter = Optional.of(objectId);
                }
            }

            List<ObjectId> toReturn = new ArrayList<>();
            closestBefore.ifPresent(toReturn::add);
            same.ifPresent(toReturn::add);
            closestAfter.ifPresent(toReturn::add);
            return toReturn;
        });
    }

    @Override
    public CompletableFuture<List<ObjectId>> getClosestSnapshots(ObjectId id, Instant createdUtc) {
        return getClosestSnapshots(asQuery(id), createdUtc);
    }

    @Override
    public CompletableFuture<List<ObjectId>> getClosestSnapshotsForUser(UUID userUUID, Instant createdUtc) {
        return getClosestSnapshots(asQuery(userUUID), createdUtc);
    }
}
