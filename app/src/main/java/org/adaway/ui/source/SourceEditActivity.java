package org.adaway.ui.source;

import static android.view.View.INVISIBLE;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;

import org.adaway.R;
import org.adaway.databinding.SourceEditActivityBinding;
import org.adaway.helper.ThemeHelper;

/**
 * This activity create, edit and delete a hosts source.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class SourceEditActivity extends AbstractSourceEditActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        /*
         * Set view content.
         */
        SourceEditActivityBinding binding = SourceEditActivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        /*
         * Configure actionbar.
         */
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        initSourceEdit(
                binding.labelEditText,
                binding.blockFormatButton,
                binding.allowFormatButton,
                binding.typeButtonGroup,
                R.id.url_button,
                R.id.file_button,
                binding.urlTextInputLayout,
                binding.locationEditText,
                binding.fileLocationTextView,
                binding.redirectedHostsCheckbox,
                binding.redirectedHostsWarningTextView,
                INVISIBLE
        );
    }

    @Override
    protected void onEditSource() {
        // The title is the activity label
    }

    @Override
    protected void onAddSource() {
        setTitle(R.string.source_edit_add_title);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.source_edit_menu, menu);
        menu.findItem(R.id.delete_action).setVisible(isEditing());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // Check item identifier
        if (item.getItemId() == R.id.delete_action) {
            deleteSource();
            return true;
        } else if (item.getItemId() == R.id.apply_action) {
            return saveSource();
        }
        return false;
    }
}
