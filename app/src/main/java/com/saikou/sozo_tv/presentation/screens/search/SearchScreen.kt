package com.saikou.sozo_tv.presentation.screens.search

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import androidx.activity.OnBackPressedCallback
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.SearchAdapter
import com.saikou.sozo_tv.data.extensions.ExtensionEngine
import com.saikou.sozo_tv.data.extensions.toSearchModel
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.databinding.SearchScreenBinding
import com.saikou.sozo_tv.domain.model.SearchModel
import com.saikou.sozo_tv.presentation.activities.PlayerActivity
import com.saikou.sozo_tv.presentation.viewmodel.SearchViewModel
import com.saikou.sozo_tv.utils.applyFocusedStyle
import com.saikou.sozo_tv.utils.resetStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.Locale

class SearchScreen : Fragment() {
    private var _binding: SearchScreenBinding? = null
    private val binding get() = _binding!!
    private lateinit var searchAdapter: SearchAdapter
    private val model: SearchViewModel by viewModel()
    private var searchJob: Job? = null
    private var lastSearchQuery = ""
    private val preference = PreferenceManager()
    private val VOICE_REQUEST_CODE = 2001

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SearchScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * The overlay is a full-screen sheet, but it used to be inert: not focusable and not
     * clickable, so while it was up the D-pad kept walking the on-screen keyboard hidden behind
     * it and BACK left the screen entirely with the recognizer still listening. It now takes
     * focus for as long as it is shown, hands focus back to the mic button when it goes away,
     * and BACK cancels recognition instead of leaving.
     */
    private fun showVoiceOverlay(show: Boolean) {
        val overlay = binding.voiceListeningOverlay.root
        overlay.visibility = if (show) View.VISIBLE else View.GONE
        cancelVoiceCallback.isEnabled = show
        if (show) {
            overlay.requestFocus()
        } else if (overlay.hasFocus() || !binding.root.hasFocus()) {
            binding.micBtn.requestFocus()
        }
    }

    private val cancelVoiceCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            stopVoiceRecognition()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, cancelVoiceCallback)
        setupRecyclerView()
        setupCustomKeyboard()
        setupSearchScopeToggle()
        initializeSearch()
        observeViewModel()
        setupTVFocusHandling()
        showInitialState()
        preventSystemKeyboard()
        setupSpeechRecognizer()
        binding.searchEdt.requestFocus()
        binding.seasonalBackground.setTheme(PreferenceManager().getSeasonalTheme())
        applyIncomingQuery()
    }

    /**
     * Runs a query handed in by another screen (AniList's "find in sources").
     *
     * The argument is REMOVED after use: this fragment is re-created whenever the
     * user navigates back to search, and a lingering argument would silently
     * re-run someone's old query instead of showing an empty search box.
     */
    private fun applyIncomingQuery() {
        val query = arguments?.getString(ARG_QUERY)?.trim().orEmpty()
        if (query.isEmpty()) return
        arguments?.remove(ARG_QUERY)

        binding.searchEdt.setText(query)
        binding.searchEdt.setSelection(query.length)
        performSearchImmediate(query)
    }

    private fun setupSpeechRecognizer() {
        val isTV = requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

        if (SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
            speechRecognizer?.setRecognitionListener(createRecognitionListener(isTV))

            binding.micBtn.setImageResource(R.drawable.ic_mic)
            binding.micBtn.setOnClickListener {
                if (isListening) {
                    stopVoiceRecognition()
                } else {
                    if (isTV) {
                        startVoiceRecognition()
                    } else {
                        checkAndStartVoiceRecognition()
                    }
                }
            }

            binding.micBtn.setOnLongClickListener {
                startAlternativeVoiceSearch()
                true
            }
        } else {
            binding.micBtn.setImageResource(R.drawable.ic_mic)
            binding.micBtn.setOnClickListener {
                startAlternativeVoiceSearch()
            }

            binding.micBtn.setOnLongClickListener {
                startAlternativeVoiceSearch()
                true
            }

            Log.w("SearchScreen", "SpeechRecognizer API not available, using activity intent")
        }
    }

    private fun createRecognitionListener(isTV: Boolean): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("SearchScreen", "Ready for speech")
                isListening = true
                requireActivity().runOnUiThread {
                    showVoiceOverlay(true)
                    binding.voiceListeningOverlay.listeningTxt.text = "Listening..."
                    binding.micBtn.setImageResource(R.drawable.ic_mic)
                }
            }

            override fun onBeginningOfSpeech() {
                Log.d("SearchScreen", "Beginning of speech")
                requireActivity().runOnUiThread {
                    binding.voiceListeningOverlay.listeningTxt.text = "Listening... Speak now"
                }
            }

            override fun onRmsChanged(rmsdB: Float) {
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d("SearchScreen", "End of speech")
                isListening = false
                requireActivity().runOnUiThread {
                    binding.micBtn.setImageResource(R.drawable.ic_mic)
                }
            }

            override fun onError(error: Int) {
                isListening = false
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown error"
                }

                Log.e("SearchScreen", "Speech recognition error: $errorMessage")

                requireActivity().runOnUiThread {
                    showVoiceOverlay(false)
                    binding.micBtn.setImageResource(R.drawable.ic_mic)

                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        if (isTV) {
                            binding.voiceListeningOverlay.listeningTxt.text =
                                "Microphone not available"
                            showVoiceOverlay(true)
                            binding.voiceListeningOverlay.root.postDelayed({
                                showVoiceOverlay(false)
                            }, 2000)
                        } else {
                            startAlternativeVoiceSearch()
                        }
                    } else if (error != SpeechRecognizer.ERROR_NO_MATCH &&
                        error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    ) {
                        binding.voiceListeningOverlay.listeningTxt.text = "Error: $errorMessage"
                        showVoiceOverlay(true)
                        binding.voiceListeningOverlay.root.postDelayed({
                            showVoiceOverlay(false)
                        }, 2000)
                    } else {
                        showVoiceOverlay(false)
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.firstOrNull() ?: ""

                Log.d("SearchScreen", "Speech results: $spokenText")

                requireActivity().runOnUiThread {
                    showVoiceOverlay(false)
                    binding.micBtn.setImageResource(R.drawable.ic_mic)

                    if (spokenText.isNotEmpty()) {
                        binding.searchEdt.setText(spokenText)
                        binding.searchEdt.setSelection(spokenText.length)
                        performSearchImmediate(spokenText)

                        binding.voiceListeningOverlay.listeningTxt.text =
                            "Searching for: $spokenText"
                        showVoiceOverlay(true)
                        binding.voiceListeningOverlay.root.postDelayed({
                            showVoiceOverlay(false)
                        }, 1500)
                    } else {
                        binding.voiceListeningOverlay.listeningTxt.text = "No speech detected"
                        showVoiceOverlay(true)
                        binding.voiceListeningOverlay.root.postDelayed({
                            showVoiceOverlay(false)
                        }, 1000)
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
            }
        }
    }

    private fun checkAndStartVoiceRecognition() {
        try {
            startVoiceRecognition()
        } catch (e: SecurityException) {
            Log.e("SearchScreen", "SecurityException: ${e.message}")
            startAlternativeVoiceSearch()
        }
    }

    private fun startVoiceRecognition() {
        try {
            val isTV =
                requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Say something to search...")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)

                if (isTV) {
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000)
                    putExtra(
                        RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                        1500
                    )
                }
            }

            speechRecognizer?.startListening(intent)

        } catch (e: Exception) {
            Log.e("SearchScreen", "Failed to start speech recognition: ${e.message}")
            binding.micBtn.setImageResource(R.drawable.ic_mic)
            showVoiceOverlay(false)

            startAlternativeVoiceSearch()
        }
    }

    private fun startAlternativeVoiceSearch() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Say something to search...")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            if (intent.resolveActivity(requireContext().packageManager) != null) {
                showVoiceOverlay(true)
                binding.voiceListeningOverlay.listeningTxt.text = "Starting voice search..."
                startActivityForResult(intent, VOICE_REQUEST_CODE)
            } else {
                Log.e("SearchScreen", "No speech recognition activity found")
                binding.voiceListeningOverlay.listeningTxt.text = "Voice search not available"
                showVoiceOverlay(true)
                binding.voiceListeningOverlay.root.postDelayed({
                    showVoiceOverlay(false)
                }, 2000)
            }
        } catch (e: Exception) {
            Log.e("SearchScreen", "Alternative voice search error: ${e.message}")
            binding.voiceListeningOverlay.listeningTxt.text = "Voice search error"
            showVoiceOverlay(true)
            binding.voiceListeningOverlay.root.postDelayed({
                showVoiceOverlay(false)
            }, 2000)
        }
    }

    private fun stopVoiceRecognition() {
        speechRecognizer?.stopListening()
        speechRecognizer?.cancel()
        isListening = false
        showVoiceOverlay(false)
        binding.micBtn.setImageResource(R.drawable.ic_mic)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == VOICE_REQUEST_CODE) {
            showVoiceOverlay(false)

            if (resultCode == Activity.RESULT_OK) {
                val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val spokenText = results?.firstOrNull() ?: ""

                if (spokenText.isNotEmpty()) {
                    binding.searchEdt.setText(spokenText)
                    binding.searchEdt.setSelection(spokenText.length)
                    performSearchImmediate(spokenText)
                    binding.voiceListeningOverlay.listeningTxt.text = "Searching for: $spokenText"
                    showVoiceOverlay(true)
                    binding.voiceListeningOverlay.root.postDelayed({
                        showVoiceOverlay(false)
                    }, 1500)
                }
            } else {
                binding.micBtn.setImageResource(R.drawable.ic_mic)
                if (resultCode == Activity.RESULT_CANCELED) {
                    binding.voiceListeningOverlay.listeningTxt.text = "Voice search cancelled"
                    showVoiceOverlay(true)
                    binding.voiceListeningOverlay.root.postDelayed({
                        showVoiceOverlay(false)
                    }, 1500)
                }
            }
        }
    }

    private fun setupTVFocusHandling() {
        binding.vgvSearch.isFocusable = true
        binding.vgvSearch.isFocusableInTouchMode = false
    }

    private fun showInitialState() {
        if (binding.searchEdt.text.toString().trim().isEmpty()) {
            clearSearchResults()
            binding.recommendationsTitle.text = getString(R.string.your_search_recommendations)
            binding.recommendationsTitle.visibility = View.VISIBLE
        }
    }

    private fun hideResultsGrid() {
        // Move focus back to the search field before hiding the grid; setting a currently-focused
        // view GONE strands D-pad focus and Android teleports it to an arbitrary first focusable.
        if (binding.vgvSearch.hasFocus()) binding.searchEdt.requestFocus()
        binding.vgvSearch.visibility = View.GONE
    }

    private fun clearSearchResults() {
        hideResultsGrid()
        binding.placeHolder.root.visibility = View.GONE
        searchAdapter.updateData(emptyList())
    }

    private fun observeViewModel() {
        model.searchResults.observe(viewLifecycleOwner) { movies ->
            Log.d("SearchScreen", "Search results: ${movies.size}")

            if (movies.isNotEmpty()) {
                binding.vgvSearch.visibility = View.VISIBLE
                searchAdapter.updateData(movies)
                binding.placeHolder.root.visibility = View.GONE
                binding.recommendationsTitle.visibility = View.VISIBLE
                binding.recommendationsTitle.text =
                    getString(R.string.search_results_for, binding.searchEdt.text.toString().trim())
            } else {
                hideResultsGrid()
                binding.placeHolder.root.visibility = View.VISIBLE
                binding.placeHolder.placeholderTxt.text = "No results found"
                binding.placeHolder.placeHolderImg.setImageResource(R.drawable.ic_place_holder_search)
            }
        }

        lifecycleScope.launch {
            model.errorData.observe(viewLifecycleOwner) { errorMessage ->
                hideResultsGrid()
                binding.placeHolder.root.visibility = View.VISIBLE
                binding.placeHolder.placeholderTxt.text = errorMessage
                binding.placeHolder.placeHolderImg.setImageResource(R.drawable.ic_network_error)
            }
        }
    }

    private fun setupRecyclerView() {
        searchAdapter = SearchAdapter()
        searchAdapter.setOnItemClickListener { searchModel ->
            Log.d("SearchScreen", "Item clicked: ${searchModel.title}")
            openOnSelectedProvider(searchModel)
        }
        binding.vgvSearch.adapter = searchAdapter
    }

    /**
     * A result without a provider-registry id is resolved against the currently selected source:
     * search the provider by title, take the best match's stable id, then open the existing
     * detail/player flow with it.
     */
    private fun openOnSelectedProvider(searchModel: SearchModel) {
        // A provider result already has a registry id — open it directly.
        searchModel.id?.let { regId ->
            launchPlayer(regId, searchModel.averageScore == 1)
            return
        }
        val title = searchModel.title?.trim().orEmpty()
        if (title.isEmpty()) return

        Toast.makeText(requireContext(), "Opening “$title”…", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val regId = withContext(Dispatchers.IO) {
                try {
                    ExtensionEngine.shared.search(null, title)
                        ?.items?.firstOrNull()?.toSearchModel()?.id
                } catch (e: Exception) {
                    Log.e("SearchScreen", "provider bridge failed: ${e.message}")
                    null
                }
            }
            if (!isAdded) return@launch
            if (regId != null && regId != -1) {
                launchPlayer(regId, false)
            } else {
                Toast.makeText(
                    requireContext(),
                    "“$title” not found on the selected source. Open Sources to pick a provider.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun launchPlayer(registryId: Int, isMovie: Boolean) {
        val intent = Intent(requireActivity(), PlayerActivity::class.java)
        intent.putExtra("model", registryId)
        intent.putExtra("isMovie", isMovie)
        requireActivity().startActivity(intent)
    }

    /** false = active source only (default), true = every installed source. */
    private var searchAllSources = false

    private fun setupSearchScopeToggle() {
        binding.searchScopeToggle.setOnClickListener {
            searchAllSources = !searchAllSources
            binding.searchScopeToggle.text =
                if (searchAllSources) "All sources" else "This source"
            // Re-run the text already on screen so flipping the scope shows its
            // effect immediately instead of waiting for the next keystroke.
            val current = lastSearchQuery.ifEmpty { binding.searchEdt.text?.toString().orEmpty() }
            if (current.trim().length >= 2) performSearchImmediate(current)
        }
    }

    private fun performSearchImmediate(query: String) {
        if (query.isNotEmpty()) {
            val q = query.trim()
            // Active provider by default; "All sources" fans out across every
            // installed one (bounded + failure-tolerant in ExtensionEngine).
            if (searchAllSources) model.searchAllSources(q) else model.searchAnime(q)
            searchAdapter.setQueryText(q)
            binding.recommendationsTitle.visibility = View.VISIBLE
            binding.recommendationsTitle.text = if (searchAllSources) {
                "All sources — \"$query\""
            } else {
                "Search Results for \"$query\""
            }
        }
    }


    private fun scheduleSearch(query: String) {
        searchJob?.cancel()

        if (query != lastSearchQuery && query.length >= 2) {
            searchJob = lifecycleScope.launch {
                delay(800)
                performSearchImmediate(query)
                lastSearchQuery = query
            }
        }
    }

    private fun cancelPendingSearch() {
        searchJob?.cancel()
        searchJob = null
        lastSearchQuery = ""
    }

    private fun initializeSearch() {
        binding.searchEdt.apply {
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val query = s.toString().trim()
                    if (query.isEmpty()) {
                        cancelPendingSearch()
                        showInitialState()
                    }
                }

                override fun afterTextChanged(s: Editable?) {}
            })

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
                    performSearchImmediate(query)
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun preventSystemKeyboard() {
        binding.searchEdt.apply {
            showSoftInputOnFocus = false
            setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    val imm =
                        requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(view.windowToken, 0)
                    view.applyFocusedStyle()
                } else {
                    view.resetStyle()
                }
            }
            setOnClickListener {
                val imm =
                    requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(it.windowToken, 0)
                it.requestFocus()
            }
        }
    }

    private fun setupCustomKeyboard() {
        binding.customKeyboard.setOnKeyClickListener { key ->
            val currentText = binding.searchEdt.text.toString()
            val cursorPosition = binding.searchEdt.selectionStart
            val newText = StringBuilder(currentText).insert(cursorPosition, key).toString()
            binding.searchEdt.setText(newText)
            binding.searchEdt.setSelection(cursorPosition + 1)
            if (newText.trim().isNotEmpty()) {
                scheduleSearch(newText.trim())
            }
        }

        binding.customKeyboard.setOnBackspaceClickListener {
            val currentText = binding.searchEdt.text.toString()
            val cursorPosition = binding.searchEdt.selectionStart
            if (cursorPosition > 0) {
                val newText = StringBuilder(currentText).deleteCharAt(cursorPosition - 1).toString()
                binding.searchEdt.setText(newText)
                binding.searchEdt.setSelection(cursorPosition - 1)
                if (newText.trim().isEmpty()) {
                    cancelPendingSearch()
                    showInitialState()
                } else {
                    scheduleSearch(newText.trim())
                }
            }
        }

        binding.customKeyboard.setOnClearClickListener {
            binding.searchEdt.setText("")
            binding.searchEdt.setSelection(0)
            cancelPendingSearch()
            showInitialState()
        }
    }

    override fun onPause() {
        super.onPause()
        stopVoiceRecognition()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopVoiceRecognition()
        speechRecognizer?.destroy()
        speechRecognizer = null
        cancelPendingSearch()
        _binding = null
    }

    companion object {
        /** Fragment argument carrying a pre-filled search query. */
        const val ARG_QUERY = "query"
    }

}