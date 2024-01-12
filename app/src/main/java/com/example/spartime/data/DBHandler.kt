package com.example.spartime.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.widget.Toast
import com.example.spartime.data.models.Training

val DATABASE_NAME = "spartime"
val TABLE_NAME = "training"
val COL_TITLE = "title"
val COL_DATE = "date"
val COL_DURATION = "duration"
val COL_DIFICULTY_SCALE = "difficultyScale"
val COL_DESCRIPTION = "description"
val COL_ID = "id"

class DBHandler (var context: Context) :SQLiteOpenHelper(context, DATABASE_NAME, null, 1){
    override fun onCreate(p0: SQLiteDatabase?) {
        val createTable = "CREATE TABLE" + TABLE_NAME + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                COL_TITLE + " VARCHAR(256)," +
                COL_DATE + " VARCHAR(256)," +
                COL_DURATION + " VARCHAR(256)," +
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
        cv.put(COL_DURATION, training.duration)
        cv.put(COL_DIFICULTY_SCALE, training.difficultyScale)
        cv.put(COL_DESCRIPTION, training.description)
        val result = db.insert(TABLE_NAME, null,cv)
        if (result == -1.toLong())
            Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show()
        else
            Toast.makeText(context, "Success", Toast.LENGTH_SHORT).show()

    }
}