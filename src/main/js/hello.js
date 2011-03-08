var sys = require('sys'),
  http = require('http');

http.createServer(
  function(req, res) {
    res.writeHead(200, {'Connection' : 'keep-alive', 'Content-Type': 'text/plain; charset=UTF-8'});
    res.write("<p>Hello world</p>");
    res.end();
  }).listen(8080);


