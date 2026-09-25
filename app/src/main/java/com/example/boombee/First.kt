package com.example.boombee

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import com.example.boombee.coach.Difficulty
import com.example.boombee.coach.TrainingMode
import com.example.boombee.coach.VoiceStyle
import com.example.boombee.data.DBHandler
import com.example.boombee.data.models.Training
import com.example.boombee.databinding.FragmentFirstBinding
import com.example.boombee.viewmodel.MainViewModel
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
        observeViewModel()
        handleDialogResponse()
        binding.versionLabel.text = "v${AppVersion.VERSION}"

        return binding.root
    }
    
    private fun initializeDefaults() {
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
                    mainViewModel.resetCoachSession()
                    it.findNavController().navigate(R.id.action_first_to_second)
                }
            }
            btnSettings.setOnClickListener {
                it.findNavController().navigate(R.id.action_first_to_settings)
            }
            btnHistory.setOnClickListener {
                it.findNavController().navigate(R.id.action_first_to_historyTraining)
            }

            modeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                val isCoach = checkedId == R.id.mode_coach_btn
                mainViewModel.setTrainingMode(if (isCoach) TrainingMode.BOXING_COACH else TrainingMode.TIMER_ONLY)
                val visibility = if (isCoach) View.VISIBLE else View.GONE
                difficultyLabel.visibility = visibility
                difficultyToggleGroup.visibility = visibility
                voiceStyleLabel.visibility = visibility
                voiceStyleToggleGroup.visibility = visibility
                warmupLabel.visibility = visibility
                warmupToggleGroup.visibility = visibility
            }

            difficultyToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                val difficulty = when (checkedId) {
                    R.id.difficulty_intermediate_btn -> Difficulty.INTERMEDIATE
                    R.id.difficulty_advanced_btn -> Difficulty.ADVANCED
                    else -> Difficulty.BEGINNER
                }
                mainViewModel.setCoachDifficulty(difficulty)
            }

            voiceStyleToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                val style = if (checkedId == R.id.voice_words_btn) VoiceStyle.WORDS else VoiceStyle.NUMBERS
                mainViewModel.setVoiceStyle(style)
            }

            warmupToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                val seconds = when (checkedId) {
                    R.id.warmup_30_btn -> 30
                    R.id.warmup_60_btn -> 60
                    else -> 0
                }
                mainViewModel.setWarmupSeconds(seconds)
            }

            roundsMinusBtn.setOnClickListener { stepRounds(-1) }
            roundsPlusBtn.setOnClickListener { stepRounds(1) }
            roundTimeMinusBtn.setOnClickListener { stepRoundTime(-1) }
            roundTimePlusBtn.setOnClickListener { stepRoundTime(1) }
            restMinusBtn.setOnClickListener { stepRest(-1) }
            restPlusBtn.setOnClickListener { stepRest(1) }
        }
    }

    private fun stepRounds(delta: Int) {
        round = (round + delta).coerceIn(1, 50)
        binding.roundsValue.text = round.toString()
        mainViewModel.setNumOfRounds(round)
        mainViewModel.setCurrentRound(1)
    }

    private fun stepRoundTime(delta: Int) {
        time = (time + delta).coerceIn(1, 60)
        binding.roundTimeValue.text = time.toString()
        mainViewModel.setRoundLengthInMin(time)
    }

    private fun stepRest(delta: Int) {
        rest = (rest + delta).coerceIn(1, 60)
        binding.restValue.text = rest.toString()
        mainViewModel.setPauseLengthInSecs(rest)
    }
    
    private fun validateInputs(): Boolean {
        // The steppers (stepRounds/stepRoundTime/stepRest) clamp on every
        // click, so there's no out-of-range value to reject here.
        mainViewModel.apply {
            setNumOfRounds(round)
            setCurrentRound(1)
            setRoundLengthInMin(time)
            setPauseLengthInSecs(rest)
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
        val roundLength = mainViewModel.roundLengthInMin.value ?: 0
        val difficulty = mainViewModel.getSelectedDifficulty()
        val trainingType = mainViewModel.trainingType.value ?: "Custom"

        val title = when (trainingType) {
            "BOXING" -> "Boxing Training"
            "MMA" -> "MMA Training"
            else -> "Custom Training"
        }
        val mode = mainViewModel.trainingMode.value ?: TrainingMode.TIMER_ONLY
        val stats = mainViewModel.coachSession?.stats

        val training = Training(
            title,
            currentTime,
            currentRound,
            roundLength,
            difficulty,
            "Interrupted at round $currentRound",
            trainingMode = mode.name,
            coachDifficulty = mainViewModel.coachDifficulty.value?.name,
            totalPunches = stats?.totalPunches() ?: 0,
            totalCombos = stats?.attackCalls ?: 0,
            totalTacticalCommands = (stats?.defenseCalls ?: 0) + (stats?.distanceCalls ?: 0),
            punchBreakdown = stats?.serializeBreakdown(),
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
        mainViewModel.apply {
            setNumOfRounds(5)
            setCurrentRound(1)
            setRoundLengthInMin(5)
            setPauseLengthInSecs(1)
        }
        updateLocalValues(5, 1, 5)
    }
    
    private fun setupBoxingDefaults() {
        mainViewModel.apply {
            setNumOfRounds(12)
            setCurrentRound(1)
            setRoundLengthInMin(3)
            setPauseLengthInSecs(1)
        }
        updateLocalValues(12, 1, 3)
    }
    
    private fun setupDefaultBoxingConfig() {
        // Provide reasonable defaults for boxing training, keeping whatever
        // the user already dialed in on the steppers if they touched them.
        val currentRounds = if (round > 0) round else 3
        val currentRest = if (rest > 0) rest else 1
        val currentTime = if (time > 0) time else 3

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
        binding.roundsValue.text = rounds.toString()
        binding.restValue.text = rest.toString()
        binding.roundTimeValue.text = time.toString()
    }


    @RequiresApi(Build.VERSION_CODES.O)
    fun getCurrentDateTime(): String {
        val currentDateTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        return currentDateTime.format(formatter)
    }
}