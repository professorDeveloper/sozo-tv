package com.saikou.sozo_tv.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.data.model.SectionItem
import com.saikou.sozo_tv.databinding.AccountItemBinding
import com.saikou.sozo_tv.databinding.AccountTypeItemBinding
import com.saikou.sozo_tv.databinding.ProfileSectionItemBinding
import com.saikou.sozo_tv.databinding.ProfileTopItemBinding
import com.saikou.sozo_tv.presentation.activities.ProfileActivity

class ProfileAdapter(
    private val accounts: MutableList<String>,
    private val sectionList: List<SectionItem>,
    private val recyclerView: RecyclerView
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var accounType = ""
    private lateinit var exitItemListener: () -> Unit
    private lateinit var itemListener: () -> Unit
    private lateinit var onSectionClick: (SectionItem, Int) -> Unit

    private var selectedSectionIndex: Int = RecyclerView.NO_POSITION

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
        fun bind(account: String) {
            binding.userNameTxt.isSelected = true
            binding.userNameTxt.visibility = View.VISIBLE
            binding.phoneTxt.text = account
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

        fun bind(section: SectionItem) {
            binding.sectionTxt.text = section.sectionTitle
            binding.sectionImg.setImageResource(section.sectionImg)

            val sectionPosition = sectionList.indexOf(section)
            val isSelected = sectionPosition == selectedSectionIndex


            if (isSelected) {
                binding.root.requestFocus()
                setSectionSelected(sectionPosition)

            } else {
                binding.root.clearFocus()
            }

            binding.root.isFocusable = true
            binding.root.isFocusableInTouchMode = true

            binding.root.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    setSectionSelected(sectionPosition)
                    onSectionClick(section, sectionPosition)
                }
                val animation = if (hasFocus) {
                    AnimationUtils.loadAnimation(binding.root.context, R.anim.zoom_in)
                } else {
                    AnimationUtils.loadAnimation(binding.root.context, R.anim.zoom_out)
                }
                binding.root.startAnimation(animation)
                animation.fillAfter = true
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

    fun addAccount(account: String) {
        accounts.add(account)
        notifyItemInserted(accounts.size)
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
