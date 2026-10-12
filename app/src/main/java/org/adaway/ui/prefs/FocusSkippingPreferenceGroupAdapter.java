package org.adaway.ui.prefs;

import android.annotation.SuppressLint;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * A preference list adapter whose disabled rows are not focusable.
 * <p>
 * The stock adapter disables a row's click handling when its {@link Preference} is disabled (a
 * whole section like the root ad blocker settings here, when VPN mode is active), but leaves its
 * row view focusable regardless, since disabling a preference and disabling its view are two
 * different things the library does not tie together. A disabled row is still clickable-inert but
 * remains an individual D-pad stop, so reaching whatever comes after a several-row disabled
 * section (e.g. after "Clear diagnostic log", the whole root ad blocker category) takes one D-pad
 * press per disabled row instead of jumping straight past them, and every one of those presses
 * looks like nothing happened. This ties the two back together: disabled means unfocusable, the
 * same relationship every other disabled control (a plain disabled {@code Button}, for one)
 * already has by default.
 * <p>
 * Used by every {@link PrefsFragment} via {@code onCreateAdapter}, not
 * only the one this was reported on: the same stock behaviour, and the same fix, apply wherever a
 * preference is conditionally disabled (a category here, individual rows behind
 * {@code app:dependency} or a manual {@code setEnabled(false)} in {@link PrefsUpdateFragment}).
 * Touch is unaffected either way: focusability only changes what a D-pad or keyboard can land on,
 * never what a tap does.
 * <p>
 * This wraps the stock adapter from {@link PreferenceFragmentCompat#onCreateAdapter} rather than
 * extending it, as that class is internal to the AndroidX Preference library. Binding a
 * {@link Preference} sets its row view enabled state from {@link Preference#isEnabled()} and its
 * focusable state from {@link Preference#isSelectable()}, so after the stock binding a row stays
 * focusable only when it is both.
 */
class FocusSkippingPreferenceGroupAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
        implements PreferenceGroup.PreferencePositionCallback {
    @SuppressWarnings("rawtypes") // The stock adapter type is internal to the library
    private final RecyclerView.Adapter delegate;

    FocusSkippingPreferenceGroupAdapter(@NonNull RecyclerView.Adapter<?> delegate) {
        this.delegate = delegate;
        super.setHasStableIds(delegate.hasStableIds());
        delegate.registerAdapterDataObserver(new ForwardingObserver());
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return this.delegate.onCreateViewHolder(parent, viewType);
    }

    @SuppressWarnings("unchecked") // The holder comes from the delegate's onCreateViewHolder
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        this.delegate.onBindViewHolder(holder, position);
        skipFocusIfDisabled(holder);
    }

    @SuppressWarnings("unchecked") // The holder comes from the delegate's onCreateViewHolder
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        this.delegate.onBindViewHolder(holder, position, payloads);
        skipFocusIfDisabled(holder);
    }

    private static void skipFocusIfDisabled(RecyclerView.ViewHolder holder) {
        holder.itemView.setFocusable(holder.itemView.isFocusable() && holder.itemView.isEnabled());
    }

    @Override
    public int getItemCount() {
        return this.delegate.getItemCount();
    }

    @Override
    public int getItemViewType(int position) {
        return this.delegate.getItemViewType(position);
    }

    @Override
    public long getItemId(int position) {
        return this.delegate.getItemId(position);
    }

    @Override
    public int getPreferenceAdapterPosition(@NonNull String key) {
        return this.delegate instanceof PreferenceGroup.PreferencePositionCallback
                ? ((PreferenceGroup.PreferencePositionCallback) this.delegate).getPreferenceAdapterPosition(key)
                : RecyclerView.NO_POSITION;
    }

    @Override
    public int getPreferenceAdapterPosition(@NonNull Preference preference) {
        return this.delegate instanceof PreferenceGroup.PreferencePositionCallback
                ? ((PreferenceGroup.PreferencePositionCallback) this.delegate).getPreferenceAdapterPosition(preference)
                : RecyclerView.NO_POSITION;
    }

    /**
     * Forwards the stock adapter's change notifications (a preference added, removed, changed or
     * shown) to the list.
     */
    private class ForwardingObserver extends RecyclerView.AdapterDataObserver {
        @SuppressLint("NotifyDataSetChanged") // Forwards the stock adapter's own full refresh
        @Override
        public void onChanged() {
            notifyDataSetChanged();
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount) {
            notifyItemRangeChanged(positionStart, itemCount);
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount, Object payload) {
            notifyItemRangeChanged(positionStart, itemCount, payload);
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            notifyItemRangeInserted(positionStart, itemCount);
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            notifyItemRangeRemoved(positionStart, itemCount);
        }

        @Override
        public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
            // RecyclerView only supports moving one item at a time
            notifyItemMoved(fromPosition, toPosition);
        }
    }
}
