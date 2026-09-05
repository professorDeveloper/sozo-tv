package com.saikou.sozo_tv.engine.link

import com.saikou.sozo_tv.data.extensions.ExtGroup
import com.saikou.sozo_tv.data.extensions.ShortcodeRegistry
import java.util.Locale
import java.security.SecureRandom

/**
 * The rules behind "add a source from your phone", with no Android in them.
 *
 * A television is the wrong device to type a repo URL into. The manual shortcode installer
 * was removed from the Sources screen for exactly that reason, which left the TV able to
 * install only the four curated repos compiled into [ShortcodeRegistry] — a viewer with a
 * repo of their own had no way in at all. The phone in their hand is the right keyboard, so
 * the TV opens a small server on the local network, shows a QR code, and the phone posts the
 * URL to it.
 *
 * Everything that can be got wrong is here rather than in the server or the screen: what a
 * pairing code looks like, what counts as the same code, what a pasted URL has to be turned
 * into before it will install, and which engine it belongs to. All of it is pure, so all of
 * it has a test.
 */
object ExtensionLinkRules {

    /**
     * Pairing-code alphabet, with `I`, `O`, `0` and `1` left out.
     *
     * The code is read off a television across a room and typed into a phone. Those four
     * characters are the ones that get read wrong at that distance, and a mistyped code is
     * indistinguishable from a hostile one — the server refuses both.
     */
    const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    const val CODE_LENGTH = 6

    /** Preferred port. Short enough to type by hand when a camera is not to hand. */
    const val PREFERRED_PORT = 8787

    fun newCode(random: java.util.Random = SecureRandom()): String =
        buildString(CODE_LENGTH) {
            repeat(CODE_LENGTH) { append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]) }
        }

    /**
     * Whether [given] is the code the TV is showing.
     *
     * Case- and space-insensitive because the phone keyboard capitalises and the viewer
     * copies what they see, spaces and all. Hyphens too — a six-character code reads more
     * easily on screen as `ABC-DEF` and people type back what they read.
     */
    fun codeMatches(expected: String, given: String?): Boolean {
        if (given == null) return false
        val a = expected.filter { it.isLetterOrDigit() }.uppercase(Locale.ROOT)
        val b = given.filter { it.isLetterOrDigit() }.uppercase(Locale.ROOT)
        return a.isNotEmpty() && a == b
    }

    /**
     * Turns whatever the viewer pasted into a URL the repo installer can actually fetch, or
     * null when there is nothing sane to make of it.
     *
     * The cases are not hypothetical — they are what ends up on a phone clipboard:
     *
     *  * **A GitHub page URL.** Copying a repo.json from the browser gives
     *    `github.com/u/r/blob/builds/repo.json`, which serves an HTML page. The installer
     *    fetches it, parses no plugins, and reports a repo that added nothing — the exact
     *    failure that reads as "the app is broken". Rewritten to `raw.githubusercontent.com`.
     *  * **A shortcode.** `phisher` is easier to say out loud than any URL, and the curated
     *    list already maps them.
     *  * **A bare host.** People drop the scheme. `https` is assumed, never `http`.
     *  * **Quotes, angle brackets and stray whitespace** from a chat app or a code block.
     *  * **A non-web scheme.** `file:`, `data:` and `javascript:` are refused outright. This
     *    endpoint hands its argument to code that downloads and loads a plugin, so the set
     *    of accepted schemes is a closed list, not a blocklist.
     */
    fun normalizeRepoUrl(input: String?, group: String? = null): String? {
        var s = (input ?: "").trim()
        if (s.isEmpty()) return null
        s = s.trim('"', '\'', '<', '>', '`')
            .filterNot { it == '\n' || it == '\r' || it == '\t' || it == ' ' }
        if (s.isEmpty()) return null

        // A shortcode from the curated list, in whichever group was asked for (or either).
        val groups = if (group != null) listOf(group) else listOf(ExtGroup.CLOUDSTREAM, ExtGroup.ANIYOMI)
        for (g in groups) {
            ShortcodeRegistry.resolve(g, s)?.let { return it }
        }

        if (!s.contains("://")) {
            // Bare host, and only if it looks like one — otherwise a typo becomes a URL.
            if (!s.contains('.') || s.startsWith('.')) return null
            s = "https://$s"
        }

        val lower = s.lowercase(Locale.ROOT)
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null

        return rawifyGithub(s)
    }

    /**
     * `github.com/u/r/blob/<ref>/path` and `.../raw/<ref>/path` both serve a web page, not the
     * file. Only the `raw.githubusercontent.com` form is fetchable.
     */
    fun rawifyGithub(url: String): String {
        val marker = "github.com/"
        val at = url.indexOf(marker, ignoreCase = true)
        if (at < 0) return url
        val path = url.substring(at + marker.length)
        val parts = path.split('/')
        if (parts.size < 5) return url
        val kind = parts[2]
        if (!kind.equals("blob", true) && !kind.equals("raw", true)) return url
        val owner = parts[0]
        val repo = parts[1]
        val rest = parts.drop(3).joinToString("/")
        if (owner.isEmpty() || repo.isEmpty() || rest.isEmpty()) return url
        return "https://raw.githubusercontent.com/$owner/$repo/$rest"
    }

    /**
     * Which engine a repo URL belongs to, by the file it points at.
     *
     * Aniyomi publishes `index.min.json`; CloudStream publishes a `repo.json` — but not
     * always under that name (`Netflix.json`, `CS.json` are both in the curated list), so
     * CloudStream is the fallback rather than a second pattern to match. Null only when the
     * URL says nothing either way, and the caller then asks the viewer.
     */
    fun guessGroup(url: String): String? {
        val lower = url.lowercase(Locale.ROOT)
        if (lower.contains("index.min.json") || lower.contains("index.json")) {
            return ExtGroup.ANIYOMI
        }
        if (lower.contains("aniyomi") || lower.contains("tachiyomi")) return ExtGroup.ANIYOMI
        if (lower.endsWith(".json")) return ExtGroup.CLOUDSTREAM
        return null
    }

    /** The address to print under the QR, for a viewer whose camera will not focus. */
    fun lanUrl(ip: String, port: Int): String = "http://$ip:$port"

    /**
     * Whether [ip] is an address a phone on the same Wi-Fi can actually reach.
     *
     * Loopback is what the HLS proxy binds to and is useless here; a link-local `169.254.x`
     * address means DHCP never completed, so showing it would send the viewer to a page that
     * cannot load. Both look like perfectly good addresses in a list, which is why this is a
     * rule and not an eyeball check.
     */
    fun isReachableLanAddress(ip: String?): Boolean {
        if (ip.isNullOrBlank()) return false
        if (ip.contains(':')) return false // IPv6: not worth printing under a QR
        if (ip.startsWith("127.")) return false
        if (ip.startsWith("169.254.")) return false
        if (ip == "0.0.0.0") return false
        return ip.count { it == '.' } == 3
    }
}
