package org.adaway.ui.lists.type;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.arch.core.util.Function;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.adaway.R;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.ListType;
import org.adaway.ui.dialog.AlertDialogValidator;

/**
 * This class is a {@link AbstractListFragment} for the lists whose items are a hostname only,
 * the blocked and allowed hosts, which share the same add and edit dialog.
 */
public abstract class HostnameListFragment extends AbstractListFragment {
    /**
     * Get the type of the list items.
     *
     * @return The type of the list items.
     */
    protected abstract ListType getListType();

    /**
     * Get the layout of the add and edit dialog, with a {@code list_dialog_hostname} input.
     *
     * @return The dialog layout.
     */
    @LayoutRes
    protected abstract int getDialogLayout();

    @StringRes
    protected abstract int getAddDialogTitle();

    @StringRes
    protected abstract int getEditDialogTitle();

    /**
     * Get the hostname validator.
     *
     * @return The validator returning whether a hostname is valid for this list.
     */
    protected abstract Function<String, Boolean> getHostnameValidator();

    @Override
    public void addItem() {
        showDialog(null);
    }

    @Override
    protected void editItem(HostListItem item) {
        showDialog(item);
    }

    /**
     * Show the add or edit dialog.
     *
     * @param item The item to edit, {@code null} to add a new one.
     */
    private void showDialog(@Nullable HostListItem item) {
        // Create dialog view
        View view = LayoutInflater.from(this.mActivity).inflate(getDialogLayout(), null);
        EditText inputEditText = view.findViewById(R.id.list_dialog_hostname);
        if (item != null) {
            inputEditText.setText(item.getHost());
            // Move cursor to end of EditText
            inputEditText.setSelection(inputEditText.getText().length());
        }
        Function<String, Boolean> validator = getHostnameValidator();
        // Create dialog
        AlertDialog alertDialog = new MaterialAlertDialogBuilder(this.mActivity)
                .setCancelable(true)
                .setTitle(item == null ? getAddDialogTitle() : getEditDialogTitle())
                .setView(view)
                // Setup buttons
                .setPositiveButton(
                        item == null ? R.string.button_add : R.string.button_save,
                        (dialog, which) -> {
                            // Close dialog
                            dialog.dismiss();
                            // Check hostname validity
                            String hostname = inputEditText.getText().toString();
                            if (!validator.apply(hostname)) {
                                return;
                            }
                            if (item == null) {
                                this.mViewModel.addListItem(getListType(), hostname, null);
                            } else {
                                this.mViewModel.updateListItem(item, hostname, null);
                            }
                        }
                )
                .setNegativeButton(
                        R.string.button_cancel,
                        (dialog, which) -> dialog.dismiss()
                )
                .create();
        // Show dialog
        alertDialog.show();
        // Set button validation behavior: an edited item starts from a valid hostname
        inputEditText.addTextChangedListener(
                new AlertDialogValidator(alertDialog, validator, item != null)
        );
    }
}
