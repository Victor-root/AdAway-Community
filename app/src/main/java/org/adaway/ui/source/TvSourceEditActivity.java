package org.adaway.ui.source;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;

import org.adaway.R;
import org.adaway.helper.ThemeHelper;

/**
 * This is the Android TV activity to create, edit and delete a hosts source.
 * <p>
 * Not {@link SourceEditActivity} reused with patches: that layout puts Save/Delete in the
 * ActionBar's overflow, reachable only by steering D-pad focus up into the toolbar, and lays
 * its fields out with ConstraintLayout bias rather than a plain stack, so default D-pad search
 * has no guaranteed top-to-bottom path through it. Reported after testing on a real TV: it works,
 * but is not practical with a remote. This gives it the same shape as the other TV screens
 * (header buttons instead of a menu, one linear column of fields) while reusing every field
 * widget, string and validation rule as-is.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class TvSourceEditActivity extends AbstractSourceEditActivity {
    private TextView titleTextView;
    private MaterialButton deleteButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.tv_activity_source_edit);
        this.titleTextView = findViewById(R.id.tv_source_edit_title);
        MaterialButton applyButton = findViewById(R.id.tv_source_edit_apply);
        this.deleteButton = findViewById(R.id.tv_source_edit_delete);
        applyButton.setOnClickListener(v -> saveSource());
        this.deleteButton.setOnClickListener(v -> deleteSource());
        initSourceEdit(
                findViewById(R.id.tv_source_label),
                findViewById(R.id.tv_source_block_button),
                findViewById(R.id.tv_source_allow_button),
                findViewById(R.id.tv_source_type_group),
                R.id.tv_source_url_button,
                R.id.tv_source_file_button,
                findViewById(R.id.tv_source_url_layout),
                findViewById(R.id.tv_source_location),
                findViewById(R.id.tv_source_file_location),
                findViewById(R.id.tv_source_redirected_checkbox),
                findViewById(R.id.tv_source_redirected_warning),
                GONE
        );
    }

    @Override
    protected void onEditSource() {
        this.deleteButton.setVisibility(VISIBLE);
        this.titleTextView.setText(R.string.source_edit_title);
        setTitle(R.string.source_edit_title);
    }

    @Override
    protected void onAddSource() {
        this.titleTextView.setText(R.string.source_edit_add_title);
        setTitle(R.string.source_edit_add_title);
    }
}
