var sys = require('sys'),
http = require('http');

http.createServer(function(req, res) {
  sys.log('Got ' + req.url);

  var id = req.url.substring(1);
    setTimeout(function() {
      res.writeHead(200, {'Content-Type': 'text/plain; charset=UTF-8',
      'Conteng-Length' : '2'});
      res.write(req.url);
      res.end();
      sys.log('Done ' + req.url);
    }, 1000/id);
}).listen(8080);


