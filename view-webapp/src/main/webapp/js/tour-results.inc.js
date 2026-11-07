// Patch one result row (cells + the "known" counters) from a {id, result} descriptor. `previous` is
// the prior symbol: passed by the local click, derived from the cell for an SSE echo (shared primitive,
// called by both the direct effect and the ResultUpdated SSE handler).
function applyResult({ id, result, previous }) {
  let row = $(`#results-list tr#result-${id}`);
  if (row.length === 0) return;
  let resultCell = row.find('td.result');
  if (previous === undefined) previous = resultCell.data('result');
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
  resultCell.text(dispResult).data('result', result);
  // the "known" counters track played games: bump when a result appears/disappears
  let delta = (previous === '?' && result !== '?') ? 1 : (previous !== '?' && result === '?') ? -1 : 0;
  if (delta) ['#known', '#known2'].forEach(sel => {
    let ind = $(sel)[0];
    if (ind) ind.innerText = parseInt(ind.innerText) + delta;
  });
}

function clearResultsCells() {
  $('#results-list tbody tr').forEach(tr => {
    let id = tr.attr('id')?.replace('result-', '');
    if (id) applyResult({ id, result: '?' });
  });
}

function setResult(id, result, previous) {
  mutate({
    url: `tour/${tour_id}/res/${activeRound}`, body: { id: id, result: result },
    source: 'results', effect: () => applyResult({ id, result, previous })
  });
}

function clearResults() {
  mutate({
    method: 'delete', url: `tour/${tour_id}/res/${activeRound}`,
    source: 'results', effect: () => clearResultsCells()
  });
}

const results = [ '?', 'w', 'b', '=', 'X', '#', '0' ];

onLoad(()=>{
  // collaborative echoes: patch the cell in place (game json carries the result symbol in 'r')
  sseEffect('ResultUpdated', data => { applyResult({ id: data.data.id, result: data.data.r }); return true; });
  sseEffect('ResultsCleared', () => { clearResultsCells(); return true; });

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
  $('#results-table .result').on('dblclick', e => {
    let cell = e.target.closest('.result');
    let gameId = e.target.closest('tr').data('id');
    let oldResult = cell.data('result');
    let newResult = '?';
    setResult(gameId, newResult, oldResult);
  });
  // team boards only: override a single board's colours (the match result is unaffected)
  $('#results-table .swap-colours').on('click', e => {
    let gameId = e.target.closest('tr').data('id');
    api.putJson(`tour/${tour_id}/res/${activeRound}`, { id: gameId, swap: true })
      .then(res => { if (res !== 'error') document.location.reload(); });
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
  $('#clear-results').on('click', e => {
    if (confirm($('#confirmation')[0].textContent)) {
      clearResults();
    }
  });
  $('#publish-results').on('click', e => {
    let form = $('#tournament-infos')[0];
    let code = form.val('shortName');
    if (!code) {
      showError('Tournament short name is required for publishing');
      return;
    }
    api.postJson(`webhook/publish/results/${code}/${activeRound}?id=${tour_id}`, {})
      .then(data => {
        if (data === 'error') return;
        if (!data.status) showError(data.message || 'Publish failed');
        else showSuccess(`Results for round ${activeRound} published to website`);
      });
  });
});
