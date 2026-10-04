package com.example.boombee.tutorial

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.boombee.databinding.ItemTutorialGameBinding
import com.example.boombee.databinding.ItemTutorialPageBinding

class TutorialAdapter(
    private val pages: List<TutorialPage>,
    private val onListen: (TutorialPage) -> Unit,
    private val createGame: (ItemTutorialGameBinding) -> ComboGameController,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var selectedPosition = 0
    private var game: ComboGameController? = null

    override fun getItemCount() = pages.size

    override fun getItemViewType(position: Int) = pages[position].type.ordinal

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TutorialPage.Type.GAME.ordinal) {
            val binding = ItemTutorialGameBinding.inflate(inflater, parent, false)
            game = createGame(binding)
            object : RecyclerView.ViewHolder(binding.root) {}
        } else {
            PageHolder(ItemTutorialPageBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is PageHolder) holder.bind(pages[position])
        else if (position == selectedPosition) game?.onShown()
    }

    fun onPageSelected(position: Int) {
        selectedPosition = position
        if (pages[position].type == TutorialPage.Type.GAME) game?.onShown() else game?.onHidden()
    }

    fun release() {
        game?.onHidden()
    }

    private inner class PageHolder(private val binding: ItemTutorialPageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(page: TutorialPage) = with(binding) {
            figure.visibility = if (page.pose != null) View.VISIBLE else View.GONE
            page.pose?.let { figure.pose = it }
            emoji.visibility = if (page.emoji != null) View.VISIBLE else View.GONE
            emoji.text = page.emoji
            title.setText(page.title)
            body.setText(page.body)
            listenBtn.visibility = if (page.clips != null) View.VISIBLE else View.GONE
            listenBtn.setOnClickListener { onListen(page) }
        }
    }
}
