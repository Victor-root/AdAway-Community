package org.adaway.ui.hosts;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.adaway.R;
import org.adaway.db.entity.HostsSource;

/**
 * This class is a the {@link RecyclerView.Adapter} for the hosts sources view.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class HostsSourcesAdapter extends ListAdapter<HostsSource, HostsSourcesAdapter.ViewHolder> {

    /**
     * This callback is use to call view actions.
     */
    @NonNull
    private final HostsSourcesViewCallback viewCallback;

    /**
     * Constructor.
     *
     * @param viewCallback The view callback.
     */
    HostsSourcesAdapter(@NonNull HostsSourcesViewCallback viewCallback) {
        super(HostsSourceFormat.DIFF_CALLBACK);
        this.viewCallback = viewCallback;
    }

    @NonNull
    @Override
    public HostsSourcesAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        View view = layoutInflater.inflate(R.layout.hosts_sources_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HostsSource source = this.getItem(position);
        holder.enabledCheckBox.setChecked(source.isEnabled());
        holder.enabledCheckBox.setOnClickListener(view -> viewCallback.toggleEnabled(source));
        holder.labelTextView.setText(source.getLabel());
        holder.urlTextView.setText(source.getUrl());
        holder.updateTextView.setText(HostsSourceFormat.getUpdateText(this.viewCallback.getContext(), source));
        holder.sizeTextView.setText(HostsSourceFormat.getHostCount(this.viewCallback.getContext(), source));
        holder.itemView.setOnClickListener(view -> viewCallback.edit(source));
    }

    /**
     * This class is a the {@link RecyclerView.ViewHolder} for the hosts sources view.
     *
     * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        final CheckBox enabledCheckBox;
        final TextView labelTextView;
        final TextView urlTextView;
        final TextView updateTextView;
        final TextView sizeTextView;

        /**
         * Constructor.
         *
         * @param itemView The hosts sources view.
         */
        ViewHolder(View itemView) {
            super(itemView);
            this.enabledCheckBox = itemView.findViewById(R.id.sourceEnabledCheckBox);
            this.labelTextView = itemView.findViewById(R.id.sourceLabelTextView);
            this.urlTextView = itemView.findViewById(R.id.sourceUrlTextView);
            this.updateTextView = itemView.findViewById(R.id.sourceUpdateTextView);
            this.sizeTextView = itemView.findViewById(R.id.sourceSizeTextView);
        }
    }
}
