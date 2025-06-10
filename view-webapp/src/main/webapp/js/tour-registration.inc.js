const SEARCH_DELAY = 100;
let searchTimer = undefined;
let resultTemplate;
let searchResult;
let searchHighlight;
let manualRating = false;
let manualRank = false;

function searchResultShown() {
  return !(!searchResult || searchResult.length === 0 || $('#search-result').hasClass('hidden'));
}

function browseScroll() {
  $('#search-result .result-line').removeClass('spotted');
  searchHighlight = -1;
  let scrollTo = $('#needle')[0].value.trim();
  while (scrollTo.length > 0) {
    let target = $(`#search-result .result-line[data-name^="${scrollTo}"i]`);
    if (target.length > 0) {
      target.addClass('spotted');
      let first = target[0];
      first.scrollIntoView({behavior: "smooth", block: "center"});
      searchHighlight = Array.prototype.indexOf.call(first.parentNode.children, first);
      first.addClass('highlighted');
      break;
    }
    scrollTo = scrollTo.substring(0, scrollTo.length - 1);
  }
}

function clearSearch() {
  $('#search-result').clear();
  searchTimer = undefined;
  searchResult = undefined;
  searchHighlight = undefined;
}

function search(needle) {
  needle = needle.trim();
  if (needle && (needle === '*' || needle.length > 2)) {
    let form = $('#search-form')[0];
    let search = {
      needle: needle,
      // aga: form.val('aga'),
      egf: form.val('egf'),
      ffg: form.val('ffg')
    }
    let country = form.val('countryFilter');
    if (country) search.countryFilter = country;
    api.postJson('search', search)
      .then(result => {
        if (Array.isArray(result)) {
          searchResult = result
          let html = resultTemplate.render(result);
          $('#search-result')[0].innerHTML = html;
          if (needle === '*') {
            setTimeout(() => browseScroll(), 0);
          } else {
            $('#search-result').removeClass('hidden');
            let scrollable = $('#player .popup-body');
            scrollable[0].scrollTop = 0;
          }
        } else console.log(result);
      });
  } else {
    // needle is empty (and by construction we can't be in browse mode) - clear search result
    clearSearch();
  }
}

function initSearch() {
  let needle = $('#needle')[0].value.trim();
  if (searchTimer) {
    clearTimeout(searchTimer);
  }
  searchTimer = setTimeout(() => {
    let form = $('#search-form')[0];
    let browsing = !!form.val('browse');
    if (!browsing || !searchResult) {
      $('#search-result .result-line').removeClass('spotted');
      search(browsing ? '*' : needle);
    } else if (browsing) {
      if (needle.length) {
        $('#search-result').removeClass('hidden');
        browseScroll();
      } else {
        $('#search-result').addClass('hidden');
      }
    } else {
      $('#search-result').removeClass('hidden');
    }
  }, SEARCH_DELAY);
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

function displayRank(rank) {
  rank = parseInt(rank);
  return rank < 0 ? `${-rank}k` : `${rank + 1}d`;
}

function fillPlayer(player) {
  // hack UK / GB
  let country = player.country.toLowerCase();
  if ('uk' === country) country = 'gb';
  let form = $('#player-form')[0];
  form.val('name', player.name);
  form.val('firstname', player.firstname);
  form.val('country', country);
  form.val('club', player.club);
  form.val('rank', parseRank(player.rank));
  form.val('rating', player.rating);
  form.val('final', false);
  form.val('ffg_id', player.ffg);
  form.val('egf_id', player.egf);
  $('#needle')[0].value = '';
  initSearch();
  $('#register').removeClass('disabled').focus();
}

function addPlayers() {
  let form = $('#player-form')[0];
  // keep preliminary/final status
  let status = form.val('final') || false;
  form.reset();
  form.val('final', status);
  $('#player').removeClass('edit').addClass('create');
  $('#register').removeClass('disabled');
  modal('player');
  setTimeout(() => {
    $('#needle').focus();

  }, 100);
  store('addingPlayers', true);
}

function bulkUpdate(players) {
  Promise.all(players.map(p => api.putJson(`tour/${tour_id}/part/${p.id}`, p)))
    .then((values) => window.location.reload());
}

function navigateResults(ev) {
  console.log(`searchHighlight = ${searchHighlight}`);
  let lines = $('.result-line');
  lines.removeClass('highlighted');
  searchHighlight = Math.max(searchHighlight, 0);
  searchHighlight = Math.min(searchHighlight, lines.length - 1);
  let targeted = lines[searchHighlight];
  if (targeted) {
    targeted.addClass('highlighted');
    // let's scroll into view manually, since DOM API scrollIntoView() is fooled by the sticky header.
    let scrollContainer = targeted.closest('.popup-body');
    // TODO - the "24" is the search-result padding. Avoid hardcoding it.
    let scrollTop = scrollContainer.scrollTop + 24;
    let scrollBottom = scrollContainer.scrollTop + scrollContainer.clientHeight - 24 - $('#search-form')[0].offsetHeight;
    let top = targeted.offsetTop;
    let bottom = top + targeted.offsetHeight;
    if (top < scrollTop) {
      scrollContainer.scrollTop -= (scrollTop - top);
    } else if (bottom > scrollBottom) {
      scrollContainer.scrollTop += (bottom - scrollBottom);
    }
  }
  if (ev) {
    ev.preventDefault();
    ev.cancelBubble = true;
    ev.stopPropagation();
  }
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
  $('#search-form').on('submit', e => {
    // this form is never meant to be submitted
    e.preventDefault();
    return false;
  });
  $('#player-form').on('submit', e => {
    e.preventDefault();
    if ($('#register').hasClass('disabled')) {
      // user pressed enter
      return false;
    }
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
    for (let origin of ['egf', 'ffg']) {
      let value = form.val(`${origin}_id`);
      if (value) {
        player[origin] = value;
      }
    }
    if ($('#player').hasClass('create')) {
      api.postJson(`tour/${tour_id}/part`, player)
        .then(player => {
          if (player !== 'error') {
            store('registrationSuccess', true);
            store('scrollIntoView', player.id)
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
            store('scrollIntoView', id)
            window.location.reload();
          }
        });
    }
  });
  $('#players > tbody > tr').on('click', e => {
    let regStatus = e.target.closest('td.reg-status, td.participating');
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
          $('#player').removeClass('create').addClass('edit');
          $('#register').addClass('disabled');
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
    initSearch();
  });
  let searchFormState = store('searchFormState')
  if (searchFormState) {
    for (let id of ["countryFilter", /* "aga", */ "egf", "ffg", "browse"]) {
      let ctl = $(`#${id}`);
      if (ctl.length !== 0) {
        ctl[0].checked = searchFormState[id];
      }
    }
  }
  $('#search-form .toggle').on('click', e => {
    let chk = e.target.closest('.toggle');
    let checkbox = chk.find('input')[0];
    checkbox.checked = !checkbox.checked;
    //
    // let id = checkbox.getAttribute('id');
    // let value = checkbox.checked;
    // store(id, value);
    let form = $('#search-form')[0];
    let searchFormState = {
      countryFilter: !!form.val('countryFilter'),
      // aga: search.aga,
      egf: !!form.val('egf'),
      ffg: !!form.val('ffg'),
      browse: !!form.val('browse')
    };
    store('searchFormState', searchFormState);
    clearSearch();
    initSearch();
  });
  $('#reglist-mode').on('change', e => {
    let mode = e.target.value;
    $('td.reg-status').forEach(node => node.parentNode.removeClass('filtered'));
    if (mode === 'prelim') {
      $('td.reg-status.final').forEach(node => node.parentNode.addClass('filtered'));
    } else if (mode === 'final') {
      $('td.reg-status:not(.final)').forEach(node => node.parentNode.addClass('filtered'));
    }
  });
  document.on('click', e => {
    let resultLine = e.target.closest('.result-line');
    if (resultLine) {
      let index = e.target.closest('.result-line').data('index');
      fillPlayer(searchResult[index]);
      return;
    }
    let tab = document.location.hash;
    if (store('addingPlayers') && tab === '#registration') {
      let modal = e.target.closest('#player');
      if (!modal) {
        let button = e.target.closest('button');
        if (!button) {
          if (searchResultShown()) {
            $('#needle')[0].value = '';
            initSearch();
          } else {
            close_modal();
          }
        }
      }
    }
  });
  $('#unregister').on('click', e => {
    let form = $('#player-form')[0];
    let id = form.val('id');
    let confirmMessage = $('#unregister-player').text();
    if (confirm(confirmMessage)) {
      api.deleteJson(`tour/${tour_id}/part/${id}`)
        .then(ret => {
          if (ret !== 'error') {
            window.location.reload();
          }
        });
    }
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
        tr.toggleClass('final');
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
  manualRating = ($('#rating')[0].value !== '');
  manualRank = ($('#rank')[0].value !== '');
  $('#player input[name="rating"]').on('input', e=>{
    manualRating = true;
  });
  $('#player select[name="rank"]').on('input', e=>{
    let rank = e.target.value;
    let ratingCtl = $('#player input[name="rating"]')[0];
    if (!$('#rating')[0].value || !manualRating) {
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
  }
  store.remove('registrationSuccess');
  let scrollIntoView = store('scrollIntoView');
  if (scrollIntoView) {
    let row = $(`tr[data-id="${scrollIntoView}"`);
    if (row.length !== 0) {
      row.addClass('highlighted');
      store.remove('scroll');
      setTimeout(()=>{
        row[0].scrollIntoView({ behavior: "smooth", block: "center" });
      }, 100);
    }
  }
  store.remove('scrollIntoView');
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
  $('.player-fields').on('change input', e => {
    $('#register').removeClass('disabled');
  });
  $('.participation label').on('click', e => {
    let part = e.target;
    let id = part.closest('tr').data('id');
    let round = parseInt(part.text());
    let skip = new Set(part.closest('.participation').find('label.red').map(it => parseInt(it.innerText)));
    if (skip.has(round)) skip.delete(round);
    else skip.add(round);
    api.putJson(`tour/${tour_id}/part/${id}`, {
      id: id,
      skip: Array.from(skip)
    }).then(player => {
      if (player !== 'error') {
        part.toggleClass('red');
        part.toggleClass('green');
        standingsUpToDate = false;
        pairablesUpToDate = false;
      }
    });
    e.preventDefault();
    return false;
  });
  $('#rating').on('input', e => {
    if (!$('#rank')[0].value || !manualRank) {
      let rank = (e.target.value - 2050) / 100;
      console.log(rank);
      $('#rank')[0].value = `${rank}`;
    }
    return true;
  });
  $('#rank').on('input', e => {
    manualRank = true;
  });
});
