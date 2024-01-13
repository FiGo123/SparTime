package com.example.spartime.data.models

class Training{
    var id: Int = -1
    var title: String = ""
    var date: String = ""
    var numberOfRounds: Int = -1
    var roundDuration: Int = -1
    var difficultyScale: Int = -1
    var description: String = ""

    constructor(title: String, date: String, numberOfRounds: Int, roundDuration: Int, dificultyScale: Int, description: String){
        this.title = title
        this.date = date
        this.numberOfRounds = numberOfRounds
        this.roundDuration = roundDuration
        this.difficultyScale = dificultyScale
        this.description = description
    }
}