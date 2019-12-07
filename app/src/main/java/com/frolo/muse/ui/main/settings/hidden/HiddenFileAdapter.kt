package com.frolo.muse.ui.main.settings.hidden

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.frolo.muse.R
import com.frolo.muse.model.media.MyFile
import kotlinx.android.synthetic.main.item_hidden_file.view.*


class HiddenFileAdapter constructor(
        private val onRemoveClick: (item: MyFile) -> Unit
): ListAdapter<MyFile, HiddenFileAdapter.HiddenFileViewHolder>(HiddenFileItemCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HiddenFileViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_hidden_file, parent, false)

        return HiddenFileViewHolder(view)
    }

    override fun onBindViewHolder(holder: HiddenFileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HiddenFileViewHolder(itemView: View):
            RecyclerView.ViewHolder(itemView),
            View.OnClickListener {

        init {
            itemView.btn_remove.setOnClickListener(this)
        }

        fun bind(item: MyFile) {
            with(itemView) {
                tv_filename.text = item.javaFile.absolutePath
            }
        }

        override fun onClick(v: View?) {
            onRemoveClick(getItem(adapterPosition))
        }

    }

    object HiddenFileItemCallback : DiffUtil.ItemCallback<MyFile>() {

        override fun areItemsTheSame(oldItem: MyFile, newItem: MyFile): Boolean {
            return oldItem.javaFile.absolutePath == newItem.javaFile.absolutePath
        }

        override fun areContentsTheSame(oldItem: MyFile, newItem: MyFile): Boolean {
            return oldItem.isSongFile == newItem.isSongFile
                    && oldItem.javaFile == newItem.javaFile
        }

    }

}