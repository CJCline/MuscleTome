package com.chy.muscletome.data.repository

import com.chy.muscletome.data.remote.WgerExerciseInfo
import com.chy.muscletome.data.remote.WgerPage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class WgerPaginationTest {

    private fun wgerItem() = WgerExerciseInfo(
        id = 1,
        categoryName = null,
        muscles = emptyList(),
        musclesSecondary = emptyList(),
        equipment = emptyList(),
        licenseAuthor = null,
        licenseName = null,
        englishName = "Exercise",
        englishDescription = null,
        hasEnglish = true,
        mainImageUrl = null,
    )

    @Test
    fun stopsAfterPageWithNoNextLink() = runBlocking {
        val offsets = mutableListOf<Int>()

        forEachWgerPage(
            maxPages = 10,
            fetchPage = { offset, limit ->
                offsets += offset
                assertEquals(50, limit)
                WgerPage(count = 1, next = null, results = listOf(wgerItem()))
            },
            onPage = { _, _ -> },
        )

        assertEquals(listOf(0), offsets)
    }

    @Test
    fun stopsOnEmptyPageEvenIfNextLinkExists() = runBlocking {
        val offsets = mutableListOf<Int>()

        forEachWgerPage(
            maxPages = 10,
            fetchPage = { offset, _ ->
                offsets += offset
                WgerPage(count = 100, next = "next", results = emptyList())
            },
            onPage = { _, _ -> },
        )

        assertEquals(listOf(0), offsets)
    }

    @Test
    fun continuesWhenNextPageExistsAndResultsArePresent() = runBlocking {
        val offsets = mutableListOf<Int>()
        val processedPages = mutableListOf<Int>()

        forEachWgerPage(
            maxPages = 10,
            fetchPage = { offset, _ ->
                offsets += offset
                WgerPage(
                    count = 100,
                    next = if (offset == 0) "next" else null,
                    results = listOf(wgerItem()),
                )
            },
            onPage = { page, _ -> processedPages += page },
        )

        assertEquals(listOf(0, 50), offsets)
        assertEquals(listOf(0, 1), processedPages)
    }
}
