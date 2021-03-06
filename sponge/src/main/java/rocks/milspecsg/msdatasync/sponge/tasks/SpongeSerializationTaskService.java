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

package rocks.milspecsg.msdatasync.sponge.tasks;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import rocks.milspecsg.msdatasync.common.tasks.CommonSerializationTaskService;
import rocks.milspecsg.msdatasync.sponge.plugin.MSDataSync;
import rocks.milspecsg.msrepository.api.data.registry.Registry;
import rocks.milspecsg.msrepository.api.plugin.PluginInfo;

import java.util.concurrent.TimeUnit;

@Singleton
public class SpongeSerializationTaskService extends CommonSerializationTaskService<User, Text, CommandSource> {

    @Inject
    PluginInfo<Text> pluginInfo;

    private Task task = null;

    @Inject
    public SpongeSerializationTaskService(Registry registry) {
        super(registry);
    }

    @Override
    public void startSerializationTask() {
        if (baseInterval > 0) {
            Sponge.getServer().getConsole().sendMessage(Text.of(pluginInfo.getPrefix(), TextColors.YELLOW, "Submitting sync task! Upload interval: ", baseInterval, " minutes"));
            task = Task.builder().async().interval(30, TimeUnit.SECONDS).execute(getSerializationTask()).submit(MSDataSync.plugin);
        } else {
            Sponge.getServer().getConsole().sendMessage(Text.of(pluginInfo.getPrefix(), TextColors.RED, "Sync task has been disabled from config!"));
        }
    }

    @Override
    public void stopSerializationTask() {
        if (task != null) task.cancel();
    }

    @Override
    public Runnable getSerializationTask() {
        return () -> {
            if (snapshotOptimizationManager.getPrimaryComponent().isOptimizationTaskRunning()) {
                Sponge.getServer().getConsole().sendMessage(Text.of(pluginInfo.getPrefix(), TextColors.RED, "Optimization task already running! Task will skip"));
            } else {
                snapshotOptimizationManager.getPrimaryComponent().optimize(Sponge.getServer().getOnlinePlayers(), Sponge.getServer().getConsole(), "Auto", MSDataSync.plugin);
            }
        };
    }
}
