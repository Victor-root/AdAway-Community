package org.adaway.ui.hosts;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.adaway.R;
import org.adaway.db.entity.HostsSource;

/**
 * The {@link RecyclerView.Adapter} for {@link TvHostsSourcesActivity}'s source list.
 * <p>
 * Deliberately not {@link HostsSourcesAdapter}: that one is built around a checkbox to toggle a
 * source and a plain click to edit it, and here every row is a single click that opens a dialog
 * for both, the same way {@link org.adaway.ui.lists.TvListsAdapter} replaces the equivalent
 * checkbox-plus-click pair on the Lists screen. The status-text and host-count formatting is
 * shared with {@code HostsSourcesAdapter} through {@link HostsSourceFormat}.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class TvHostsSourcesAdapter extends ListAdapter<HostsSource, TvHostsSourcesAdapter.ViewHolder> {
    interface OnItemClickListener {
        void onItemClick(HostsSource source);
    }

    private final Context context;
    private final OnItemClickListener onItemClickListener;

    TvHostsSourcesAdapter(Context context, OnItemClickListener onItemClickListener) {
        super(HostsSourceFormat.DIFF_CALLBACK);
        this.context = context;
        this.onItemClickListener = onItemClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.tv_item_source, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HostsSource source = getItem(position);
        holder.labelTextView.setText(source.getLabel());
        holder.urlTextView.setText(source.getUrl());
        holder.statusTextView.setText(HostsSourceFormat.getUpdateText(this.context, source));
        holder.countTextView.setText(HostsSourceFormat.getHostCount(this.context, source));
        holder.itemView.setAlpha(source.isEnabled() ? 1f : 0.5f);
        holder.itemView.setOnClickListener(v -> this.onItemClickListener.onItemClick(source));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView labelTextView;
        final TextView urlTextView;
        final TextView statusTextView;
        final TextView countTextView;

        ViewHolder(View itemView) {
            super(itemView);
            this.labelTextView = itemView.findViewById(R.id.tv_source_item_label);
            this.urlTextView = itemView.findViewById(R.id.tv_source_item_url);
            this.statusTextView = itemView.findViewById(R.id.tv_source_item_status);
            this.countTextView = itemView.findViewById(R.id.tv_source_item_count);
        }
    }
}
