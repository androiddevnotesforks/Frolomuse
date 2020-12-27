package com.frolo.muse.common

import com.frolo.muse.engine.AudioSource
import com.frolo.muse.model.media.Song

val Song.durationInSeconds: Int get() = duration / 1000

fun Song.toAudioSource(): AudioSource {
    return if (this is AudioSource) this
    else Util.createAudioSource(this)
}

fun List<Song>.toAudioSources(): List<AudioSource> = map { it.toAudioSource() }