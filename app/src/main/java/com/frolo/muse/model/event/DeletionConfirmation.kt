package com.frolo.muse.model.event

import com.frolo.muse.model.media.Media


/**
 * Confirmation of deletion of [mediaItem] which is associated with [associatedMediaItem].
 */
data class DeletionConfirmation<E: Media>(
    val mediaItem: E,
    val associatedMediaItem: Media?
)