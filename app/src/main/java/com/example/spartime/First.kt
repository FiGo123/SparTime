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
        
        initializeDefaults()
        setupUI()
        setupListeners()
        observeViewModel()
        handleDialogResponse()
        
        return binding.root
    }
    
    private fun initializeDefaults() {
        // Initialize with boxing defaults immediately
        binding.apply {
            fragmentFirstEdtxtRound.setText("3")
            edtxtRest.setText("1")
            edtxtTime.setText("3")
        }
        
        // Set ViewModel defaults
        mainViewModel.apply {
            setNumOfRounds(3)
            setCurrentRound(1)
            setRoundLengthInMin(3)
            setPauseLengthInSecs(1)
        }
        
        updateLocalValues(3, 1, 3)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    private fun setupUI() {
        binding.apply {
            firstFragmentStartBtn.setOnClickListener {
                if (validateInputs()) {
                    it.findNavController().navigate(R.id.action_first_to_second)
                }
            }
            btnSettings.setOnClickListener {
                it.findNavController().navigate(R.id.action_first_to_settings)
            }
            btnHistory.setOnClickListener {
                it.findNavController().navigate(R.id.action_first_to_historyTraining)
            }
        }
    }
    
    private fun validateInputs(): Boolean {
        val rounds = binding.fragmentFirstEdtxtRound.text.toString().toIntOrNull()
        val restTime = binding.edtxtRest.text.toString().toIntOrNull()
        val roundTime = binding.edtxtTime.text.toString().toIntOrNull()
        
        if (rounds == null || rounds <= 0 || rounds > 50) {
            binding.fragmentFirstEdtxtRound.error = "Enter valid number of rounds (1-50)"
            return false
        }
        
        if (restTime == null || restTime <= 0 || restTime > 60) {
            binding.edtxtRest.error = "Enter valid rest time (1-60 minutes)"
            return false
        }
        
        if (roundTime == null || roundTime <= 0 || roundTime > 60) {
            binding.edtxtTime.error = "Enter valid round time (1-60 minutes)"
            return false
        }
        
        // Update ViewModel with validated values
        mainViewModel.apply {
            setNumOfRounds(rounds)
            setCurrentRound(1)
            setRoundLengthInMin(roundTime)
            setPauseLengthInSecs(restTime)
        }
        
        return true
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
        // Set default boxing configuration if no training type is set
        mainViewModel.getDefaultTrainingType()
        
        mainViewModel.trainingType.observe(viewLifecycleOwner) { trainingType ->
            when (trainingType) {
                "MMA" -> setupMMADefaults()
                "BOXING" -> setupBoxingDefaults()
                else -> setupDefaultBoxingConfig() // Always provide defaults
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
        updateLocalValues(5, 1, 5)
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
        updateLocalValues(12, 1, 3)
    }
    
    private fun setupDefaultBoxingConfig() {
        // Provide reasonable defaults for boxing training
        binding.apply {
            if (fragmentFirstEdtxtRound.text.isNullOrEmpty()) {
                fragmentFirstEdtxtRound.setText("3")
            }
            if (edtxtRest.text.isNullOrEmpty()) {
                edtxtRest.setText("1")
            }
            if (edtxtTime.text.isNullOrEmpty()) {
                edtxtTime.setText("3")
            }
        }
        
        // Set defaults in ViewModel if not already set
        val currentRounds = binding.fragmentFirstEdtxtRound.text.toString().toIntOrNull() ?: 3
        val currentRest = binding.edtxtRest.text.toString().toIntOrNull() ?: 1
        val currentTime = binding.edtxtTime.text.toString().toIntOrNull() ?: 3
        
        mainViewModel.apply {
            setNumOfRounds(currentRounds)
            setCurrentRound(1)
            setRoundLengthInMin(currentTime)
            setPauseLengthInSecs(currentRest)
        }
        updateLocalValues(currentRounds, currentRest, currentTime)
    }
    
    private fun updateLocalValues(rounds: Int, rest: Int, time: Int) {
        this.round = rounds
        this.rest = rest
        this.time = time
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