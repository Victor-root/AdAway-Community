package org.adaway.ui.lists;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * This class tests the conversion of the list search to an SQL LIKE pattern.
 */
public class ListsFilterTest {
    @Test
    public void testEmptySearchMatchesEverything() {
        assertEquals("%%", ListsFilter.ALL.sqlQuery);
    }

    @Test
    public void testWildcardsAreConverted() {
        assertEquals("%ads%", new ListsFilter(true, "ads").sqlQuery);
        assertEquals("%%.example.com%", new ListsFilter(true, "*.example.com").sqlQuery);
        assertEquals("%ad_.com%", new ListsFilter(false, "ad?.com").sqlQuery);
    }
}
