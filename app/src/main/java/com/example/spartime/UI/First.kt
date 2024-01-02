package com.example.spartime.UI

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import com.example.spartime.R
import com.example.spartime.databinding.FragmentFirstBinding
import com.example.spartime.databinding.FragmentSecondBinding
import com.example.spartime.viewmodel.MainViewModel

/**
 * A simple [Fragment] subclass.
 * Use the [First.newInstance] factory method to
 * create an instance of this fragment.
 */
class First : Fragment() {
    private lateinit var bindingRoundFragment : FragmentSecondBinding
    var round = 0
    var rest = 0
    var time = 0

    private val mainViewModel: MainViewModel by activityViewModels()



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_first, container, false)
        val binding= FragmentFirstBinding.inflate(layoutInflater)
        val startBtn: Button = view.findViewById(R.id.first_fragment_start_btn)

        startBtn.setOnClickListener {
            it.findNavController().navigate(R.id.action_first_to_second)
        }

        setupListeners(binding,view)

        return view
    }


    private fun setupListeners(binding: FragmentFirstBinding, view: View){
        val edtxtRound: EditText = view.findViewById(R.id.fragment_first_edtxt_round)
        edtxtRound.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                round = Integer.parseInt(s.toString())
                mainViewModel.setNumOfRounds(round)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        val edtxtRest: EditText = view.findViewById(R.id.edtxt_rest)
        edtxtRest.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                rest = Integer.parseInt(s.toString())
                mainViewModel.setPauseLengthInSecs(rest)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }
        })
        val edtxtTime: EditText = view.findViewById(R.id.edtxt_time)
        edtxtTime.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                time = Integer.parseInt(s.toString())
                mainViewModel.setRoundLengthInMin(time)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }
        })

    }
}