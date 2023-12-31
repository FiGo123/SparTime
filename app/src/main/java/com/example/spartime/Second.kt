package com.example.spartime

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
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
 * Use the [Second.newInstance] factory method to
 * create an instance of this fragment.
 */
class Second : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null
    var numRounds = 0
    var roundLength = 0
    var pauseLength = 0

    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }

    }

   // override fun onBackPressed() {
    //    println("catch on back pressed")
   //     view?.let { Navigation.findNavController(it).navigate(R.id.action_second_to_first) }
   // }
    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
       val view = inflater.inflate(R.layout.fragment_second, container, false)
       val binding= FragmentSecondBinding.inflate(layoutInflater)
       val startBtn: Button = view.findViewById(R.id.roundFragmentBtn)
       binding.roundFragmentBtn.setOnClickListener {
           println("Back")
           it.findNavController().navigate(R.id.action_second_to_first)
       }
       startBtn.setOnClickListener {
           println("Stop")
           it.findNavController().navigate(R.id.action_second_to_first)
       }
       val roundTime: TextView? = view?.findViewById(R.id.timeCounter)
       mainViewModel.numOfRounds.observe(viewLifecycleOwner
       ) { num ->
           numRounds = num
       }
       mainViewModel.roundLengthInMin.observe(viewLifecycleOwner
       ) { length ->
           roundTime?.text = length.toString()
           roundLength = length
           roundTime?.text = length.toString()
       }
       mainViewModel.pauseLengthInSecs.observe(viewLifecycleOwner
       ) { pauseLengthInObserver ->
           println("PauseObserver")
           pauseLength = pauseLengthInObserver
       }


        // Inflate the layout for this fragment
       return inflater.inflate(R.layout.fragment_second, container, false)
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment Second.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            Second().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}