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

function editGame(game) {
  let t = game.find('.table');
  let w = game.find('.white');
  let b = game.find('.black');
  let h = game.find('.handicap');

  let form = $('#pairing-form')[0];
  form.val('id', game.data('id'));
  form.val('t', t.data('value'));
  form.val('w', w.data('id'));
  $('#edit-pairing-white').text(w.text());
  form.val('b', b.data('id'));
  $('#edit-pairing-black').text(b.text());
  form.val('h', h.data('value'));

  $('#update-pairing').addClass('disabled');

  modal('edit-pairing');
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
      if (e.detail === 1) {
        focused = target.toggleClass('selected').attr('draggable', target.hasClass('selected'));
      } else {
        focused = target.attr('draggable', target.hasClass('selected'));
        editGame(focused);
      }
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
      $('#paired .listitem').addClass('selected');
      games = $('#paired')[0].childNodes.filter('.selected.listitem').map(item => parseInt(item.data("id")));
    }
    unpair(games);
  });
  $('#pairing-form [name]').on('input', e => {
    $('#update-pairing').removeClass('disabled');
  });
  $('#pairing-exchange').on('click', e => {
    let form = $('#pairing-form')[0];
    let w = form.val('w');
    let b = form.val('b');
    form.val('w', b);
    form.val('b', w);
    let wName = $('#edit-pairing-white').text();
    let bName = $('#edit-pairing-black').text();
    $('#edit-pairing-white').text(bName);
    $('#edit-pairing-black').text(wName);
    $('#update-pairing').removeClass('disabled');
  });
  $('#pairing-form').on('submit', e => {
    e.preventDefault();
    return false;
  });
  $('#update-pairing').on('click', e => {
    let form = $('#pairing-form')[0];
    let game = {
      id: form.val('id'),
      t: form.val('t'),
      w: form.val('w'),
      b: form.val('b'),
      h: form.val('h')
    }
    api.putJson(`tour/${tour_id}/pair/${activeRound}`, game)
      .then(game => {
        if (game !== 'error') {
          document.location.reload();
        }
      });
  });
});
