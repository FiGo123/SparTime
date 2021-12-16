package com.example.spartime

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.spartime.databinding.FragmentFirstBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding :FragmentFirstBinding
    var round = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

    }


}