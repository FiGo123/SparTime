package com.example.spartime

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.Navigation
import com.example.spartime.databinding.FragmentFirstBinding
import com.example.spartime.databinding.FragmentRoundCounterBinding
import com.example.spartime.databinding.FragmentSecondBinding
import com.example.spartime.databinding.MainFragmentBinding

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
    private lateinit var binding : FragmentFirstBinding
    private lateinit var bindingRoundFragment : FragmentSecondBinding
    var round = 0
    var rest = 0
    var time = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bindingRoundFragment = FragmentSecondBinding.inflate(layoutInflater)
        //setContentView(binding.root)
        setupListeners(binding, bindingRoundFragment)

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = layoutInflater.inflate(R.layout.fragment_first, container, false)
        binding = FragmentFirstBinding.inflate(layoutInflater)
        bindingRoundFragment = FragmentSecondBinding.inflate(layoutInflater)
        binding.startBtn.setOnClickListener{
            Navigation.findNavController(view).navigate(R.id.action_first_to_second)
        }

        return view
    }


    private fun setupListeners(binding: FragmentFirstBinding, bindingRoundFragment: FragmentSecondBinding){
        binding.edtxtRound.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                round = Integer.parseInt(s.toString())

            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }
        })
        binding.edtxtRest.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }
        })
        binding.edtxtTime.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }
        })

        binding.startBtn.setOnClickListener {
       //     setContentView(bindingRoundFragment.root)

        }

    }
}