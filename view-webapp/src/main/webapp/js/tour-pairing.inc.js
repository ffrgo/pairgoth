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

function renumberTables() {
  api.putJson(`tour/${tour_id}/pair/${activeRound}`, {})
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

function editPairable(pairable) {
  let id = pairable.data('id');
  let form = $('#pairable-form')[0];
  form.val('id', id);
  let name = pairable.find('.name')[0].textContent;
  $('#edit-pairable-disp')[0].textContent = name;
  let box = pairable.closest('.multi-select');
  let state = box.attr('id') === 'pairables';
  form.val('pairable', state);
  modal('edit-pairable');
}

function updatePairable() {
  let form = $('#pairable-form')[0];
  let id = form.val('id');
  let status = form.val('pairable');
  let origSkip = $(`#players tr[data-id="${id}"] td.participating label`)
    .map(disk => disk.hasClass('red'));
  let skip = status ? [] : [ activeRound ];
  for (let i = 0; i < origSkip.length; ++i) {
    let round = i + 1;
    if (round !== activeRound && origSkip[i]) skip.push(round);
  }
  api.putJson(`tour/${tour_id}/part/${id}`, {
    id: id,
    skip: skip
  }).then(player => {
      if (player !== 'error') {
        window.location.reload();
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
      if (e.detail === 1) {
        focused = target.toggleClass('selected').attr('draggable', target.hasClass('selected'));
      } else if (target.closest('#paired')) {
        focused = target.attr('draggable', target.hasClass('selected'));
        editGame(focused);
      } else {
        editPairable(focused);
      }
    }
  });
  $('#pair').on('click', e => {
    let parts = $('#pairables .selected.listitem').map(item => parseInt(item.data("id")));
    if (parts.length == 0) {
      $('#pairables .listitem').addClass('selected');
      parts = $('#pairables .selected.listitem').map(item => parseInt(item.data("id")));
    }
    pair(parts);
  });
  $('#unpair').on('click', e => {
    let games = $('#paired .selected.listitem').map(item => parseInt(item.data("id")));
    if (games.length == 0) {
      $('#paired .listitem').addClass('selected');
      games = $('#paired .selected.listitem').map(item => parseInt(item.data("id")));
    }
    unpair(games);
  });
  $('#renumber-tables').on('click', e => {
    renumberTables();
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
  $('.multi-select').on('dblclick', e => {
    let box = e.target.closest('.multi-select');
    if (!e.target.closest('.listitem')) {
      box.find('.listitem').removeClass('selected');
    }
  });
  $('#update-pairable').on('click', e => {
    updatePairable();
  });
  window.on('unload', e => {
    store('pairablesScroll', $('#pairables')[0].scrollTop);
    store('unpairablesScroll', $('#unpairables')[0].scrollTop);
    store('pairedScroll', $('#paired')[0].scrollTop);
  });
  setTimeout(() => {
    if (store.has('pairablesScroll')) $('#pairables')[0].scrollTop = store('pairablesScroll');
    if (store.has('unpairablesScroll')) $('#unpairables')[0].scrollTop = store('unpairablesScroll');
    if (store.has('pairedScroll')) $('#paired')[0].scrollTop = store('pairedScroll');
  }, 0);
  if ($('#paired .listitem').length === 0) {
    $('body').addClass('nogame');
  }
});
