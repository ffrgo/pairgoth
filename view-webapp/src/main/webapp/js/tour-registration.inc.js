const SEARCH_DELAY = 100;
let searchTimer = undefined;
let resultTemplate;
let searchResult;
let searchHighlight;

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
  let form = $('#player-form')[0];
  form.val('name', player.name);
  form.val('firstname', player.firstname);
  form.val('country', player.country.toLowerCase());
  form.val('club', player.club);
  form.val('rank', parseRank(player.rank));
  form.val('rating', player.rating);
  $('#needle')[0].value = '';
  initSearch();
  $('#register').focus();
}

onLoad(() => {
  $('input.numeric').imask({
    mask: Number,
    scale: 0,
    min: 0,
    max: 4000
  });
  new Tablesort($('#players')[0]);
  $('#add').on('click', e => {
    let form = $('#player-form')[0];
    form.addClass('add');
    // $('#player-form input.participation').forEach(chk => chk.checked = true);
    form.reset();
    $('#player').removeClass('edit').addClass('create');
    modal('player');
    $('#needle').focus();
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
    if (!valid) return;
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
      skip: form.find('input.participation').map((input,i) => [i+1, input.checked]).filter(arr => !arr[1]).map(arr => arr[0])
    }
    if (form.hasClass('add')) {
      api.postJson(`tour/${tour_id}/part`, player)
        .then(player => {
          if (player !== 'error') {
            window.location.reload();
          }
        });
    } else {
      let id = form.val('id');
      player['id'] = id;
      api.putJson(`tour/${tour_id}/part/${id}`, player)
        .then(player => {
          if (player !== 'error') {
            window.location.reload();
          }
        });
    }
  });
  $('#players > tbody > tr').on('click', e => {
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
          form.val('country', player.country);
          form.val('club', player.club);
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
});
