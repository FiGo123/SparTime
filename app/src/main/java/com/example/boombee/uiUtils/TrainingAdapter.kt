package com.example.boombee.uiUtils

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.boombee.R
import com.example.boombee.data.models.Training

class TrainingAdapter(
    private val trainingList: MutableList<Training>,
    private val onDelete: (Training, Int) -> Unit
) : RecyclerView.Adapter<TrainingAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.textViewTitle)
        val date: TextView = itemView.findViewById(R.id.textViewDate)
        val numOfRounds: TextView = itemView.findViewById(R.id.numOfRounds)
        val roundDuration: TextView = itemView.findViewById(R.id.roundDuration)
        val difficultyValue: TextView = itemView.findViewById(R.id.difficultyValue)
        val description: TextView = itemView.findViewById(R.id.description)
        val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_training, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val training = trainingList[position]
        holder.title.text = training.title
        holder.date.text = training.date
        holder.numOfRounds.text = training.numberOfRounds.toString()
        holder.roundDuration.text = training.roundDuration.toString()
        holder.difficultyValue.text = "${training.difficultyScale}/5"
        holder.description.text = training.description
        holder.deleteButton.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_ID.toInt()) {
                onDelete(training, pos)
            }
        }
    }

    override fun getItemCount(): Int = trainingList.size

    fun removeAt(position: Int) {
        trainingList.removeAt(position)
        notifyItemRemoved(position)
    }
}
