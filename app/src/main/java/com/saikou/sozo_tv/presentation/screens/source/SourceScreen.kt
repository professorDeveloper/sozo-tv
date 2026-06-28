package com.saikou.sozo_tv.presentation.screens.source

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.data.extensions.ExtGroup
import com.saikou.sozo_tv.data.extensions.ExtProvider
import com.saikou.sozo_tv.data.extensions.ExtensionEngine
import com.saikou.sozo_tv.data.extensions.ShortcodeRegistry
import com.saikou.sozo_tv.databinding.SourceScreenBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

/**
 * Extension source manager (TV-focusable).
 *
 *  - Two tabs: Aniyomi (default) / CloudStream — both engines usable.
 *  - Search the installed providers (field pinned at the top of the header).
 *  - Pick the active provider that Home / Search / Categories pull from.
 *
 * The whole screen is one [androidx.recyclerview.widget.RecyclerView] (header item + provider
 * rows) so the header scrolls up with the list and D-pad focus moves freely between the search
 * field and the rows. Header state is kept in the fragment so it survives the header being
 * recycled while scrolling.
 */
class SourceScreen : Fragment() {

    private var _binding: SourceScreenBinding? = null
    private val binding get() = _binding!!

    private val engine: ExtensionEngine by inject()
    private lateinit var adapter: SourceAdapter

    /** Live reference to the currently-bound header (null while it is scrolled out / recycled). */
    private var header: SourceHeaderViews? = null
    private var searchWatcher: TextWatcher? = null

    // --- Persisted header state (re-applied whenever the header re-binds) ---
    private var currentGroup: String = ExtGroup.ANIYOMI
    private var searchText: String = ""
    private var selectedRepo: String? = null   // null = all repos
    private var statusText: String? = null
    private var progressVisible: Boolean = false
    private var emptyText: String? = null
    private var loadError: String? = null
    private var pendingScrollToSelected: Boolean = true

    /** Groups whose curated default repos we've already tried to auto-install this session. */
    private val bootstrappedGroups = mutableSetOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = SourceScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentGroup = engine.getActiveGroup()

        adapter = SourceAdapter(
            onBindHeader = { views -> bindHeader(views) },
            onProviderClick = { provider -> onProviderPicked(provider) },
            onProviderLongClick = { provider -> openSettings(provider) },
        )

        binding.screenRv.apply {
            layoutManager = FocusGuardLayoutManager(requireContext())
            adapter = this@SourceScreen.adapter
            itemAnimator = null
            setHasFixedSize(false)
        }

        loadProviders()
    }

    /** Wire listeners + restore persisted state every time the header (re)binds. */
    private fun bindHeader(v: SourceHeaderViews) {
        header = v

        v.btnTabAniyomi.setOnClickListener { switchTab(ExtGroup.ANIYOMI) }
        v.btnTabCloudstream.setOnClickListener { switchTab(ExtGroup.CLOUDSTREAM) }

        // Provider search field — set text before (re)attaching the watcher so it doesn't self-trigger.
        searchWatcher?.let { v.etSearchProvider.removeTextChangedListener(it) }
        v.etSearchProvider.setText(searchText)
        v.etSearchProvider.setSelection(searchText.length)
        searchWatcher = afterTextChanged {
            searchText = it
            adapter.filter(it)
            refreshEmptyState()
        }
        v.etSearchProvider.addTextChangedListener(searchWatcher)

        v.etSearchProvider.onFocusChangeListener =
            View.OnFocusChangeListener { fv, hasFocus -> if (!hasFocus) hideIme(fv) }
        // On TV the keyboard isn't reliably raised on focus; raise it on click (D-pad center).
        v.etSearchProvider.setOnClickListener { showIme(it) }

        applyTabUi()
        renderRepoChips()
        applyHeaderState()
    }

    /** Build the repo filter chips ("All" + one per installed repo) for the current tab. */
    private fun renderRepoChips() {
        val container = header?.repoFilterContainer ?: return
        container.removeAllViews()
        val repos = adapter.repos()
        // Only worth showing when there's more than one repo to choose between.
        if (repos.size < 2) {
            container.visibility = View.GONE
            return
        }
        container.visibility = View.VISIBLE
        addRepoChip(container, "All", null)
        repos.forEach { addRepoChip(container, it, it) }
    }

    private fun addRepoChip(container: LinearLayout, label: String, repo: String?) {
        val selected = selectedRepo == repo
        val chip = TextView(requireContext()).apply {
            text = label
            textSize = 13f
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            setPadding(34, 16, 34, 16)
            setBackgroundResource(
                if (selected) R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected
            )
            setTextColor((if (selected) 0xFF111417 else 0xFFCCCCCC).toInt())
            setOnClickListener {
                if (selectedRepo != repo) {
                    selectedRepo = repo
                    adapter.setRepoFilter(repo)
                    refreshEmptyState()
                    renderRepoChips()
                }
            }
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { marginEnd = 14 }
        container.addView(chip, lp)
    }

    private fun switchTab(group: String) {
        if (group == currentGroup) return
        currentGroup = group
        searchText = ""
        selectedRepo = null
        header?.let { v ->
            searchWatcher?.let { v.etSearchProvider.removeTextChangedListener(it) }
            v.etSearchProvider.setText("")
            searchWatcher?.let { v.etSearchProvider.addTextChangedListener(it) }
        }
        adapter.filter("")
        adapter.setRepoFilter(null)
        applyTabUi()
        renderRepoChips()
        loadProviders()
    }

    private fun applyTabUi() {
        val v = header ?: return
        styleTab(v.btnTabAniyomi, currentGroup == ExtGroup.ANIYOMI)
        styleTab(v.btnTabCloudstream, currentGroup == ExtGroup.CLOUDSTREAM)
    }

    /** Selected tab = white pill with dark text; unselected = outlined with light text. */
    private fun styleTab(tab: TextView, selected: Boolean) {
        tab.setBackgroundResource(
            if (selected) R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected
        )
        tab.setTextColor((if (selected) 0xFF111417 else 0xFFCCCCCC).toInt())
    }

    private fun loadProviders() {
        progressVisible = true
        loadError = null
        applyHeaderState()
        viewLifecycleOwner.lifecycleScope.launch {
            var result = withContext(Dispatchers.IO) { runCatching { engine.providers(currentGroup) } }
            // The manual shortcode installer was removed, so auto-install the curated
            // default repos. We install any default repo that isn't already present (once
            // per session) — this seeds a fresh install AND adds newly-shipped defaults
            // (e.g. CSX) for users who already have other providers.
            if (bootstrappedGroups.add(currentGroup)) {
                val installed = withContext(Dispatchers.IO) {
                    runCatching { engine.listRepos(currentGroup).map { it.url }.toSet() }
                        .getOrDefault(emptySet())
                }
                val missing = ShortcodeRegistry.entries(currentGroup)
                    .filter { it.url !in installed }
                if (missing.isNotEmpty()) {
                    statusText = "Setting up sources… please wait"
                    applyHeaderState()
                    withContext(Dispatchers.IO) {
                        missing.forEachIndexed { index, entry ->
                            runCatching {
                                engine.addRepo(currentGroup, entry.url) { current, total ->
                                    binding.root.post {
                                        if (_binding == null) return@post
                                        statusText = "Setting up ${entry.name} " +
                                            "(${index + 1}/${missing.size})" +
                                            if (total > 0) " · $current/$total" else "…"
                                        header?.tvStatus?.let { it.text = statusText; it.isVisible = true }
                                    }
                                }
                            }
                        }
                    }
                    result = withContext(Dispatchers.IO) { runCatching { engine.providers(currentGroup) } }
                    statusText = null
                }
            }
            // First run: activate the first provider if none is active yet, so Home/Search work
            // immediately without the user having to pick one manually.
            if (engine.getActiveProvider() == null) {
                result.getOrNull()?.firstOrNull()?.let {
                    engine.setActiveProvider(it.id, it.group, it.name)
                }
            }
            progressVisible = false
            loadError = result.exceptionOrNull()?.message
            adapter.submit(result.getOrDefault(emptyList()), engine.getActiveProvider())
            renderRepoChips()
            refreshEmptyState()
            applyHeaderState()
            scrollToSelectedIfPending()
        }
    }

    /**
     * On first open, make sure the top of the list is visible. The active provider is
     * already pinned to the top, so we DON'T steal focus onto it (the user navigates down
     * into the list themselves).
     */
    private fun scrollToSelectedIfPending() {
        if (!pendingScrollToSelected) return
        pendingScrollToSelected = false
        binding.screenRv.post {
            if (_binding == null) return@post
            (binding.screenRv.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(0, 0)
        }
    }

    private fun openSettings(provider: ExtProvider): Boolean {
        if (!provider.isAniyomi) return false
        findNavController().navigate(
            R.id.action_source_to_aniyomi_settings,
            bundleOf(AniyomiSourceSettingsFragment.ARG_PROVIDER to provider.id),
        )
        return true
    }

    private fun onProviderPicked(provider: ExtProvider) {
        engine.setActiveProvider(provider.id, provider.group, provider.name)
        // The episode screen / series player read the active source via SourceManager
        // (LocalData.SOURCE); the sentinel routes them to the ExtensionParser.
        com.saikou.sozo_tv.data.local.pref.PreferenceManager()
            .putString(com.saikou.sozo_tv.utils.LocalData.SOURCE, com.saikou.sozo_tv.parser.sources.AnimeSources.EXTENSION)
        adapter.setSelected(provider.id)
        toast("Active source: ${provider.name}")
    }

    /** Show the empty/no-results message under the search field when there are no rows. */
    private fun refreshEmptyState() {
        emptyText = when {
            adapter.providerCount() > 0 -> null
            loadError != null -> "Couldn't load providers: $loadError"
            searchText.isBlank() -> "No providers available."
            else -> "No providers match “$searchText”."
        }
        applyHeaderState()
    }

    /** Push the persisted header state onto the live header views (if currently bound). */
    private fun applyHeaderState() {
        val v = header ?: return
        v.tvStatus.text = statusText.orEmpty()
        v.tvStatus.isVisible = !statusText.isNullOrEmpty()
        v.progressBar.isVisible = progressVisible
        v.tvEmpty.text = emptyText.orEmpty()
        v.tvEmpty.isVisible = !emptyText.isNullOrEmpty()
    }

    private fun afterTextChanged(cb: (String) -> Unit): TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
        override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) {
            cb(s?.toString().orEmpty())
        }
    }

    private fun toast(msg: String) {
        if (isAdded) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun hideIme(view: View) {
        val imm = requireContext()
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun showIme(view: View) {
        view.requestFocus()
        val imm = requireContext()
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private class FocusGuardLayoutManager(context: Context) : LinearLayoutManager(context) {
        // Pre-lay out one extra screenful of rows in each direction so the next/prev
        // row is already attached when D-pad focus moves to it. Without this, fast
        // scrolling leaves focus stuck on the old row (findViewByPosition == null)
        // while the list scrolls underneath it — the reported TV focus lag.
        override fun calculateExtraLayoutSpace(state: RecyclerView.State, extraLayoutSpace: IntArray) {
            val extra = height
            extraLayoutSpace[0] = extra
            extraLayoutSpace[1] = extra
        }

        override fun onInterceptFocusSearch(focused: View, direction: Int): View? {
            if (direction != View.FOCUS_DOWN && direction != View.FOCUS_UP) {
                return super.onInterceptFocusSearch(focused, direction)
            }
            val current = findContainingItemView(focused)
                ?: return super.onInterceptFocusSearch(focused, direction)
            val curPos = getPosition(current)
            if (curPos == RecyclerView.NO_POSITION || curPos == 0) {
                return super.onInterceptFocusSearch(focused, direction)
            }
            val nextPos = if (direction == View.FOCUS_DOWN) curPos + 1 else curPos - 1
            if (nextPos < 0 || nextPos >= itemCount) return focused
            return findViewByPosition(nextPos) ?: run { scrollToPosition(nextPos); focused }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        header = null
        searchWatcher = null
        _binding = null
    }
}
