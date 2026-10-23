import Foundation
func parse(_ text: String) throws -> [SubtitleCue] { try SidecarSubtitles.parse(Data(text.utf8)) }
let vtt = try parse("""
WEBVTT

NOTE comment
ignored

intro
00:01.000 --> 00:03.500 align:start position:20%
<v Speaker><b>Hello</b> &amp; welcome</v>
Second line

00:04.000 --> 00:05.000
Next
""")
precondition(vtt.count == 2 && vtt[0].text == "Hello & welcome\nSecond line")
precondition(SidecarSubtitles.text(at: 1, cues: vtt) == vtt[0].text)
precondition(SidecarSubtitles.text(at: 3.5, cues: vtt) == nil)
precondition(SidecarSubtitles.text(at: .nan, cues: vtt) == nil)
let srt = "1\r\n00:00:01,250 --> 00:00:02,500\r\nHi &#39;TV&#39;\r\n\r\n2\r\n00:00:02,000 --> 00:00:04,000\r\nOverlap\r\n"
let srtCues = try SidecarSubtitles.parse(srt.data(using: .utf16)!)
precondition(srtCues.count == 2 && srtCues[0].start == 1.25 && srtCues[0].text == "Hi 'TV'")
precondition(SidecarSubtitles.text(at: 2.25, cues: srtCues) == "Hi 'TV'\nOverlap")
let unsafe = try parse("WEBVTT\n\n00:00.000 --> 00:02.000\n<script>alert('x')</script><i>Safe</i> &lt;tag&gt;\n")
precondition(unsafe[0].text == "Safe <tag>")
func rejects(_ data: Data) {
    do { _ = try SidecarSubtitles.parse(data); fatalError("Must reject invalid subtitles") }
    catch { }
}
rejects(Data("<html>Not captions</html>".utf8))
rejects(Data("1\n00:99:01,000 --> 00:00:02,000\nNo".utf8))
rejects(Data("1\n00:00:03,000 --> 00:00:02,000\nNo".utf8))
rejects(Data(repeating: 65, count: 2 * 1024 * 1024 + 1))
let bigEndian = Data([0xFE, 0xFF]) + srt.data(using: .utf16BigEndian)!
let beCues = try SidecarSubtitles.parse(bigEndian)
precondition(beCues == srtCues)
let bomCues = try parse("\u{FEFF}WEBVTT\n\n00:00.000 --> 00:01.000\nBOM")
precondition(bomCues[0].text == "BOM")
rejects(Data("1\n00:00:00,000 --> 02:00:00,000\nToo long".utf8))
rejects(Data("1\n00:00:00,000 --> 00:00:01,000\n".utf8) + Data(repeating: 65, count: 8193))
let sorted = try parse("1\n00:00:09,000 --> 00:00:10,000\nLater\n\n2\n00:00:01,000 --> 00:00:02,000\nEarlier")
precondition(sorted[0].text == "Earlier")
print("PASS: VTT identifiers/settings and multiline text; UTF16 SRT; overlaps/end boundaries; script stripping/entities; invalid times and input bounds; sorted cues")
// Error pages containing copied subtitle timestamps must not become usable captions.
rejects(Data("<!doctype html><html><body>\n00:00.000 --> 00:01.000\nService unavailable\n</body></html>".utf8))
precondition(SidecarSubtitles.isRemoteURL(URL(string: "https://media.test/en.vtt?token=opaque")!))
precondition(!SidecarSubtitles.isRemoteURL(URL(string: "https://user:secret@media.test/en.vtt")!))
precondition(!SidecarSubtitles.isRemoteURL(URL(string: "file:///tmp/en.vtt")!))
precondition(!SidecarSubtitles.isRemoteURL(URL(string: "https:/en.vtt")!))
print("PASS: HTML error documents and credential-bearing subtitle URLs rejected")
