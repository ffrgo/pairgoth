onLoad(() => {
  $('#edit').on('click', e => {
    e.preventDefault();
    $('#tournament-infos').addClass('edit');
    return false;
  });

  $('#cancel, #close').on('click', e => {
    e.preventDefault();
    if ($('#tournament-infos').hasClass('edit') && typeof(tour_id) !== 'undefined') {
      $('#tournament-infos').removeClass('edit')
    } else {
      document.location.href = '/index';
    }
    return false;
  });

  $('#parameters').on('click', e => {
    modal('parameters-modal');
  });

  $('#validate').on('click', e => {
    let form = e.target.closest('form');
    let valid = true;
    // validate required fields
    let required = ['name', 'shortName', 'startDate', 'endDate'];
    if (!form.find('input[name="online"]')[0].checked) required.push('location')
    for (let name of required) {
      let ctl = form.find(`input[name=${name}]`)[0];
      let val = ctl.value;
      if (val) {
        ctl.setCustomValidity('');
      } else {
        valid = false;
        ctl.setCustomValidity(msg('required_field'));
      }
    }
    if (!valid) return;
    // validate short_name
    let shortNameCtl = form.find('input[name="shortName"]')[0];
    let shortName = shortNameCtl.value;
    if (safeRegex.test(shortName)) {
      shortNameCtl.setCustomValidity('');
    } else {
      valid  = false;
      shortNameCtl.setCustomValidity(msg('invalid_character'));
    }
    // if (!valid) return;
    // ...
  });

  for(let name of ['startDate', 'endDate']) {
    let control = $(`input[name="${name}"]`)[0];
    if (control.value) {
      control.value = formatDate(control.value);
    }
  }
  new DateRangePicker($('#date-range')[0], {
    autohide: true,
    language: datepickerLocale || 'en'
  });

  $('input[name="online"]').on('change', e => {
    $('input[name="location"]')[0].disabled = e.target.checked;
  });

  $('select[name="timeSystemType"]').on('change', e => {
    switch (e.target.value) {
      case 'CANADIAN':
        $('#increment').addClass('hidden');
        $('#maxTime').addClass('hidden');
        $('#byoyomi').removeClass('hidden');
        $('#periods').addClass('hidden');
        $('#stones').removeClass('hidden');
        break;
      case 'FISCHER':
        $('#increment').removeClass('hidden');
        $('#maxTime').removeClass('hidden');
        $('#byoyomi').addClass('hidden');
        $('#periods').addClass('hidden');
        $('#stones').addClass('hidden');
        break;
      case 'STANDARD':
        $('#increment').addClass('hidden');
        $('#maxTime').addClass('hidden');
        $('#byoyomi').removeClass('hidden');
        $('#periods').removeClass('hidden');
        $('#stones').addClass('hidden');
        break;
      case 'SUDDEN_DEATH':
        $('#increment').addClass('hidden');
        $('#maxTime').addClass('hidden');
        $('#byoyomi').addClass('hidden');
        $('#periods').addClass('hidden');
        $('#stones').addClass('hidden');
        break;
    }
  });

  $('input.duration').imask({
    mask: '00:00:00',
    lazy: false,
    overwrite: true
  });

  $('#export').on('click', e => {
    modal('export-modal');
  });

  $('#export-pairgoth').on('click', e => {
    close_modal();
    let form = $('#tournament-infos')[0];
    let shortName = form.val('shortName');
    let hdrs = headers();
    hdrs['Accept'] = 'application/json';
    fetch(`${base}tour/${tour_id}`, {
      headers: hdrs
    }).then(resp => {
      if (resp.ok) return resp.text()
      else throw "export error"
    }).then(txt => {
      let blob = new Blob(['\uFEFF', txt.trim()], {type: 'application/json;charset=utf-8'});
      downloadFile(blob, `${shortName}.tour`);
    }).catch(err => showError(err));
  });

  $('#export-opengotha').on('click', e => {
    close_modal();
    let form = $('#tournament-infos')[0];
    let shortName = form.val('shortName');
    let hdrs = headers();
    hdrs['Accept'] = 'application/xml';
    fetch(`${base}tour/${tour_id}`, {
      headers: hdrs
    }).then(resp => {
      if (resp.ok) return resp.text()
      else throw "export error"
    }).then(txt => {
      let blob = new Blob(['\uFEFF', txt.trim()], {type: 'application/xml;charset=utf-8'});
      downloadFile(blob, `${shortName}.xml`);
    }).catch(err => showError(err));
  });

  $('#tournament-infos').on('submit', e => {
    e.preventDefault();
    let form = e.target;
    let tour = {
      name: form.val('name'),
      shortName: form.val('shortName'),
      startDate: parseDate(form.val('startDate')),
      endDate: parseDate(form.val('endDate')),
      type: form.val('type'),
      rounds: form.val('rounds'),
      country: form.val('country'),
      online: form.val('online'),
      location: form.val('location'),
      pairing: {
        type: form.val('pairing'),
        // mmFloor: form.val('mmFloor'),
        mmBar: form.val('mmBar'),
        main: {
          firstSeed: form.val('firstSeed'),
          secondSeed: form.val('secondSeed')
        },
        handicap: {
          correction: -form.val('correction'),
          threshold: form.val('threshold')
        }
      },
      timeSystem: {
        type: form.val('timeSystemType'),
        mainTime: fromHMS(form.val('mainTime')),
        increment: fromHMS(form.val('increment')),
        maxTime: fromHMS(form.val('maxTime')),
        byoyomi: fromHMS(form.val('byoyomi')),
        periods: form.val('periods'),
        stones: form.val('stones')
      }
    }
    console.log(tour);
    if (typeof(tour_id) !== 'undefined') {
      api.putJson(`tour/${tour_id}`, tour)
        .then(tour => {
          if (tour !== 'error') {
            window.location.reload();
          }
        });
    } else {
      api.postJson('tour', tour)
        .then(tour => {
          if (tour !== 'error') {
            window.location.href += `?id=${tour.id}`;
          }
        });
    }
  });
  $('#update-parameters').on('click', e => {
    let form = $('#parameters-form')[0];
    let tour = {
      pairing: {
        base: {
          deterministic: form.val('deterministic'),
          colorBalanceWeight: form.val('colorBalance') ? 1000000.0 : 0.0 // TODO use client side boolean
        },
        main: {
          mmsValueAbsent: form.val('mmsValueAbsent'),
          firstSeedLastRound: form.val('firstSeedLastRound'),
          firstSeedAddCrit: form.val('firstSeedAddRating') ? 'RATING' : 'NONE', // TODO use client side boolean
          firstSeed: form.val('firstSeed'),
          secondSeedAddCrit: form.val('secondSeedAddRating') ? 'RATING' : 'NONE', // TODO use client side boolean
          secondSeed: form.val('secondSeed'),
          upDownCompensate: form.val('upDownCompensate'),
          upDownUpperMode: form.val('upDownUpperMode'),
          upDownLowerMode: form.val('upDownLowerMode')
        },
        secondary: {
          rankThreshold: form.val('rankThreshold'),
          winsThreshold: form.val('winsThreshold'),
          barThreshold: form.val('barThreshold')
        },
        geo: {
          mmsDiffCountry: form.val('mmsDiffCountry'),
          mmsDiffClub: form.val('mmsDiffClub')
        },
        handicap: {
          useMMS: form.val('useMMS'),
          ceiling: form.val('ceiling')
        }
      }
    }
    console.log(tour);
    api.putJson(`tour/${tour_id}`, tour)
      .then(tour => {
        if (tour !== 'error') {
          window.location.reload();
        }
      });
  });
});
