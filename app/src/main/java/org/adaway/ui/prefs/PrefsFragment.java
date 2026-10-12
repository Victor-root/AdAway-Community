package org.adaway.ui.prefs;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

/**
 * This class is the base of the {@link PrefsActivity} screens: they show their title in the app
 * bar and skip disabled rows when navigating with a D-pad.
 */
public abstract class PrefsFragment extends PreferenceFragmentCompat {
    /**
     * Get the screen title.
     *
     * @return The screen title string resource.
     */
    @StringRes
    protected abstract int getTitle();

    @NonNull
    @Override
    public RecyclerView.Adapter<?> onCreateAdapter(@NonNull PreferenceScreen preferenceScreen) {
        return new FocusSkippingPreferenceGroupAdapter(super.onCreateAdapter(preferenceScreen));
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        PrefsActivity.setAppBarTitle(this, getTitle());
    }
}
