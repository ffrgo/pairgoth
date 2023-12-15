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
          modal('player');
        }
      });
  });
  $('#needle').on('input', e => {
    let needle = $('#needle')[0].value;
    if (needle && needle.length > 2) {
      let form = $('#player-form')[0];
      let search = {
        needle: needle,
        aga: form.val('aga'),
        egf: form.val('egf'),
        ffg: form.val('ffg')
      }
      api.postJson('search', search)
        .then(result => {
          console.log(result);
        })
    } else $('#search-result').addClass('hidden');
  });
});
