package com.splitease.app.domain.category

/**
 * Picks a built-in category from an expense title using simple keyword heuristics.
 *
 * More specific matches (bus, train, food, utilities) win over broader ones (travel).
 * Returns [DefaultCategories] general when nothing matches.
 */
object ExpenseCategoryMatcher {
    private data class Rule(
        val categoryId: String,
        val keywords: List<String>,
    )

    /**
     * Ordered most-specific first. First keyword hit wins.
     * Utility phrases such as "gas bill" must precede travel's "gas".
     * Keywords are matched as whole-word substrings (case-insensitive).
     */
    private val RULES: List<Rule> =
        listOf(
            Rule(
                "cat_bus",
                listOf(
                    "bus",
                    "autobus",
                    "coach",
                    "shuttle",
                ),
            ),
            Rule(
                "cat_train",
                listOf(
                    "train",
                    "railway",
                    "railroad",
                    "irctc",
                    "metro",
                    "subway",
                    "tube",
                ),
            ),
            Rule(
                "cat_food",
                listOf(
                    "food",
                    "lunch",
                    "dinner",
                    "breakfast",
                    "brunch",
                    "pizza",
                    "coffee",
                    "cafe",
                    "café",
                    "restaurant",
                    "grocery",
                    "groceries",
                    "meal",
                    "snack",
                    "snacks",
                    "burger",
                    "sushi",
                    "biryani",
                    "thali",
                    "chai",
                    "tea",
                    "drinks",
                    "bar",
                    "pub",
                    "kitchen",
                    "dining",
                    "takeout",
                    "takeaway",
                    "swiggy",
                    "zomato",
                ),
            ),
            Rule(
                "cat_rent",
                listOf(
                    "rent",
                    "lease",
                    "landlord",
                    "housing",
                ),
            ),
            Rule(
                "cat_utilities",
                listOf(
                    "utility",
                    "utilities",
                    "electricity",
                    "electric",
                    "water bill",
                    "wifi",
                    "wi-fi",
                    "internet",
                    "broadband",
                    "gas bill",
                    "phone bill",
                    "mobile bill",
                    "recharge",
                ),
            ),
            Rule(
                "cat_travel",
                listOf(
                    "travel",
                    "trip",
                    "flight",
                    "airfare",
                    "airport",
                    "hotel",
                    "uber",
                    "ola",
                    "taxi",
                    "cab",
                    "lyft",
                    "petrol",
                    "diesel",
                    "fuel",
                    "gas",
                    "parking",
                    "toll",
                    "visa",
                    "passport",
                    "booking",
                    "airbnb",
                ),
            ),
            Rule(
                "cat_entertainment",
                listOf(
                    "movie",
                    "cinema",
                    "netflix",
                    "spotify",
                    "concert",
                    "theatre",
                    "theater",
                    "game",
                    "gaming",
                    "party",
                    "tickets",
                    "show",
                    "entertainment",
                ),
            ),
        )

    private val GENERAL_ID = "cat_general"

    /** Stable category id inferred from [title], or general when no keyword matches. */
    fun matchCategoryId(title: String): String {
        val normalized = title.trim().lowercase()
        if (normalized.isEmpty()) return GENERAL_ID
        for (rule in RULES) {
            if (rule.keywords.any { keyword -> containsKeyword(normalized, keyword) }) {
                return rule.categoryId
            }
        }
        return GENERAL_ID
    }

    private fun containsKeyword(
        text: String,
        keyword: String,
    ): Boolean {
        if (!keyword.contains(' ')) {
            // Whole-word match so "bus" does not hit inside "business".
            val pattern = Regex("""\b${Regex.escape(keyword)}\b""", RegexOption.IGNORE_CASE)
            return pattern.containsMatchIn(text)
        }
        return text.contains(keyword)
    }
}
