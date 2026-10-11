package com.example.boombee.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
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

// History v3 columns — added in DATABASE_VERSION 3 (see Training).
val COL_STARTED_AT = "startedAt"
val COL_ACTIVE_SECONDS = "activeSeconds"
val COL_PLANNED_ROUNDS = "plannedRounds"
val COL_COMPLETED = "completed"
val COL_REST_SECONDS = "restSeconds"
val COL_WARMUP_SECONDS = "warmupSeconds"
val COL_VOICE_STYLE = "voiceStyle"
val COL_PLATFORM = "platform"
val COL_DEFENSE_CALLS = "defenseCalls"
val COL_DISTANCE_CALLS = "distanceCalls"
val COL_FREESTYLE_CALLS = "freestyleCalls"
val COL_ROUND_PUNCHES = "roundPunches"
val COL_EFFORT = "effort"
val COL_NOTES = "notes"

private val V3_COLUMNS = listOf(
    COL_STARTED_AT to "INTEGER",
    COL_ACTIVE_SECONDS to "INTEGER",
    COL_PLANNED_ROUNDS to "INTEGER",
    COL_COMPLETED to "INTEGER",
    COL_REST_SECONDS to "INTEGER",
    COL_WARMUP_SECONDS to "INTEGER",
    COL_VOICE_STYLE to "VARCHAR(32)",
    COL_PLATFORM to "VARCHAR(16)",
    COL_DEFENSE_CALLS to "INTEGER",
    COL_DISTANCE_CALLS to "INTEGER",
    COL_FREESTYLE_CALLS to "INTEGER",
    COL_ROUND_PUNCHES to "VARCHAR(512)",
    COL_EFFORT to "INTEGER",
    COL_NOTES to "TEXT",
)

// Heart rate columns — added in DATABASE_VERSION 4 (watch sessions only).
val COL_HR_AVG = "hrAvg"
val COL_HR_MAX = "hrMax"
val COL_HR_MAX_HR = "hrMaxHr"
val COL_HR_ZONE_SECONDS = "hrZoneSeconds"
val COL_HR_ROUND_AVG = "hrRoundAvg"
val COL_HR_ROUND_MAX = "hrRoundMax"
val COL_HR_ROUND_TARGET = "hrRoundTarget"
val COL_HR_RECOVERY = "hrRecovery"
val COL_HR_SAMPLES = "hrSamples"

private val V4_COLUMNS = listOf(
    COL_HR_AVG to "INTEGER",
    COL_HR_MAX to "INTEGER",
    COL_HR_MAX_HR to "INTEGER",
    COL_HR_ZONE_SECONDS to "VARCHAR(64)",
    COL_HR_ROUND_AVG to "VARCHAR(256)",
    COL_HR_ROUND_MAX to "VARCHAR(256)",
    COL_HR_ROUND_TARGET to "VARCHAR(128)",
    COL_HR_RECOVERY to "VARCHAR(256)",
    COL_HR_SAMPLES to "TEXT",
)

private const val DATABASE_VERSION = 4

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
                COL_PUNCH_BREAKDOWN + " VARCHAR(128), " +
                (V3_COLUMNS + V4_COLUMNS).joinToString(", ") { (name, type) -> "$name $type" } + ")"

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
        if (oldVersion < 3) {
            V3_COLUMNS.forEach { (name, type) -> db?.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $name $type") }
        }
        if (oldVersion < 4) {
            V4_COLUMNS.forEach { (name, type) -> db?.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $name $type") }
        }
    }

    /** Inserts [training] and returns its new row id (-1 on failure). */
    fun insertData(training: Training): Long {
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
        cv.put(COL_STARTED_AT, training.startedAt)
        cv.put(COL_ACTIVE_SECONDS, training.activeSeconds)
        cv.put(COL_PLANNED_ROUNDS, training.plannedRounds)
        cv.put(COL_COMPLETED, if (training.completed) 1 else 0)
        cv.put(COL_REST_SECONDS, training.restSeconds)
        cv.put(COL_WARMUP_SECONDS, training.warmupSeconds)
        cv.put(COL_VOICE_STYLE, training.voiceStyle)
        cv.put(COL_PLATFORM, training.platform)
        cv.put(COL_DEFENSE_CALLS, training.defenseCalls)
        cv.put(COL_DISTANCE_CALLS, training.distanceCalls)
        cv.put(COL_FREESTYLE_CALLS, training.freestyleCalls)
        cv.put(COL_ROUND_PUNCHES, training.roundPunches)
        training.effort?.let { cv.put(COL_EFFORT, it) }
        cv.put(COL_NOTES, training.notes)
        training.hrAvg?.let { cv.put(COL_HR_AVG, it) }
        training.hrMax?.let { cv.put(COL_HR_MAX, it) }
        training.hrMaxHr?.let { cv.put(COL_HR_MAX_HR, it) }
        cv.put(COL_HR_ZONE_SECONDS, training.hrZoneSeconds)
        cv.put(COL_HR_ROUND_AVG, training.hrRoundAvg)
        cv.put(COL_HR_ROUND_MAX, training.hrRoundMax)
        cv.put(COL_HR_ROUND_TARGET, training.hrRoundTarget)
        cv.put(COL_HR_RECOVERY, training.hrRecovery)
        cv.put(COL_HR_SAMPLES, training.hrSamples)
        return db.insert(TABLE_NAME, null, cv)
    }

    /** Saves the post-session effort rating (1–10, null clears it) and notes. */
    fun updateEffort(id: Long, effort: Int?, notes: String?) {
        val cv = ContentValues()
        if (effort != null) cv.put(COL_EFFORT, effort) else cv.putNull(COL_EFFORT)
        cv.put(COL_NOTES, notes?.trim()?.takeIf { it.isNotEmpty() })
        writableDatabase.update(TABLE_NAME, cv, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun getTraining(id: Long): Training? = getAllTraining().firstOrNull { it.id.toLong() == id }

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
                    readV3Columns(cursor, training)
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

    private fun readV3Columns(cursor: Cursor, t: Training) {
        fun int(col: String): Int? = cursor.getColumnIndex(col).takeIf { it != -1 && !cursor.isNull(it) }?.let(cursor::getInt)
        fun long(col: String): Long? = cursor.getColumnIndex(col).takeIf { it != -1 && !cursor.isNull(it) }?.let(cursor::getLong)
        fun str(col: String): String? = cursor.getColumnIndex(col).takeIf { it != -1 && !cursor.isNull(it) }?.let(cursor::getString)

        t.startedAt = long(COL_STARTED_AT) ?: 0L
        t.activeSeconds = int(COL_ACTIVE_SECONDS) ?: 0
        t.plannedRounds = int(COL_PLANNED_ROUNDS) ?: 0
        t.completed = (int(COL_COMPLETED) ?: if (t.description.startsWith("Interrupted")) 0 else 1) == 1
        t.restSeconds = int(COL_REST_SECONDS) ?: 0
        t.warmupSeconds = int(COL_WARMUP_SECONDS) ?: 0
        t.voiceStyle = str(COL_VOICE_STYLE)
        t.platform = str(COL_PLATFORM) ?: "phone"
        t.defenseCalls = int(COL_DEFENSE_CALLS) ?: 0
        t.distanceCalls = int(COL_DISTANCE_CALLS) ?: 0
        t.freestyleCalls = int(COL_FREESTYLE_CALLS) ?: 0
        t.roundPunches = str(COL_ROUND_PUNCHES)
        t.effort = int(COL_EFFORT)
        t.notes = str(COL_NOTES)
        t.hrAvg = int(COL_HR_AVG)
        t.hrMax = int(COL_HR_MAX)
        t.hrMaxHr = int(COL_HR_MAX_HR)
        t.hrZoneSeconds = str(COL_HR_ZONE_SECONDS)
        t.hrRoundAvg = str(COL_HR_ROUND_AVG)
        t.hrRoundMax = str(COL_HR_ROUND_MAX)
        t.hrRoundTarget = str(COL_HR_ROUND_TARGET)
        t.hrRecovery = str(COL_HR_RECOVERY)
        t.hrSamples = str(COL_HR_SAMPLES)
    }
}
