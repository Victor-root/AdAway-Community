package org.adaway.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Objects;

import static androidx.room.ForeignKey.CASCADE;

/**
 * This entity represents a black, white or redirect list item.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
@Entity(
        tableName = "hosts_lists",
        indices = {
                @Index(value = "host"),
                @Index(value = "source_id")
        },
        foreignKeys = @ForeignKey(
                entity = HostsSource.class,
                parentColumns = "id",
                childColumns = "source_id",
                onUpdate = CASCADE,
                onDelete = CASCADE
        )
)
public class HostListItem {
    @PrimaryKey(autoGenerate = true)
    private int id;
    @NonNull
    private String host;
    @NonNull
    private ListType type;
    private boolean enabled;
    private String redirection;
    @ColumnInfo(name = "source_id")
    private int sourceId;

    /**
     * Create an enabled item of the user list.
     *
     * @param type        The list type.
     * @param host        The host name.
     * @param redirection The redirection IP, {@code null} unless redirected.
     * @return The user list item.
     */
    public static HostListItem userItem(@NonNull ListType type, @NonNull String host, String redirection) {
        HostListItem item = new HostListItem();
        item.setType(type);
        item.setHost(host);
        item.setRedirection(redirection);
        item.setEnabled(true);
        item.setSourceId(HostsSource.USER_SOURCE_ID);
        return item;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @NonNull
    public String getHost() {
        return host;
    }

    public void setHost(@NonNull String host) {
        this.host = host;
    }

    @NonNull
    public ListType getType() {
        return type;
    }

    public void setType(@NonNull ListType type) {
        this.type = type;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRedirection() {
        return redirection;
    }

    public void setRedirection(String redirection) {
        this.redirection = redirection;
    }

    public int getSourceId() {
        return sourceId;
    }

    public void setSourceId(int sourceId) {
        this.sourceId = sourceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HostListItem item = (HostListItem) o;

        if (id != item.id) return false;
        if (enabled != item.enabled) return false;
        if (sourceId != item.sourceId) return false;
        if (!host.equals(item.host)) return false;
        if (type != item.type) return false;
        return Objects.equals(redirection, item.redirection);

    }

    @Override
    public int hashCode() {
        int result = id;
        result = 31 * result + host.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + (enabled ? 1 : 0);
        result = 31 * result + (redirection != null ? redirection.hashCode() : 0);
        result = 31 * result + sourceId;
        return result;
    }
}
