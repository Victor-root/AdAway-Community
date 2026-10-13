package org.adaway.ui.prefs;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.adaway.R;
import org.adaway.helper.ThemeHelper;

/**
 * This activity is the preferences activity.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class PrefsActivity extends AppCompatActivity implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    static final String PREFERENCE_NOT_FOUND = "preference not found";

    static void setAppBarTitle(PreferenceFragmentCompat fragment, @StringRes int title) {
        FragmentActivity activity = fragment.getActivity();
        if (!(activity instanceof PrefsActivity)) {
            return;
        }
        ActionBar supportActionBar = ((PrefsActivity) activity).getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setTitle(title);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        /*
         * Set view content.
         */
        setContentView(R.layout.prefs_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_container, new PrefsMainFragment())
                    .commit();
        }
        /*
         * Configure actionbar.
         */
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
        } else {
            super.onSupportNavigateUp();
        }
        return true;
    }

    @Override
    public boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller, Preference pref) {
        // Instantiate the new fragment
        String fragmentClassName = pref.getFragment();
        if (fragmentClassName == null) {
            return false;
        }
        Fragment fragment = getSupportFragmentManager().getFragmentFactory().instantiate(
                getClassLoader(),
                fragmentClassName
        );
        Bundle args = pref.getExtras();
        fragment.setArguments(args);
        // The caller used to be recorded on the new fragment with setTargetFragment, copied from
        // an older revision of Android's settings guide. Nothing ever read it back, and the
        // method is deprecated, so it is gone: the sub-screens receive what they need through
        // their arguments.
        // Replace the existing Fragment with the new Fragment
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(
                        R.animator.prefs_fragment_open_enter, R.animator.prefs_fragment_open_exit,
                        R.animator.prefs_fragment_close_enter, R.animator.prefs_fragment_close_exit
                )
                .replace(R.id.settings_container, fragment)
                .addToBackStack(null)
                .commit();
        return true;
    }
}
