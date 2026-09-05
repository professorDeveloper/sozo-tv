package com.saikou.sozo_tv.engine.link

import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * A one-page web server the TV runs while the "Add from phone" screen is open.
 *
 * The phone does not need the Sozo app for this — any browser on the same Wi-Fi can open the
 * page, which is the point: the viewer already has the repo URL somewhere on their phone, and
 * the shortest path from there to the television is a text field they can paste into.
 *
 * Lifetime is the screen's. It binds on open and closes on leave, so there is no listener on
 * the network at any other time; nothing here survives the fragment.
 *
 * ## Why the code is not decoration
 *
 * `POST /add` ends at `ExtensionEngine.addRepo`, which downloads a `.cs3` and loads it into
 * this process. That is arbitrary code execution, granted to whoever can reach the port. So
 * the code is checked on every request that can cause an install, the page itself says
 * nothing about whether a guess was close, and the whole thing is unreachable the moment the
 * viewer leaves the screen.
 */
class ExtensionLinkServer(
    private val onSubmit: (group: String, url: String) -> Unit,
) {

    /** What the phone is shown, and what the TV screen renders. */
    sealed class Status {
        data object Waiting : Status()
        data class Received(val url: String, val group: String) : Status()
        data class Installing(val url: String, val current: Int, val total: Int) : Status()
        data class Installed(val url: String, val sources: Int) : Status()
        data class Failed(val url: String, val message: String) : Status()
    }

    val code: String = ExtensionLinkRules.newCode()

    @Volatile
    var status: Status = Status.Waiting
        private set

    /** Set by the screen as the install proceeds, and read by the phone's poller. */
    fun publish(next: Status) {
        status = next
    }

    private var server: Server? = null

    var port: Int = 0
        private set

    /** The address to print under the QR, or null when this TV is not on a usable network. */
    val lanIp: String? get() = firstReachableIpv4()

    /** The full URL the QR encodes — code included, so a scan needs no typing. */
    fun pairingUrl(): String? {
        val ip = lanIp ?: return null
        return "${ExtensionLinkRules.lanUrl(ip, port)}/?c=$code"
    }

    /** The address a viewer types when the camera will not focus, without the code. */
    fun typedUrl(): String? {
        val ip = lanIp ?: return null
        return ExtensionLinkRules.lanUrl(ip, port)
    }

    @Synchronized
    fun start() {
        if (server != null) return
        // The preferred port makes the printed address short enough to type. Another app
        // (or a previous instance whose socket has not been released) can hold it, and
        // failing to open the screen over that would be worse than a longer URL.
        val s = runCatching {
            Server(ExtensionLinkRules.PREFERRED_PORT).also { it.start(SOCKET_TIMEOUT, true) }
        }.getOrElse {
            Server(0).also { it.start(SOCKET_TIMEOUT, true) }
        }
        server = s
        port = s.listeningPort
    }

    @Synchronized
    fun stop() {
        server?.stop()
        server = null
        port = 0
    }

    private inner class Server(port: Int) : NanoHTTPD(null, port) {
        override fun serve(session: IHTTPSession): Response =
            runCatching { route(session) }.getOrElse {
                newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "error",
                )
            }
    }

    private fun route(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response = when {
        session.uri == "/state" -> stateResponse(session)
        session.uri == "/add" && session.method == NanoHTTPD.Method.POST -> add(session)
        session.uri == "/" || session.uri.isEmpty() -> html()
        else -> NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "not found",
        )
    }

    private fun add(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val body = HashMap<String, String>()
        runCatching { session.parseBody(body) }
        val params = session.parameters
        fun field(name: String): String? =
            params[name]?.firstOrNull() ?: body[name]

        if (!ExtensionLinkRules.codeMatches(code, field("code"))) {
            // Deliberately the same shape of answer as a bad URL: the page must not tell a
            // caller whether the code was the part that was wrong.
            return json(NanoHTTPD.Response.Status.FORBIDDEN, "ok" to false, "error" to "code")
        }

        val requested = field("group")?.takeIf { it.isNotBlank() }
        val url = ExtensionLinkRules.normalizeRepoUrl(field("url"), requested)
            ?: return json(NanoHTTPD.Response.Status.BAD_REQUEST, "ok" to false, "error" to "url")

        val group = requested ?: ExtensionLinkRules.guessGroup(url)
            ?: return json(NanoHTTPD.Response.Status.BAD_REQUEST, "ok" to false, "error" to "group")

        status = Status.Received(url, group)
        onSubmit(group, url)
        return json(NanoHTTPD.Response.Status.OK, "ok" to true, "url" to url, "group" to group)
    }

    private fun stateResponse(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        if (!ExtensionLinkRules.codeMatches(code, session.parameters["c"]?.firstOrNull())) {
            return json(NanoHTTPD.Response.Status.FORBIDDEN, "ok" to false)
        }
        val o = JSONObject()
        when (val s = status) {
            is Status.Waiting -> o.put("state", "waiting")
            is Status.Received -> o.put("state", "received").put("url", s.url)
            is Status.Installing -> o.put("state", "installing")
                .put("current", s.current).put("total", s.total)
            is Status.Installed -> o.put("state", "installed").put("sources", s.sources)
            is Status.Failed -> o.put("state", "failed").put("message", s.message)
        }
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK, "application/json", o.toString(),
        )
    }

    private fun json(
        code: NanoHTTPD.Response.Status,
        vararg pairs: Pair<String, Any?>,
    ): NanoHTTPD.Response {
        val o = JSONObject()
        for ((k, v) in pairs) o.put(k, v)
        return NanoHTTPD.newFixedLengthResponse(code, "application/json", o.toString())
    }

    /**
     * The page, inline.
     *
     * No external stylesheet, font or script: the phone is on a local address with no route
     * to this TV's internet, sometimes no internet at all, and a page that waits on a CDN is
     * a blank screen at the exact moment the viewer is looking for reassurance.
     */
    private fun html(): NanoHTTPD.Response {
        val chips = buildString {
            for (e in com.saikou.sozo_tv.data.extensions.ShortcodeRegistry.cloudstream) {
                append("""<button type="button" class="chip" data-u="${e.url}" data-g="cloudstream">${e.name}</button>""")
            }
            for (e in com.saikou.sozo_tv.data.extensions.ShortcodeRegistry.aniyomi) {
                append("""<button type="button" class="chip" data-u="${e.url}" data-g="aniyomi">${e.name}</button>""")
            }
        }
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK, "text/html; charset=utf-8", PAGE.replace("%%CHIPS%%", chips),
        )
    }

    /**
     * The IPv4 address a phone on the same Wi-Fi can reach.
     *
     * Interfaces are walked in order and the first usable address wins, which on a set-top
     * box with both Ethernet and Wi-Fi up is whichever the system lists first — either is
     * reachable from the same LAN. Loopback and link-local are excluded by
     * [ExtensionLinkRules.isReachableLanAddress], not by eye.
     */
    private fun firstReachableIpv4(): String? = runCatching {
        for (nic in NetworkInterface.getNetworkInterfaces()) {
            if (!nic.isUp || nic.isLoopback) continue
            for (addr in nic.inetAddresses) {
                if (addr !is Inet4Address) continue
                val ip = addr.hostAddress
                if (ExtensionLinkRules.isReachableLanAddress(ip)) return ip
            }
        }
        null
    }.getOrNull()

    companion object {
        private const val SOCKET_TIMEOUT = 15_000

        private val PAGE = """
<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Sozo TV — add a source</title>
<style>
:root{color-scheme:dark}
*{box-sizing:border-box}
body{margin:0;background:#0b0d0f;color:#f2f4f6;font:16px/1.45 -apple-system,system-ui,Roboto,sans-serif;padding:22px 18px 48px}
h1{font-size:20px;margin:0 0 4px}
p.sub{margin:0 0 22px;color:#9aa3ab;font-size:14px}
label{display:block;font-size:13px;color:#9aa3ab;margin:16px 0 6px}
input,select{width:100%;padding:13px 14px;border-radius:11px;border:1px solid #262b30;background:#14181c;color:#f2f4f6;font-size:16px}
input:focus,select:focus{outline:none;border-color:#e50914}
#code{letter-spacing:.32em;text-transform:uppercase;font-weight:700}
button.go{width:100%;margin-top:20px;padding:15px;border:0;border-radius:11px;background:#e50914;color:#fff;font-size:16px;font-weight:700}
button.go:disabled{background:#3a2226;color:#8b8b8b}
.chips{display:flex;flex-wrap:wrap;gap:8px;margin-top:8px}
.chip{padding:8px 13px;border-radius:999px;border:1px solid #2b3137;background:#14181c;color:#cfd6dc;font-size:13px}
#out{margin-top:20px;padding:14px;border-radius:11px;font-size:14px;display:none}
.ok{background:#10251a;border:1px solid #1f5c3a;color:#8ce0ac}
.bad{background:#2a1416;border:1px solid #6b2229;color:#f2a0a6}
.busy{background:#141a22;border:1px solid #26364a;color:#a9c2e0}
</style></head><body>
<h1>Add a source</h1>
<p class="sub">Paste a repository link. It installs on the television, not on this phone.</p>
<form id="f">
  <label for="code">Code shown on the TV</label>
  <input id="code" name="code" autocomplete="off" autocapitalize="characters" placeholder="ABC-234">
  <label for="url">Repository link or shortcode</label>
  <input id="url" name="url" autocomplete="off" autocapitalize="none" spellcheck="false" placeholder="https://raw.githubusercontent.com/…/repo.json">
  <div class="chips">%%CHIPS%%</div>
  <label for="group">Engine</label>
  <select id="group" name="group">
    <option value="">Detect from the link</option>
    <option value="cloudstream">CloudStream</option>
    <option value="aniyomi">Aniyomi</option>
  </select>
  <button class="go" type="submit">Send to TV</button>
</form>
<div id="out"></div>
<script>
var q=new URLSearchParams(location.search);
if(q.get('c')){document.getElementById('code').value=q.get('c');}
var out=document.getElementById('out');
function say(cls,msg){out.className=cls;out.textContent=msg;out.style.display='block';}
document.querySelectorAll('.chip').forEach(function(b){
  b.onclick=function(){
    document.getElementById('url').value=b.dataset.u;
    document.getElementById('group').value=b.dataset.g;
  };
});
var poll=null;
function watch(code){
  clearInterval(poll);
  poll=setInterval(function(){
    fetch('/state?c='+encodeURIComponent(code)).then(function(r){return r.json()}).then(function(s){
      if(s.state==='installing'){say('busy','Installing on the TV… '+(s.total?s.current+'/'+s.total:''));}
      else if(s.state==='installed'){clearInterval(poll);say('ok',s.sources>0?('Done — '+s.sources+' source'+(s.sources===1?'':'s')+' added.'):'Installed, but it added no sources. Check the link.');}
      else if(s.state==='failed'){clearInterval(poll);say('bad',s.message||'The TV could not install it.');}
    }).catch(function(){});
  },1200);
}
document.getElementById('f').onsubmit=function(e){
  e.preventDefault();
  var code=document.getElementById('code').value;
  var body=new URLSearchParams({code:code,url:document.getElementById('url').value,group:document.getElementById('group').value});
  say('busy','Sending…');
  fetch('/add',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:body.toString()})
    .then(function(r){return r.json().then(function(j){return {s:r.status,j:j}})})
    .then(function(x){
      if(x.j.ok){say('busy','Sent. The TV is installing it…');watch(code);}
      else if(x.j.error==='url'){say('bad','That does not look like a repository link.');}
      else if(x.j.error==='group'){say('bad','Pick the engine — the link does not say which it is.');}
      else{say('bad','Wrong code, or the TV stopped waiting.');}
    })
    .catch(function(){say('bad','Could not reach the TV. Same Wi-Fi?');});
};
</script></body></html>
""".trimIndent()
    }
}
