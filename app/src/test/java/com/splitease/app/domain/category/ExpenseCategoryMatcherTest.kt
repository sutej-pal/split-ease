package com.splitease.app.domain.category

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExpenseCategoryMatcherTest {
    @Test
    fun emptyTitle_defaultsToGeneral() {
        assertEquals("cat_general", ExpenseCategoryMatcher.matchCategoryId(""))
        assertEquals("cat_general", ExpenseCategoryMatcher.matchCategoryId("   "))
    }

    @Test
    fun foodKeywords_matchFood() {
        assertEquals("cat_food", ExpenseCategoryMatcher.matchCategoryId("Pizza night"))
        assertEquals("cat_food", ExpenseCategoryMatcher.matchCategoryId("Team lunch"))
        assertEquals("cat_food", ExpenseCategoryMatcher.matchCategoryId("Zomato order"))
    }

    @Test
    fun busAndTrain_preferSpecificOverTravel() {
        assertEquals("cat_bus", ExpenseCategoryMatcher.matchCategoryId("Bus ticket"))
        assertEquals("cat_train", ExpenseCategoryMatcher.matchCategoryId("Train to Delhi"))
        assertEquals("cat_train", ExpenseCategoryMatcher.matchCategoryId("Metro pass"))
        assertEquals("cat_travel", ExpenseCategoryMatcher.matchCategoryId("Uber to airport"))
        assertEquals("cat_travel", ExpenseCategoryMatcher.matchCategoryId("Flight booking"))
    }

    @Test
    fun busDoesNotMatchBusiness() {
        assertEquals("cat_general", ExpenseCategoryMatcher.matchCategoryId("Business meeting"))
        assertEquals("cat_food", ExpenseCategoryMatcher.matchCategoryId("Business dinner"))
    }

    @Test
    fun gasBill_isUtilities_notTravel() {
        assertEquals("cat_utilities", ExpenseCategoryMatcher.matchCategoryId("gas bill"))
        assertEquals("cat_travel", ExpenseCategoryMatcher.matchCategoryId("gas"))
        assertEquals("cat_bus", ExpenseCategoryMatcher.matchCategoryId("Bus tickets"))
    }

    @Test
    fun rentUtilitiesEntertainment() {
        assertEquals("cat_rent", ExpenseCategoryMatcher.matchCategoryId("March rent"))
        assertEquals("cat_utilities", ExpenseCategoryMatcher.matchCategoryId("Wifi bill"))
        assertEquals("cat_entertainment", ExpenseCategoryMatcher.matchCategoryId("Movie tickets"))
    }
}
