package com.example.spartime

import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import com.example.spartime.data.DBHandler
import com.example.spartime.data.Dao
import com.example.spartime.data.models.Training
import com.example.spartime.databinding.FragmentFirstBinding
import com.example.spartime.viewmodel.MainViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * A simple [Fragment] subclass.
 * Use the [First.newInstance] factory method to
 * create an instance of this fragment.
 */
class First() : Fragment() {
    private lateinit var bindingRoundFragment : FragmentFirstBinding
    var round = 0
    var rest = 0
    var time = 0

    private val mainViewModel: MainViewModel by activityViewModels()



    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {


        val view = inflater.inflate(R.layout.fragment_first, container, false)
        val binding= FragmentFirstBinding.inflate(layoutInflater)
        val startBtn: Button = view.findViewById(R.id.first_fragment_start_btn)
        val settingsBtn: Button = view.findViewById(R.id.btn_settings)
        val historyBtn: Button = view.findViewById(R.id.btn_history)
        val roundBtn: EditText = view.findViewById(R.id.fragment_first_edtxt_round)
        val restBtn: EditText = view.findViewById(R.id.edtxt_rest)
        val roundTime: EditText = view.findViewById(R.id.edtxt_time)
        startBtn.setOnClickListener {
            it.findNavController().navigate(R.id.action_first_to_second)
        }
        settingsBtn.setOnClickListener {
            it.findNavController().navigate(R.id.action_first_to_settings)
        }
        historyBtn.setOnClickListener{
            it.findNavController().navigate(R.id.action_first_to_historyTraining)
        }
        val dialogAnswer = mainViewModel.getDialogAnswer()
        var db = DBHandler(requireContext())
        val time = getCurrentDateTime()
        if (dialogAnswer == true){
            //TODO Change training to save
            val training = Training("Boxing Training", time, 12, 3,3, "Odradjen boks trening")
            db.insertData(training)
        }
        mainViewModel.getDefaultTrainingType()
        val trainingType = mainViewModel.trainingType.value
        if (trainingType != null){
            if (trainingType == "MMA"){
                roundBtn.setText("5")
                restBtn.setText("1")
                roundTime.setText("5")
                binding.fragmentFirstEdtxtRound.setText("5")
                binding.edtxtRest.setText("1")
                binding.edtxtTime.setText("5")
                mainViewModel.numOfRounds.value = 5
                mainViewModel.currentRound.value = 1
                mainViewModel.roundLengthInMin.value = 5
                mainViewModel.pauseLengthInMin.value = 1
            }else if (trainingType == "BOXING"){
                roundBtn.setText("12")
                restBtn.setText("1")
                roundTime.setText("3")
                mainViewModel.numOfRounds.value = 12
                mainViewModel.currentRound.value = 1
                mainViewModel.roundLengthInMin.value = 3
                mainViewModel.pauseLengthInMin.value = 1
            }
        }else{
            mainViewModel.numOfRounds.value = 0
            mainViewModel.currentRound.value = 0
            mainViewModel.roundLengthInMin.value = 0
            mainViewModel.pauseLengthInMin.value = 0
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
                mainViewModel.setCurrentRound(1)
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
                println("hajnbc")
                mainViewModel.setRoundLengthInMin(time)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }
        })

    }
    @RequiresApi(Build.VERSION_CODES.O)
    fun getCurrentDateTime(): String {
        val currentDateTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        return currentDateTime.format(formatter)
    }
}