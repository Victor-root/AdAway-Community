package org.adaway.ui.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.util.log.ApplicationLog;

import static java.util.Objects.requireNonNull;
import static org.adaway.model.adblocking.AdBlockMethod.ROOT;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.adaway.ui.prefs.PrefsActivity.PREFERENCE_NOT_FOUND;
import static org.adaway.util.Constants.PREFS_NAME;

/**
 * This fragment is the preferences main fragment.
 * <p>
 * It carries every setting, including the VPN and root ad blocker sections that used to be
 * sub-screens reached from here. Their behaviour lives in {@link VpnPrefsBinder} and
 * {@link RootPrefsBinder} so this class stays about the screen as a whole.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class PrefsMainFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private VpnPrefsBinder vpnPrefs;
    private RootPrefsBinder rootPrefs;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Configure preferences
        getPreferenceManager().setSharedPreferencesName(PREFS_NAME);
        addPreferencesFromResource(R.xml.preferences_main);
        // Bind pref actions
        bindThemePrefAction();
        this.vpnPrefs = new VpnPrefsBinder(this);
        this.vpnPrefs.bind();
        this.rootPrefs = new RootPrefsBinder(this);
        this.rootPrefs.bind();
        bindAdBlockMethod();
        hideDebugCategoryOnReleaseBuild();
        // The root section restarts the web server when its icon setting changes
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @NonNull
    @Override
    public RecyclerView.Adapter<?> onCreateAdapter(@NonNull PreferenceScreen preferenceScreen) {
        return new FocusSkippingPreferenceGroupAdapter(super.onCreateAdapter(preferenceScreen));
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        PrefsActivity.setAppBarTitle(this, R.string.pref_main_title);
    }

    @Override
    public void onResume() {
        super.onResume();
        PrefsActivity.setAppBarTitle(this, R.string.pref_main_title);
        this.rootPrefs.onResume();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        this.rootPrefs.onSharedPreferenceChanged(key);
    }

    private void bindThemePrefAction() {
        Preference darkThemePref = findPreference(getString(R.string.pref_dark_theme_mode_key));
        requireNonNull(darkThemePref, PREFERENCE_NOT_FOUND);
        darkThemePref.setOnPreferenceChangeListener((preference, newValue) -> {
            requireActivity().recreate();
            // Allow preference change
            return true;
        });
    }

    /**
     * Dim the ad blocker section that does not apply to the method in use. Both sections are
     * kept on screen so the user can see what the other mode offers, exactly as the two entries
     * that led to these settings behaved before they were inlined here.
     */
    private void bindAdBlockMethod() {
        AdBlockMethod adBlockMethod = PreferenceHelper.getAdBlockMethod(requireContext());
        setCategoryEnabled(R.string.pref_root_ad_block_method_key, adBlockMethod == ROOT);
        setCategoryEnabled(R.string.pref_vpn_ad_block_method_key, adBlockMethod == VPN);
    }

    /**
     * Enable or disable every setting of a category. A category does not pass its own enabled
     * state down to its children, so each one is set explicitly.
     *
     * @param keyResId The category key resource.
     * @param enabled  Whether the settings it holds can be used.
     */
    private void setCategoryEnabled(int keyResId, boolean enabled) {
        PreferenceCategory category = findPreference(getString(keyResId));
        requireNonNull(category, PREFERENCE_NOT_FOUND);
        category.setEnabled(enabled);
        if (enabled) {
            // Deliberately not touching the children here. Several of them are governed by a
            // dependency (the web server switch) or by their own binding (the IPv6 redirection
            // follows the IPv6 setting), and forcing them all enabled would override that.
            return;
        }
        for (int index = 0; index < category.getPreferenceCount(); index++) {
            category.getPreference(index).setEnabled(false);
        }
    }

    /**
     * Hide the whole debug category on a release build. The category only holds
     * developer tooling (debug logging), so it is irrelevant to end users.
     * A release APK is not flagged debuggable, which is what we test.
     */
    private void hideDebugCategoryOnReleaseBuild() {
        if (ApplicationLog.isApplicationDebuggable(requireContext())) {
            return;
        }
        PreferenceCategory debugCategory = findPreference(getString(R.string.pref_debug_category_key));
        if (debugCategory != null) {
            debugCategory.setVisible(false);
        }
    }
}
