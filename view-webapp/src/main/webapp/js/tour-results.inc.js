function setResult(id, result) {
  api.putJson(`tour/${tour_id}/res/${activeRound}`, { id: id, result: result })
    .then(res => {
      if (res !== 'error') {
        let row = $(`#results-list tr#result-${id}`);
        row.find('td').removeClass('winner').removeClass('looser');
        let white = row.find('td.white');
        let black = row.find('td.black');
        let dispResult = result;
        switch (result) {
          case '?': break;
          case 'w': white.addClass('winner'); black.addClass('looser'); dispResult = '1-0'; break;
          case 'b': black.addClass('winner'); white.addClass('looser'); dispResult = '0-1'; break;
          case '=': dispResult = '½-½'; break;
          case 'X': break;
          case '#': white.addClass('winner'); black.addClass('winner'); dispResult = '1-1'; break;
          case '0': white.addClass('looser'); black.addClass('looser'); dispResult = '0-0'; break;
        }
        let resultCell = row.find('td.result');
        resultCell.text(dispResult).data('result', result);
        standingsUpToDate = false;
      }
    })
}

const results = [ '?', 'w', 'b', '=', 'X', '#', '0' ];

onLoad(()=>{
  new Tablesort($('#results-table')[0]);
  $('#results-table .player').on('click', e => {
    let cell = e.target.closest('.player');
    let gameId = e.target.closest('tr').data('id');
    let result = cell.hasClass('white') ? 'w' : 'b';
    setResult(gameId, result);
  });
  $('#results-table .result').on('click', e => {
    let cell = e.target.closest('.result');
    let gameId = e.target.closest('tr').data('id');
    let result = cell.data('result');
    let index = results.indexOf(result);
    result = results[(index + 1)%results.length];
    setResult(gameId, result);
  });
});
