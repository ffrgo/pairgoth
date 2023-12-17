const SEARCH_DELAY = 100;
let searchTimer = undefined;
let resultTemplate;

function initSearch() {
  let needle = $('#needle')[0].value;
  if (searchTimer) {
    clearTimeout(searchTimer);
  }
  searchTimer = setTimeout(() => {
    search(needle);
  }, SEARCH_DELAY);
}

function search(needle) {
  needle = needle.trim();
  console.log(needle)
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
    console.log(search)
    api.postJson('search', search)
      .then(result => {
        if (Array.isArray(result)) {
          console.log(result)
          let html = resultTemplate.render(result);
          $('#search-result')[0].innerHTML = html;
        } else console.log(result);
      })
  } else $('#search-result').clear();

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
  });
  $('#cancel-register').on('click', e => {
    e.preventDefault();
    close_modal();
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
  });
  $('#player-form').on('submit', e => {
    e.preventDefault();
    let form = e.target;
    let player = {
      name: form.val('name'),
      firstname: form.val('firstname'),
      rating: form.val('rating'),
      rank: form.val('rank'),
      country: form.val('country'),
      club: form.val('club'),
      skip: form.find('input.participation').map((input,i) => [i+1, input.checked]).filter(arr => !arr[1]).map(arr => arr[0])
    }
    console.log(player);
    if (form.hasClass('add')) {
      api.postJson(`tour/${tour_id}/part`, player)
        .then(player => {
          console.log(player)
          if (player !== 'error') {
            window.location.reload();
          }
        });
    } else {
      let id = form.val('id');
      player['id'] = id;
      api.putJson(`tour/${tour_id}/part/${id}`, player)
        .then(player => {
          console.log(player)
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
});
