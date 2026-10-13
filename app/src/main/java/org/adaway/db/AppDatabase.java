package org.adaway.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;

import org.adaway.R;
import org.adaway.db.converter.ListTypeConverter;
import org.adaway.db.converter.ZonedDateTimeConverter;
import org.adaway.db.dao.HostEntryDao;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.HostEntry;
import org.adaway.util.AppExecutors;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import timber.log.Timber;

import static org.adaway.db.Migrations.MIGRATION_1_2;
import static org.adaway.db.Migrations.MIGRATION_2_3;
import static org.adaway.db.Migrations.MIGRATION_3_4;
import static org.adaway.db.Migrations.MIGRATION_4_5;
import static org.adaway.db.Migrations.MIGRATION_5_6;
import static org.adaway.db.Migrations.MIGRATION_6_7;
import static org.adaway.db.Migrations.MIGRATION_7_8;
import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.adaway.db.entity.HostsSource.USER_SOURCE_URL;

/**
 * This class is the application database based on Room.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
@Database(entities = {HostsSource.class, HostListItem.class, HostEntry.class}, version = 8)
@TypeConverters({ListTypeConverter.class, ZonedDateTimeConverter.class})
public abstract class AppDatabase extends RoomDatabase {
    /**
     * The database singleton instance.
     */
    private static volatile AppDatabase instance;
    /**
     * Set from {@link Callback#onCreate}, on a fresh install only - stays {@code null} for the
     * whole life of the process on every other run, since {@code onCreate} itself never fires
     * then. See {@link #awaitInitialization(Context)}.
     */
    private static volatile FutureTask<Void> initializationTask;

    /**
     * Get the database instance.
     *
     * @param context The application context.
     * @return The database instance.
     */
    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "app.db"
                    ).addCallback(new Callback() {
                        @Override
                        public void onCreate(@NonNull SupportSQLiteDatabase db) {
                            FutureTask<Void> task = new FutureTask<>(
                                    () -> AppDatabase.initialize(context, instance), null);
                            initializationTask = task;
                            AppExecutors.getInstance().diskIO().execute(task);
                        }
                    }).addMigrations(
                            MIGRATION_1_2,
                            MIGRATION_2_3,
                            MIGRATION_3_4,
                            MIGRATION_4_5,
                            MIGRATION_5_6,
                            MIGRATION_6_7,
                            MIGRATION_7_8
                    ).build();
                }
            }
        }
        return instance;
    }

    /**
     * Block the calling thread until the default hosts sources seeded by {@link
     * Callback#onCreate} have actually landed, if that seeding is still in flight.
     * <p>
     * On a fresh install, the app's very first real query is what makes Room open the database
     * file and fire {@code onCreate} - on whichever thread issued that query. {@code onCreate}
     * itself only hands the insert off to a separate background executor and returns
     * immediately, so without this, that same first caller could read the hosts source table
     * before its own seeding had landed, see zero rows, and (for a sync) wrongly conclude there
     * was nothing to do - exactly what a fresh Android TV install's first sync did.
     * <p>
     * {@code getWritableDatabase()} forces that same lazy open to happen right here if it has
     * not happened yet, so {@code initializationTask} is guaranteed to already be set (on a
     * fresh install) by the time it is read below, instead of this method racing that open
     * itself. A cheap no-op on every call after the first: the open is cached by Room, and
     * {@link FutureTask#get()} on an already-finished task returns immediately.
     *
     * @param context The application context.
     */
    public static void awaitInitialization(Context context) {
        getInstance(context).getOpenHelper().getWritableDatabase();
        FutureTask<Void> task = initializationTask;
        if (task == null) {
            return;
        }
        try {
            task.get();
        } catch (ExecutionException e) {
            Timber.w(e, "Database initialization failed.");
        } catch (InterruptedException e) {
            Timber.w(e, "Interrupted while waiting for database initialization.");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Initialize the database content.
     */
    private static void initialize(Context context, AppDatabase database) {
        // Check if there is no hosts source
        HostsSourceDao hostsSourceDao = database.hostsSourceDao();
        if (!hostsSourceDao.getAll().isEmpty()) {
            return;
        }
        // User list
        HostsSource userSource = new HostsSource();
        userSource.setLabel(context.getString(R.string.hosts_user_source));
        userSource.setId(USER_SOURCE_ID);
        userSource.setUrl(USER_SOURCE_URL);
        userSource.setAllowEnabled(true);
        userSource.setRedirectEnabled(true);
        hostsSourceDao.insert(userSource);
        // AdAway official
        HostsSource source1 = new HostsSource();
        source1.setLabel(context.getString(R.string.hosts_adaway_source));
        source1.setUrl("https://adaway.org/hosts.txt");
        hostsSourceDao.insert(source1);
        // StevenBlack
        HostsSource source2 = new HostsSource();
        source2.setLabel(context.getString(R.string.hosts_stevenblack_source));
        source2.setUrl("https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts");
        hostsSourceDao.insert(source2);
        // Pete Lowe
        HostsSource source3 = new HostsSource();
        source3.setLabel(context.getString(R.string.hosts_peterlowe_source));
        source3.setUrl("https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext");
        hostsSourceDao.insert(source3);
    }

    /**
     * Get the hosts source DAO.
     *
     * @return The hosts source DAO.
     */
    public abstract HostsSourceDao hostsSourceDao();

    /**
     * Get the hosts list item DAO.
     *
     * @return The hosts list item DAO.
     */
    public abstract HostListItemDao hostsListItemDao();

    /**
     * Get the hosts entry DAO.
     *
     * @return The hosts entry DAO.
     */
    public abstract HostEntryDao hostEntryDao();
}
