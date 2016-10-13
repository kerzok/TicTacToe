var express = require('express');
var request = require('request');
var router = express.Router();

/* GET home page. */
router.get('/', function(req, res, next) {
  res.render('index');
});

router.post('/createGame', function(req, res, next) {
    var side = req.body.side;
    request.post(
        'http://localhost:8080/createGame',
        { json: {side: side}},
        function (error, response, body) {
            if (!error && response.statusCode == 200) {
                console.log(response.body)
                res.send(response.body)
            }
        }
    )
});

router.post('/joinGame', function(req, res, next) {
    var gameId = req.body.gameId
    request.post(
        'http://localhost:8080/joinGame',
        {json:{gameId: gameId}},
        function (error, response, body) {
            if (!error && response.statusCode == 200) {
                console.log(response.body)
                res.send(response.body)
            }
        }
    )
})

module.exports = router;
