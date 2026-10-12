package org.adaway.ui.source;

import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
import static android.view.View.VISIBLE;
import static java.util.Objects.requireNonNull;
import static org.adaway.ui.Animations.hideView;
import static org.adaway.ui.Animations.setHidden;
import static org.adaway.ui.Animations.setShown;
import static org.adaway.ui.Animations.showView;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.IdRes;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.util.AppExecutors;

import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * This class is the base of the mobile and Android TV activities to create, edit and delete a
 * hosts source. They differ in their layout and in how they show their actions, and share the
 * source loading, the form behavior, its validation and the saving.
 */
public abstract class AbstractSourceEditActivity extends AppCompatActivity {
    /**
     * The source identifier extra.
     */
    public static final String SOURCE_ID = "sourceId";
    /**
     * The any type mime type.
     */
    private static final String ANY_MIME_TYPE = "*/*";
    private static final Executor DISK_IO_EXECUTOR = AppExecutors.getInstance().diskIO();
    private static final Executor MAIN_THREAD_EXECUTOR = AppExecutors.getInstance().mainThread();

    private HostsSourceDao hostsSourceDao;
    private ActivityResultLauncher<Intent> startActivityLauncher;
    private boolean editing;
    private HostsSource edited;

    private TextInputEditText labelEditText;
    private MaterialButton blockFormatButton;
    private MaterialButton allowFormatButton;
    private MaterialButtonToggleGroup typeButtonGroup;
    @IdRes
    private int urlButtonId;
    @IdRes
    private int fileButtonId;
    private TextInputLayout urlTextInputLayout;
    private TextInputEditText locationEditText;
    private TextView fileLocationTextView;
    private MaterialCheckBox redirectedHostsCheckbox;
    private TextView redirectedHostsWarningTextView;
    private int hiddenLocationVisibility;

    /**
     * Set up the form once the content view is set.
     *
     * @param hiddenLocationVisibility The visibility of the location field not in use, INVISIBLE or GONE.
     */
    protected void initSourceEdit(
            TextInputEditText labelEditText,
            MaterialButton blockFormatButton,
            MaterialButton allowFormatButton,
            MaterialButtonToggleGroup typeButtonGroup,
            @IdRes int urlButtonId,
            @IdRes int fileButtonId,
            TextInputLayout urlTextInputLayout,
            TextInputEditText locationEditText,
            TextView fileLocationTextView,
            MaterialCheckBox redirectedHostsCheckbox,
            TextView redirectedHostsWarningTextView,
            int hiddenLocationVisibility
    ) {
        this.labelEditText = labelEditText;
        this.blockFormatButton = blockFormatButton;
        this.allowFormatButton = allowFormatButton;
        this.typeButtonGroup = typeButtonGroup;
        this.urlButtonId = urlButtonId;
        this.fileButtonId = fileButtonId;
        this.urlTextInputLayout = urlTextInputLayout;
        this.locationEditText = locationEditText;
        this.fileLocationTextView = fileLocationTextView;
        this.redirectedHostsCheckbox = redirectedHostsCheckbox;
        this.redirectedHostsWarningTextView = redirectedHostsWarningTextView;
        this.hiddenLocationVisibility = hiddenLocationVisibility;
        // Get database access
        this.hostsSourceDao = AppDatabase.getInstance(this).hostsSourceDao();
        // Register for activity
        registerForStartActivity();
        // Set up values
        checkInitialValueFromIntent();
    }

    /**
     * Called when the activity edits an existing source, before it is loaded.
     */
    protected abstract void onEditSource();

    /**
     * Called when the activity adds a new source.
     */
    protected abstract void onAddSource();

    protected boolean isEditing() {
        return this.editing;
    }

    private void registerForStartActivity() {
        this.startActivityLauncher = registerForActivityResult(new StartActivityForResult(), result -> {
            Uri uri;
            Intent data = result.getData();
            if (result.getResultCode() == RESULT_OK
                    && data != null
                    && (uri = data.getData()) != null) {
                // Persist read permission
                getContentResolver().takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION);
                // Update file location
                this.fileLocationTextView.setText(uri.toString());
                this.fileLocationTextView.setError(null);
            }
        });
    }

    private void checkInitialValueFromIntent() {
        Intent intent = getIntent();
        int sourceId = intent.getIntExtra(SOURCE_ID, -1);
        this.editing = sourceId != -1;
        if (this.editing) {
            onEditSource();
            DISK_IO_EXECUTOR.execute(() -> {
                Optional<HostsSource> hostsSource = this.hostsSourceDao.getById(sourceId);
                hostsSource.ifPresent(source -> {
                    this.edited = source;
                    MAIN_THREAD_EXECUTOR.execute(() -> {
                        applyInitialValues(source);
                        bindLocation();
                        bindFormats();
                    });
                });
            });
        } else {
            onAddSource();
            bindLocation();
            bindFormats();
        }
    }

    private void applyInitialValues(HostsSource source) {
        this.labelEditText.setText(source.getLabel());
        this.blockFormatButton.setChecked(!source.isAllowEnabled());
        this.allowFormatButton.setChecked(source.isAllowEnabled());
        switch (source.getType()) {
            case URL:
                this.typeButtonGroup.check(this.urlButtonId);
                this.locationEditText.setText(source.getUrl());
                break;
            case FILE:
                this.typeButtonGroup.check(this.fileButtonId);
                this.fileLocationTextView.setText(source.getUrl());
                this.fileLocationTextView.setVisibility(VISIBLE);
                this.urlTextInputLayout.setVisibility(this.hiddenLocationVisibility);
                break;
        }
        this.redirectedHostsCheckbox.setChecked(source.isRedirectEnabled());
        if (source.isAllowEnabled()) {
            setHidden(this.redirectedHostsCheckbox);
            setHidden(this.redirectedHostsWarningTextView);
        } else {
            setShown(this.redirectedHostsCheckbox);
            setShown(this.redirectedHostsWarningTextView);
        }
    }

    private void bindLocation() {
        this.typeButtonGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            // Keep always one button checked
            if (group.getCheckedButtonId() == View.NO_ID) {
                group.check(checkedId);
                return;
            }
            if (isChecked) {
                boolean isFile = checkedId == this.fileButtonId;
                this.locationEditText.setText(isFile ? "" : "https://");
                this.locationEditText.setEnabled(!isFile);
                if (isFile) {
                    openDocument();
                }
                this.urlTextInputLayout.setVisibility(isFile ? this.hiddenLocationVisibility : VISIBLE);
                this.fileLocationTextView.setVisibility(isFile ? VISIBLE : this.hiddenLocationVisibility);
            }
        });
        this.fileLocationTextView.setOnClickListener(view -> openDocument());
    }

    private void bindFormats() {
        this.blockFormatButton.addOnCheckedChangeListener((button, isChecked) -> {
            if (isChecked) {
                showView(this.redirectedHostsCheckbox);
                showView(this.redirectedHostsWarningTextView);
            } else {
                hideView(this.redirectedHostsCheckbox);
                hideView(this.redirectedHostsWarningTextView);
            }
        });
    }

    /**
     * Save the form source, replacing the edited one, then finish the activity.
     *
     * @return {@code false} if the form is not valid, {@code true} if the source is being saved.
     */
    protected boolean saveSource() {
        HostsSource source = validate();
        if (source == null) {
            return false;
        }
        DISK_IO_EXECUTOR.execute(() -> {
            if (this.editing) {
                this.hostsSourceDao.delete(this.edited);
            }
            this.hostsSourceDao.insert(source);
            setResult(RESULT_OK);
            finish();
        });
        return true;
    }

    /**
     * Delete the edited source then finish the activity.
     */
    protected void deleteSource() {
        DISK_IO_EXECUTOR.execute(() -> this.hostsSourceDao.delete(this.edited));
        setResult(RESULT_OK);
        finish();
    }

    private HostsSource validate() {
        String label = requireNonNull(this.labelEditText.getText()).toString();
        if (label.isEmpty()) {
            this.labelEditText.setError(getString(R.string.source_edit_label_required));
            return null;
        }
        String url;
        if (this.typeButtonGroup.getCheckedButtonId() == this.urlButtonId) {
            url = requireNonNull(this.locationEditText.getText()).toString();
            if (url.isEmpty()) {
                this.locationEditText.setError(getString(R.string.source_edit_url_location_required));
                return null;
            }
            if (!HostsSource.isValidUrl(url)) {
                this.locationEditText.setError(getString(R.string.source_edit_location_invalid));
                return null;
            }
        } else {
            url = this.fileLocationTextView.getText().toString();
            if (!HostsSource.isValidUrl(url)) {
                this.fileLocationTextView.setError(getString(R.string.source_edit_location_invalid));
                return null;
            }
        }
        HostsSource source = new HostsSource();
        source.setLabel(label);
        source.setUrl(url);
        boolean allowFormat = this.allowFormatButton.isChecked();
        source.setAllowEnabled(allowFormat);
        source.setRedirectEnabled(!allowFormat && this.redirectedHostsCheckbox.isChecked());
        return source;
    }

    private void openDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(ANY_MIME_TYPE);
        this.startActivityLauncher.launch(intent);
    }
}
