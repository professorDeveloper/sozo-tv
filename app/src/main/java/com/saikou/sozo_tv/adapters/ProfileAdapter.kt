package com.saikou.sozo_tv.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.data.model.SectionItem
import com.saikou.sozo_tv.data.model.anilist.Profile
import com.saikou.sozo_tv.databinding.AccountItemBinding
import com.saikou.sozo_tv.databinding.AccountTypeItemBinding
import com.saikou.sozo_tv.databinding.ProfileSectionItemBinding
import com.saikou.sozo_tv.databinding.ProfileTopItemBinding
import com.saikou.sozo_tv.presentation.activities.ProfileActivity
import com.saikou.sozo_tv.utils.applyTvFocusScale
import com.saikou.sozo_tv.utils.loadImage

class ProfileAdapter(
    private val accounts: MutableList<Profile>,
    private val sectionList: List<SectionItem>,
    private val recyclerView: RecyclerView,
    /** Only decides whether the trailing Exit row is treated as an Exit row. */
    private val isSignedIn: Boolean = false
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var accounType = ""
    // Safe default: the Exit row is shown whenever the TV is linked, but the real listener is
    // only wired after the profile fetch succeeds. A no-op default prevents a crash (was
    // lateinit -> UninitializedPropertyAccessException) if Exit is pressed before/without it.
    private var exitItemListener: () -> Unit = {}
    private lateinit var itemListener: () -> Unit
    private lateinit var onSectionClick: (SectionItem, Int) -> Unit

    private var selectedSectionIndex: Int = RecyclerView.NO_POSITION

    /** Consumed by the first bind of the selected row; see bind(). */
    private var focusPending: Boolean = true
    private var pendingNav: Runnable? = null

    fun setOnExitClickListener(listener: () -> Unit) {
        exitItemListener = listener
    }

    fun sectionClickListener(listener: (SectionItem, Int) -> Unit) {
        onSectionClick = listener
    }


    companion object {
        private const val VIEW_TYPE_BACK_BUTTON = 0
        private const val VIEW_TYPE_ACCOUNT = 1
        private const val VIEW_TYPE_ACCOUNT_TYPE = 2
        private const val VIEW_TYPE_SECTION = 3
    }

    inner class BackButtonViewHolder(private val binding: ProfileTopItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            binding.root.isFocusable = true
            binding.root.isFocusableInTouchMode = true
            binding.root.setOnClickListener {
                val item = SectionItem("", 1)
                onSectionClick(item, ProfileActivity.HOME_BUTTON)
            }
        }
    }

    inner class AccountViewHolder(private val binding: AccountItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(account: Profile) {
            binding.userNameTxt.isSelected = true
            binding.userNameTxt.visibility = View.VISIBLE
            binding.phoneTxt.text = account.name
            // Keyed on the avatar, not on a guest id sentinel: a linked account may have no photo,
            // and loadImage() paints the 404 wallpaper for a blank URL — which would read as a
            // broken avatar rather than the tinted placeholder this view already shows.
            if (!account.avatarUrl.isNullOrBlank()) {
                ImageViewCompat.setImageTintList(binding.accountImg, null)
                binding.accountImg.loadImage(account.avatarUrl)
            }

            binding.phoneTxt.textSize = 10f
            binding.phoneTxt.alpha = 0.7f
            binding.root.isFocusable = true
            binding.root.isFocusableInTouchMode = true
        }
    }

    inner class AccountTypeViewHolder(private val binding: AccountTypeItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(type: String) {
            binding.accountTypeTxt.text = type
            binding.root.isFocusable = false
            binding.root.isFocusableInTouchMode = false
        }
    }

    inner class SectionViewHolder(private val binding: ProfileSectionItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // Captured before any bind touches them: the exit row overrides the layout's own vertical
        // margins, and this holder gets recycled into ordinary rows afterwards.
        private val defaultTopMargin: Int
        private val defaultBottomMargin: Int

        init {
            val params = binding.root.layoutParams as ViewGroup.MarginLayoutParams
            defaultTopMargin = params.topMargin
            defaultBottomMargin = params.bottomMargin
        }

        fun bind(section: SectionItem) {
            binding.sectionTxt.text = section.sectionTitle
            binding.sectionImg.setImageResource(section.sectionImg)

            val sectionPosition = sectionList.indexOf(section)
            val isSelected = sectionPosition == selectedSectionIndex


            // Focus is placed ONCE, on the first bind of the selected row. Requesting it
            // on every bind dragged focus back to the rail whenever a row rebound, so
            // moving right into the content pane did not stick.
            if (isSelected && focusPending) {
                focusPending = false
                binding.root.requestFocus()
            }
            // No setSectionSelected() here: it calls notifyItemChanged, and mutating the
            // adapter from inside bind runs during layout. The focus listener below is
            // what owns selection.
            // No clearFocus() either — clearing focus on a recycled view strands the
            // d-pad, since Android does not hand it anywhere afterwards.

            binding.root.isFocusable = true
            binding.root.isFocusableInTouchMode = true

            // These rows are full-width, so a scale-up grows them past the rail's edge
            // and over the screen border. The white focused background is the
            // affordance; 1f keeps the z-lift and the callback without the overflow.
            binding.root.applyTvFocusScale(scale = 1f) { _, hasFocus ->
                if (hasFocus) {
                    setSectionSelected(sectionPosition)
                    // Debounce: passing focus THROUGH rail items must not load every screen
                    // (each navigate() instantiates a fragment, some of which fetch network data).
                    // Only the item focus settles on for 300ms actually navigates.
                    pendingNav?.let { recyclerView.removeCallbacks(it) }
                    val r = Runnable { onSectionClick(section, sectionPosition) }
                    pendingNav = r
                    recyclerView.postDelayed(r, 300)
                }
            }

            val isExitRow = isSignedIn && section.sectionImg == R.drawable.ic_exit
            if (!isExitRow) {
                binding.spaceVw1.visibility = View.GONE
                binding.spaceVw2.visibility = View.GONE
                binding.root.setBackgroundResource(R.drawable.background_button)
                val layoutParams = binding.root.layoutParams as ViewGroup.MarginLayoutParams
                layoutParams.topMargin = defaultTopMargin
                layoutParams.bottomMargin = defaultBottomMargin
                binding.root.layoutParams = layoutParams
                binding.root.setOnClickListener(null)
            } else {
                binding.spaceVw1.visibility = View.VISIBLE
                binding.spaceVw2.visibility = View.VISIBLE
                binding.root.setBackgroundResource(R.drawable.background_button_exit)
                val context = binding.root.context
                val layoutParams = binding.root.layoutParams as ViewGroup.MarginLayoutParams
                layoutParams.topMargin =
                    context.resources.getDimensionPixelSize(R.dimen.exit_margin_top)
                layoutParams.bottomMargin =
                    context.resources.getDimensionPixelSize(R.dimen.exit_margin_bottom)
                binding.root.layoutParams = layoutParams
                binding.root.setOnClickListener { exitItemListener.invoke() }
            }

        }
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            position == 0 -> VIEW_TYPE_BACK_BUTTON
            position in 1 until accounts.size + 1 -> VIEW_TYPE_ACCOUNT
            position == accounts.size + 1 -> VIEW_TYPE_ACCOUNT_TYPE
            position > accounts.size + 1 -> VIEW_TYPE_SECTION
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_BACK_BUTTON -> {
                val binding = ProfileTopItemBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                BackButtonViewHolder(binding)
            }

            VIEW_TYPE_ACCOUNT_TYPE -> {
                val binding = AccountTypeItemBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                AccountTypeViewHolder(binding)
            }

            VIEW_TYPE_ACCOUNT -> {
                val binding =
                    AccountItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                AccountViewHolder(binding)
            }

            VIEW_TYPE_SECTION -> {
                val binding = ProfileSectionItemBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                SectionViewHolder(binding)
            }

            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun getItemCount(): Int {
        return accounts.size + sectionList.size + 2
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is BackButtonViewHolder -> holder.bind()
            is AccountViewHolder -> {
                if (position - 1 in 0 until accounts.size) {
                    holder.bind(accounts[position - 1])
                }
            }

            is AccountTypeViewHolder -> holder.bind(accounType)
            is SectionViewHolder -> {
                val sectionPosition = position - accounts.size - 2
                if (sectionPosition in sectionList.indices) {
                    holder.bind(sectionList[sectionPosition])
                }
            }
        }
    }

    fun updateAccountType(newAccounType: String) {
        accounType = newAccounType
        notifyItemChanged(accounts.size + 1)
    }


    /**
     * Replaces the rail's single account rather than appending. profileData is a LiveData that
     * replays its last value to every new observer, so an append would add a duplicate row on
     * each configuration change.
     */
    fun setAccount(account: Profile = Profile(-1, "Guest", null, "")) {
        if (accounts.isEmpty()) {
            accounts.add(account)
            notifyItemInserted(accounts.size)
            return
        }
        accounts[0] = account
        notifyItemChanged(1)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        // The pending navigation outlives the view it was scheduled on. Left to run,
        // it reaches an activity that is already tearing down.
        pendingNav?.let { recyclerView.removeCallbacks(it) }
        pendingNav = null
    }

    fun setSectionSelected(index: Int) {
        if (index == selectedSectionIndex) return
        val previousIndex = selectedSectionIndex
        selectedSectionIndex = index

        if (previousIndex != RecyclerView.NO_POSITION) {
            notifyItemChanged(previousIndex + accounts.size + 2)
        }
        notifyItemChanged(selectedSectionIndex + accounts.size + 2)
    }

}
