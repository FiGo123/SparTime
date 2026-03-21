package com.example.spartime.uiUtils

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.spartime.R
import com.example.spartime.data.models.Training

class TrainingAdapter(
    private val trainingList: MutableList<Training>,
    private val onDelete: (Training, Int) -> Unit
) : RecyclerView.Adapter<TrainingAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.textViewTitle)
        val dateTextView: TextView = itemView.findViewById(R.id.textViewDate)
        val numOfRoundsTextView: TextView = itemView.findViewById(R.id.numOfRounds)
        val descriptionTextView: TextView = itemView.findViewById(R.id.description)
        val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_training, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val training = trainingList[position]
        holder.titleTextView.text = training.title
        holder.dateTextView.text = training.date
        holder.numOfRoundsTextView.text = training.numberOfRounds.toString()
        holder.descriptionTextView.text = training.description
        holder.deleteButton.setOnClickListener {
            val adapterPosition = holder.adapterPosition
            if (adapterPosition != RecyclerView.NO_ID.toInt()) {
                onDelete(training, adapterPosition)
            }
        }
    }

    override fun getItemCount(): Int = trainingList.size

    fun removeAt(position: Int) {
        trainingList.removeAt(position)
        notifyItemRemoved(position)
    }
}
