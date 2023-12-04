onLoad(() => {
  $('input.numeric').imask({
    mask: Number,
    scale: 0,
    min: 0,
    max: 4000
  });
  $('#register').on('click', e => {
    let form = e.target.closest('form');
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
      club: form.val('club')
    }
    console.log(player);
    api.postJson(`tour/${tour_id}/part`, player)
      .then(player => {
        console.log(player)
        if (player !== 'error') {
          window.location.reload();
        }
      });
  });
});
