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

import androidx.arch.core.util.Function;
import androidx.lifecycle.LiveData;
import androidx.paging.PagingData;

import org.adaway.R;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.ListType;
import org.adaway.util.RegexUtils;

/**
 * This class is a {@link AbstractListFragment} to display and manage blocked hosts.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class BlockedHostsFragment extends HostnameListFragment {
    @Override
    protected LiveData<PagingData<HostListItem>> getData() {
        return this.mViewModel.getBlockedListItems();
    }

    @Override
    protected ListType getListType() {
        return ListType.BLOCKED;
    }

    @Override
    protected int getDialogLayout() {
        return R.layout.lists_blocked_dialog;
    }

    @Override
    protected int getAddDialogTitle() {
        return R.string.list_add_dialog_black;
    }

    @Override
    protected int getEditDialogTitle() {
        return R.string.list_edit_dialog_black;
    }

    @Override
    protected Function<String, Boolean> getHostnameValidator() {
        return RegexUtils::isValidHostname;
    }
}
