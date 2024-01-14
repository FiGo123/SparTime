package com.example.spartime.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import android.widget.Toast
import com.example.spartime.data.models.Training

val DATABASE_NAME = "spartime"
val TABLE_NAME = "training"
val COL_TITLE = "title"
val COL_DATE = "date"
val COL_NUMBER_OF_ROUNDS = "numberOfRounds"
val COL_ROUND_DURATION = "roundDuration"
val COL_DIFICULTY_SCALE = "difficultyScale"
val COL_DESCRIPTION = "description"
val COL_ID = "id"

class DBHandler (var context: Context) :SQLiteOpenHelper(context, DATABASE_NAME, null, 1){
    override fun onCreate(p0: SQLiteDatabase?) {
        val createTable = "CREATE TABLE" + TABLE_NAME + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                COL_TITLE + " VARCHAR(256)," +
                COL_DATE + " VARCHAR(256)," +
                COL_NUMBER_OF_ROUNDS + " INTEGER," +
                COL_ROUND_DURATION + " INTEGER," +
                COL_DIFICULTY_SCALE + " INTEGER," +
                COL_DESCRIPTION + " VARCHAR(256))";


        p0?.execSQL(createTable)

    }

    override fun onUpgrade(p0: SQLiteDatabase?, p1: Int, p2: Int) {
        TODO("Not yet implemented")
    }

    fun insertData(training: Training){
        val db = this.writableDatabase
        val cv = ContentValues()
        cv.put(COL_TITLE, training.title)
        cv.put(COL_DATE, training.date)
        cv.put(COL_NUMBER_OF_ROUNDS, training.numberOfRounds)
        cv.put(COL_DIFICULTY_SCALE, training.difficultyScale)
        cv.put(COL_DESCRIPTION, training.description)
        val result = db.insert(TABLE_NAME, null,cv)
        if (result == -1.toLong())
            Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show()
        else
            Toast.makeText(context, "Success", Toast.LENGTH_SHORT).show()

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

                    val training = Training(title, date, numberOfRounds, roundDuration, difficultyScale, description)
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