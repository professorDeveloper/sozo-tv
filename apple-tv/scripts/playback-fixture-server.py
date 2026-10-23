#!/usr/bin/env python3
"""Loopback-only byte-range server for generated AVKit UI test media."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import re

FILES = {'/clip.mp4': (Path('/tmp/sozo-tvos-playback-smoke.mp4'), 'video/mp4'),
         '/captions.vtt': (Path('/tmp/sozo-player-fixtures/captions.vtt'), 'text/vtt')}
class Handler(BaseHTTPRequestHandler):
    def do_HEAD(self): self.respond(False)
    def do_GET(self): self.respond(True)
    def respond(self, body):
        entry = FILES.get(self.path.split('?', 1)[0])
        if not entry or not entry[0].is_file(): return self.send_error(404)
        data = entry[0].read_bytes(); size = len(data); start, end = 0, size - 1
        requested = self.headers.get('Range')
        if requested:
            match = re.fullmatch(r'bytes=(\d*)-(\d*)', requested)
            if not match: return self.send_error(416)
            a, b = match.groups()
            if a: start = int(a); end = min(int(b), end) if b else end
            elif b: start = max(0, size - int(b))
            else: return self.send_error(416)
            if start > end or start >= size: return self.send_error(416)
        payload = data[start:end + 1]
        self.send_response(206 if requested else 200)
        self.send_header('Content-Type', entry[1]); self.send_header('Accept-Ranges', 'bytes')
        self.send_header('Content-Length', str(len(payload)))
        if requested: self.send_header('Content-Range', f'bytes {start}-{end}/{size}')
        self.end_headers()
        if body:
            try: self.wfile.write(payload)
            except (BrokenPipeError, ConnectionResetError): pass

if __name__ == '__main__': ThreadingHTTPServer(('127.0.0.1', 38657), Handler).serve_forever()
