package com.saikou.sozo_tv.presentation.screens.history

import androidx.fragment.app.DialogFragment

class HistoryAlertDialog() : DialogFragment() {

    private var _binding: HistoryAlertDialogBinding? = null
    private val binding get() = _binding!!

    private lateinit var noClearListener: () -> Unit

    private lateinit var yesContinueListener: () -> Unit

    fun setNoClearListener(listener: () -> Unit) {
        noClearListener = listener
    }

    fun setYesContinueListener(listener: () -> Unit) {
        yesContinueListener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = HistoryAlertDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog!!.window?.setBackgroundDrawable(ColorDrawable(0))
        dialog!!.window?.setWindowAnimations(R.style.DialogAnimation)
        binding.noContinueBtn.setOnClickListener {
            noClearListener.invoke()
        }
        binding.yesContinueBtn.setOnClickListener {
            yesContinueListener.invoke()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
