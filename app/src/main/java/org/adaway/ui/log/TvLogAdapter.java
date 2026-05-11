package org.adaway.ui.log;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.adaway.R;
import org.adaway.db.entity.ListType;
import org.adaway.ui.log.LogEntry;

public class TvLogAdapter extends ListAdapter<LogEntry, TvLogAdapter.ViewHolder> {

    private final Context context;
    private final OnItemClickListener onItemClickListener;

    public interface OnItemClickListener {
        void onItemClick(LogEntry entry);
    }

    public TvLogAdapter(Context context, OnItemClickListener onItemClickListener) {
        super(new DiffCallback());
        this.context = context;
        this.onItemClickListener = onItemClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.tv_item_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LogEntry entry = getItem(position);
        holder.bind(entry);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView statusIcon;
        private final TextView hostName;
        private final TextView timestamp;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            statusIcon = itemView.findViewById(R.id.iv_status_icon);
            hostName = itemView.findViewById(R.id.tv_host_name);
            timestamp = itemView.findViewById(R.id.tv_timestamp);

            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    onItemClickListener.onItemClick(getItem(position));
                }
            });
        }

        public void bind(LogEntry entry) {
            hostName.setText(entry.getHost());
            
            // Format timestamp - LogEntry doesn't have getTimestamp currently available in the public model shown.
            // Assuming it might be added or we just skip it for now if not available.
            // Checking LogEntry.java content again... it ONLY has host and type.
            // So we can't show timestamp.
            timestamp.setVisibility(View.GONE);

            // Set icon based on status
            ListType type = entry.getType();
            if (type != null && type == ListType.BLOCKED) {
                statusIcon.setImageResource(R.drawable.baseline_block_24);
                statusIcon.setColorFilter(context.getResources().getColor(R.color.blocked, null));
            } else if (type != null && type == ListType.ALLOWED) {
                statusIcon.setImageResource(R.drawable.baseline_check_24);
                statusIcon.setColorFilter(context.getResources().getColor(R.color.allowed, null));
            } else {
                statusIcon.setImageResource(R.drawable.dot_outline);
                statusIcon.clearColorFilter();
            }
        }
    }

    static class DiffCallback extends DiffUtil.ItemCallback<LogEntry> {
        @Override
        public boolean areItemsTheSame(@NonNull LogEntry oldItem, @NonNull LogEntry newItem) {
            return oldItem.getHost().equals(newItem.getHost());
        }

        @Override
        public boolean areContentsTheSame(@NonNull LogEntry oldItem, @NonNull LogEntry newItem) {
            return oldItem.equals(newItem);
        }
    }
}
