package com.splitease.app.data.pinboard

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates pin board load / save. Stateless — debounce lives in the ViewModel.
 */
@Singleton
class PinBoardInteractor
    @Inject
    constructor(
        private val remote: PinBoardRemoteDataSource,
    ) {
        /**
         * Loads the board content and metadata for [groupId].
         *
         * @return The DTO, or a blank board stub when none exists yet.
         */
        suspend fun load(groupId: String): PinBoardDto =
            remote.fetch(groupId) ?: PinBoardDto(groupId = groupId, content = "")

        /**
         * Persists [content] for [groupId], stamping the current user as editor.
         */
        suspend fun save(groupId: String, content: String, userId: String) {
            remote.upsert(
                PinBoardDto(
                    groupId = groupId,
                    content = content,
                    updatedBy = userId,
                ),
            )
        }
    }
