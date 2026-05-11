package org.adaway.ui.log;

import static org.adaway.ui.Animations.removeView;
import static org.adaway.ui.Animations.showView;
import static java.lang.Boolean.TRUE;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.adaway.R;
import org.adaway.db.entity.ListType;
import org.adaway.helper.ThemeHelper;
import org.adaway.util.Clipboard;

public class TvLogActivity extends AppCompatActivity {

    private LogViewModel mViewModel;
    private TvLogAdapter adapter;
    private RecyclerView logList;
    private TextView emptyTextView;
    private Button refreshButton;
    private Button toggleRecordingButton;
    private Button clearButton;

    private boolean vpnIsActive = false;
    private boolean vpnStatusInitialized = false;
    private boolean isRecordingLogs = false;

    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (mViewModel != null) {
                // The isRecordingLogs field is updated by the observer, so we use it directly here.
                if (isRecordingLogs) {
                    mViewModel.updateLogs();
                }
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.tv_activity_log);

        logList = findViewById(R.id.log_list);
        emptyTextView = findViewById(R.id.empty_text_view);
        refreshButton = findViewById(R.id.btn_refresh);
        toggleRecordingButton = findViewById(R.id.btn_toggle_recording);
        clearButton = findViewById(R.id.btn_clear);

        mViewModel = new ViewModelProvider(this).get(LogViewModel.class);

        adapter = new TvLogAdapter(this, this::onLogItemClicked);
        logList.setLayoutManager(new LinearLayoutManager(this));
        logList.setAdapter(adapter);

        mViewModel.getLogs().observe(this, logEntries -> {
            if (logEntries.isEmpty()) {
                showView(emptyTextView);
            } else {
                removeView(emptyTextView);
            }
            adapter.submitList(logEntries);
        });

        mViewModel.isRecording().observe(this, recording -> {
            isRecordingLogs = recording;
            // If VPN becomes inactive while recording, stop recording.
            if (vpnStatusInitialized && !vpnIsActive && isRecordingLogs) {
                mViewModel.toggleRecording(); // This will set isRecordingLogs to false
            }
            updateToggleRecordingButtonState();
            if (isRecordingLogs) {
                mViewModel.updateLogs();
            }
        });

        mViewModel.getVpnStatus().observe(this, vpnActive -> {
            vpnIsActive = vpnActive;
            vpnStatusInitialized = true;
            // If VPN becomes inactive while recording, stop recording.
            if (!vpnIsActive && isRecordingLogs) {
                mViewModel.toggleRecording(); // This will set isRecordingLogs to false
            }
            updateToggleRecordingButtonState();
        });

        refreshButton.setOnClickListener(v -> mViewModel.updateLogs());
        toggleRecordingButton.setOnClickListener(v -> mViewModel.toggleRecording());
        clearButton.setOnClickListener(v -> mViewModel.clearLogs());
    }

    private void updateToggleRecordingButtonState() {
        if (!vpnIsActive) {
            toggleRecordingButton.setEnabled(false);
            toggleRecordingButton.setText(getString(R.string.log_start_recording_vpn_inactive));
        } else {
            toggleRecordingButton.setEnabled(true);
            toggleRecordingButton.setText(isRecordingLogs ? getString(R.string.log_stop_recording) : getString(R.string.log_start_recording_button));
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        mViewModel.updateLogs();
        handler.postDelayed(updateRunnable, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(updateRunnable);
    }

    private void onLogItemClicked(LogEntry entry) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(entry.getHost());

        String[] options = {
            "Add to Allowed",
            "Add to Blocked",
            "Copy Hostname"
        };
        
        ListType type = entry.getType();
        
        // Adjust options based on current status
        if (type == ListType.ALLOWED) {
             options[0] = "Remove from Allowed";
        } else if (type == ListType.BLOCKED) {
             options[1] = "Remove from Blocked";
        }

        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0: // Allowed
                    if (type == ListType.ALLOWED) {
                        mViewModel.removeListItem(entry.getHost());
                    } else {
                        mViewModel.addListItem(entry.getHost(), ListType.ALLOWED, null);
                    }
                    break;
                case 1: // Blocked
                    if (type == ListType.BLOCKED) {
                         mViewModel.removeListItem(entry.getHost());
                    } else {
                        mViewModel.addListItem(entry.getHost(), ListType.BLOCKED, null);
                    }
                    break;
                case 2: // Copy
                    Clipboard.copyHostToClipboard(this, entry.getHost());
                    break;
            }
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
}
