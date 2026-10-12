package com.robsterad.infiniteinv.storage;

import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

/** Upgrades every item in the mod's custom saved-data tree before the current codec reads it. */
public final class LegacyDataMigration {
    private static final String FLAT_FILE = "infinite_inventory.dat";
    private static final String CURRENT_FILE = "infinite-inventory/infinite_inventory.dat";

    private LegacyDataMigration() {}

    public static void migrate(MinecraftServer server) {
        Path dataDir = server.getWorldPath(LevelResource.ROOT).resolve("data");
        int targetVersion = SharedConstants.getCurrentVersion().dataVersion().version();
        DynamicOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
        try {
            migrateFile(dataDir, server.getFixerUpper(), targetVersion, ops, state -> {
                state.setDirty();
                server.overworld().getDataStorage().set(InfiniteInventoryState.TYPE, state);
            });
        } catch (Exception error) {
            // Abort startup rather than allowing an empty fallback state to overwrite a failed import.
            throw new IllegalStateException("[InfiniteInv] Inventory migration failed; original save retained at "
                    + dataDir + ". Restore missing item mods or inspect the save before retrying.", error);
        }
    }

    public static boolean migrateFile(Path dataDir, DataFixer fixer, int targetVersion,
                                      DynamicOps<Tag> ops, Consumer<InfiniteInventoryState> install) throws IOException {
        Path target = dataDir.resolve(CURRENT_FILE);
        Path flat = dataDir.resolve(FLAT_FILE);
        // Once present, the current file is authoritative. Never resurrect a stale flat inventory.
        Path source = Files.exists(target) ? target : flat;
        if (!Files.exists(source)) return false;
        CompoundTag original = NbtIo.readCompressed(source, NbtAccounter.unlimitedHeap());
        int sourceVersion = requireVersion(original);
        if (sourceVersion > targetVersion) {
            throw new IllegalArgumentException("Cannot downgrade inventory data from " + sourceVersion + " to " + targetVersion);
        }
        if (source.equals(target) && sourceVersion == targetVersion) return false;

        CompoundTag upgraded = upgradeItems(original, fixer, targetVersion);
        // Strictly validate all players and items before touching either file. Partial decoding is not accepted.
        InfiniteInventoryState state = InfiniteInventoryState.CODEC.parse(ops,
                upgraded.getCompound("data").orElseThrow()).getOrThrow();

        Path backup = source.resolveSibling(source.getFileName() + ".pre-migration-" + sourceVersion + ".bak");
        if (!Files.exists(backup)) Files.copy(source, backup);
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "infinite-inventory-migration-", ".tmp");
        try {
            NbtIo.writeCompressed(upgraded, temporary);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unavailable) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        install.accept(state);
        System.out.println("[InfiniteInv] Upgraded inventory data " + sourceVersion + " -> " + targetVersion
                + ". Original backup: " + backup);
        return true;
    }

    private static int requireVersion(CompoundTag root) {
        int version = root.getInt("DataVersion").orElseThrow(
                () -> new IllegalArgumentException("Inventory save is missing DataVersion; refusing to guess its format"));
        if (version < 0) throw new IllegalArgumentException("Invalid inventory DataVersion: " + version);
        return version;
    }

    public static CompoundTag upgradeItems(CompoundTag original, DataFixer fixer, int targetVersion) {
        int sourceVersion = requireVersion(original);
        if (sourceVersion > targetVersion) throw new IllegalArgumentException("Inventory downgrades are unsupported");
        CompoundTag root = original.copy();
        CompoundTag data = root.getCompound("data").orElseThrow(
                () -> new IllegalArgumentException("Inventory save is missing its data compound"));
        CompoundTag players = data.getCompound("players").orElseThrow(
                () -> new IllegalArgumentException("Inventory save is missing its players compound"));
        for (String player : players.keySet()) {
            Tag value = players.get(player);
            if (!(value instanceof ListTag items)) throw new IllegalArgumentException("Invalid item list for " + player);
            for (Tag entry : items) {
                if (!(entry instanceof CompoundTag saved)) throw new IllegalArgumentException("Invalid saved item for " + player);
                Tag item = saved.get("item");
                if (!(item instanceof CompoundTag)) throw new IllegalArgumentException("Saved item is missing its item compound");
                if (saved.getLong("count").orElseThrow() <= 0) throw new IllegalArgumentException("Invalid stored item count");
                saved.put("item", fixer.update(References.ITEM_STACK, new Dynamic<>(NbtOps.INSTANCE, item),
                        sourceVersion, targetVersion).getValue());
            }
        }
        root.putInt("DataVersion", targetVersion);
        return root;
    }
}

