package com.saikou.sozo_tv.presentation.screens.wrong_title

import android.annotation.SuppressLint
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.DialogFragment
import com.google.android.material.internal.ViewUtils.hideKeyboard
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.WrongTitleSearchAdapter
import com.saikou.sozo_tv.databinding.FilterDialogBinding
import com.saikou.sozo_tv.databinding.WrongTitleDialogBinding
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.presentation.screens.category.CustomSpinnerAdapter
import com.saikou.sozo_tv.presentation.screens.category.dialog.FilterDialog
import com.saikou.sozo_tv.presentation.viewmodel.WrongTitleViewModel
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.Resource
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.hideKeyboard
import com.saikou.sozo_tv.utils.visible
import com.skydoves.powerspinner.IconSpinnerItem
import org.koin.androidx.viewmodel.ext.android.viewModel

class WrongTitleDialog : DialogFragment() {

    private var _binding: WrongTitleDialogBinding? = null
    private val model: WrongTitleViewModel by viewModel()
    private val binding get() = _binding!!
    var onWrongTitleChanged: ((ShowResponse) -> Unit)? = null
    private var selectedRating: String? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = WrongTitleDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.closeBtn.setOnClickListener {
            dismiss()
        }
        val title = arguments?.getString("animeTitle")
        binding.inputAnimeName.setText(arguments?.getString("animeTitle"))
        binding.inputAnimeName.requestFocus()
        binding.inputAnimeName.apply {
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId in listOf(
                        EditorInfo.IME_ACTION_GO,
                        EditorInfo.IME_ACTION_SEARCH,
                        EditorInfo.IME_ACTION_SEND,
                        EditorInfo.IME_ACTION_NEXT,
                        EditorInfo.IME_ACTION_DONE
                    )
                ) {
                    val query = text.toString()
                    if (query.isNotEmpty()) {
                        model.findEpisodes(query)
                        hideKeyboard()
                    }
                    true
                } else {
                    false
                }
            }
        }
        model.findEpisodes(title.toString())
        dialog!!.window?.setWindowAnimations(R.style.DialogAnimation)
        dialog!!.window?.setBackgroundDrawable(ColorDrawable(0))
        binding.searchBtn.setOnClickListener {
            model.findEpisodes(binding.inputAnimeName.text.toString())
        }
        model.dataFound.observe(viewLifecycleOwner) { result ->
            when (result) {
                is Resource.Error -> {
                    binding.placeholderTxt.placeholderTxt.text = result.throwable.message
                    binding.bookmarkPlaceHolder.visible()
                    binding.loadingPlaceHolder.gone()
                    binding.vgvSearch.gone()
                }

                Resource.Loading -> {
                    binding.bookmarkPlaceHolder.gone()
                    binding.loadingPlaceHolder.visible()
                    binding.vgvSearch.gone()
                }

                is Resource.Success -> {
                    binding.bookmarkPlaceHolder.gone()
                    binding.loadingPlaceHolder.gone()
                    binding.vgvSearch.visible()
                    hideKeyboard()
                    if (result.data.isEmpty()) {
                        binding.bookmarkPlaceHolder.visible()
                        binding.placeholderTxt.placeholderTxt.text = "Result Not Found"
                        binding.loadingPlaceHolder.gone()
                        binding.vgvSearch.gone()
                    } else {
                        binding.bookmarkPlaceHolder.gone()
                        val adapter = WrongTitleSearchAdapter()
                        binding.vgvSearch.adapter = adapter
                        adapter.updateData(result.data)
                        adapter.setOnItemClickListener {
                            onWrongTitleChanged?.invoke(it)
                        }
                    }
                }

                else -> {

                }
            }
        }

    }

    private fun hideKeyboard() {
        val imm =
            activity?.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(
            animeTitle: String
        ): WrongTitleDialog {
            val dialog = WrongTitleDialog()
            val args = Bundle()
            args.putString("animeTitle", animeTitle)
            dialog.arguments = args
            return dialog
        }
    }

}


