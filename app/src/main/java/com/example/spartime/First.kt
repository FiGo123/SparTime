package com.example.spartime

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import com.example.spartime.databinding.FragmentFirstBinding
import com.example.spartime.databinding.FragmentSecondBinding
import com.example.spartime.viewmodel.MainViewModel

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

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
        val myBtn: Button = view.findViewById(R.id.first_fragment_start_btn)

        myBtn.setOnClickListener {
            println("zabizuresddstea")
            it.findNavController().navigate(R.id.action_first_to_second)
        }

        setupListeners(binding,view)

        return view
    }


    private fun setupListeners(binding: FragmentFirstBinding,view: View){
        val edtxtRound: EditText = view.findViewById(R.id.fragment_first_edtxt_round)
        edtxtRound.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                println("coroonaa")

                round = Integer.parseInt(s.toString())
                println("zabizuretea $round")
                mainViewModel.setNumOfRounds(round)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                println("coroonaa")
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                println("coroonaa")
            }
        })
        val edtxtRest: EditText = view.findViewById(R.id.edtxt_rest)
        edtxtRest.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {

            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                println("coroonaa")
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                println("coroonaa")

            }
        })
        val edtxtTime: EditText = view.findViewById(R.id.edtxt_time)
        edtxtTime.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                println("jezuiti ${Integer.parseInt(s.toString())}")
                mainViewModel.setRoundLengthInMin(Integer.parseInt(s.toString()))
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                println("coroonaa")
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                println("coroonaa")
            }
        })

    }
}