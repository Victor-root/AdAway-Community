package org.adaway.db;

import static org.adaway.db.Migrations.MIGRATION_1_2;
import static org.adaway.db.Migrations.MIGRATION_2_3;
import static org.adaway.db.Migrations.MIGRATION_3_4;
import static org.adaway.db.Migrations.MIGRATION_4_5;
import static org.adaway.db.Migrations.MIGRATION_5_6;
import static org.adaway.db.Migrations.MIGRATION_6_7;
import static org.adaway.db.Migrations.MIGRATION_7_8;

import androidx.room.migration.Migration;
import androidx.room.testing.MigrationTestHelper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * This class tests the database migrations against the exported schemas (app/schemas).
 */
@RunWith(AndroidJUnit4.class)
public class MigrationTest {
    private static final String TEST_DB = "migration-test";
    private static final int LATEST_VERSION = 8;
    private static final Migration[] ALL_MIGRATIONS = {
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8
    };

    @Rule
    public MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase.class
    );

    /**
     * Check every version migrates to the latest schema, as installs can update from any version.
     */
    @Test
    public void migrateFromEveryVersion() throws IOException {
        for (int version = 1; version < LATEST_VERSION; version++) {
            String name = TEST_DB + "-" + version;
            this.helper.createDatabase(name, version).close();
            this.helper.runMigrationsAndValidate(name, LATEST_VERSION, true, ALL_MIGRATIONS).close();
        }
    }
}
