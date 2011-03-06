var http = require('http');

var options = {
  host: 'localhost',
  port: 3000,
  path: '/upload',
  method: 'POST'
};

var req = http.request(options, function(res) {
//  console.log('STATUS: ' + res.statusCode);
//  console.log('HEADERS: ' + JSON.stringify(res.headers));
  res.setEncoding('utf8');
  res.on('data', function (chunk) {
    console.log(chunk);
  });
});

// write data to request body
req.write('data');
req.write('dataa');
req.write('dataaa');
req.write('dataaaa');
req.write('dataaaaa');
req.write('dataaaaaa');
req.write('dataaaaaaa');
req.write('dataaaaaaaa');
req.write('dataaaaaaaaa');
req.write('dataaaaaaaaaa');
req.end();