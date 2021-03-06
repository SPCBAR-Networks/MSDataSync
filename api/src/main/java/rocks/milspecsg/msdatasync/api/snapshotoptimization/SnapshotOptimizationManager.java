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

package rocks.milspecsg.msdatasync.api.snapshotoptimization;

import rocks.milspecsg.msdatasync.api.snapshotoptimization.component.SnapshotOptimizationService;
import rocks.milspecsg.msrepository.api.manager.Manager;

import java.util.concurrent.CompletableFuture;

public interface SnapshotOptimizationManager<
    TUser,
    TString,
    TCommandSource>
    extends Manager<SnapshotOptimizationService<?, TUser, TCommandSource, ?>> {

    @Override
    default String getDefaultIdentifierSingularUpper() {
        return "Snapshot optimization";
    }

    @Override
    default String getDefaultIdentifierPluralUpper() {
        return "Snapshot optimizations";
    }

    @Override
    default String getDefaultIdentifierSingularLower() {
        return "snapshot optimization";
    }

    @Override
    default String getDefaultIdentifierPluralLower() {
        return "snapshot optimizations";
    }

    CompletableFuture<TString> info();

    CompletableFuture<TString> stop();
}
