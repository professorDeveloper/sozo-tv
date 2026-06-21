package com.saikou.sozo_tv.presentation.screens.source

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
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
 *  - Install repos by **shortcode** (curated suggestion chips + free-text field),
 *    resolved to a repo URL via [ShortcodeRegistry].
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
    private var shortcodeWatcher: TextWatcher? = null
    private var searchWatcher: TextWatcher? = null

    // --- Persisted header state (re-applied whenever the header re-binds) ---
    private var currentGroup: String = ExtGroup.ANIYOMI
    private var shortcodeText: String = ""
    private var searchText: String = ""
    private var statusText: String? = null
    private var progressVisible: Boolean = false
    private var emptyText: String? = null
    private var loadError: String? = null
    private var pendingScrollToSelected: Boolean = true

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
        )

        binding.screenRv.apply {
            layoutManager = LinearLayoutManager(requireContext())
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

        // Shortcode field — set text before (re)attaching the watcher so it doesn't self-trigger.
        shortcodeWatcher?.let { v.etShortcode.removeTextChangedListener(it) }
        v.etShortcode.setText(shortcodeText)
        v.etShortcode.setSelection(shortcodeText.length)
        shortcodeWatcher = afterTextChanged { shortcodeText = it }
        v.etShortcode.addTextChangedListener(shortcodeWatcher)

        v.btnInstall.setOnClickListener {
            val code = shortcodeText.trim()
            if (code.isEmpty()) toast("Enter a shortcode") else install(code)
        }

        // Provider search field.
        searchWatcher?.let { v.etSearchProvider.removeTextChangedListener(it) }
        v.etSearchProvider.setText(searchText)
        v.etSearchProvider.setSelection(searchText.length)
        searchWatcher = afterTextChanged {
            searchText = it
            adapter.filter(it)
            refreshEmptyState()
        }
        v.etSearchProvider.addTextChangedListener(searchWatcher)

        applyTabUi()
        renderChips()
        applyHeaderState()
    }

    private fun switchTab(group: String) {
        if (group == currentGroup) return
        currentGroup = group
        searchText = ""
        header?.let { v ->
            searchWatcher?.let { v.etSearchProvider.removeTextChangedListener(it) }
            v.etSearchProvider.setText("")
            searchWatcher?.let { v.etSearchProvider.addTextChangedListener(it) }
        }
        adapter.filter("")
        applyTabUi()
        renderChips()
        loadProviders()
    }

    private fun applyTabUi() {
        val v = header ?: return
        val aniyomiActive = currentGroup == ExtGroup.ANIYOMI
        styleTab(v.btnTabAniyomi, aniyomiActive)
        styleTab(v.btnTabCloudstream, !aniyomiActive)
    }

    /** Selected tab = white pill with dark text; unselected = outlined with light text. */
    private fun styleTab(tab: TextView, selected: Boolean) {
        tab.setBackgroundResource(
            if (selected) R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected
        )
        tab.setTextColor((if (selected) 0xFF111417 else 0xFFCCCCCC).toInt())
    }

    /** Curated shortcode suggestion chips for the active group. */
    private fun renderChips() {
        val container = header?.chipContainer ?: return
        container.removeAllViews()
        for (entry in ShortcodeRegistry.entries(currentGroup)) {
            val chip = TextView(requireContext()).apply {
                text = "${entry.name}  (${entry.code})"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 13f
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.bg_tab_unselected)
                isFocusable = true
                isClickable = true
                setPadding(36, 18, 36, 18)
                setOnClickListener { install(entry.code) }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = 18 }
            container.addView(chip, lp)
        }
    }

    private fun install(code: String) {
        val url = ShortcodeRegistry.resolve(currentGroup, code)
        if (url == null) {
            toast("Unknown shortcode: $code")
            return
        }
        progressVisible = true
        statusText = "Installing $code…"
        applyHeaderState()
        viewLifecycleOwner.lifecycleScope.launch {
            val count = try {
                engine.addRepo(currentGroup, url) { current, total ->
                    binding.root.post {
                        if (_binding != null) {
                            statusText = "Installing $code… $current / $total"
                            header?.tvStatus?.let { it.text = statusText; it.isVisible = true }
                        }
                    }
                }
            } catch (e: Exception) {
                -1
            }
            progressVisible = false
            if (count > 0) {
                statusText = "Installed $count source(s) from $code"
                shortcodeText = ""
                header?.let { v ->
                    shortcodeWatcher?.let { v.etShortcode.removeTextChangedListener(it) }
                    v.etShortcode.setText("")
                    shortcodeWatcher?.let { v.etShortcode.addTextChangedListener(it) }
                }
                loadProviders()
            } else {
                statusText = "Nothing installed for $code (check the repo)"
            }
            applyHeaderState()
        }
    }

    private fun loadProviders() {
        progressVisible = true
        loadError = null
        applyHeaderState()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { engine.providers(currentGroup) } }
            progressVisible = false
            loadError = result.exceptionOrNull()?.message
            adapter.submit(result.getOrDefault(emptyList()), engine.getActiveProvider())
            refreshEmptyState()
            applyHeaderState()
            scrollToSelectedIfPending()
        }
    }

    /** On first open, bring the already-selected provider into view (and focus it). */
    private fun scrollToSelectedIfPending() {
        if (!pendingScrollToSelected) return
        val pos = adapter.selectedAdapterPosition()
        if (pos < 0) return
        pendingScrollToSelected = false
        binding.screenRv.post {
            if (_binding == null) return@post
            (binding.screenRv.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(pos, 0)
            binding.screenRv.post {
                if (_binding == null) return@post
                binding.screenRv.findViewHolderForAdapterPosition(pos)?.itemView?.requestFocus()
            }
        }
    }

    private fun onProviderPicked(provider: ExtProvider) {
        engine.setActiveProvider(provider.id, provider.group)
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
            searchText.isBlank() -> "No providers yet. Install a shortcode above."
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

    override fun onDestroyView() {
        super.onDestroyView()
        header = null
        shortcodeWatcher = null
        searchWatcher = null
        _binding = null
    }
}
