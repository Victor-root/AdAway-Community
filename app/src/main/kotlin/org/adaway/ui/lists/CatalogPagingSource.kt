package org.adaway.ui.lists

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.adaway.engine.Engine
import java.io.File

/** Pages of the rules the enabled sources contain, matching the search text. */
class CatalogPagingSource(private val catalog: File, private val filter: String) : PagingSource<Int, String>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, String> = withContext(Dispatchers.IO) {
        val offset = params.key ?: 0
        try {
            val lines = Engine.queryCatalog(catalog.path, filter, offset, params.loadSize).toList()
            LoadResult.Page(
                data = lines,
                prevKey = null,
                nextKey = if (lines.size < params.loadSize) null else offset + lines.size,
            )
        } catch (exception: RuntimeException) {
            LoadResult.Error(exception)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, String>): Int? = null
}
