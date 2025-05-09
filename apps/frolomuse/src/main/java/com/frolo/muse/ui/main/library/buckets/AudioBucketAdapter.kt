package com.frolo.muse.ui.main.library.buckets

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.frolo.core.ui.inflateChild
import com.frolo.muse.R
import com.frolo.music.model.MediaBucket
import com.frolo.muse.ui.main.library.base.BaseAdapter
import com.frolo.muse.ui.main.library.base.sectionIndexAt
import com.l4digital.fastscroll.FastScroller


class AudioBucketAdapter : BaseAdapter<MediaBucket, AudioBucketAdapter.ViewHolder>(), FastScroller.SectionIndexer {

    override fun getSectionText(position: Int) = sectionIndexAt(position) { displayName }

    override fun onCreateBaseViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder = ViewHolder(parent.inflateChild(R.layout.item_audio_bucket))

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
        item: MediaBucket,
        selected: Boolean,
        selectionChanged: Boolean
    ) {
        with(holder) {
            tvBucketName.text = item.displayName
        }
    }

    class ViewHolder(itemView: View): BaseViewHolder(itemView) {
        override val viewOptionsMenu: View? = null
        val tvBucketName: TextView = itemView.findViewById(R.id.tv_bucket_name)
    }
}