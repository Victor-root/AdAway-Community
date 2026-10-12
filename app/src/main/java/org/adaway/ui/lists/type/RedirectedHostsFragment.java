/*
 * Copyright (C) 2011-2012 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This file is part of AdAway.
 *
 * AdAway is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AdAway is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AdAway.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.adaway.ui.lists.type;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.LiveData;
import androidx.paging.PagingData;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.adaway.R;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.ListType;
import org.adaway.ui.dialog.AlertDialogValidator;
import org.adaway.util.RegexUtils;

/**
 * This class is a {@link AbstractListFragment} to display and manage redirected hosts.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class RedirectedHostsFragment extends AbstractListFragment {
    @Override
    protected boolean isTwoRowsItem() {
        return true;
    }

    @Override
    protected LiveData<PagingData<HostListItem>> getData() {
        return this.mViewModel.getRedirectedListItems();
    }

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
        View view = LayoutInflater.from(this.mActivity).inflate(R.layout.lists_redirected_dialog, null);
        EditText hostnameEditText = view.findViewById(R.id.list_dialog_hostname);
        EditText ipEditText = view.findViewById(R.id.list_dialog_ip);
        if (item != null) {
            hostnameEditText.setText(item.getHost());
            ipEditText.setText(item.getRedirection());
            // Move cursor to end of EditText
            hostnameEditText.setSelection(hostnameEditText.getText().length());
        }
        // Create dialog
        AlertDialog alertDialog = new MaterialAlertDialogBuilder(this.mActivity)
                .setCancelable(true)
                .setTitle(item == null ? R.string.list_add_dialog_redirect : R.string.list_edit_dialog_redirect)
                .setView(view)
                // Setup buttons
                .setPositiveButton(
                        item == null ? R.string.button_add : R.string.button_save,
                        (dialog, which) -> {
                            // Close dialog
                            dialog.dismiss();
                            // Check hostname and IP validity
                            String hostname = hostnameEditText.getText().toString();
                            String ip = ipEditText.getText().toString();
                            if (!isValid(hostname, ip)) {
                                return;
                            }
                            if (item == null) {
                                this.mViewModel.addListItem(ListType.REDIRECTED, hostname, ip);
                            } else {
                                this.mViewModel.updateListItem(item, hostname, ip);
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
        // Set button validation behavior: an edited item starts from a valid redirection
        AlertDialogValidator validator = new AlertDialogValidator(
                alertDialog,
                input -> isValid(hostnameEditText.getText().toString(), ipEditText.getText().toString()),
                item != null
        );
        hostnameEditText.addTextChangedListener(validator);
        ipEditText.addTextChangedListener(validator);
    }

    private static boolean isValid(String hostname, String ip) {
        return RegexUtils.isValidHostname(hostname) && RegexUtils.isValidIP(ip);
    }
}