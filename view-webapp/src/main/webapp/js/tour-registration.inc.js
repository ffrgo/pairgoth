const SEARCH_DELAY = 100;
let searchTimer = undefined;
let resultTemplate;
let searchResult;
let searchHighlight;
let manualRating;

function initSearch() {
  let needle = $('#needle')[0].value.trim();
  if (searchTimer) {
    clearTimeout(searchTimer);
  }
  searchTimer = setTimeout(() => {
    search(needle);
  }, SEARCH_DELAY);
}

function searchResultShown() {
  return !(typeof(searchHighlight) === 'undefined' || !searchResult || !searchResult.length || typeof(searchResult[searchHighlight]) === 'undefined')
}

function search(needle) {
  needle = needle.trim();
  if (needle && needle.length > 2) {
    let form = $('#player-form')[0];
    let search = {
      needle: needle,
      aga: form.val('aga'),
      egf: form.val('egf'),
      ffg: form.val('ffg'),
    }
    let country = form.val('countryFilter');
    if (country) search.countryFilter = country;
    let searchFormState = {
      countryFilter: country ? true : false,
      aga: search.aga,
      egf: search.egf,
      ffg: search.ffg
    };
    store('searchFormState', searchFormState);
    api.postJson('search', search)
      .then(result => {
        console.log(result)
        if (Array.isArray(result)) {
          searchResult = result
          let html = resultTemplate.render(result);
          $('#search-result')[0].innerHTML = html;
        } else console.log(result);
      });
  } else {
    $('#search-result').clear();
    searchTimer = undefined;
    searchResult = undefined;
    searchHighlight = undefined;
  }
}

function parseRank(rank) {
  let groups = /(\d+)([kd])/.exec(rank)
  if (groups) {
    let level = parseInt(groups[1]);
    let letter = groups[2];
    switch (letter) {
      case 'k': return -level;
      case 'd': return level - 1;
    }
  }
  return '';
}

function fillPlayer(player) {
  // hack UK / GB
  let country = player.country.toLowerCase();
  if ('uk' === country) country = 'gb';
  let form = $('#player-form')[0];
  form.val('name', player.name);
  form.val('firstname', player.firstname);
  console.log(country);
  form.val('country', country);
  form.val('club', player.club);
  form.val('rank', parseRank(player.rank));
  form.val('rating', player.rating);
  form.val('final', false);
  $('#needle')[0].value = '';
  initSearch();
  $('#register').focus();
}

function addPlayers() {
  let form = $('#player-form')[0];
  form.addClass('add');
  // keep preliminary/final status
  let status = form.val('final') || false;
  form.reset();
  // initial search checkboxes position
  ['countryFilter', 'aga', 'egf', 'ffg'].forEach(id => {
    let value = store(id);
    if (value !== null && typeof(value) !== 'undefined') {
      $(`#${id}`)[0].checked = value;
    }
  });
  form.val('final', status);
  $('#player').removeClass('edit').addClass('create');
  modal('player');
  $('#needle').focus();
  store('addingPlayers', true);
}

function bulkUpdate(players) {
  Promise.all(players.map(p => api.putJson(`tour/${tour_id}/part/${p.id}`, p)))
    .then((values) => window.location.reload());
}

let tableSort;

onLoad(() => {
  $('input.numeric').imask({
    mask: Number,
    scale: 0,
    min: 0,
    max: 4000
  });

  let prevSort = store('registrationSort');
  if (prevSort) {
    let columns = $('#players thead th');
    columns.forEach(th => {
      th.removeAttribute('data-sort-default');
      th.removeAttribute('aria-sort');
    })
    prevSort.forEach(i => {
      let col = columns[Math.abs(i)];
      col.setAttribute('data-sort-default', '1');
      if (i < 0) {
        // take into account TableSort initiailization bug
        col.setAttribute('aria-sort', 'ascending');
      }
    });
  }
  tableSort = new Tablesort($('#players')[0]);
  $('#players').on('afterSort', e => {
    let sort = [];
    $('#players thead th').forEach((th, i) => {
      let attr = th.attr('aria-sort');
      if (attr) {
        let dir = i;
        if (attr === 'descending') dir = -dir;
        sort.push(dir);
      }
    });
    store('registrationSort', sort);
  });

  $('#add').on('click', e => {
    addPlayers();
  });
  $('#cancel-register').on('click', e => {
    e.preventDefault();
    close_modal();
    searchHighlight = undefined;
    return false;
  });

  $('#register').on('click', e => {
    let form = e.target.closest('form');
    let valid = true;
    let required = ['name', 'firstname', 'country', 'club', 'rank', 'rating'];
    for (let name of required) {
      let ctl = form.find(`[name=${name}]`)[0];
      let val = ctl.value;
      if (val) {
        ctl.setCustomValidity('');
      } else {
        valid = false;
        ctl.setCustomValidity(msg('required_field'));
      }
    }
    if (!valid) {
      $('#player :invalid').forEach(elem => elem.reportValidity());
      return;
    }
    // $('#player-form')[0].requestSubmit() not working?!
    $('#player-form')[0].dispatchEvent(new CustomEvent('submit', {cancelable: true}));
  });
  $('#player-form').on('submit', e => {
    e.preventDefault();
    let form = $('#player-form')[0];
    let player = {
      name: form.val('name'),
      firstname: form.val('firstname'),
      rating: form.val('rating'),
      rank: form.val('rank'),
      country: form.val('country'),
      club: form.val('club'),
      skip: form.find('input.participation').map((input,i) => [i+1, input.checked]).filter(arr => !arr[1]).map(arr => arr[0]),
      final: form.val('final')
    }
    if (form.hasClass('add')) {
      api.postJson(`tour/${tour_id}/part`, player)
        .then(player => {
          console.log(player)
          if (player !== 'error') {
            store('registrationSuccess', true);
            window.location.reload();
          }
        });
    } else {
      let id = form.val('id');
      player['id'] = id;
      api.putJson(`tour/${tour_id}/part/${id}`, player)
        .then(player => {
          if (player !== 'error') {
            store('registrationSuccess', true);
            window.location.reload();
          }
        });
    }
  });
  $('#players > tbody > tr').on('click', e => {
    let regStatus = e.target.closest('td.reg-status');
    if (regStatus) return;
    let id = e.target.closest('tr').attr('data-id');
    api.getJson(`tour/${tour_id}/part/${id}`)
      .then(player => {
        if (player !== 'error') {
          let form = $('#player-form')[0];
          form.val('id', player.id);
          form.val('name', player.name);
          form.val('firstname', player.firstname);
          form.val('rating', player.rating);
          form.val('rank', player.rank);
          form.val('country', player.country.toLowerCase());
          form.val('club', player.club);
          form.val('final', player.final);
          if (player.final) $('#final-reg').addClass('final');
          else $('#final-reg').removeClass('final');
          for (r = 1; r <= tour_rounds; ++r) {
            form.val(`r${r}`, !(player.skip && player.skip.includes(r)));
          }
          form.removeClass('add');
          $('#player').removeClass('create').addClass('edit');
          modal('player');
        }
      });
  });
  resultTemplate = jsrender.templates($('#result')[0]);
  $('#needle').on('input', e => {
    initSearch();
  });
  $('#clear-search').on('click', e => {
    $('#needle')[0].value = '';
    $('#search-result').clear();
  });
  let searchFromState = store('searchFormState')
  if (searchFromState) {
    for (let id of ["countryFilter", "aga", "egf", "ffg"]) {
      $(`#${id}`)[0].checked = searchFromState[id];
    }
  }
  $('.toggle').on('click', e => {
    let chk = e.target.closest('.toggle');
    let checkbox = chk.find('input')[0];
    checkbox.checked = !checkbox.checked;
    let id = checkbox.getAttribute('id');
    let value = checkbox.checked;
    store(id, value);
    initSearch();
  });
  document.on('click', e => {
    let resultLine = e.target.closest('.result-line');
    if (resultLine) {
      let index = e.target.closest('.result-line').data('index');
      fillPlayer(searchResult[index]);
    }
  });
  $('#unregister').on('click', e => {
    let form = $('#player-form')[0];
    let id = form.val('id');
    api.deleteJson(`tour/${tour_id}/part/${id}`)
      .then(ret => {
        if (ret !== 'error') {
          window.location.reload();
        }
    });
  });
  $('#reg-status').on('click', e => {
    let current = $('#final-reg').hasClass('final');
    if (current) {
      $('input[name="final"]')[0].value = false;
      $('#final-reg').removeClass('final');
    } else {
      $('input[name="final"]')[0].value = true;
      $('#final-reg').addClass('final');
    }
  });
  $('.reg-status').on('click', e => {
    let cell = e.target.closest('td');
    let tr = e.target.closest('tr');
    let id = tr.data('id');
    let newStatus = !cell.hasClass('final');
    api.putJson(`tour/${tour_id}/part/${id}`, {
      id: id,
      final: newStatus
    }).then(player => {
        if (player !== 'error') {
          cell.toggleClass('final');
          standingsUpToDate = false;
          pairablesUpToDate = false;
        }
      });
    e.preventDefault();
    return false;
  });
  $('#filter').on('input', (e) => {
    let input = e.target;
    let value = input.value.toUpperCase();
    if (value === '') $('tbody > tr').removeClass('hidden');
    else $('tbody > tr').forEach(tr => {
      let txt = tr.data('text');
      if (txt && txt.indexOf(value) === -1) tr.addClass('hidden');
      else tr.removeClass('hidden');
    });
  });
  manualRating = ($('#player input[name="rating"]')[0].value !== '');
  $('#player input[name="rating"]').on('input', e=>{
    manualRating = true;
  });
  $('#player select[name="rank"]').on('input', e=>{
    let rank = e.target.value;
    let ratingCtl = $('#player input[name="rating"]')[0];
    if (!manualRating) {
      ratingCtl.value = 2050 + 100 * rank;
    }
  });
  $('#filter-box i').on('click', e => {
    $('#filter')[0].value = '';
    $('tbody > tr').removeClass('hidden');
  });
  $('#edit-macmahon-groups').on('click', e => {
    modal('macmahon-groups');
    store('macmahonGroups', true);
  });
  if (store('addingPlayers')) {
    addPlayers();
    if (store('registrationSuccess')) {
      $('#player').addClass('successful');
      setTimeout(() => $('#player .success-feedback').addClass('done'), 0);
    }
    store.remove('registrationSuccess');
  }
  if (store('macmahonGroups')) {
    modal('macmahon-groups');
  }
  // mac mahon groups...
  $('#under-to-top').on('click', e => {
    let players = $('#under-group .selected').map(item => (
    {
      id: parseInt(item.data("id")),
      mmsCorrection: parseInt(item.data("correction")) + 1
    }));
    bulkUpdate(players);
  });
  $('#top-to-under').on('click', e => {
    let players = $('#top-group .selected').map(item => (
      {
        id: parseInt(item.data("id")),
        mmsCorrection: parseInt(item.data("correction")) - 1
      }));
    bulkUpdate(players);
  });
  $('#top-to-super').on('click', e => {
    let players = $('#top-group .selected').map(item => (
      {
        id: parseInt(item.data("id")),
        mmsCorrection: parseInt(item.data("correction")) + 1
      }));
    bulkUpdate(players);
  });
  $('#super-to-top').on('click', e => {
    let players = $('#super-group .selected').map(item => (
      {
        id: parseInt(item.data("id")),
        mmsCorrection: parseInt(item.data("correction")) - 1
      }));
    bulkUpdate(players);
  });
  $('#reset-macmahon-groups').on('click', e => {
    let players = $('#macmahon-groups .listitem').map(item => (
      {
        id: parseInt(item.data("id")),
        mmsCorrection: 0
      }));
    bulkUpdate(players);
  });
});
