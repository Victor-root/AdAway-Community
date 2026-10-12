package org.adaway.db.dao;

import androidx.lifecycle.LiveData;
import androidx.paging.PagingSource;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import org.adaway.db.entity.HostListItem;

import java.util.List;
import java.util.Optional;

import static androidx.room.OnConflictStrategy.REPLACE;

/**
 * This interface is the DAO for {@link HostListItem} entities.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
@Dao
public interface HostListItemDao {
    @Insert(onConflict = REPLACE)
    void insert(HostListItem... item);

    @Update
    void update(HostListItem item);

    @Delete
    void delete(HostListItem item);

    @Query("DELETE FROM hosts_lists WHERE source_id = 1 AND host = :host")
    void deleteUserFromHost(String host);

    @Query("SELECT * FROM hosts_lists WHERE type = :type AND host LIKE :query AND ((:includeSources == 0 AND source_id == 1) || (:includeSources == 1)) GROUP BY host ORDER BY host ASC")
    PagingSource<Integer, HostListItem> loadList(int type, boolean includeSources, String query);

    @Query("SELECT * FROM hosts_lists WHERE source_id = 1")
    List<HostListItem> getUserList();

    @Query("SELECT id FROM hosts_lists WHERE host = :host AND source_id = 1 LIMIT 1")
    Optional<Integer> getHostId(String host);

    /**
     * Add a host to the user list, replacing the user list item of the same host if any, so a
     * host can't end up in two user lists (both allowed and blocked) at once.
     *
     * @param item The user list item to add.
     */
    @Transaction
    default void upsertUserItem(HostListItem item) {
        Optional<Integer> id = getHostId(item.getHost());
        if (id.isPresent()) {
            item.setId(id.get());
            update(item);
        } else {
            insert(item);
        }
    }

    @Query("SELECT COUNT(DISTINCT host) FROM hosts_lists WHERE type = 0 AND enabled = 1")
    LiveData<Integer> getBlockedHostCount();

    // Same query as getBlockedHostCount(), as a blocking call for logging from a background
    // thread that isn't guaranteed to have anyone observing the LiveData yet.
    @Query("SELECT COUNT(DISTINCT host) FROM hosts_lists WHERE type = 0 AND enabled = 1")
    int countBlockedHosts();

    @Query("SELECT COUNT(DISTINCT host) FROM hosts_lists WHERE type = 1 AND enabled = 1")
    LiveData<Integer> getAllowedHostCount();

    @Query("SELECT COUNT(DISTINCT host) FROM hosts_lists WHERE type = 2 AND enabled = 1")
    LiveData<Integer> getRedirectHostCount();

    @Query("DELETE FROM hosts_lists WHERE source_id = :sourceId")
    void clearSourceHosts(int sourceId);
}
