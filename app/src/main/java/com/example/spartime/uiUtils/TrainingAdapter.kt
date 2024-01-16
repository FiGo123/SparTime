package com.example.spartime.uiUtils

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.spartime.data.models.Training
import com.example.spartime.R

class TrainingAdapter(private val trainingList: List<Training>) :
    RecyclerView.Adapter<TrainingAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.textViewTitle)
        val dateTextView: TextView = itemView.findViewById(R.id.textViewDate)
        // Add more TextViews for other fields if needed
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_training, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val training = trainingList[position]
        holder.titleTextView.text = training.title
        holder.dateTextView.text = training.date
        // Bind other fields if needed
    }

    override fun getItemCount(): Int {
        return trainingList.size
    }
}