package com.example.spartime


import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import com.example.spartime.databinding.FragmentRoundCounterBinding
import com.example.spartime.databinding.MainFragmentBinding

class MainActivity : AppCompatActivity() {

    var round = 0
    var rest = 0
    var time = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = MainFragmentBinding.inflate(layoutInflater)
        val bindingRoundFragment = FragmentRoundCounterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupListeners(binding, bindingRoundFragment)
    }

    private fun setupListeners(binding: MainFragmentBinding, bindingRoundFragment: FragmentRoundCounterBinding){
        binding.edtxtRound.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                round = Integer.parseInt(s.toString())
                println("amazon $s")
                println("pablo $round")
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }
        })
        binding.edtxtRest.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                println("namjera $round")
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }
        })
        binding.edtxtTime.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                println("trenutak $round")
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }
        })

        binding.startBtn.setOnClickListener {
            setContentView(bindingRoundFragment.root)
        }
    }
}