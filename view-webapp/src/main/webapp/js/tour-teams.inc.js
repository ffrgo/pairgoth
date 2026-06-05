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
      let all = true;
      let any = false;
      for (let rst of rsts) {
        all = all && rst.success;
        any = any || rst.success;
        if (!rst.success) console.error(rst.error)
      }
      if (any) document.location.reload();
    });
}

function join(players, team) {
  console.log(team)
  console.log(teams.get(team))
  api.putJson(`tour/${tour_id}/team/${team}`, {
    "players": teams.get(team).players.concat(players)
  }).then(rst => {
    if (rst !== 'error') {
      document.location.reload();
    }
  });
}

function leave(teamId, playerId) {
  let team = teams.get(teamId);
  let index = team.players.indexOf(playerId);
  if (index > -1) {
    let newPlayers = team.players.slice();
    newPlayers.splice(index, 1);
    api.putJson(`tour/${tour_id}/team/${teamId}`, {
      "players": newPlayers
    }).then(rst => {
      if (rst !== 'error') {
        document.location.reload();
      }
    });
  }
}

function showTeam(teamId) {
  let team = teams.get(teamId);
  let comp = $('#composition')[0];
  comp.clearChildren();
  $('#composition').removeClass('hidden');
  $('#composition').data('id', teamId);
  // real, editable title (team name + pencil), replacing the former .multi-select pseudo-title
  let header = document.createElement('div');
  header.className = 'comp-title';
  let nameSpan = document.createElement('span');
  nameSpan.className = 'comp-name';
  nameSpan.textContent = team.name;
  header.appendChild(nameSpan);
  header.insertAdjacentHTML('beforeend', '<i class="ui pencil edit icon"></i>');
  comp.appendChild(header);
  for (i = 0; i < team.players.length; ++i) {
    let listitem = `<div data-id="${team.players[i]}" class="listitem"><span>${team.names[i]}</span><span>${displayRank(team.ranks[i])}&nbsp;<i class="ui red sign out icon"></i></span></div>`
    comp.insertAdjacentHTML('beforeend', listitem);
  }
}

// Inline-rename the team from the composition header: pencil/title click swaps the name for an input;
// Enter (or blur) saves, Escape cancels.
function startRenameTeam() {
  let teamId = parseInt($('#composition').data('id'));
  let titleEl = $('#composition .comp-title')[0];
  if (!titleEl || titleEl.querySelector('input')) return;
  let current = teams.get(teamId).name;
  let input = document.createElement('input');
  input.className = 'comp-name-edit';
  input.type = 'text';
  input.value = current;
  titleEl.clearChildren();
  titleEl.appendChild(input);
  input.focus();
  input.select();
  let done = false;
  let finish = save => {
    if (done) return;
    done = true;
    let v = input.value.trim();
    if (save && v && v !== current) renameTeam(teamId, v);  // PUT → reload on success
    else showTeam(teamId);                                  // restore the header
  };
  input.addEventListener('keydown', ev => {
    if (ev.key === 'Enter') { ev.preventDefault(); finish(true); }
    else if (ev.key === 'Escape') { ev.preventDefault(); finish(false); }
  });
  input.addEventListener('blur', () => finish(true));
}

function renameTeam(teamId, name) {
  let team = teams.get(teamId);
  // teamFromJson requires the players array, so send it alongside the new name
  api.putJson(`tour/${tour_id}/team/${teamId}`, { name: name, players: team.players })
    .then(rst => { if (rst !== 'error') window.location.reload(); });
}

onLoad(() => {
  $('#teamup').on('click', e => {
    let rows = $('#teamables .selected.listitem')
    let players = rows.map(item => parseInt(item.data("id")));
    if (players.length !== 0) teamUp(players);
  });
  $('#split').on('click', e => {
    let rows = $('#teams .selected.listitem')
    if (rows.length == 0) {
      $('#teams .listitem').addClass('selected');
      rows = $('#teams .selected.listitem');
    }
    let teams = rows.map(item => parseInt(item.data("id")));
    if (teams.length !== 0) split(teams);
  });
  $('#join').on('click', e => {
    let rows = $('#teamables .selected.listitem');
    let players = rows.map(item => parseInt(item.data("id")));
    let teams = $('#teams .selected.listitem');
    if (players.length !== 0 && teams.length === 1) {
      join(players, parseInt(teams[0].data("id")));
    }
  });
  $('#team-name').on('input', () => {
    if ($('#team-name')[0].value === '') {
      $('#teamup').addClass('disabled');
    } else if ($('#teamables .selected.listitem').length > 0) {
      $('#teamup').removeClass('disabled');
    }
    $('#team-name').data('manual', true);
  });
  $('#teamables, #teams').on('listitems', () => {
    let players = $('#teamables .selected.listitem');
    let teams = $('#teams .selected.listitem');
    if (players.length === 0) {
      $('#teamup').addClass('disabled');
      $('#join').addClass('disabled');
      if(!$('#team-name').data('manual')) {
        $('#team-name')[0].value = '';
      }
    } else {
      if(!$('#team-name').data('manual')) {
        $('#team-name')[0].value = players.map(row => row.data('name')).join('-');
      }
      if ($('#team-name')[0].value !== '') {
        $('#teamup').removeClass('disabled');
      }
      if (teams.length === 1) {
        $('#join').removeClass('disabled');
      } else {
        $('#join').addClass('disabled');
      }
    }
    if (teams.length === 0) {
      $('#split').addClass('disabled');
      $('#composition').addClass('hidden');
    } else {
      $('#split').removeClass('disabled');
      if (focused && focused.closest('.multi-select').attr('id') === 'teams') {
        showTeam(parseInt(focused.data('id')));
      }
    }
  });
  /* If we want double-click...
  $('#teams').on('listitem-dblclk', e => {
    console.log(teams.get(e.detail));
  });
   */
  $('#composition').on('click', e => {
    // remove a player from the team (red sign-out icon on a member line)
    if (e.target.matches('.listitem i')) {
      let team = parseInt($('#composition').data('id'));
      let player = parseInt(e.target.closest('.listitem').data('id'));
      leave(team, player);
      return;
    }
    // edit the team name (click the title line / pencil)
    if (e.target.closest('.comp-title')) {
      startRenameTeam();
    }
  });
});
