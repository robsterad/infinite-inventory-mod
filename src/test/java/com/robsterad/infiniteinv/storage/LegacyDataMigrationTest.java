package com.robsterad.infiniteinv.storage;

import com.mojang.serialization.DynamicOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.packs.repository.ServerPacksSource;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.datafix.DataFixers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class LegacyDataMigrationTest {
    private static HolderLookup.Provider registries;
    private static final String PLAYER = "00000000-0000-0000-0000-000000000001";
    private static final String CURRENT_FILE = "infinite-inventory/infinite_inventory.dat";

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RegistryAccess.Frozen builtin = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var resources = new net.minecraft.server.packs.resources.MultiPackResourceManager(
                net.minecraft.server.packs.PackType.SERVER_DATA, java.util.List.of(ServerPacksSource.createVanillaPackSource().fullResources()));
        builtin.registries().forEach(entry -> {
            net.minecraft.tags.TagLoader.loadTagsForRegistry(resources, (net.minecraft.core.WritableRegistry<?>) entry.value());
            entry.value().freeze();
        });
        var world = RegistryDataLoader.load(resources, builtin.listRegistries().toList(),
                RegistryDataLoader.WORLD_REGISTRIES, Runnable::run).join();
        registries = HolderLookup.Provider.create(Stream.concat(builtin.listRegistries(), world.listRegistries()));
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries).forEach(pending -> pending.apply());
    }

    private static int targetVersion() { return SharedConstants.getCurrentVersion().dataVersion().version(); }
    private static DynamicOps<Tag> ops() {
        return RegistryOps.create(NbtOps.INSTANCE, registries);
    }

    private static CompoundTag fixture(int version, String item) throws Exception {
        return TagParser.parseCompoundFully("{DataVersion:" + version + ",data:{players:{\"" + PLAYER
                + "\":[{item:" + item + ",count:5000000000L,ts:123456789L}]},uiPrefs:{\"" + PLAYER
                + "\":{panelVisible:0b,sortMode:\"ALPHA_DESC\",showTooltips:1b}}}}");
    }

    private static CompoundTag item(CompoundTag root) {
        return root.getCompound("data").orElseThrow().getCompound("players").orElseThrow()
                .getList(PLAYER).orElseThrow().getCompound(0).orElseThrow().getCompound("item").orElseThrow();
    }

    @Test void upgrades116And120MetadataThroughMinecraftItemFixers() throws Exception {
        String sword = """
                {id:"minecraft:diamond_sword",Count:1b,tag:{display:{Name:'{"text":"Steve"}'},
                 Damage:37,Enchantments:[{id:"minecraft:sharpness",lvl:5s}],customTestData:"preserved"}}
                """;
        for (int sourceVersion : new int[]{2586, 3463}) {
            CompoundTag original = fixture(sourceVersion, sword);
            CompoundTag upgraded = LegacyDataMigration.upgradeItems(original, DataFixers.getDataFixer(), targetVersion());
            String converted = item(upgraded).toString();
            assertTrue(converted.contains("minecraft:custom_name"), converted);
            assertTrue(converted.contains("Steve"), converted);
            assertTrue(converted.contains("minecraft:damage"), converted);
            assertTrue(converted.contains("37"), converted);
            assertTrue(converted.contains("minecraft:sharpness"), converted);
            assertTrue(converted.contains("preserved"), converted);
            assertEquals(1, item(original).getByte("Count").orElseThrow().intValue());
            assertEquals(original.getCompound("data").orElseThrow().getCompound("uiPrefs"),
                    upgraded.getCompound("data").orElseThrow().getCompound("uiPrefs"));
        }
    }

    @Test void upgrades1205And1206ComponentItems() throws Exception {
        for (int sourceVersion : new int[]{3837, 3839}) {
            CompoundTag upgraded = LegacyDataMigration.upgradeItems(fixture(sourceVersion, """
                    {id:"minecraft:diamond_sword",count:1,components:{
                     "minecraft:custom_name":'{"text":"Steve"}',"minecraft:damage":37,
                     "minecraft:enchantments":{levels:{"minecraft:sharpness":5}}}}
                    """), DataFixers.getDataFixer(), targetVersion());
            assertTrue(item(upgraded).toString().contains("Steve"));
            assertTrue(item(upgraded).toString().contains("minecraft:sharpness"));
        }
    }

    @Test void upgrades1218AndEarlier26Items() throws Exception {
        for (int sourceVersion : new int[]{4440, 4788, 4903}) {
            if (sourceVersion > targetVersion()) continue;
            CompoundTag upgraded = LegacyDataMigration.upgradeItems(fixture(sourceVersion,
                    "{id:\"minecraft:stone\",count:1}"), DataFixers.getDataFixer(), targetVersion());
            assertEquals("minecraft:stone", item(upgraded).getString("id").orElseThrow());
        }
    }

    @Test void migratesDiskFileCountsTimestampsPreferencesAndKeepsOriginalBackup(@TempDir Path dir) throws Exception {
        Path flat = dir.resolve("infinite_inventory.dat");
        NbtIo.writeCompressed(fixture(2586, "{id:\"minecraft:grass_path\",Count:1b}"), flat);
        byte[] original = Files.readAllBytes(flat);
        InfiniteInventoryState[] loaded = new InfiniteInventoryState[1];
        assertTrue(LegacyDataMigration.migrateFile(dir, DataFixers.getDataFixer(), targetVersion(), ops(), s -> loaded[0] = s));
        assertArrayEquals(original, Files.readAllBytes(dir.resolve("infinite_inventory.dat.pre-migration-2586.bak")));
        CompoundTag saved = NbtIo.readCompressed(dir.resolve(CURRENT_FILE), NbtAccounter.unlimitedHeap());
        assertEquals("minecraft:dirt_path", item(saved).getString("id").orElseThrow());
        assertEquals(targetVersion(), saved.getInt("DataVersion").orElseThrow());
        UUID player = UUID.fromString(PLAYER);
        assertEquals(5_000_000_000L, loaded[0].players.get(player).getAllItems().values().iterator().next());
        assertEquals(123456789L, loaded[0].itemTimestamps.get(player).values().iterator().next());
        assertEquals(new InfiniteInventoryState.UiPrefs(false, "ALPHA_DESC", true), loaded[0].uiPrefs.get(player));
        assertFalse(LegacyDataMigration.migrateFile(dir, DataFixers.getDataFixer(), targetVersion(), ops(),
                s -> fail("A repeated startup must not import the old inventory again")));
    }

    @Test void currentSaveIsAuthoritativeOverStaleFlatSave(@TempDir Path dir) throws Exception {
        NbtIo.writeCompressed(fixture(2586, "{id:\"minecraft:stone\",Count:1b}"), dir.resolve("infinite_inventory.dat"));
        Path current = dir.resolve(CURRENT_FILE);
        Files.createDirectories(current.getParent());
        CompoundTag root = fixture(targetVersion(), "{id:\"minecraft:stone\",count:1}");
        NbtIo.writeCompressed(root, current);
        byte[] original = Files.readAllBytes(current);
        assertFalse(LegacyDataMigration.migrateFile(dir, DataFixers.getDataFixer(), targetVersion(), ops(),
                s -> fail("Current data must not be replaced by the stale flat file")));
        assertArrayEquals(original, Files.readAllBytes(current));
    }

    @Test void refusesUnknownItemsBeforeWritingOrInstalling(@TempDir Path dir) throws Exception {
        Path flat = dir.resolve("infinite_inventory.dat");
        NbtIo.writeCompressed(fixture(2586, "{id:\"missing_mod:removed_item\",Count:1b}"), flat);
        byte[] original = Files.readAllBytes(flat);
        assertThrows(Exception.class, () -> LegacyDataMigration.migrateFile(dir, DataFixers.getDataFixer(),
                targetVersion(), ops(), s -> fail("Partial inventories must not be installed")));
        assertArrayEquals(original, Files.readAllBytes(flat));
        if (!CURRENT_FILE.equals("infinite_inventory.dat")) assertFalse(Files.exists(dir.resolve(CURRENT_FILE)));
    }


    @Test void migratesNamedEnchantedDamagedItemsThroughTheFullStateCodec(@TempDir Path dir) throws Exception {
        for (int version : new int[]{2586, 3463, 3837, 3839}) {
            Path dataDir = dir.resolve(Integer.toString(version));
            Files.createDirectories(dataDir);
            String sword = version < 3837 ? """
                    {id:"minecraft:diamond_sword",Count:1b,tag:{display:{Name:'{"text":"Steve"}'},
                     Damage:37,Enchantments:[{id:"minecraft:sharpness",lvl:5s}],customTestData:"preserved"}}
                    """ : """
                    {id:"minecraft:diamond_sword",count:1,components:{
                     "minecraft:custom_name":'{"text":"Steve"}',"minecraft:damage":37,
                     "minecraft:enchantments":{levels:{"minecraft:sharpness":5}},
                     "minecraft:custom_data":{customTestData:"preserved"}}}
                    """;
            NbtIo.writeCompressed(fixture(version, sword), dataDir.resolve("infinite_inventory.dat"));
            InfiniteInventoryState[] loaded = new InfiniteInventoryState[1];
            LegacyDataMigration.migrateFile(dataDir, DataFixers.getDataFixer(), targetVersion(), ops(), state -> loaded[0] = state);
            var key = loaded[0].players.get(UUID.fromString(PLAYER)).getAllItems().keySet().iterator().next();
            var stack = key.toStack(1);
            assertEquals("Steve", stack.getHoverName().getString());
            assertEquals(37, stack.getDamageValue());
            String saved = item(NbtIo.readCompressed(dataDir.resolve(CURRENT_FILE), NbtAccounter.unlimitedHeap())).toString();
            assertTrue(saved.contains("minecraft:sharpness"), saved);
            assertTrue(saved.contains("preserved"), saved);
        }
    }

    @Test void refusesMissingDataVersionAndDowngrades() throws Exception {
        CompoundTag root = fixture(2586, "{id:\"minecraft:stone\",Count:1b}");
        root.remove("DataVersion");
        assertThrows(IllegalArgumentException.class, () -> LegacyDataMigration.upgradeItems(root,
                DataFixers.getDataFixer(), targetVersion()));
        assertThrows(IllegalArgumentException.class, () -> LegacyDataMigration.upgradeItems(
                fixture(targetVersion() + 1, "{id:\"minecraft:stone\",count:1}"),
                DataFixers.getDataFixer(), targetVersion()));
    }

    @Test void mergesItemsThatBecomeIdenticalAfterUpgrading(@TempDir Path dir) throws Exception {
        CompoundTag root = fixture(2586, "{id:\"minecraft:grass_path\",Count:1b}");
        var entries = root.getCompound("data").orElseThrow().getCompound("players").orElseThrow().getList(PLAYER).orElseThrow();
        entries.add(TagParser.parseCompoundFully("{item:{id:\"minecraft:dirt_path\",Count:1b},count:64L,ts:123456800L}"));
        NbtIo.writeCompressed(root, dir.resolve("infinite_inventory.dat"));
        InfiniteInventoryState[] loaded = new InfiniteInventoryState[1];
        LegacyDataMigration.migrateFile(dir, DataFixers.getDataFixer(), targetVersion(), ops(), s -> loaded[0] = s);
        assertEquals(1, loaded[0].players.get(UUID.fromString(PLAYER)).getAllItems().size());
        assertEquals(5_000_000_064L, loaded[0].players.get(UUID.fromString(PLAYER)).getAllItems().values().iterator().next());
        assertEquals(123456800L, loaded[0].itemTimestamps.get(UUID.fromString(PLAYER)).values().iterator().next());
    }
}

