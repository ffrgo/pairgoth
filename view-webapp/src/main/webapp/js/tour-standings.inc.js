function publish(format, extension, encoding) {
  let form = $('#tournament-infos')[0];
  let shortName = form.val('shortName');
  let hdrs = headers();
  hdrs['Accept'] = `${format};charset=${encoding}`
  fetch(`api/tour/${tour_id}/standings/${activeRound}`, {
    headers: hdrs
  }).then(resp => {
    if (!resp.ok) throw "publish error";
    let serverName = filenameFromContentDisposition(resp.headers.get('Content-Disposition'));
    return resp.arrayBuffer().then(bytes => [bytes, serverName]);
  }).then(([bytes, serverName]) => {
    let blob = new Blob([bytes], { type: `text/plain;charset=${encoding}` });
    downloadFile(blob, serverName || `${shortName}.${extension}`);
    close_modal();
  }).catch(err => showError(err));
}

function filenameFromContentDisposition(header) {
  if (!header) return null;
  let m = header.match(/filename\*=(?:UTF-8'')?([^;]+)/i);
  if (m) try { return decodeURIComponent(m[1].trim().replace(/^"|"$/g, '')); } catch (e) { /* fall through */ }
  m = header.match(/filename="?([^";]+)"?/i);
  return m ? m[1].trim() : null;
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
  $('.publish-website').on('click', e => {
    let form = $('#tournament-infos')[0];
    let code = form.val('shortName');
    if (!code) {
      showError('Tournament short name is required for publishing');
      return;
    }
    let teamHtml = $('#standings-table')[0]?.outerHTML;
    if (!teamHtml) {
      showError('No standings to publish');
      return;
    }
    // TEAM* tournaments expose a second table (#individual-standings-table); ship both, wrapped to distinguish.
    let individualHtml = $('#individual-standings-table')[0]?.outerHTML;
    let payload = individualHtml
      ? `<div class="standings-section team-standings">${teamHtml}</div>` +
        `<div class="standings-section individual-standings">${individualHtml}</div>`
      : teamHtml;
    close_modal();
    api.postBody(`webhook/publish/standings/${code}/${activeRound}`, payload, 'text/html')
      .then(data => {
        if (data === 'error') return;
        if (!data.status) showError(data.message || 'Publish failed');
        else showSuccess(`Standings for round ${activeRound} published to website`);
      });
  });
  $('#freeze').on('click', e => {
    if (confirm("Once frozen, names, levels and even pairings can be changed, but the scores and the standings will stay the same. Freeze the standings?")) {
      freeze()
    }
  });
});
