package org.adaway.ui.lists;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelKt;
import androidx.paging.Pager;
import androidx.paging.PagingConfig;
import androidx.paging.PagingData;

import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.ListType;
import org.adaway.ui.lists.type.AbstractListFragment;
import org.adaway.util.AppExecutors;

import java.util.concurrent.Executor;

import kotlinx.coroutines.CoroutineScope;

import static androidx.lifecycle.Transformations.switchMap;
import static androidx.paging.PagingLiveData.cachedIn;
import static androidx.paging.PagingLiveData.getLiveData;
import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;
import static org.adaway.ui.lists.ListsFilter.ALL;

/**
 * This class is an {@link AndroidViewModel} for the {@link AbstractListFragment} implementations.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class ListsViewModel extends AndroidViewModel {
    private static final Executor EXECUTOR = AppExecutors.getInstance().diskIO();
    private final HostListItemDao hostListItemDao;
    private final MutableLiveData<ListsFilter> filter;
    private final LiveData<PagingData<HostListItem>> blockedListItems;
    private final LiveData<PagingData<HostListItem>> allowedListItems;
    private final LiveData<PagingData<HostListItem>> redirectedListItems;
    private final MutableLiveData<Boolean> modelChanged;

    public ListsViewModel(@NonNull Application application) {
        super(application);
        this.hostListItemDao = AppDatabase.getInstance(application).hostsListItemDao();
        this.filter = new MutableLiveData<>(ALL);
        PagingConfig pagingConfig = new PagingConfig(50, 150, true);
        // Each PagingData can only be collected once: without cachedIn(), re-observing this
        // LiveData after the fragment is recreated (e.g. an Activity recreation on a light/dark
        // theme switch) redelivers the very same, already-collected PagingData to a brand new
        // adapter and crashes with "Attempt to collect twice from pageEventFlow". cachedIn(),
        // scoped to this ViewModel (which survives the recreation), makes each Pager's stream
        // safely re-collectable by a new adapter instance.
        this.blockedListItems = getListItems(BLOCKED, pagingConfig);
        this.allowedListItems = getListItems(ALLOWED, pagingConfig);
        this.redirectedListItems = getListItems(REDIRECTED, pagingConfig);
        this.modelChanged = new MutableLiveData<>(false);
    }

    private LiveData<PagingData<HostListItem>> getListItems(ListType type, PagingConfig pagingConfig) {
        // Each PagingData can only be collected once: without cachedIn(), re-observing this
        // LiveData after the fragment is recreated (e.g. an Activity recreation on a light/dark
        // theme switch) redelivers the very same, already-collected PagingData to a brand new
        // adapter and crashes with "Attempt to collect twice from pageEventFlow". cachedIn(),
        // scoped to this ViewModel (which survives the recreation), makes each Pager's stream
        // safely re-collectable by a new adapter instance.
        CoroutineScope viewModelScope = ViewModelKt.getViewModelScope(this);
        return switchMap(
                this.filter,
                filter -> cachedIn(getLiveData(new Pager<>(pagingConfig, () ->
                        this.hostListItemDao.loadList(type.getValue(), filter.sourcesIncluded, filter.sqlQuery)
                )), viewModelScope)
        );
    }

    public LiveData<PagingData<HostListItem>> getBlockedListItems() {
        return this.blockedListItems;
    }

    public LiveData<PagingData<HostListItem>> getAllowedListItems() {
        return this.allowedListItems;
    }

    public LiveData<PagingData<HostListItem>> getRedirectedListItems() {
        return this.redirectedListItems;
    }

    public LiveData<Boolean> getModelChanged() {
        return this.modelChanged;
    }

    public void toggleItemEnabled(HostListItem item) {
        item.setEnabled(!item.isEnabled());
        EXECUTOR.execute(() -> {
            this.hostListItemDao.update(item);
            this.modelChanged.postValue(true);
        });
    }

    public void addListItem(@NonNull ListType type, @NonNull String host, String redirection) {
        HostListItem item = HostListItem.userItem(type, host, redirection);
        EXECUTOR.execute(() -> {
            this.hostListItemDao.upsertUserItem(item);
            this.modelChanged.postValue(true);
        });
    }

    public void updateListItem(@NonNull HostListItem item, @NonNull String host, String redirection) {
        item.setHost(host);
        item.setRedirection(redirection);
        EXECUTOR.execute(() -> {
            this.hostListItemDao.update(item);
            this.modelChanged.postValue(true);
        });
    }

    public void removeListItem(HostListItem list) {
        EXECUTOR.execute(() -> {
            this.hostListItemDao.delete(list);
            this.modelChanged.postValue(true);
        });
    }

    public void search(String query) {
        ListsFilter currentFilter = getFilter();
        ListsFilter newFilter = new ListsFilter(currentFilter.sourcesIncluded, query);
        setFilter(newFilter);
    }

    public void clearSearch() {
        ListsFilter currentFilter = getFilter();
        ListsFilter newFilter = new ListsFilter(currentFilter.sourcesIncluded, "");
        setFilter(newFilter);
    }

    public void toggleSources() {
        ListsFilter currentFilter = getFilter();
        ListsFilter newFilter = new ListsFilter(!currentFilter.sourcesIncluded, currentFilter.query);
        setFilter(newFilter);
    }

    private ListsFilter getFilter() {
        ListsFilter filter = this.filter.getValue();
        return filter == null ? ALL : filter;
    }

    private void setFilter(ListsFilter filter) {
        this.filter.setValue(filter);
    }
}
