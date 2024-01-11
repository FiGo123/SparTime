package com.example.spartime.data.models

import java.time.Duration
import java.util.Date

class Training{
    var id: Int = -1
    var title: String = ""
    var date: String = ""
    var duration: Int = -1
    var difficultyScale: Int = -1
    var description: String = ""

    constructor(title: String, date: String, duration: Int, dificultyScale: Int, description: String){
        this.title = title
        this.date = date
        this.duration = duration
        this.difficultyScale = dificultyScale
        this.description = description
    }
}