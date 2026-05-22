let manualShortName;

function autofillShortName(dt, loc) {
  if (!manualShortName && dt !== '' &&  loc !== '') {
    let ymd = parseDate(dt).replaceAll(/-/g, '');
    $('input[name="shortName"]')[0].value = `${ymd}-${loc}`;
  }
}

onLoad(() => {
  $('#edit').on('click', e => {
    e.preventDefault();
    $('#tournament-infos').addClass('edit');
    return false;
  });

  $('#cancel, #close').on('click', e => {
    e.preventDefault();
    if ($('#tournament-infos').hasClass('edit') && typeof(tour_id) !== 'undefined') {
      // Reload so unsaved field edits are discarded — simpler than tracking per-field
      // initial values across the type/byoyomi/periods/etc. hide-show logic.
      window.location.reload();
    } else {
      window.location.href = '/index';
    }
    return false;
  });

  $('#parameters').on('click', e => {
    modal('parameters-modal');
    updateMainClubReadout();
  });

  // Toggle visibility of the threshold/readout block when the adjustment checkbox flips.
  $('#mainClubAdjustment').on('change', e => {
    $('#mainClubDetails')[0].style.display = e.target.checked ? '' : 'none';
    if (e.target.checked) updateMainClubReadout();
  });
  $('input[name="mainClubDetectionThreshold"]').on('input change', updateMainClubReadout);

  // Mirror of BasePairingHelper.localClub — fetches the current player list and tells
  // the operator which club the algorithm would treat as "main" at the current threshold.
  function updateMainClubReadout() {
    let readout = $('#mainClubReadout')[0];
    if (!readout || typeof(tour_id) === 'undefined') return;
    api.getJson(`tour/${tour_id}/part`).then(players => {
      if (!Array.isArray(players) || players.length === 0) {
        readout.textContent = 'No registered players yet.';
        return;
      }
      let counts = {};
      players.forEach(p => {
        let k = (p.club || '').substring(0, 4).toUpperCase();
        if (k) counts[k] = (counts[k] || 0) + 1;
      });
      let top = Object.entries(counts).sort((a, b) => b[1] - a[1])[0];
      if (!top) {
        readout.textContent = 'No club data on registered players.';
        return;
      }
      let pct = top[1] / players.length;
      let thresholdPct = (parseInt($('input[name="mainClubDetectionThreshold"]')[0].value) || 40) / 100;
      let pctTxt = `${(pct * 100).toFixed(1)}%`;
      if (pct > thresholdPct) {
        readout.textContent = `Detected main club: ${top[0]} (${top[1]}/${players.length} = ${pctTxt}).`;
      } else {
        readout.textContent = `Most-represented club: ${top[0]} (${top[1]}/${players.length} = ${pctTxt}) — below threshold; no main club detected.`;
      }
    });
  }

  $('#cancel-parameters').on('click', e => {
    // Same rationale as the main #cancel handler: reload to discard unsaved edits.
    // The global .close handler in main.js will close the modal; the reload happens
    // after, with the original server-stored values back in the form.
    if (typeof(tour_id) !== 'undefined') {
      window.location.reload();
    }
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
      case 'JAPANESE':
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
    updateAdjustedTimeInfo();
  });

  // Mirror of TimeSystem.adjustedTime() / timeSystemComment()
  function updateAdjustedTimeInfo() {
    let form = $('#tournament-infos')[0];
    let type = form.val('timeSystemType');
    let mainTime = fromHMS(form.val('mainTime'));
    let byoyomi = fromHMS(form.val('byoyomi'));
    let increment = fromHMS(form.val('increment'));
    let periods = parseInt(form.val('periods')) || 0;
    let stones = parseInt(form.val('stones')) || 0;
    let adjusted;
    switch (type) {
      case 'CANADIAN':
        adjusted = (byoyomi > 0 && stones > 0) ? mainTime + 60 * byoyomi / stones : mainTime;
        break;
      case 'JAPANESE':
        adjusted = (byoyomi > 0 && periods > 0) ? mainTime + 45 * byoyomi : mainTime;
        break;
      case 'FISCHER':
        adjusted = (increment > 0) ? mainTime + 120 * increment : mainTime;
        break;
      default:
        adjusted = mainTime;
    }
    $('#adjusted-time-info').text(`Adjusted time: ${Math.floor(adjusted / 60)} minutes`);
  }

  $('input[name="mainTime"], input[name="byoyomi"], input[name="increment"], input[name="periods"], input[name="stones"]').on('input change', updateAdjustedTimeInfo);
  updateAdjustedTimeInfo();

  $('input.duration').imask({
    mask: '00:00:00',
    lazy: false,
    overwrite: true
  });

  $('#export').on('click', e => {
    modal('export-modal');
  });

  $('#delete').on('click', e => {
    if (confirm('Supprimer ce tournoi ?')) {
      api.deleteJson(`tour/${tour_id}`, {})
        .then(resp => {
          if (resp !== 'error') {
            window.location.href = '/index';
          }
        })
    }
  });

  $('#export-pairgoth').on('click', e => {
    close_modal();
    let form = $('#tournament-infos')[0];
    let shortName = form.val('shortName');
    let hdrs = headers();
    hdrs['Accept'] = 'application/pairgoth';
    fetch(`${base}tour/${tour_id}`, {
      headers: hdrs
    }).then(resp => {
      if (resp.ok) return resp.text()
      else throw "export error"
    }).then(txt => {
      // json does not need BOM header
      // let blob = new Blob(['\uFEFF', txt.trim()], {type: 'application/json;charset=utf-8'});
      let blob = new Blob([txt.trim()], {type: 'application/json;charset=utf-8'});
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
      director: form.val('director'),
      type: form.val('type'),
      rounds: form.val('rounds'),
      country: form.val('country'),
      online: form.val('online'),
      location: form.val('location'),
      rules: form.val('rules'),
      gobanSize: form.val('gobanSize'),
      komi: form.val('komi'),
      pairing: {
        type: form.val('pairing'),
        mmFloor: form.val('mmFloor'),
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
          randomness: form.val('randomness'),
          colorBalance: form.val('colorBalance')
        },
        main: {
          mmsValueAbsent: form.val('mmsValueAbsent'),
          roundDownScore: form.val('roundDownScore'),
          sosValueAbsentUseBase: form.val('sosValueAbsentUseBase'),
          firstSeedLastRound: form.val('firstSeedLastRound'),
          firstSeedAddRating: form.val('firstSeedAddRating'),
          firstSeed: form.val('firstSeed'),
          secondSeedAddRating: form.val('secondSeedAddRating'),
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
          mmsDiffClub: form.val('mmsDiffClub'),
          avoidSameFamily: form.val('avoidSameFamily'),
          mainClubAdjustment: form.val('mainClubAdjustment'),
          // UI shows percent (1..99); model stores 0..1
          mainClubDetectionThreshold: (parseInt(form.val('mainClubDetectionThreshold')) || 40) / 100
        },
        handicap: {
          useMMS: form.val('useMMS'),
          ceiling: form.val('ceiling')
        }
      }
    }
    api.putJson(`tour/${tour_id}`, tour)
      .then(tour => {
        if (tour !== 'error') {
          window.location.reload();
        }
      });
  });
  let shortName = $('input[name="shortName"]');
  manualShortName = (shortName[0].value !== '');
  let startDate = $('input[name="startDate"]');
  let location = $('input[name="location"]');
  startDate.on('change', e => {
    if (!manualShortName) autofillShortName(startDate[0].value, location[0].value)
  });
  $('#date-range').on('changeDate', e => {
    if (!manualShortName) autofillShortName(startDate[0].value, location[0].value)
  });
  location.on('input', e => {
    if (!manualShortName) autofillShortName(startDate[0].value, location[0].value)
  });
  shortName.on('input', e => {
    manualShortName = true;
  });

  $('select[name="pairing"]').on('change', e => {
    let pairing = e.target.value.toLowerCase();
    if (pairing === 'mac_mahon') $('#tournament-infos .mms').removeClass('hidden');
    else $('#tournament-infos .mms').addClass('hidden');
    if (pairing === 'swiss') $('#tournament-infos .swiss').removeClass('hidden');
    else $('#tournament-infos .swiss').addClass('hidden');
  });
});
