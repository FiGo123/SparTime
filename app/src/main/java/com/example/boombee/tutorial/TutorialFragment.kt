package com.example.boombee.tutorial

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.example.boombee.R
import com.example.boombee.coach.ClipPlayer
import com.example.boombee.coach.ComboLibrary
import com.example.boombee.coach.CommandPart
import com.example.boombee.coach.VoiceStyle
import com.example.boombee.databinding.FragmentTutorialBinding
import com.example.boombee.viewmodel.MainViewModel
import com.google.android.material.tabs.TabLayoutMediator

/**
 * Punch tutorial + "Tap the combo" mini-game (RELEASE_TUTORIAL.md).
 * Opened from Start the first time ([SOURCE_FIRST_RUN] — finishing or
 * skipping goes straight into the training), or from the home "?" button
 * and Settings ([SOURCE_MENU] — finishing goes back).
 */
class TutorialFragment : Fragment() {

    private var _binding: FragmentTutorialBinding? = null
    private val binding get() = _binding!!
    private val mainViewModel: MainViewModel by activityViewModels()

    private var clipPlayer: ClipPlayer? = null
    private var adapter: TutorialAdapter? = null
    private val pages = TutorialPages.ALL

    private val fromFirstRun get() = arguments?.getString(ARG_SOURCE) == SOURCE_FIRST_RUN

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTutorialBinding.inflate(inflater, container, false)
        clipPlayer = ClipPlayer(requireContext())

        val level3Pool = ComboLibrary(requireContext()).let { library ->
            (3..4).flatMap(library::combosOfLength).mapNotNull { combo ->
                combo.parts.map { (it as? CommandPart.Punch)?.number ?: return@mapNotNull null }
            }
        }
        val tutorialAdapter = TutorialAdapter(
            pages = pages,
            onListen = ::playClips,
            createGame = { gameBinding ->
                ComboGameController(
                    binding = gameBinding,
                    clipPlayer = { clipPlayer },
                    voiceStyle = ::voiceStyle,
                    level3Pool = level3Pool,
                    onContinue = { binding.pager.currentItem += 1 },
                )
            },
        )
        adapter = tutorialAdapter

        binding.pager.adapter = tutorialAdapter
        binding.pager.offscreenPageLimit = 1
        TabLayoutMediator(binding.dots, binding.pager) { _, _ -> }.attach()
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                clipPlayer?.stop()
                tutorialAdapter.onPageSelected(position)
                updateNavButtons(position)
            }
        })

        binding.skipBtn.setOnClickListener { finish() }
        binding.backBtn.setOnClickListener { binding.pager.currentItem -= 1 }
        binding.nextBtn.setOnClickListener {
            if (binding.pager.currentItem == pages.lastIndex) finish() else binding.pager.currentItem += 1
        }
        updateNavButtons(0)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.pager.currentItem > 0) binding.pager.currentItem -= 1
                else findNavController().popBackStack()
            }
        })

        return binding.root
    }

    private fun updateNavButtons(position: Int) {
        val isLast = position == pages.lastIndex
        binding.backBtn.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
        binding.skipBtn.visibility = if (isLast) View.INVISIBLE else View.VISIBLE
        binding.nextBtn.setText(
            when {
                !isLast -> R.string.tutorial_next
                fromFirstRun -> R.string.tutorial_start_training
                else -> R.string.tutorial_finish
            }
        )
    }

    private fun voiceStyle(): VoiceStyle = mainViewModel.voiceStyle.value ?: VoiceStyle.WORDS

    private fun playClips(page: TutorialPage) {
        val player = clipPlayer ?: return
        val keys = page.clips?.invoke(voiceStyle()).orEmpty()
        if (!player.hasAllClips(keys)) return
        when (page.clipMode) {
            TutorialPage.ClipMode.COMBO -> player.playCommand(keys, isCombo = true)
            TutorialPage.ClipMode.SEQUENCE -> player.play(keys, gapMs = SEQUENCE_GAP_MS)
        }
    }

    private fun finish() {
        TutorialPrefs.markSeen(requireContext())
        clipPlayer?.stop()
        if (fromFirstRun) {
            mainViewModel.resetCoachSession()
            findNavController().navigate(R.id.action_tutorial_to_second)
        } else {
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter?.release()
        adapter = null
        clipPlayer?.shutdown()
        clipPlayer = null
        _binding = null
    }

    companion object {
        const val ARG_SOURCE = "source"
        const val SOURCE_FIRST_RUN = "first_run"
        const val SOURCE_MENU = "menu"
        private const val SEQUENCE_GAP_MS = 450L
    }
}
