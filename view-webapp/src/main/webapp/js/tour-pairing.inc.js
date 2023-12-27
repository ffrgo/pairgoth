let focused = undefined;

function pair(parts) {
  api.postJson(`tour/${tour_id}/pair/${activeRound}`, parts)
    .then(rst => {
      if (rst !== 'error') {
        document.location.reload();
      }
    });
}

function unpair(games) {
  api.deleteJson(`tour/${tour_id}/pair/${activeRound}`, games)
    .then(rst => {
      if (rst !== 'error') {
        document.location.reload();
      }
    });
}

onLoad(()=>{
  $('.listitem').on('click', e => {
    if (e.shiftKey && typeof(focused) !== 'undefined') {
      let from = focused.index('.listitem');
      let to = e.target.closest('.listitem').index('.listitem');
      if (from > to) {
        let tmp = from;
        from = to;
        to = tmp;
      }
      let parent = e.target.closest('.multi-select');
      let children = parent.childNodes.filter('.listitem');
      for (let j = from; j <= to; ++j) {  new Tablesort($('#players')[0]);

        children.item(j).addClass('selected');
        children.item(j).attr('draggable', true);
      }
    } else {
      let target = e.target.closest('.listitem');
      focused = target.toggleClass('selected').attr('draggable', target.hasClass('selected'));
    }
  });
  $('#pair').on('click', e => {
    let parts = $('#pairables')[0].childNodes.filter('.selected.listitem').map(item => parseInt(item.data("id")));
    if (parts.length == 0) {
      $('#pairables .listitem').addClass('selected');
      parts = $('#pairables')[0].childNodes.filter('.selected.listitem').map(item => parseInt(item.data("id")));
    }
    pair(parts);
  });
  $('#unpair').on('click', e => {
    let games = $('#paired')[0].childNodes.filter('.selected.listitem').map(item => parseInt(item.data("id")));
    if (games.length == 0) {
      games = $('#paired')[0].childNodes.filter('.selected.listitem').map(item => parseInt(item.data("id")));
    }
    unpair(games);
  });
});
