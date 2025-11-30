function publish(format, extension, encoding) {
  let form = $('#tournament-infos')[0];
  let shortName = form.val('shortName');
  let hdrs = headers();
  hdrs['Accept'] = `${format};charset=${encoding}`
  fetch(`api/tour/${tour_id}/standings/${activeRound}`, {
    headers: hdrs
  }).then(resp => {
    if (resp.ok) return resp.arrayBuffer()
    else throw "publish error"
  }).then(bytes => {
    let blob = new Blob([bytes], { type: `text/plain;charset=${encoding}` });
    downloadFile(blob, `${shortName}.${extension}`);
    close_modal();
  }).catch(err => showError(err));
}

function publishHtml() {
  let html = $('#standings-table')[0].outerHTML;
  let form = $('#tournament-infos')[0];
  let shortName = form.val('shortName');
  let blob = new Blob(['\uFEFF', html], {type: 'text/html;charset=utf-8'});
  downloadFile(blob, `${shortName}-standings-R${activeRound}.html`);
  close_modal();
}

function freeze() {
  api.put(`tour/${tour_id}/standings/${activeRound}`, {}
  ).then(resp => {
    if (resp.ok) {
      document.location.reload();
    }
    else throw "freeze error"
  }).catch(err => showError(err));
}

onLoad(() => {
  new Tablesort($('#standings-table')[0]);
  $('.criterium').on('click', e => {
    let alreadyOpen = e.target.closest('select');
    if (alreadyOpen) return;
    let select = e.target.closest('.criterium').find('select');
    $('.criterium select').removeClass('active');
    select.toggleClass('active');
  });
  document.on('click', e => {
    let crit = e.target.closest('.criterium');
    if (!crit) $('.criterium select').removeClass('active');
  });
  $('.criterium select').on('input', e => {
    let select = e.target.closest('select');
    let info = select.previousElementSibling;
    info.textContent = select.selectedOptions[0].value;
    $('.criterium select').removeClass('active');
    $('#params-submit').removeClass('hidden');
  });
  $('#params-form .cancel.button').on('click', e => {
    $('.criterium select').removeClass('active').forEach(elem => {
      elem.value = elem.data('initial');
      elem.previousElementSibling.textContent = elem.value;
    });
    $('#params-submit').addClass('hidden');
  });
  $('#params-form').on('submit', e => {
    if (!$('#params-submit').hasClass('hidden')) {
      api.putJson(`tour/${tour_id}`, {
        pairing: {
          placement: $('.criterium select').map(elem => elem.value)
        }
      }).then(rst => {
        if (rst !== 'error') {
          document.location.reload();
        }
      })
    }
    e.preventDefault();
    return false;
  });
  $('#publish').on('click', e => {
    modal('publish-modal');
  });
/*
  $('#publish-modal').on('click', e => {
    close_modal();
  });
*/
  $('.publish-ffg').on('click', e => {
    publish('application/ffg', 'tou', 'iso-8859-1');
  });
  $('.publish-egf').on('click', e => {
    publish('application/egf', 'h' + (typeof(correction) === 'number' && correction > 0 ? correction : 9 ), 'iso-8859-1');
  });
  $('.publish-csv').on('click', e => {
    publish('text/csv', 'csv', 'utf-8');
  });
  $('.publish-html').on('click', e => {
    publishHtml();
  });
  $('.publish-website').on('click', async e => {
    let connectorUrl = prefs.get('connectorUrl');
    let connectorSecret = prefs.get('connectorSecret');

    if (!connectorUrl) {
      showError('Website connector not configured. Go to Settings to configure it.');
      return;
    }

    let form = $('#tournament-infos')[0];
    let code = form.val('shortName');
    if (!code) {
      showError('Tournament short name is required for publishing');
      return;
    }

    // Get standings HTML
    let standingsHtml = $('#standings-table')[0]?.outerHTML;
    if (!standingsHtml) {
      showError('No standings to publish');
      return;
    }

    spinner(true);
    close_modal();

    try {
      let response = await fetch(`${connectorUrl}/standings/${code}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'text/html',
          'X-Pairgoth-Secret': connectorSecret
        },
        body: standingsHtml
      });

      spinner(false);

      if (!response.ok) {
        if (response.status === 401) {
          showError('Invalid connector secret');
        } else if (response.status === 404) {
          showError(`Event '${code}' not found on website`);
        } else {
          showError('Publish failed: ' + response.status);
        }
        return;
      }

      let data = await response.json();
      if (data.status) {
        showSuccess('Standings published to website');
      } else {
        showError(data.message || 'Publish failed');
      }
    } catch (err) {
      spinner(false);
      showError('Publish error: ' + err.message);
    }
  });
  $('#freeze').on('click', e => {
    if (confirm("Once frozen, names, levels and even pairings can be changed, but the scores and the standings will stay the same. Freeze the standings?")) {
      freeze()
    }
  });
});
