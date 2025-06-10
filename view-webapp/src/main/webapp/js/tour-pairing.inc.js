let focused = undefined;

function pair(parts) {

  let doWork = () => {
    api.postJson(`tour/${tour_id}/pair/${activeRound}`, parts)
      .then(rst => {
        if (rst !== 'error') {
          document.location.reload();
        }
      });
  }

  let tablesExclusionControl = $('#exclude-tables');
  let value = tablesExclusionControl[0].value;
  let origValue = tablesExclusionControl.data('orig');
  if (value === origValue) {
    // tables exclusion value did not change
    doWork();
  } else {
    // tables exclusion value has change, we must save it first
    api.putJson(`tour/${tour_id}`, { round: activeRound, excludeTables: value })
      .then(rst => {
        if (rst !== 'error') {
          doWork();
        }
      });
  }
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
  let payload = {}
  let tablesExclusionControl = $('#exclude-tables');
  let value = tablesExclusionControl[0].value;
  let origValue = tablesExclusionControl.data('orig');
  if (value !== origValue) {
    payload['excludeTables'] = value;
  }
  api.putJson(`tour/${tour_id}/pair/${activeRound}`, payload)
    .then(rst => {
      if (rst !== 'error') {
        document.location.reload();
      }
    });
}

function editGame(game) {
  // CB TODO - those should be data attributes of the parent game tag
  let t = game.find('.table');
  let w = game.find('.white');
  let b = game.find('.black');
  let h = game.find('.handicap');

  let form = $('#pairing-form')[0];
  form.val('id', game.data('id'));
  form.val('prev-table', t.data('value'));
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

function showOpponents(player) {
  let id = player.data('id');
  let games = $(`#standings-table tbody tr[data-id="${id}"] .game-result`)
  if (games.length) {
    let title = `${$('#previous_games_prefix').text()}${player.innerText.replace('\n', ' ')}${$('#previous_games_postfix').text()}`;
    $('#unpairables').addClass('hidden');
    $('#previous_games')[0].setAttribute('title', title);
    $('#previous_games')[0].clearChildren();
    $('#previous_games').removeClass('hidden');
    for (let r = 0; r < activeRound; ++r) {
      let game = games[r]
      let opponent = game.getAttribute('title');
      if (!opponent) opponent = '';
      let result = game.text().replace(/^\d+/, '');
      let listitem = `<div data-id="${id}" class="listitem"><span>R${r+1}</span><span>${opponent}</span><span>${result}</span></div>`
      $('#previous_games')[0].insertAdjacentHTML('beforeend', listitem);
    }
  }
}

function hideOpponents() {
  $('#unpairables').removeClass('hidden');
  $('#previous_games').addClass('hidden');
}

onLoad(()=>{
  // note - this handler is also in use for lists on Mac Mahon super groups and teams pages
  // CB TODO - there is some code cleaning to to around the listitems reuse and events:
  // the on('click') method should not define specific behaviors for this page, just dispatch custom events
  $('.listitem').on('click', e => {
    let listitem = e.target.closest('.listitem');
    let box = e.target.closest('.multi-select');
    let focusedBox = focused ? focused.closest('.multi-select') : undefined;
    if (e.shiftKey && typeof(focused) !== 'undefined' && box.getAttribute('id') === focusedBox.getAttribute('id')) {
      let from = focused.index('.listitem');
      let to = listitem.index('.listitem');
      if (from > to) {
        let tmp = from;
        from = to;
        to = tmp;
      }
      let children = box.childNodes.filter('.listitem');
      for (let j = from; j <= to; ++j) {  new Tablesort($('#players')[0]);
        children.item(j).addClass('selected');
        children.item(j).attr('draggable', true);
      }
    } else {
      if (e.detail === 1) {
        // single click
        focused = listitem.toggleClass('selected').attr('draggable', listitem.hasClass('selected'));
        if (box.getAttribute('id') === 'pairables') {
          if (focused.hasClass('selected')) showOpponents(focused);
          else hideOpponents();
        }
      } else {
        if (listitem.closest('#pairing-lists')) {
          // on pairing page
          if (listitem.closest('#paired')) {
            // double click
            hideOpponents()
            focused = listitem.attr('draggable', listitem.hasClass('selected'));
            editGame(focused);
          } else if (listitem.closest('#pairables')) {
            editPairable(focused);
          }
        }
        box.dispatchEvent(new CustomEvent('listitem-dblclk', { 'detail': parseInt(listitem.data('id')) }));
      }
    }
    box.dispatchEvent(new CustomEvent('listitems'));
  });
  $('#pair').on('click', e => {
    let parts = $('#pairables .selected.listitem').map(item => parseInt(item.data("id")));
    if (parts.length === 0) {
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
    let prevTable = form.val('prev-table');
    if (prevTable !== game.t && $(`.t[data-table="${game.t}"]`).length > 0) {
      if (!confirm(`This change will trigger a tables renumbering because the destination table #${game.t} is not empty. Proceed?`)) {
        return;
      }
    }
    api.putJson(`tour/${tour_id}/pair/${activeRound}`, game)
      .then(game => {
        if (game !== 'error') {
          document.location.reload();
        }
      });
  });
  document.on('dblclick', e => {
    if (!e.target.closest('.listitem')) {
      $('.listitem').removeClass('selected');
      focused = undefined;
      hideOpponents()
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
