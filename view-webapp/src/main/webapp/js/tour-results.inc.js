function setResult(id, result, previous) {
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

        if (previous === '?') {
          let indicator = $('#known')[0];
          let known = parseInt(indicator.innerText);
          indicator.innerText = ++known;
          // and again for overview
          indicator = $('#known2')[0];
          known = parseInt(indicator.innerText);
          indicator.innerText = ++known;
        } else if (result === '?') {
          let indicator = $('#known')[0];
          let known = parseInt(indicator.innerText);
          indicator.innerText = --known;
          // and again for overview
          indicator = $('#known2')[0];
          known = parseInt(indicator.innerText);
          indicator.innerText = --known;
        }
      }
    })
}

const results = [ '?', 'w', 'b', '=', 'X', '#', '0' ];

onLoad(()=>{
  new Tablesort($('#results-table')[0]);
  $('#results-table .player').on('click', e => {
    let cell = e.target.closest('.player');
    let resultCell = cell.closest('tr').find('.result');
    let oldResult = resultCell.data('result');
    let gameId = e.target.closest('tr').data('id');
    let result = cell.hasClass('white') ? 'w' : 'b';
    setResult(gameId, result, oldResult);
  });
  $('#results-table .result').on('click', e => {
    let cell = e.target.closest('.result');
    let gameId = e.target.closest('tr').data('id');
    let oldResult = cell.data('result');
    let index = results.indexOf(oldResult);
    let newResult = results[(index + 1)%results.length];
    setResult(gameId, newResult, oldResult);
  });
  $('#results-filter').on('click', e => {
    let filter = $('#results-filter input')[0];
    filter.checked = !filter.checked;
    if (filter.checked) {
      $('#results-table tbody tr').filter(':not(:has(td.result[data-result="?"]))').addClass('filtered');
    } else {
      $('#results-table tbody tr').removeClass('filtered');
    }
  });
});
