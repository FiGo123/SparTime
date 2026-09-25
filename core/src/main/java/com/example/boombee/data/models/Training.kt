package com.example.boombee.data.models

class Training {
    var id: Int = -1
    var title: String = ""
    var date: String = ""
    var numberOfRounds: Int = -1
    var roundDuration: Int = -1
    var difficultyScale: Int = -1
    var description: String = ""

    // Boxing Coach (v2) fields. Null/0 for plain timer-only sessions, and
    // for every session recorded before this schema addition (see
    // DBHandler's onUpgrade — old rows just come back with these unset).
    var trainingMode: String = "TIMER_ONLY"
    var coachDifficulty: String? = null
    var totalPunches: Int = 0
    var totalCombos: Int = 0
    var totalTacticalCommands: Int = 0
    var punchBreakdown: String? = null

    constructor(
        title: String,
        date: String,
        numberOfRounds: Int,
        roundDuration: Int,
        dificultyScale: Int,
        description: String,
        trainingMode: String = "TIMER_ONLY",
        coachDifficulty: String? = null,
        totalPunches: Int = 0,
        totalCombos: Int = 0,
        totalTacticalCommands: Int = 0,
        punchBreakdown: String? = null,
    ) {
        this.title = title
        this.date = date
        this.numberOfRounds = numberOfRounds
        this.roundDuration = roundDuration
        this.difficultyScale = dificultyScale
        this.description = description
        this.trainingMode = trainingMode
        this.coachDifficulty = coachDifficulty
        this.totalPunches = totalPunches
        this.totalCombos = totalCombos
        this.totalTacticalCommands = totalTacticalCommands
        this.punchBreakdown = punchBreakdown
    }
}