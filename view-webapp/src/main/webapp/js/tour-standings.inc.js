function publish(format, extension) {
  let form = $('#tournament-infos')[0];
  let shortName = form.val('shortName');
  let hdrs = headers();
  hdrs['Accept'] = `application/${format}`
  fetch(`api/tour/${tour_id}/standings/${activeRound}`, {
    headers: hdrs
  }).then(resp => {
    if (resp.ok) return resp.text()
    else throw "publish error"
  }).then(txt => {
    let blob = new Blob(['\uFEFF', txt.trim()], {type: 'text/plain;charset=utf-8'});
    downloadFile(blob, `${shortName}.${extension}`);
    close_modal();
  }).catch(err => showError(err));
}

function publishHtml() {
  let html = $('#standings-table')[0].outerHTML;
  console.log(html)
  let form = $('#tournament-infos')[0];
  let shortName = form.val('shortName');
  let blob = new Blob(['\uFEFF', html], {type: 'text/html;charset=utf-8'});
  downloadFile(blob, `${shortName}-standings-R${activeRound}.html`);
  close_modal();
}

onLoad(() => {
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
    publish('ffg', 'tou');
  });
  $('.publish-egf').on('click', e => {
    publish('egf', 'h9');
  });
  $('.publish-html').on('click', e => {
    publishHtml();
  });
});
