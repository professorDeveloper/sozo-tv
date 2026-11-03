import Foundation

let endpoint = URL(string: "https://api.test/api/contents/media-segment")!
let segment = "https://p16-ad-site-sign-sg.tiktokcdn.com/ad-site-i18n-sg/abc/seg-1.image?x-expires=1&sig=a+b/c=="
let relayed = "https://api.test/api/contents/media-segment?url=" + segment.addingPercentEncoding(withAllowedCharacters: CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~"))!

assert(HLSPlaylistRelay.needsManifestProxy(URL(string: "https://cdn-7.test/stream/abc/index.m3u8?t=1")!))
assert(!HLSPlaylistRelay.needsManifestProxy(URL(string: "https://cdn.test/hls/index.m3u8")!))
assert(!HLSPlaylistRelay.needsManifestProxy(URL(string: "https://cdn.test/stream/video.mp4")!))
assert(!HLSPlaylistRelay.needsManifestProxy(URL(string: "http://cdn.test/stream/index.m3u8")!))

let loader = HLSPlaylistRelay.loaderURL(URL(string: "https://cdn.test/stream/a/index.m3u8?t=1")!)!
assert(loader.absoluteString == "sozo-hls://cdn.test/stream/a/index.m3u8?t=1")
assert(HLSPlaylistRelay.upstreamURL(loader)?.absoluteString == "https://cdn.test/stream/a/index.m3u8?t=1")
assert(HLSPlaylistRelay.upstreamURL(URL(string: "https://cdn.test/a.m3u8")!) == nil)

assert(HLSPlaylistRelay.segmentRelay(URL(string: segment)!, endpoint: endpoint)?.absoluteString == relayed)
assert(HLSPlaylistRelay.segmentRelay(URL(string: "https://p19-ad-site-sign-sg.tiktokcdn.com:443/ad-site-i18n-sg/a.image")!, endpoint: endpoint) != nil)
for rejected in ["http://p16-ad-site-sign-sg.tiktokcdn.com/ad-site-i18n-sg/a.image",
                 "https://p17-ad-site-sign-sg.tiktokcdn.com/ad-site-i18n-sg/a.image",
                 "https://p16-ad-site-sign-sg.tiktokcdn.com.evil.test/ad-site-i18n-sg/a.image",
                 "https://user:pw@p16-ad-site-sign-sg.tiktokcdn.com/ad-site-i18n-sg/a.image",
                 "https://p16-ad-site-sign-sg.tiktokcdn.com:8443/ad-site-i18n-sg/a.image",
                 "https://p16-ad-site-sign-sg.tiktokcdn.com/other/a.image",
                 "https://p16-ad-site-sign-sg.tiktokcdn.com/ad-site-i18n-sg/a.ts"] {
    assert(HLSPlaylistRelay.segmentRelay(URL(string: rejected)!, endpoint: endpoint) == nil, rejected)
}
print("HLS relay: manifest routing, loader scheme round trip and the backend segment allowlist passed")

let master = """
#EXTM3U
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="a",NAME="en",URI="audio/en.m3u8"
#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=1280x720
720/index.m3u8?t=1
#EXT-X-STREAM-INF:BANDWIDTH=300000
https://other.test/360.m3u8
"""
let masterOut = HLSPlaylistRelay.rewrite(master, base: URL(string: "https://cdn.test/stream/a/master.m3u8")!, endpoint: endpoint)!.components(separatedBy: "\n")
assert(masterOut[1] == #"#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="a",NAME="en",URI="sozo-hls://cdn.test/stream/a/audio/en.m3u8""#)
assert(masterOut[2] == "#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=1280x720")
assert(masterOut[3] == "sozo-hls://cdn.test/stream/a/720/index.m3u8?t=1")
assert(masterOut[5] == "sozo-hls://other.test/360.m3u8")

let media = "#EXTM3U\r\n#EXT-X-TARGETDURATION:6\r\n#EXT-X-KEY:METHOD=AES-128,URI=\"../key.bin\",IV=0x1\r\n#EXTINF:6.0,\r\n\(segment)\r\n#EXTINF:6.0,\r\nseg-2.ts\r\n\r\n#EXT-X-ENDLIST\r\n"
let mediaOut = HLSPlaylistRelay.rewrite(media, base: URL(string: "https://cdn.test/stream/a/720/index.m3u8")!, endpoint: endpoint)!.components(separatedBy: "\n")
assert(mediaOut[0] == "#EXTM3U\r" && mediaOut[1] == "#EXT-X-TARGETDURATION:6\r")
assert(mediaOut[2] == "#EXT-X-KEY:METHOD=AES-128,URI=\"https://cdn.test/stream/a/key.bin\",IV=0x1\r")
assert(mediaOut[4] == relayed)
assert(mediaOut[6] == "https://cdn.test/stream/a/720/seg-2.ts")
assert(mediaOut[7] == "\r" && mediaOut[8] == "#EXT-X-ENDLIST\r")
assert(HLSPlaylistRelay.rewrite("<html>blocked</html>", base: URL(string: "https://cdn.test/stream/a.m3u8")!, endpoint: endpoint) == nil)
assert(HLSPlaylistRelay.rewrite("\u{FEFF}#EXTM3U\nseg.ts", base: URL(string: "https://cdn.test/stream/a.m3u8")!, endpoint: endpoint) == "\u{FEFF}#EXTM3U\nhttps://cdn.test/stream/seg.ts")
print("HLS relay: master renditions through the loader, relayed and direct segments, keys, CRLF and non-playlists passed")
