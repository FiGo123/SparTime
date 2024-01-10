package com.example.spartime.data.models

data class Training(
    var id: Int = -1,
    var title: String,
    var date: String,
    var user: Int, // Foreign key to User
    var duration: Int,
    var difficultyScale: Int,
    var description: String
)