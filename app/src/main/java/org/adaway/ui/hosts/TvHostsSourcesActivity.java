package org.adaway.ui.hosts;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.adaway.R;
import org.adaway.db.entity.HostsSource;
import org.adaway.helper.ThemeHelper;
import org.adaway.ui.adblocking.ApplyConfigurationSnackbar;
import org.adaway.ui.source.TvSourceEditActivity;

import static org.adaway.ui.source.AbstractSourceEditActivity.SOURCE_ID;

/**
 * The Android TV screen for the hosts sources, built the same way {@link
 * org.adaway.ui.lists.TvListsActivity} rebuilds the Lists screen: a from-scratch layout instead
 * of the phone's ({@link HostsSourcesActivity}/{@link HostsSourcesFragment}) floating add button
 * and checkbox-plus-click rows, both touch patterns with no clean D-pad equivalent.
 * <p>
 * The data layer is untouched: the same {@link HostsSourcesViewModel}. Adding or editing a source
 * opens {@link TvSourceEditActivity}, not the phone's {@link org.adaway.ui.source.SourceEditActivity}:
 * reported as technically usable but impractical with a remote (Save/Delete live in the ActionBar
 * overflow, the fields have no guaranteed D-pad order), the same class of problem the list screens had.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class TvHostsSourcesActivity extends AppCompatActivity {
    private HostsSourcesViewModel viewModel;
    private TvHostsSourcesAdapter adapter;
    private RecyclerView recyclerView;
    private TextView emptyTextView;
    private ActivityResultLauncher<Intent> sourceEditLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.tv_activity_hosts_sources);

        this.recyclerView = findViewById(R.id.tv_sources_recycler);
        this.emptyTextView = findViewById(R.id.tv_sources_empty_text);
        MaterialButton addButton = findViewById(R.id.tv_sources_add);
        MaterialButton applyButton = findViewById(R.id.tv_sources_apply);
        TextView applyHintText = findViewById(R.id.tv_sources_apply_hint);

        this.viewModel = new ViewModelProvider(this).get(HostsSourcesViewModel.class);
        this.adapter = new TvHostsSourcesAdapter(this, this::onItemClicked);
        this.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        this.recyclerView.setAdapter(this.adapter);

        // Adding/editing/deleting a source happens in a separate Activity (TvSourceEditActivity),
        // not through this.viewModel, so its result (RESULT_OK only on a real save or delete, not
        // on cancel/back) is what tells notifyModelChanged() a change actually happened there.
        this.sourceEditLauncher = registerForActivityResult(new StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                this.viewModel.notifyModelChanged();
            }
        });

        // Same prompt-before-apply flow as HostsSourcesFragment: syncSources=true and
        // ignoreEventDuringInstall=true match that fragment's own instantiation exactly, since
        // toggling or editing a source (unlike a Lists entry) can change what gets fetched, not
        // just what gets kept.
        //
        // createObserver()'s own Snackbar action button is a touch-only control (a Snackbar sits
        // outside the normal focus order, unreachable by D-pad), reported stuck showing "Apply"
        // with no way to actually press it. createPendingObserver() drives the header's own
        // "apply" button instead, and apply() is called directly on click; the underlying
        // sync/apply logic is exactly the one the Snackbar's own action button already used.
        //
        // getModelChanged(), not getHostsSources(): that one is the raw, reactive query result,
        // which re-emits on any write to the table, background syncs included, so the button
        // this drove used to pop up (and on TV, with nothing to auto-dismiss it, stay stuck
        // showing) from a sync the user never asked to apply. See HostsSourcesViewModel.
        View contentRoot = findViewById(android.R.id.content);
        ApplyConfigurationSnackbar applySnackbar = new ApplyConfigurationSnackbar(contentRoot, true, true);
        this.viewModel.getModelChanged().observe(this, applySnackbar.createPendingObserver(() -> {
            applyButton.setVisibility(View.VISIBLE);
            applyHintText.setVisibility(View.VISIBLE);
            addButton.setNextFocusRightId(R.id.tv_sources_apply);
        }));
        this.viewModel.getHostsSources().observe(this, sources -> {
            this.adapter.submitList(sources);
            updateEmptyState(sources.isEmpty());
        });

        addButton.setOnClickListener(v -> startSourceEdition(null));
        applyButton.setOnClickListener(v -> {
            applyButton.setVisibility(View.GONE);
            applyHintText.setVisibility(View.GONE);
            addButton.setNextFocusRightId(View.NO_ID);
            applySnackbar.apply();
        });
    }

    private void updateEmptyState(boolean empty) {
        this.emptyTextView.setVisibility(empty ? View.VISIBLE : View.GONE);
        this.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void onItemClicked(HostsSource source) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(source.getLabel());
        String[] options = {
                getString(source.isEnabled() ? R.string.tv_dialog_disable_entry : R.string.tv_dialog_enable_entry),
                getString(R.string.checkbox_list_context_edit),
        };
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                this.viewModel.toggleSourceEnabled(source);
            } else if (which == 1) {
                startSourceEdition(source);
            }
        });
        builder.setNegativeButton(R.string.button_cancel, null);
        builder.show();
    }

    private void startSourceEdition(@Nullable HostsSource source) {
        Intent intent = new Intent(this, TvSourceEditActivity.class);
        if (source != null) {
            intent.putExtra(SOURCE_ID, source.getId());
        }
        this.sourceEditLauncher.launch(intent);
    }
}
