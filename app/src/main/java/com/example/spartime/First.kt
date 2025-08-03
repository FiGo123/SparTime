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
class First : Fragment() {
    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!
    
    private var round = 0
    private var rest = 0
    private var time = 0

    private val mainViewModel: MainViewModel by activityViewModels()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        
        setupUI()
        setupListeners()
        observeViewModel()
        handleDialogResponse()
        
        return binding.root
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    private fun setupUI() {
        binding.apply {
            firstFragmentStartBtn.setOnClickListener {
                it.findNavController().navigate(R.id.action_first_to_second)
            }
            btnSettings.setOnClickListener {
                it.findNavController().navigate(R.id.action_first_to_settings)
            }
            btnHistory.setOnClickListener {
                it.findNavController().navigate(R.id.action_first_to_historyTraining)
            }
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleDialogResponse() {
        val dialogAnswer = mainViewModel.getDialogAnswer()
        if (dialogAnswer == true) {
            mainViewModel.setDialogAnswer(false)
            saveInterruptedTraining()
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    private fun saveInterruptedTraining() {
        val db = DBHandler(requireContext())
        val currentTime = getCurrentDateTime()
        
        val currentRound = mainViewModel.currentRound.value ?: 0
        val leftTime = mainViewModel.leftTime.value ?: 0
        
        val training = Training(
            "Boxing Training", 
            currentTime,
            currentRound, 
            leftTime, 
            3, 
            "Interrupted training session"
        )
        db.insertData(training)
    }
    
    private fun observeViewModel() {
        mainViewModel.getDefaultTrainingType()
        
        mainViewModel.trainingType.observe(viewLifecycleOwner) { trainingType ->
            when (trainingType) {
                "MMA" -> setupMMADefaults()
                "BOXING" -> setupBoxingDefaults()
                else -> setupCustomDefaults()
            }
        }
    }
    
    private fun setupMMADefaults() {
        binding.apply {
            fragmentFirstEdtxtRound.setText("5")
            edtxtRest.setText("1")
            edtxtTime.setText("5")
        }
        mainViewModel.apply {
            setNumOfRounds(5)
            setCurrentRound(1)
            setRoundLengthInMin(5)
            setPauseLengthInSecs(1)
        }
    }
    
    private fun setupBoxingDefaults() {
        binding.apply {
            fragmentFirstEdtxtRound.setText("12")
            edtxtRest.setText("1")
            edtxtTime.setText("3")
        }
        mainViewModel.apply {
            setNumOfRounds(12)
            setCurrentRound(1)
            setRoundLengthInMin(3)
            setPauseLengthInSecs(1)
        }
    }
    
    private fun setupCustomDefaults() {
        mainViewModel.apply {
            setNumOfRounds(0)
            setCurrentRound(0)
            setRoundLengthInMin(0)
            setPauseLengthInSecs(0)
        }
    }


    private fun setupListeners() {
        binding.apply {
            fragmentFirstEdtxtRound.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    s?.toString()?.toIntOrNull()?.let { rounds ->
                        round = rounds
                        mainViewModel.setNumOfRounds(rounds)
                        mainViewModel.setCurrentRound(1)
                    }
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
            
            edtxtRest.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    s?.toString()?.toIntOrNull()?.let { restTime ->
                        rest = restTime
                        mainViewModel.setPauseLengthInSecs(restTime)
                    }
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
            
            edtxtTime.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    s?.toString()?.toIntOrNull()?.let { roundTime ->
                        time = roundTime
                        mainViewModel.setRoundLengthInMin(roundTime)
                    }
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    fun getCurrentDateTime(): String {
        val currentDateTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        return currentDateTime.format(formatter)
    }
}