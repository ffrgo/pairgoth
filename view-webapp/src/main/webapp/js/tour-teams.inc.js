function teamUp(players) {
  api.postJson(`tour/${tour_id}/team`, {
    "name": $('#team-name')[0].value,
    "players": players
  }).then(rst => {
    if (rst !== 'error') {
      document.location.reload();
    }
  });
}

function split(teams) {
  let promises = teams.map(team => api.deleteJson(`tour/${tour_id}/team/${team}`));
  Promise.all(promises)
    .then(rsts => {
      for (let rst of rsts) {
        if (!rst.success) console.error(rst.error)
      }
      document.location.reload();
    });
}

onLoad(() => {
  $('#teamup').on('click', e => {
    let rows = $('#teamables .selected.listitem')
    let players = rows.map(item => parseInt(item.data("id")));
    if (players.length !== 0) teamUp(players);
  });
  $('#split').on('click', e => {
    let rows = $('#teams .selected.listitem')
    let teams = rows.map(item => parseInt(item.data("id")));
    if (teams.length !== 0) split(teams);
  });
  $('#teamables').on('listitems', () => {
    let rows = $('#teamables .selected.listitem');
    if (rows.length === teamSize) {
      $('#team-name')[0].value = rows.map(row => row.data('name')).join('-');
      $('#teamup').removeClass('disabled');
    } else {
      $('#team-name')[0].value = '';
      $('#teamup').addClass('disabled');
    }
  });
});