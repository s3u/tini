var connect = require('connect');
var resource = require('resource-router');
var Mu = require('mu');

function main(app) {
  app.resource('/', {
    'get' : function(req, res) {
      Mu.templateRoot = './src/main/java/nettd/examples/mustache/';

      var ctx = {
        name: "Subbu"
      };

      Mu.render('index.mustache', ctx, {}, function (err, output) {
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

          res.body(buffer, 'utf8');
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
server.listen(4000);
console.log('Connect server listening on port 4000');
