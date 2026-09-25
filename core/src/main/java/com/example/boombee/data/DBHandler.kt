package com.example.boombee.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.example.boombee.data.models.Training

val DATABASE_NAME = "boombee"
val TABLE_NAME = "training"
val COL_TITLE = "title"
val COL_DATE = "date"
val COL_NUMBER_OF_ROUNDS = "numberOfRounds"
val COL_ROUND_DURATION = "roundDuration"
val COL_DIFICULTY_SCALE = "difficultyScale"
val COL_DESCRIPTION = "description"
val COL_ID = "id"

// Boxing Coach (v2) columns — added in DATABASE_VERSION 2.
val COL_TRAINING_MODE = "trainingMode"
val COL_COACH_DIFFICULTY = "coachDifficulty"
val COL_TOTAL_PUNCHES = "totalPunches"
val COL_TOTAL_COMBOS = "totalCombos"
val COL_TOTAL_TACTICAL_COMMANDS = "totalTacticalCommands"
val COL_PUNCH_BREAKDOWN = "punchBreakdown"

private const val DATABASE_VERSION = 2

class DBHandler(var context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(p0: SQLiteDatabase?) {
        val createTable = "CREATE TABLE " + TABLE_NAME + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_TITLE + " VARCHAR(256), " +
                COL_DATE + " VARCHAR(256), " +
                COL_NUMBER_OF_ROUNDS + " INTEGER, " +
                COL_ROUND_DURATION + " INTEGER, " +
                COL_DIFICULTY_SCALE + " INTEGER, " +
                COL_DESCRIPTION + " VARCHAR(256), " +
                COL_TRAINING_MODE + " VARCHAR(32), " +
                COL_COACH_DIFFICULTY + " VARCHAR(32), " +
                COL_TOTAL_PUNCHES + " INTEGER, " +
                COL_TOTAL_COMBOS + " INTEGER, " +
                COL_TOTAL_TACTICAL_COMMANDS + " INTEGER, " +
                COL_PUNCH_BREAKDOWN + " VARCHAR(128))"

        p0?.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db?.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_TRAINING_MODE VARCHAR(32)")
            db?.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_COACH_DIFFICULTY VARCHAR(32)")
            db?.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_TOTAL_PUNCHES INTEGER")
            db?.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_TOTAL_COMBOS INTEGER")
            db?.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_TOTAL_TACTICAL_COMMANDS INTEGER")
            db?.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_PUNCH_BREAKDOWN VARCHAR(128)")
        }
    }

    fun insertData(training: Training) {
        val db = this.writableDatabase
        val cv = ContentValues()
        cv.put(COL_TITLE, training.title)
        cv.put(COL_DATE, training.date)
        cv.put(COL_NUMBER_OF_ROUNDS, training.numberOfRounds)
        cv.put(COL_ROUND_DURATION, training.roundDuration)
        cv.put(COL_DIFICULTY_SCALE, training.difficultyScale)
        cv.put(COL_DESCRIPTION, training.description)
        cv.put(COL_TRAINING_MODE, training.trainingMode)
        cv.put(COL_COACH_DIFFICULTY, training.coachDifficulty)
        cv.put(COL_TOTAL_PUNCHES, training.totalPunches)
        cv.put(COL_TOTAL_COMBOS, training.totalCombos)
        cv.put(COL_TOTAL_TACTICAL_COMMANDS, training.totalTacticalCommands)
        cv.put(COL_PUNCH_BREAKDOWN, training.punchBreakdown)
        db.insert(TABLE_NAME, null, cv)
    }

    fun deleteTraining(id: Int) {
        val db = this.writableDatabase
        db.delete(TABLE_NAME, "$COL_ID = ?", arrayOf(id.toString()))
        db.close()
    }

    fun getAllTraining(): List<Training> {
        val trainingList = mutableListOf<Training>()
        val db = this.readableDatabase
        val query = "SELECT * FROM $TABLE_NAME"
        val cursor = db.rawQuery(query, null)

        try {
            if (cursor.moveToFirst()) {
                do {
                    // Log column indices to identify the issue
                    val idIndex = cursor.getColumnIndex(COL_ID)
                    val titleIndex = cursor.getColumnIndex(COL_TITLE)
                    val dateIndex = cursor.getColumnIndex(COL_DATE)
                    val numberOfRoundsIndex = cursor.getColumnIndex(COL_NUMBER_OF_ROUNDS)
                    val roundDurationIndex = cursor.getColumnIndex(COL_ROUND_DURATION)
                    val difficultyScaleIndex = cursor.getColumnIndex(COL_DIFICULTY_SCALE)
                    val descriptionIndex = cursor.getColumnIndex(COL_DESCRIPTION)

                    if (idIndex == -1 || titleIndex == -1 || dateIndex == -1 ||
                        numberOfRoundsIndex == -1 || roundDurationIndex == -1 ||
                        difficultyScaleIndex == -1 || descriptionIndex == -1) {
                        // Log the cursor's column names if any index is -1
                        val columnNames = cursor.columnNames.joinToString(", ")
                        Log.e("DBHandler", "Column names in cursor: $columnNames")
                        throw IllegalArgumentException("Column index was -1")
                    }

                    val id = cursor.getInt(idIndex)
                    val title = cursor.getString(titleIndex)
                    val date = cursor.getString(dateIndex)
                    val numberOfRounds = cursor.getInt(numberOfRoundsIndex)
                    val roundDuration = cursor.getInt(roundDurationIndex)
                    val difficultyScale = cursor.getInt(difficultyScaleIndex)
                    val description = cursor.getString(descriptionIndex)

                    // Boxing Coach columns: absent on rows written before this
                    // schema version, so every read here is null-tolerant.
                    val trainingModeIndex = cursor.getColumnIndex(COL_TRAINING_MODE)
                    val coachDifficultyIndex = cursor.getColumnIndex(COL_COACH_DIFFICULTY)
                    val totalPunchesIndex = cursor.getColumnIndex(COL_TOTAL_PUNCHES)
                    val totalCombosIndex = cursor.getColumnIndex(COL_TOTAL_COMBOS)
                    val totalTacticalIndex = cursor.getColumnIndex(COL_TOTAL_TACTICAL_COMMANDS)
                    val punchBreakdownIndex = cursor.getColumnIndex(COL_PUNCH_BREAKDOWN)

                    val trainingMode = if (trainingModeIndex != -1 && !cursor.isNull(trainingModeIndex)) {
                        cursor.getString(trainingModeIndex)
                    } else {
                        "TIMER_ONLY"
                    }
                    val coachDifficulty = if (coachDifficultyIndex != -1 && !cursor.isNull(coachDifficultyIndex)) {
                        cursor.getString(coachDifficultyIndex)
                    } else {
                        null
                    }
                    val totalPunches = if (totalPunchesIndex != -1 && !cursor.isNull(totalPunchesIndex)) {
                        cursor.getInt(totalPunchesIndex)
                    } else {
                        0
                    }
                    val totalCombos = if (totalCombosIndex != -1 && !cursor.isNull(totalCombosIndex)) {
                        cursor.getInt(totalCombosIndex)
                    } else {
                        0
                    }
                    val totalTacticalCommands = if (totalTacticalIndex != -1 && !cursor.isNull(totalTacticalIndex)) {
                        cursor.getInt(totalTacticalIndex)
                    } else {
                        0
                    }
                    val punchBreakdown = if (punchBreakdownIndex != -1 && !cursor.isNull(punchBreakdownIndex)) {
                        cursor.getString(punchBreakdownIndex)
                    } else {
                        null
                    }

                    val training = Training(
                        title,
                        date,
                        numberOfRounds,
                        roundDuration,
                        difficultyScale,
                        description,
                        trainingMode,
                        coachDifficulty,
                        totalPunches,
                        totalCombos,
                        totalTacticalCommands,
                        punchBreakdown,
                    )
                    training.id = id
                    trainingList.add(training)
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor.close()
        }

        return trainingList
    }
}
