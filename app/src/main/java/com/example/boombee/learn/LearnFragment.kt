package com.example.boombee.learn

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.boombee.R
import com.example.boombee.databinding.FragmentLearnBinding
import com.example.boombee.databinding.ItemLearnChapterBinding
import com.example.boombee.tutorial.ComboGame
import com.example.boombee.tutorial.LearnProgress
import com.example.boombee.tutorial.TutorialChapter
import com.example.boombee.tutorial.TutorialChapters
import com.example.boombee.tutorial.TutorialFragment

/** Learn tab: the tutorial as a book of chapters, plus the games. */
class LearnFragment : Fragment() {

    private var _binding: FragmentLearnBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLearnBinding.inflate(inflater, container, false)
        binding.readAllBtn.setOnClickListener { open(null) }
        return binding.root
    }

    // Rebuilt on every return so ✓ marks and the best score are current.
    override fun onResume() {
        super.onResume()
        val context = requireContext()
        val chapters = TutorialChapters.ALL
        val done = chapters.count { LearnProgress.isDone(context, it.id) }
        binding.learnProgressText.text = if (done == chapters.size) {
            "All $done chapters read 🐝"
        } else {
            "$done of ${chapters.size} chapters read"
        }
        binding.learnProgressBar.max = chapters.size
        binding.learnProgressBar.setProgressCompat(done, false)

        binding.chapterList.removeAllViews()
        chapters.forEachIndexed { i, chapter ->
            addCard(binding.chapterList, chapter, badge = "${i + 1}", kicker = "CHAPTER ${i + 1}",
                status = if (LearnProgress.isDone(context, chapter.id)) "✓" else "")
        }
        binding.gameList.removeAllViews()
        val best = LearnProgress.gameBest(context)
        val total = ComboGame.build(emptyList()).total
        addCard(binding.gameList, TutorialChapters.GAME, badge = "🎯", kicker = "GAME",
            status = if (LearnProgress.isDone(context, TutorialChapters.GAME.id)) "Best $best/$total" else "Play")
    }

    private fun addCard(parent: LinearLayout, chapter: TutorialChapter, badge: String, kicker: String, status: String) {
        val card = ItemLearnChapterBinding.inflate(layoutInflater, parent, false)
        val done = status.isNotEmpty() && status != "Play"
        card.chapterBadge.text = badge
        card.chapterBadge.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(requireContext(), if (done || chapter.isGame) R.color.timer_active else R.color.gray_dark))
        }
        card.chapterBadge.setTextColor(ContextCompat.getColor(requireContext(), if (done || chapter.isGame) R.color.black else R.color.white))
        card.chapterKicker.text = kicker
        card.chapterTitle.text = chapter.title
        card.chapterSubtitle.text = chapter.subtitle
        card.chapterStatus.text = status
        card.root.setOnClickListener { open(chapter) }
        parent.addView(card.root)
    }

    private fun open(chapter: TutorialChapter?) {
        findNavController().navigate(
            R.id.action_learn_to_tutorial,
            bundleOf(
                TutorialFragment.ARG_SOURCE to TutorialFragment.SOURCE_MENU,
                TutorialFragment.ARG_CHAPTER to chapter?.id,
            ),
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
