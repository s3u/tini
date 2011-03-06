var http = require('http'),
  httpProxy = require('http-proxy');

// Create your proxy server
httpProxy.createServer(3001, 'localhost').listen(3031);
