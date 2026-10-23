// Local contract fixture: real backend gateway/room manager; only persistence and auth are in-memory.
// Usage: node tools/watch-party-fixture.cjs [port]. Never accepts production credentials.
const path = require('path');
const backend = path.resolve(__dirname, '../../sozo-backend');
const {Server} = require(path.join(backend, 'node_modules/socket.io'));
const service = require(path.join(backend, 'src/services/watchParty/watchPartyService'));
const gateway = require(path.join(backend, 'src/socket/watchPartyGateway'));
const docs = new Map();
service.getActiveByCode = async code => docs.get(code);
service.persistMembers = async () => {};
service.persistPlayback = async () => {};
service.persistContent = async () => {};
service.closeParty = async code => { docs.delete(code); };
const http = require('http').createServer(async (req, res) => {
  res.setHeader('Content-Type', 'application/json');
  if (req.method === 'GET' && req.url === '/health') return res.end('{"ok":true}');
  const token = req.headers.authorization?.replace('Bearer ', '');
  if (!['fixture-host', 'fixture-guest'].includes(token)) { res.statusCode = 401; return res.end('{}'); }
  if (req.method === 'POST' && req.url === '/api/watch-party') {
    let raw = ''; for await (const chunk of req) raw += chunk;
    const body = JSON.parse(raw || '{}'); const code = Math.random().toString(36).slice(2, 8).toUpperCase();
    const doc = {code, host:{userId:'host'}, settings:{onlyHostControls:true}, maxMembers:2,
      content:service.sanitizeContent(body.content), playback:{positionSec:0,isPlaying:false,rate:1}};
    docs.set(code, doc); return res.end(JSON.stringify({code,party:doc}));
  }
  if (req.method === 'DELETE' && req.url.startsWith('/api/watch-party/')) {
    const code = req.url.split('/').pop(); gateway.emitClosed(code); docs.delete(code); return res.end('{}');
  }
  if (req.method === 'POST' && req.url === '/fixture/disconnect') {
    // Transport interruption, not Socket.IO namespace disconnect: clients must reconnect and rejoin.
    for (const socket of io.of('/watch').sockets.values()) socket.conn.close();
    return res.end('{}');
  }
  res.statusCode = 404; res.end('{}');
});
const io = new Server(http, {path:'/socket.io', pingInterval:1000, pingTimeout:1000});
const ns = io.of('/watch');
ns.use((socket,next) => {
  const token = socket.handshake.auth?.token;
  if (!['fixture-host','fixture-guest'].includes(token)) return next(new Error('unauthorized'));
  socket.data.user = {id:token.slice(8),username:token,photoURL:null}; next();
});
gateway.registerWatchPartyGateway(ns);
http.listen(Number(process.argv[2] || 18768), '127.0.0.1', () => console.log(`watch-party fixture http://127.0.0.1:${http.address().port}/api`));
