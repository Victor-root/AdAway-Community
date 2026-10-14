package org.adaway.blocklist

import org.junit.Assert.assertEquals
import org.junit.Test

class LikePatternTest {
    @Test
    fun convertsWildcardsAndEscapesSqlCharacters() {
        assertEquals("%%", UserRuleRepository.likePattern(""))
        assertEquals("%ads.example%", UserRuleRepository.likePattern(" Ads.Example "))
        assertEquals("%a%b_c%", UserRuleRepository.likePattern("a*b?c"))
        assertEquals("%f\\_a\\%\\\\%", UserRuleRepository.likePattern("f_a%\\"))
    }
}
