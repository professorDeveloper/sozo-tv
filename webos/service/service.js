'use strict';
// webOS service: starts the loopback relay and tells the app where it is.
var Service = require('webos-service');
var proxy = require('./proxy.js');

var ID = 'com.soplay.sozo.webos.service';
var APP = 'com.soplay.sozo.webos';
var service = new Service(ID);
var relay = null;
var waiting = [];

function withRelay(cb) {
  if (relay) { cb(null, relay); return; }
  waiting.push(cb);
  if (waiting.length > 1) return;
  proxy.start(function (err, r) {
    if (!err) relay = r;
    var list = waiting;
    waiting = [];
    list.forEach(function (f) { f(err, r); });
  });
}

// The app holds this subscription while it runs, which keeps the service alive. Only Sozo itself gets the token.
service.register('relay', function (message) {
  if (!proxy.callerAllowed(message.sender, APP)) {
    message.respond({ returnValue: false, errorText: 'caller not allowed' });
    return;
  }
  withRelay(function (err, r) {
    if (err) { message.respond({ returnValue: false, errorText: String(err.message || err) }); return; }
    message.respond({ returnValue: true, port: r.port, token: r.token, subscribed: !!message.isSubscription });
  });
}, function () {});
