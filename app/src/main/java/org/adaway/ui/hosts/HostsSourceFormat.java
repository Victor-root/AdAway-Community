package org.adaway.ui.hosts;

import android.content.Context;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import org.adaway.R;
import org.adaway.db.entity.HostsSource;

import java.time.Duration;
import java.time.ZonedDateTime;

/**
 * This class formats the hosts sources for the mobile and TV source lists.
 */
final class HostsSourceFormat {
    /**
     * This callback is use to compare hosts sources.
     */
    static final DiffUtil.ItemCallback<HostsSource> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<HostsSource>() {
                @Override
                public boolean areItemsTheSame(@NonNull HostsSource oldSource, @NonNull HostsSource newSource) {
                    return oldSource.getUrl().equals(newSource.getUrl());
                }

                @Override
                public boolean areContentsTheSame(@NonNull HostsSource oldSource, @NonNull HostsSource newSource) {
                    // NOTE: if you use equals, your object must properly override Object#equals()
                    // Incorrectly returning false here will result in too many animations.
                    return oldSource.equals(newSource);
                }
            };
    private static final String[] QUANTITY_PREFIXES = new String[]{"k", "M", "G"};

    private HostsSourceFormat() {

    }

    /**
     * Get the update status of a source.
     *
     * @param context The application context.
     * @param source  The source.
     * @return The update status text.
     */
    static String getUpdateText(Context context, HostsSource source) {
        // Check if source is enabled
        if (!source.isEnabled()) {
            return context.getString(R.string.hosts_source_disabled);
        }
        // Check if the last retrieval failed
        if (source.isSyncFailed()) {
            return context.getString(R.string.hosts_source_sync_failed);
        }
        // Check modification dates
        boolean lastOnlineModificationDefined = source.getOnlineModificationDate() != null;
        boolean lastLocalModificationDefined = source.getLocalModificationDate() != null;
        // Check if last online modification date is known
        if (lastOnlineModificationDefined) {
            // Get last online modification delay
            String approximateDelay = getApproximateDelay(context, source.getOnlineModificationDate());
            if (!lastLocalModificationDefined) {
                return context.getString(R.string.hosts_source_last_update, approximateDelay);
            } else if (source.getOnlineModificationDate().isAfter(source.getLocalModificationDate())) {
                return context.getString(R.string.hosts_source_need_update, approximateDelay);
            } else {
                return context.getString(R.string.hosts_source_up_to_date, approximateDelay);
            }
        } else if (lastLocalModificationDefined) {
            String approximateDelay = getApproximateDelay(context, source.getLocalModificationDate());
            return context.getString(R.string.hosts_source_installed, approximateDelay);
        } else {
            return context.getString(R.string.hosts_source_unknown_status);
        }
    }

    private static String getApproximateDelay(Context context, ZonedDateTime from) {
        // Get resource for plurals
        Resources resources = context.getResources();
        // Get delay between from and now in minutes
        long delay = Duration.between(from, ZonedDateTime.now()).toMinutes();
        // Check if delay is lower than an hour
        if (delay < 60) {
            return resources.getString(R.string.hosts_source_few_minutes);
        }
        // Get delay in hours
        delay /= 60;
        // Check if delay is lower than a day
        if (delay < 24) {
            int hours = (int) delay;
            return resources.getQuantityString(R.plurals.hosts_source_hours, hours, hours);
        }
        // Get delay in days
        delay /= 24;
        // Check if delay is lower than a month
        if (delay < 30) {
            int days = (int) delay;
            return resources.getQuantityString(R.plurals.hosts_source_days, days, days);
        }
        // Get delay in months
        int months = (int) delay / 30;
        return resources.getQuantityString(R.plurals.hosts_source_months, months, months);
    }

    /**
     * Get the host count of a source.
     *
     * @param context The application context.
     * @param source  The source.
     * @return The host count text, empty for a disabled or empty source.
     */
    static String getHostCount(Context context, HostsSource source) {
        if (source.getSize() <= 0 || !source.isEnabled()) {
            return "";
        }
        return context.getString(R.string.hosts_count, getCompactCount(source.getSize()));
    }

    /**
     * Format a count with a k, M or G suffix from a thousand on.
     * Note: NumberFormat.getCompactNumberInstance is Java 12 only.
     *
     * @param count The count to format, positive.
     * @return The compact count.
     */
    static String getCompactCount(int count) {
        // Compute count decimal length
        int length = 1;
        for (int size = count; size >= 10; size /= 10) {
            length++;
        }
        // Compute prefix to use: none below a thousand, then k, M and G (an int stays below 3G)
        int prefixIndex = (length - 1) / 3 - 1;
        if (prefixIndex < 0) {
            return Integer.toString(count);
        }
        long rounded = Math.round(count / Math.pow(10, (prefixIndex + 1) * 3D));
        return rounded + QUANTITY_PREFIXES[prefixIndex];
    }
}
