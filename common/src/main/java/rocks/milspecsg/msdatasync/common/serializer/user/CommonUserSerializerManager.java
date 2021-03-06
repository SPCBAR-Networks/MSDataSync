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

package rocks.milspecsg.msdatasync.common.serializer.user;

import com.google.inject.Inject;
import rocks.milspecsg.msdatasync.api.member.MemberManager;
import rocks.milspecsg.msdatasync.api.model.member.Member;
import rocks.milspecsg.msdatasync.api.model.snapshot.Snapshot;
import rocks.milspecsg.msdatasync.api.serializer.user.UserSerializerManager;
import rocks.milspecsg.msdatasync.api.serializer.user.component.UserSerializerComponent;
import rocks.milspecsg.msrepository.api.data.registry.Registry;
import rocks.milspecsg.msrepository.api.plugin.PluginInfo;
import rocks.milspecsg.msrepository.api.util.StringResult;
import rocks.milspecsg.msrepository.api.util.TimeFormatService;
import rocks.milspecsg.msrepository.api.util.UserService;
import rocks.milspecsg.msrepository.common.manager.CommonManager;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public class CommonUserSerializerManager<
    TMember extends Member<?>,
    TSnapshot extends Snapshot<?>,
    TUser,
    TPlayer,
    TString,
    TCommandSource>
    extends CommonManager<UserSerializerComponent<?, TSnapshot, TUser, ?>>
    implements UserSerializerManager<TSnapshot, TUser, TString> {

    @Inject
    MemberManager<TMember, TSnapshot, TUser, TString> memberManager;

    @Inject
    StringResult<TString, TCommandSource> stringResult;

    @Inject
    PluginInfo<TString> pluginInfo;

    @Inject
    UserService<TUser, TPlayer> userService;

    @Inject
    TimeFormatService timeFormatService;

    @Inject
    public CommonUserSerializerManager(Registry registry) {
        super(registry);
    }

    @Override
    public CompletableFuture<TString> serialize(Collection<? extends TUser> users) {
        if (users.isEmpty()) {
            return CompletableFuture.completedFuture(
                stringResult.builder()
                    .append(pluginInfo.getPrefix())
                    .red().append("There are no players currently online")
                    .build()
            );
        }

        ConcurrentLinkedQueue<TUser> successful = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<TUser> unsuccessful = new ConcurrentLinkedQueue<>();
        CompletableFuture<TString> result = new CompletableFuture<>();

        for (TUser user : users) {
            getPrimaryComponent().serialize(user, "Manual").thenAcceptAsync(optionalSnapshot -> {
                if (optionalSnapshot.isPresent()) {
                    successful.add(user);
                } else {
                    unsuccessful.add(user);
                }
                if (successful.size() + unsuccessful.size() >= users.size()) {
                    StringResult.Builder<TString, TCommandSource> builder = stringResult.builder();
                    if (!successful.isEmpty()) {
                        String s = successful.stream().map(u -> userService.getUserName(user)).collect(Collectors.joining(", "));
                        builder.yellow().append("The following players were successfully serialized:\n").green().append(s);
                    }
                    if (!unsuccessful.isEmpty()) {
                        String s = unsuccessful.stream().map(u -> userService.getUserName(user)).collect(Collectors.joining(", "));
                        builder.red().append("The following players were unsuccessfully serialized:\n").green().append(s);
                    }
                    result.complete(builder.build());
                }
            });
        }
        return result;
    }

    @Override
    public CompletableFuture<TString> serialize(TUser user, String name) {
        return getPrimaryComponent().serialize(user, name).thenApplyAsync(optionalSnapshot ->
            optionalSnapshot.isPresent()
                ? stringResult.builder()
                .append(pluginInfo.getPrefix())
                .yellow().append("Successfully uploaded snapshot ")
                .gold().append(timeFormatService.format(optionalSnapshot.get().getCreatedUtc()), " (", name, ")")
                .yellow().append(" for ", userService.getUserName(user), "!")
                .build()
                : stringResult.builder()
                .append(pluginInfo.getPrefix())
                .red().append("An error occurred while serializing ", name, " for ", userService.getUserName(user), "!")
                .build());
    }

    @Override
    public CompletableFuture<TString> serialize(TUser user) {
        return serialize(user, "Manual");
    }

    @Override
    public CompletableFuture<TString> deserialize(TUser user, Object plugin, String event) {
        return getPrimaryComponent().deserialize(user, plugin).thenApplyAsync(optionalSnapshot ->
            optionalSnapshot.isPresent()
                ? stringResult.builder()
                .append(pluginInfo.getPrefix())
                .yellow().append("Successfully downloaded snapshot ")
                .gold().append(timeFormatService.format(optionalSnapshot.get().getCreatedUtc()), " (", optionalSnapshot.get().getName(), ")")
                .yellow().append(" for ", userService.getUserName(user), " on ", event, "!")
                .build()
                : stringResult.builder()
                .append(pluginInfo.getPrefix())
                .red().append("An error occurred while deserializing ", userService.getUserName(user), " on ", event, "!")
                .build());
    }

    @Override
    public CompletableFuture<TString> deserialize(TUser user, Object plugin) {
        return deserialize(user, plugin, "N/A");
    }

    @Override
    public CompletableFuture<TString> restore(UUID userUUID, Optional<String> optionalString, Object plugin) {
        return memberManager.getPrimaryComponent().getSnapshotForUser(userUUID, optionalString).thenApplyAsync(optionalSnapshot -> {
            Optional<TUser> optionalUser = userService.get(userUUID);
            if (!optionalUser.isPresent()) {
                return stringResult.builder()
                    .append(pluginInfo.getPrefix())
                    .red().append("Could not find ", userUUID)
                    .build();
            }
            String userName = userService.getUserName(optionalUser.get());
            if (!optionalSnapshot.isPresent()) {
                return stringResult.builder()
                    .append(pluginInfo.getPrefix())
                    .red().append("Could not find snapshot for ", userName)
                    .build();
            }
            String createdString = timeFormatService.format(optionalSnapshot.get().getCreatedUtc());
            getPrimaryComponent().deserialize(optionalUser.get(), plugin, optionalSnapshot.get());
            return stringResult.builder()
                .append(pluginInfo.getPrefix())
                .yellow().append("Restored snapshot ", createdString, " for ", userName)
                .build();
        });
    }
}
