package com.saikou.sozo_tv.presentation.screens.source

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
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
import com.saikou.sozo_tv.data.repository.AnilistSourceRegistry
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
    private val anilistSourceRegistry: AnilistSourceRegistry by inject()
    private lateinit var adapter: SourceAdapter

    /** Live reference to the currently-bound header (null while it is scrolled out / recycled). */
    private var header: SourceHeaderViews? = null
    private var searchWatcher: TextWatcher? = null

    /** The filter chips currently in the header, keyed by the repo/mode they select (null = All). */
    private val repoChips = mutableListOf<Pair<String?, TextView>>()

    // --- Persisted header state (re-applied whenever the header re-binds) ---
    private var currentGroup: String = ExtGroup.ANIYOMI
    private var searchText: String = ""
    private var selectedRepo: String? = null   // null = all repos
    private var selectedMode: String? = null   // server tab: null = all modes
    private var statusText: String? = null
    private var progressVisible: Boolean = false
    private var emptyText: String? = null
    private var loadError: String? = null
    private var pendingScrollToSelected: Boolean = true
    private var countText: String? = null
    private var updating: Boolean = false

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

        adapter.anilistSources = anilistSourceRegistry.all()

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

        v.btnUpdateSources.setOnClickListener { updateSources() }

        v.btnTabServer.setOnClickListener { switchTab(ExtGroup.SERVER) }
        v.btnTabAniyomi.setOnClickListener { switchTab(ExtGroup.ANIYOMI) }
        v.btnTabCloudstream.setOnClickListener { switchTab(ExtGroup.CLOUDSTREAM) }

        // Provider search field — set text before (re)attaching the watcher so it doesn't self-trigger.
        searchWatcher?.let { v.etSearchProvider.removeTextChangedListener(it) }
        v.etSearchProvider.setText(searchText)
        v.etSearchProvider.setSelection(searchText.length)
        searchWatcher = afterTextChanged {
            searchText = it
            adapter.filter(it)
            refreshCount()
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

    /**
     * Build the header filter chips for the current tab. On the Sozo (server) tab these filter
     * by delivery mode (All / Cloud / Hybrid / Local); on the extension tabs they filter by the
     * installed repo each provider came from.
     *
     * The chips are only torn down when the set of filters itself changes. Rebuilding them on
     * every pick destroyed the very chip the user had just pressed, and the D-pad landed back at
     * the top of the screen; a load finishing under a focused chip did the same.
     */
    private fun renderRepoChips() {
        val container = header?.repoFilterContainer ?: return
        val server = currentGroup == ExtGroup.SERVER
        val values = if (server) adapter.modes() else adapter.repos()
        // A single filter is no filter at all.
        if (values.size < 2) {
            container.removeAllViews()
            repoChips.clear()
            container.visibility = View.GONE
            // Drop any filter along with the chips: rows hidden by a control that is no longer on
            // screen read as missing providers.
            if (server) selectMode(null) else selectRepo(null)
            return
        }
        container.visibility = View.VISIBLE

        val keys = listOf<String?>(null) + values
        val live = repoChips.firstOrNull()?.second?.parent === container
        if (live && repoChips.map { it.first } == keys) {
            refreshChipSelection()
            return
        }
        container.removeAllViews()
        repoChips.clear()
        keys.forEach { key ->
            val label = when {
                key == null -> "All"
                server -> modeLabel(key)
                else -> key
            }
            addChip(container, key, label) { if (server) selectMode(key) else selectRepo(key) }
        }
        refreshChipSelection()
    }

    /** Repaint the chips in place so the focused one survives a pick. */
    private fun refreshChipSelection() {
        val current = if (currentGroup == ExtGroup.SERVER) selectedMode else selectedRepo
        repoChips.forEach { (key, chip) -> stylePill(chip, key == current) }
    }

    private fun selectRepo(repo: String?) {
        if (selectedRepo == repo) return
        selectedRepo = repo
        adapter.setRepoFilter(repo)
        refreshCount()
        refreshEmptyState()
        refreshChipSelection()
    }

    private fun selectMode(mode: String?) {
        if (selectedMode == mode) return
        selectedMode = mode
        adapter.setModeFilter(mode)
        refreshCount()
        refreshEmptyState()
        refreshChipSelection()
    }

    /** Friendly label for a server provider's delivery mode. */
    private fun modeLabel(mode: String): String = when (mode) {
        "server" -> "Cloud"
        "hybrid" -> "Hybrid"
        "client" -> "Local"
        else -> mode.replaceFirstChar { it.uppercase() }
    }

    private fun addChip(
        container: LinearLayout,
        key: String?,
        label: String,
        onClick: () -> Unit,
    ) {
        val chip = TextView(requireContext()).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.tv_text_label))
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            setPadding(dp(18), dp(8), dp(18), dp(8))
            setOnClickListener { onClick() }
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { marginEnd = dp(7) }
        container.addView(chip, lp)
        repoChips += key to chip
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun switchTab(group: String) {
        if (group == currentGroup) return
        currentGroup = group
        searchText = ""
        selectedRepo = null
        selectedMode = null
        header?.let { v ->
            searchWatcher?.let { v.etSearchProvider.removeTextChangedListener(it) }
            v.etSearchProvider.setText("")
            searchWatcher?.let { v.etSearchProvider.addTextChangedListener(it) }
        }
        adapter.filter("")
        adapter.setRepoFilter(null)
        adapter.setModeFilter(null)
        // Drop the previous tab's rows and its "Setting up sources…" status now: leaving them on
        // screen while the new group loads shows e.g. CloudStream providers under the Sozo tab.
        adapter.submit(emptyList(), engine.getActiveProvider())
        countText = null
        statusText = null
        emptyText = null
        applyTabUi()
        renderRepoChips()
        loadProviders()
    }

    private fun applyTabUi() {
        val v = header ?: return
        stylePill(v.btnTabServer, currentGroup == ExtGroup.SERVER)
        stylePill(v.btnTabAniyomi, currentGroup == ExtGroup.ANIYOMI)
        stylePill(v.btnTabCloudstream, currentGroup == ExtGroup.CLOUDSTREAM)
    }

    /** Selected = white pill with dark text; unselected = outlined with light text. Both
     *  backgrounds carry the focus stroke, so this must not be swapped for a flat colour. */
    private fun stylePill(pill: TextView, selected: Boolean) {
        pill.setBackgroundResource(
            if (selected) R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected
        )
        pill.setTextColor((if (selected) 0xFF111417 else 0xFFCCCCCC).toInt())
    }

    /**
     * Load the providers of the current tab.
     *
     * A load can run for a long time (the first visit to a group also downloads its curated
     * default repos), so the user can switch tabs while one is in flight. Every load is therefore
     * pinned to the [group] it started with and refuses to touch the UI if the tab moved on
     * underneath it. Without that, the slower CloudStream load lands last and fills the list with
     * CloudStream providers while the Sozo tab is the one highlighted. A superseded load is not
     * cancelled — it may be part-way through downloading a repo, which we want to finish.
     */
    private fun loadProviders() {
        val group = currentGroup
        progressVisible = true
        loadError = null
        applyHeaderState()
        viewLifecycleOwner.lifecycleScope.launch {
            var result = withContext(Dispatchers.IO) { runCatching { engine.providers(group) } }
            // The manual shortcode installer was removed, so auto-install the curated
            // default repos. We install any default repo that isn't already present (once
            // per session) — this seeds a fresh install AND adds newly-shipped defaults
            // (e.g. CSX) for users who already have other providers.
            if (bootstrappedGroups.add(group)) {
                val installed = withContext(Dispatchers.IO) {
                    runCatching { engine.listRepos(group).map { it.url }.toSet() }
                        .getOrDefault(emptySet())
                }
                val missing = ShortcodeRegistry.entries(group)
                    .filter { it.url !in installed }
                if (missing.isNotEmpty()) {
                    if (group == currentGroup) {
                        statusText = "Setting up sources… please wait"
                        applyHeaderState()
                    }
                    withContext(Dispatchers.IO) {
                        missing.forEachIndexed { index, entry ->
                            runCatching {
                                engine.addRepo(group, entry.url) { current, total ->
                                    binding.root.post {
                                        if (_binding == null || group != currentGroup) return@post
                                        statusText = "Setting up ${entry.name} " +
                                            "(${index + 1}/${missing.size})" +
                                            if (total > 0) " · $current/$total" else "…"
                                        header?.tvStatus?.let { it.text = statusText; it.isVisible = true }
                                    }
                                }
                            }
                        }
                    }
                    result = withContext(Dispatchers.IO) { runCatching { engine.providers(group) } }
                    if (group == currentGroup) statusText = null
                }
            }
            // The tab moved on while we were loading — this result belongs to a tab the user
            // is no longer looking at, and the newer load owns the UI now.
            if (group != currentGroup) return@launch
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
            refreshCount()
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
        v.tvProviderCount.text = countText.orEmpty()
        v.tvProviderCount.isVisible = !countText.isNullOrEmpty()
        // Dimmed rather than disabled: a disabled View loses focus, so starting an update threw the
        // D-pad off the button the user had just pressed. updateSources() ignores the repeat press.
        val busy = updating || progressVisible
        v.btnUpdateSources.alpha = if (busy) 0.5f else 1f
        v.btnUpdateSources.text =
            if (updating) getString(R.string.sources_updating) else getString(R.string.sources_update)
    }

    /**
     * Pulls fresh plugin versions for the tab in view.
     *
     * Scoped to one engine: refreshing every group would re-download repos the
     * user isn't looking at and leave the visible list stale anyway.
     */
    private fun updateSources() {
        if (updating || progressVisible) return
        val group = currentGroup
        updating = true
        statusText = getString(R.string.sources_checking_updates)
        applyHeaderState()
        viewLifecycleOwner.lifecycleScope.launch {
            val updated = withContext(Dispatchers.IO) {
                runCatching {
                    engine.checkUpdates(group) { current, total ->
                        binding.root.post {
                            if (_binding == null || group != currentGroup) return@post
                            statusText = getString(R.string.sources_updating_progress, current, total)
                            header?.tvStatus?.let { it.text = statusText; it.isVisible = true }
                        }
                    }
                }
            }
            updating = false
            if (group != currentGroup) return@launch
            statusText = null
            updated.onFailure {
                toast(getString(R.string.sources_update_failed, it.message ?: "unknown error"))
            }.onSuccess {
                toast(
                    if (it > 0) resources.getQuantityString(R.plurals.sources_updated, it, it)
                    else getString(R.string.sources_up_to_date)
                )
            }
            applyHeaderState()
            loadProviders()
        }
    }

    /** "42 sources · 3 repos" next to the title, so the tab's scale is visible at a glance. */
    private fun refreshCount() {
        val providers = adapter.providerCount()
        val repos = adapter.repos().size
        countText = when {
            providers <= 0 -> null
            repos > 1 -> resources.getQuantityString(R.plurals.source_count, providers, providers) +
                " · " + resources.getQuantityString(R.plurals.repo_count, repos, repos)
            else -> resources.getQuantityString(R.plurals.source_count, providers, providers)
        }
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
            // Going UP into the header, hand back to the default search: the header holds several
            // controls stacked vertically and forcing focus onto the whole item skipped straight
            // past the chips and the search field to the button at the very top.
            if (nextPos <= 0) return super.onInterceptFocusSearch(focused, direction)
            if (nextPos >= itemCount) return focused
            return findViewByPosition(nextPos) ?: run { scrollToPosition(nextPos); focused }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        header = null
        searchWatcher = null
        repoChips.clear()
        _binding = null
    }
}
