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

  // The dialog commits/cancels only itself, never via reload — a reload would discard
  // in-progress main-form edits (the two scopes are independent).
  let paramsSnapshot = null;
  function parametersControls() {
    return $('#parameters-form')[0].find('input, select');
  }

  function syncMainClubDetails() {
    $('#mainClubDetails')[0].style.display = $('#mainClubAdjustment')[0].checked ? '' : 'none';
  }

  $('#parameters').on('click', e => {
    paramsSnapshot = [...parametersControls()].map(c => c.type === 'checkbox' ? c.checked : c.value);
    modal('parameters-modal');
    updateMainClubReadout();
  });

  $('#mainClubAdjustment').on('change', e => {
    syncMainClubDetails();
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
    // Restore the dialog to its on-open state; the global .close handler in main.js
    // closes the modal.
    if (paramsSnapshot) {
      [...parametersControls()].forEach((c, i) => {
        if (c.type === 'checkbox') c.checked = paramsSnapshot[i];
        else c.value = paramsSnapshot[i];
      });
      syncMainClubDetails();
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
      // external auth: reuse the provisioned id so the created file matches the granted symlink
      if (typeof newTournamentId !== 'undefined' && newTournamentId) tour.id = newTournamentId;
      api.postJson('tour', tour)
        .then(tour => {
          if (tour !== 'error') {
            let search = `id=${tour.id}`;
            // a provisioned landing is already at ?id=N → assigning the same search won't navigate, so reload
            if (`?${search}` === window.location.search) window.location.reload();
            else window.location.search = search;
          }
        });
    }
  });
  $('#update-parameters').on('click', async e => {
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
    let rst = await mutate({ url: `tour/${tour_id}`, body: tour, source: 'information' });
    if (rst !== 'error' && rst !== 'readonly') close_modal();
  });

  // Model json → dialog controls; inverse of the payload built by #update-parameters.
  // Guards on control presence (some fields are Mac Mahon-only).
  function patchParametersForm(pairing) {
    let form = $('#parameters-form')[0];
    const set = (name, value) => { let c = form.find(`[name="${name}"]`)[0]; if (c) c.value = value; };
    const check = (name, on) => { let c = form.find(`[name="${name}"]`)[0]; if (c) c.checked = !!on; };
    let { base, main, secondary, geo, handicap } = pairing;
    set('randomness', base.random == 0 ? 'none' : base.deterministic ? 'deterministic' : 'non-deterministic');
    check('colorBalance', base.colorBalanceWeight);
    check('roundDownScore', main.roundDownScore);
    set('mmsValueAbsent', main.mmsValueAbsent);
    set('sosValueAbsentUseBase', main.sosValueAbsentUseBase);
    set('firstSeedLastRound', main.firstSeedLastRound);
    check('firstSeedAddRating', main.firstSeedAddCrit === 'RATING');
    set('firstSeed', main.firstSeed);
    check('secondSeedAddRating', main.secondSeedAddCrit === 'RATING');
    set('secondSeed', main.secondSeed);
    check('upDownCompensate', main.upDownCompensate);
    set('upDownUpperMode', main.upDownUpperMode);
    set('upDownLowerMode', main.upDownLowerMode);
    set('rankThreshold', secondary.rankThreshold);
    check('winsThreshold', secondary.winsThreshold);
    check('barThreshold', secondary.barThreshold);
    set('mmsDiffCountry', geo.mmsDiffCountry);
    set('mmsDiffClub', geo.mmsDiffClub);
    check('avoidSameFamily', geo.avoidSameFamily);
    check('mainClubAdjustment', geo.mainClubAdjustment);
    set('mainClubDetectionThreshold', Math.round((geo.mainClubDetectionThreshold || 0.4) * 100));
    check('useMMS', handicap.useMMS);
    set('ceiling', handicap.ceiling);
    syncMainClubDetails();
  }

  // Advanced params live only in the dialog: patch it in place (a no-op for the actor,
  // a refresh for observers) instead of the coarse reload/warn — this is what keeps
  // in-progress main-form edits alive across a parameters update.
  sseEffect('PairingParamsUpdated', tour => {
    patchParametersForm(tour.pairing);
    paramsSnapshot = null; // dialog now shows server state; a later open re-snapshots
    if ($('#parameters-modal').hasClass('shown')) updateMainClubReadout();
    return true;
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
