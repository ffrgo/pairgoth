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

  $('#validate').on('click', e => {
    let valid = true;
    // validate required fields
    let required = ['name', 'shortName', 'startDate', 'endDate'];
    if (!$('input[name="online"]')[0].checked) required.push('location')
    for (let name of required) {
      let ctl = $(`input[name=${name}]`)[0];
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
    let shortNameCtl = $('input[name="shortName"]')[0];
    let shortName = shortNameCtl.value;
    if (safeRegex.test(shortName)) {
      shortNameCtl.setCustomValidity('');
    } else {
      valid  = false;
      shortNameCtl.setCustomValidity(msg('invalid_character'));
    }
    if (!valid) return;
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

  $('#tournament-infos').on('submit', e => {
    e.preventDefault();
    let tour = {
      name: formValue('name'),
      shortName: formValue('shortName'),
      startDate: parseDate(formValue('startDate')),
      endDate: parseDate(formValue('endDate')),
      type: formValue('type'),
      rounds: formValue('rounds'),
      country: formValue('country'),
      online: formValue('online'),
      location: formValue('online') ? "" : formValue('location'),
      pairing: {
        type: formValue('pairing'),
        // mmFloor: formValue('mmFloor'),
        mmBar: formValue('mmBar'),
        main: {
          firstSeed: formValue('firstSeed'),
          secondSeed: formValue('secondSeed')
        },
        handicap: {
          correction: formValue('correction'),
          treshold: formValue('treshold')
        }
      },
      timeSystem: {
        type: formValue('timeSystemType'),
        mainTime: fromHMS(formValue('mainTime')),
        increment: fromHMS(formValue('increment')),
        maxTime: fromHMS(formValue('maxTime')),
        byoyomi: fromHMS(formValue('byoyomi')),
        periods: formValue('periods'),
        stones: formValue('stones')
      }
    }
    console.log(tour);
    if (typeof(tour_id) !== 'undefined') {
      api.putJson(`tour/${tour_id}`, tour)
        .then(tour => {
          window.location.reload();
        });
    } else {
      api.postJson('tour', tour)
        .then(tour => {
          window.location.href += `?id=${tour.id}`;
        });
    }
  });

  if (!window.location.hash && window.location.search) {
    window.location.hash = '#information'
  }
  if (window.location.hash) {
    let step = window.location.hash.substring(1);
    chooseStep(step);
  }

});
