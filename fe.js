var connect = require('connect');
var resource = require('resource-router');
var Mu = require('../../nodes/Mu/lib/mu.js');
var multinode = require('../../nodes/multi-node/lib/multi-node.js');

function main(app) {
  app.resource('/', {
    'get' : function(req, res) {
      Mu.templateRoot = '.';

      var ctx = {
        name: "Subbu"
      };

      Mu.render('index', ctx, {}, function (err, output) {
        if(err) {
          throw err;
        }

        var buffer = '';

        output.addListener('data', function (c) {
          buffer += c;
        });

        output.addListener('end', function () {
          res.writeHead(200, {
            'Content-Type': 'text/html',
            'Content-Length': buffer.length
          });

          res.end(buffer, 'utf8');
        });
      });
    }
  });
}


var server = connect.createServer(
  connect.logger({ buffer: true }),
  connect.cache(),
  connect.gzip()
  );

server.use(resource(main));

var nodes = multinode.listen({
        port: 4000, 
        nodes: 2
    }, server);

console.log('Connect server listening on port 4000');
