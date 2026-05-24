const SEARCH_DELAY = 100;
let searchTimer = undefined;
let resultTemplate;
let searchResult;
let searchHighlight;
let chained = false;

// EGD-canonical rank/rating helpers (mirror pairgoth-common/util/RankRating.kt)
const MIN_RANK = -30, MAX_RANK = 8, MIN_PRO = 1, MAX_PRO = 9;
const PRO_BASE_RATING = 2700, PRO_STEP = 30;
function clamp(v, lo, hi) { return Math.max(lo, Math.min(hi, v)); }
function ratingToRankInt(r) { return clamp(Math.floor((r - 2050) / 100), MIN_RANK, MAX_RANK); }
function rankIntToRating(k) { return 2050 + 100 * k; }
function ratingToProLevel(r) { return clamp(Math.round((r - PRO_BASE_RATING) / PRO_STEP) + 1, MIN_PRO, MAX_PRO); }
function proLevelToRating(p) { return PRO_BASE_RATING + PRO_STEP * (p - 1); }
function isProFormValue(v) { return typeof v === 'string' && /^p[1-9]$/i.test(v); }
function proLevelOf(v) { return parseInt(String(v).substring(1)); }

// Returns the form select value for a player payload: "pN" if pro > 0, "<int>" for amateur.
// `player.rank` may be an int or a display string ("1d", "2k", "1p").
function playerToFormRank(player) {
  if (player == null) return '';
  if (player.pro && player.pro >= MIN_PRO && player.pro <= MAX_PRO) return `p${player.pro}`;
  if (typeof player.rank === 'number') return String(player.rank);
  return parseRank(player.rank) ?? '';
}

// Returns the form select value parsed from a display string. For pro returns "pN", for amateur an int as string, null on garbage.
function parseRank(rank) {
  if (rank == null) return null;
  let groups = /^(\d+)([kdp])$/i.exec(String(rank).trim());
  if (!groups) return null;
  let level = parseInt(groups[1]);
  if (!(level >= 1)) return null;
  switch (groups[2].toLowerCase()) {
    case 'k': return level <= 30 ? String(-level) : null;
    case 'd': return level <= 9 ? String(level - 1) : null;
    case 'p': return level <= 9 ? `p${level}` : null;
  }
  return null;
}

// Same input grammar as parseRank, but returns the {rank, pro} ints the API expects.
// Pro inputs derive rank from the canonical pro→rating→rank mapping so pairing/MMS treat
// pros at their rating equivalent.
function parseRankAndPro(rankStr) {
  if (rankStr == null) return null;
  let groups = /^(\d+)([kdp])$/i.exec(String(rankStr).trim());
  if (!groups) return null;
  let n = parseInt(groups[1]);
  if (!(n >= 1)) return null;
  switch (groups[2].toLowerCase()) {
    case 'k': return n <= 30 ? { rank: -n, pro: 0 } : null;
    case 'd': return n <= 9 ? { rank: n - 1, pro: 0 } : null;
    case 'p': return n <= 9 ? { rank: ratingToRankInt(proLevelToRating(n)), pro: n } : null;
  }
  return null;
}

function displayRank(rank, pro) {
  if (pro && pro >= MIN_PRO && pro <= MAX_PRO) return `${pro}p`;
  rank = parseInt(rank);
  return rank < 0 ? `${-rank}k` : `${rank + 1}d`;
}

// Chain detection: chain is "on" iff the dropdown value matches what the rating implies in the active domain.
function updateChainState() {
  let rating = parseInt($('#rating')[0].value);
  let rankValue = $('#rank')[0].value;
  let isPro = isProFormValue(rankValue);
  let isAmateur = !isPro && rankValue !== '' && !isNaN(parseInt(rankValue));
  if (isNaN(rating) || (!isPro && !isAmateur)) {
    chained = false;
  } else if (isPro) {
    let p = proLevelOf(rankValue);
    chained = rating >= PRO_BASE_RATING && rating < PRO_BASE_RATING + PRO_STEP * MAX_PRO + Math.ceil(PRO_STEP / 2)
              && ratingToProLevel(rating) === p;
  } else {
    let k = parseInt(rankValue);
    chained = ratingToRankInt(rating) === k && k >= MIN_RANK && k <= MAX_RANK;
  }
  if (chained) $('#chain-rating').addClass('chained');
  else $('#chain-rating').removeClass('chained');
  updateLinkHints();
}

// When rating and rank are unlinked, the "Rank" field is an honorary grade decoupled from
// strength. Spell that out in italic under each field: the rating's effective pairing rank,
// and that the rank is just a label. When linked the two agree, so we show nothing.
function updateLinkHints() {
  let ratingEl = document.getElementById('rating');
  let rankEl = document.getElementById('rank');
  let ratingInfo = ratingEl && ratingEl.closest('.field') && ratingEl.closest('.field').querySelector('.info');
  let rankInfo = rankEl && rankEl.closest('.field') && rankEl.closest('.field').querySelector('.info');
  if (chained) {
    if (ratingInfo) ratingInfo.innerHTML = '';
    if (rankInfo) rankInfo.innerHTML = '';
    return;
  }
  let rating = parseInt(ratingEl ? ratingEl.value : NaN);
  if (ratingInfo) ratingInfo.innerHTML = isNaN(rating) ? '' : `<i>(effective rank: ${displayRank(ratingToRankInt(rating))})</i>`;
  if (rankInfo) rankInfo.innerHTML = '<i>(honorary grade)</i>';
}

function searchResultShown() {
  return !(!searchResult || searchResult.length === 0 || $('#search-result').hasClass('hidden'));
}

function browseScroll() {
  $('#search-result .result-line').removeClass('spotted');
  searchHighlight = -1;
  let scrollTo = $('#needle')[0].value.trim();
  while (scrollTo.length > 0) {
    let target = $(`#search-result .result-line[data-name^="${scrollTo}"i]`);
    if (target.length > 0) {
      target.addClass('spotted');
      let first = target[0];
      first.scrollIntoView({behavior: "smooth", block: "center"});
      searchHighlight = Array.prototype.indexOf.call(first.parentNode.children, first);
      first.addClass('highlighted');
      break;
    }
    scrollTo = scrollTo.substring(0, scrollTo.length - 1);
  }
}

function clearSearch() {
  $('#search-result').clear();
  searchTimer = undefined;
  searchResult = undefined;
  searchHighlight = undefined;
}

function search(needle) {
  needle = needle.trim();
  if (needle && (needle === '*' || needle.length > 2)) {
    let form = $('#search-form')[0];
    let search = {
      needle: needle,
      // aga: form.val('aga'),
      egf: form.val('egf'),
      ffg: form.val('ffg')
    }
    let country = form.val('countryFilter');
    if (country) search.countryFilter = country;
    api.postJson('search', search)
      .then(result => {
        if (Array.isArray(result)) {
          searchResult = result
          let html = resultTemplate.render(result);
          $('#search-result')[0].innerHTML = html;
          if (needle === '*') {
            setTimeout(() => browseScroll(), 0);
          } else {
            $('#search-result').removeClass('hidden');
            let scrollable = $('#player .popup-body');
            scrollable[0].scrollTop = 0;
          }
        } else console.log(result);
      });
  } else {
    // needle is empty (and by construction we can't be in browse mode) - clear search result
    clearSearch();
  }
}

function initSearch() {
  let needle = $('#needle')[0].value.trim();
  if (searchTimer) {
    clearTimeout(searchTimer);
  }
  searchTimer = setTimeout(() => {
    let form = $('#search-form')[0];
    let browsing = !!form.val('browse');
    if (!browsing || !searchResult) {
      $('#search-result .result-line').removeClass('spotted');
      search(browsing ? '*' : needle);
    } else if (browsing) {
      if (needle.length) {
        $('#search-result').removeClass('hidden');
        browseScroll();
      } else {
        $('#search-result').addClass('hidden');
      }
    } else {
      $('#search-result').removeClass('hidden');
    }
  }, SEARCH_DELAY);
}

function fillPlayer(player) {
  console.log(player);
  // hack UK / GB
  let country = player.country.toLowerCase();
  if ('uk' === country) country = 'gb';
  let form = $('#player-form')[0];
  form.val('name', player.name);
  form.val('firstname', player.firstname);
  form.val('country', country);
  form.val('club', player.club);
  form.val('rank', playerToFormRank(player));
  form.val('rating', player.rating);
  form.val('final', false);
  form.val('ffg', player.ffg);
  form.val('egf', player.egf);
  form.val('aga', player.aga);
  form.val('ext', player.ext);
  // search result carries the FFG echelle char in `license`; snapshot the L-vs-not bit
  form.val('licensed', player.license ? String(player.license === 'L') : '');
  updateChainState();
  $('#needle')[0].value = '';
  initSearch();
  $('#register').removeClass('disabled').focus();
}

function addPlayers() {
  let form = $('#player-form')[0];
  // keep preliminary/final status
  let status = form.val('final') || false;
  form.reset();
  form.val('final', status);
  chained = true;
  $('#chain-rating').addClass('chained');
  updateLinkHints();
  $('#player').removeClass('edit').addClass('create');
  $('#register').removeClass('disabled');
  modal('player');
  setTimeout(() => {
    $('#needle').focus();

  }, 100);
  store('addingPlayers', true);
}

function bulkUpdate(players) {
  Promise.all(players.map(p => api.putJson(`tour/${tour_id}/part/${p.id}`, p)))
    .then((values) => window.location.reload());
}

function navigateResults(ev) {
  console.log(`searchHighlight = ${searchHighlight}`);
  let lines = $('.result-line');
  lines.removeClass('highlighted');
  searchHighlight = Math.max(searchHighlight, 0);
  searchHighlight = Math.min(searchHighlight, lines.length - 1);
  let targeted = lines[searchHighlight];
  if (targeted) {
    targeted.addClass('highlighted');
    // let's scroll into view manually, since DOM API scrollIntoView() is fooled by the sticky header.
    let scrollContainer = targeted.closest('.popup-body');
    // TODO - the "24" is the search-result padding. Avoid hardcoding it.
    let scrollTop = scrollContainer.scrollTop + 24;
    let scrollBottom = scrollContainer.scrollTop + scrollContainer.clientHeight - 24 - $('#search-form')[0].offsetHeight;
    let top = targeted.offsetTop;
    let bottom = top + targeted.offsetHeight;
    if (top < scrollTop) {
      scrollContainer.scrollTop -= (scrollTop - top);
    } else if (bottom > scrollBottom) {
      scrollContainer.scrollTop += (bottom - scrollBottom);
    }
  }
  if (ev) {
    ev.preventDefault();
    ev.cancelBubble = true;
    ev.stopPropagation();
  }
}

let tableSort;

onLoad(() => {
  $('input.numeric').imask({
    mask: Number,
    scale: 0,
    min: rankIntToRating(MIN_RANK), // -950 (30k); EGD ratings go negative for weak players
    max: 4000
  });

  let prevSort = store('registrationSort');
  if (prevSort) {
    let columns = $('#players thead th');
    columns.forEach(th => {
      th.removeAttribute('data-sort-default');
      th.removeAttribute('aria-sort');
    })
    prevSort.forEach(i => {
      let col = columns[Math.abs(i)];
      col.setAttribute('data-sort-default', '1');
      if (i < 0) {
        // take into account TableSort initiailization bug
        col.setAttribute('aria-sort', 'ascending');
      }
    });
  }
  tableSort = new Tablesort($('#players')[0]);
  $('#players').on('afterSort', e => {
    let sort = [];
    $('#players thead th').forEach((th, i) => {
      let attr = th.attr('aria-sort');
      if (attr) {
        let dir = i;
        if (attr === 'descending') dir = -dir;
        sort.push(dir);
      }
    });
    store('registrationSort', sort);
  });

  $('#add').on('click', e => {
    addPlayers();
  });
  $('#cancel-register').on('click', e => {
    e.preventDefault();
    close_modal();
    searchHighlight = undefined;
    return false;
  });

  $('#register').on('click', e => {
    let form = e.target.closest('form');
    let valid = true;
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
    if (!valid) {
      $('#player :invalid').forEach(elem => elem.reportValidity());
      return;
    }
    // $('#player-form')[0].requestSubmit() not working?!
    $('#player-form')[0].dispatchEvent(new CustomEvent('submit', {cancelable: true}));
  });
  $('#search-form').on('submit', e => {
    // this form is never meant to be submitted
    e.preventDefault();
    return false;
  });
  $('#player-form').on('submit', e => {
    e.preventDefault();
    if ($('#register').hasClass('disabled')) {
      // user pressed enter
      return false;
    }
    let form = $('#player-form')[0];
    let rankValue = form.val('rank');
    let pro = 0;
    let rank = rankValue;
    if (isProFormValue(rankValue)) {
      pro = proLevelOf(rankValue);
      rank = ratingToRankInt(proLevelToRating(pro));   // amateur-equivalent strength for pairing/MMS
    }
    let player = {
      name: form.val('name'),
      firstname: form.val('firstname'),
      rating: form.val('rating'),
      rank: rank,
      pro: pro,
      country: form.val('country'),
      club: form.val('club'),
      skip: form.find('input.participation').map((input,i) => [i+1, input.checked]).filter(arr => !arr[1]).map(arr => arr[0]),
      final: form.val('final')
    }
    for (let origin of ['egf', 'ffg']) {
      let value = form.val(origin);
      if (value) {
        player[origin] = value;
      }
    }
    // FFG licence snapshot (FR), carried from the picked index entry
    let licensed = form.val('licensed');
    if (licensed === 'true') player.licensed = true;
    else if (licensed === 'false') player.licensed = false;
    if ($('#player').hasClass('create')) {
      api.postJson(`tour/${tour_id}/part`, player)
        .then(player => {
          if (player !== 'error') {
            store('registrationSuccess', true);
            store('scrollIntoView', player.id)
            window.location.reload();
          }
        });
    } else {
      let id = form.val('id');
      player['id'] = id;
      api.putJson(`tour/${tour_id}/part/${id}`, player)
        .then(player => {
          if (player !== 'error') {
            store('registrationSuccess', true);
            store('scrollIntoView', id)
            window.location.reload();
          }
        });
    }
  });
  $('#players > tbody > tr').on('click', e => {
    let regStatus = e.target.closest('td.reg-status, td.participating');
    if (regStatus) return;
    let id = e.target.closest('tr').attr('data-id');
    api.getJson(`tour/${tour_id}/part/${id}`)
      .then(player => {
        if (player !== 'error') {
          let form = $('#player-form')[0];
          form.val('id', player.id);
          form.val('name', player.name);
          form.val('firstname', player.firstname);
          form.val('rating', player.rating);
          form.val('rank', playerToFormRank(player));
          form.val('country', player.country.toLowerCase());
          form.val('club', player.club);
          form.val('final', player.final);
          form.val('egf', player.egf);
          form.val('ffg', player.ffg);
          form.val('aga', player.aga);
          form.val('ext', player.ext);
          form.val('licensed', player.licensed == null ? '' : String(player.licensed));
          if (player.final) $('#final-reg').addClass('final');
          else $('#final-reg').removeClass('final');
          for (r = 1; r <= tour_rounds; ++r) {
            form.val(`r${r}`, !(player.skip && player.skip.includes(r)));
          }
          $('#player').removeClass('create').addClass('edit');
          $('#register').addClass('disabled');
          updateChainState();
          modal('player');
        }
      });
  });
  resultTemplate = jsrender.templates($('#result')[0]);
  $('#needle').on('input', e => {
    initSearch();
  });
  $('#clear-search').on('click', e => {
    $('#needle')[0].value = '';
    initSearch();
  });
  let searchFormState = store('searchFormState')
  if (searchFormState) {
    for (let id of ["countryFilter", /* "aga", */ "egf", "ffg", "browse"]) {
      let ctl = $(`#${id}`);
      if (ctl.length !== 0) {
        ctl[0].checked = searchFormState[id];
      }
    }
  }
  $('#search-form .toggle').on('click', e => {
    let chk = e.target.closest('.toggle');
    let checkbox = chk.find('input')[0];
    checkbox.checked = !checkbox.checked;
    //
    // let id = checkbox.getAttribute('id');
    // let value = checkbox.checked;
    // store(id, value);
    let form = $('#search-form')[0];
    let searchFormState = {
      countryFilter: !!form.val('countryFilter'),
      // aga: search.aga,
      egf: !!form.val('egf'),
      ffg: !!form.val('ffg'),
      browse: !!form.val('browse')
    };
    store('searchFormState', searchFormState);
    clearSearch();
    initSearch();
  });
  $('#reglist-mode').on('change', e => {
    let mode = e.target.value;
    $('td.reg-status').forEach(node => node.parentNode.removeClass('filtered'));
    if (mode === 'prelim') {
      $('td.reg-status.final').forEach(node => node.parentNode.addClass('filtered'));
    } else if (mode === 'final') {
      $('td.reg-status:not(.final)').forEach(node => node.parentNode.addClass('filtered'));
    }
  });
  document.on('click', e => {
    let resultLine = e.target.closest('.result-line');
    if (resultLine) {
      let index = e.target.closest('.result-line').data('index');
      fillPlayer(searchResult[index]);
      return;
    }
    let tab = document.location.hash;
    if (store('addingPlayers') && tab === '#registration') {
      let modal = e.target.closest('#player');
      if (!modal) {
        let button = e.target.closest('button');
        if (!button) {
          if (searchResultShown()) {
            $('#needle')[0].value = '';
            initSearch();
          } else {
            close_modal();
          }
        }
      }
    }
  });
  $('#unregister').on('click', e => {
    let form = $('#player-form')[0];
    let id = form.val('id');
    let confirmMessage = $('#unregister-player').text();
    if (confirm(confirmMessage)) {
      api.deleteJson(`tour/${tour_id}/part/${id}`)
        .then(ret => {
          if (ret !== 'error') {
            window.location.reload();
          }
        });
    }
  });
  $('#reg-status').on('click', e => {
    let current = $('#final-reg').hasClass('final');
    if (current) {
      $('input[name="final"]')[0].value = false;
      $('#final-reg').removeClass('final');
    } else {
      $('input[name="final"]')[0].value = true;
      $('#final-reg').addClass('final');
    }
    $('#register').removeClass('disabled');
  });
  $('.reg-status').on('click', e => {
    let cell = e.target.closest('td');
    let tr = e.target.closest('tr');
    let id = tr.data('id');
    let newStatus = !cell.hasClass('final');
    api.putJson(`tour/${tour_id}/part/${id}`, {
      id: id,
      final: newStatus
    }).then(player => {
      if (player !== 'error') {
        tr.toggleClass('final');
        cell.toggleClass('final');
        let confirmed = $('#confirmed-count')[0];
        if (confirmed) confirmed.innerText = parseInt(confirmed.innerText) + (newStatus ? 1 : -1);
        standingsUpToDate = false;
        pairablesUpToDate = false;
      }
    });
    e.preventDefault();
    return false;
  });
  $('#filter').on('input', (e) => {
    let input = e.target;
    let value = input.value.toUpperCase();
    if (value === '') $('tbody > tr').removeClass('hidden');
    else $('tbody > tr').forEach(tr => {
      let txt = tr.data('text');
      if (txt && txt.indexOf(value) === -1) tr.addClass('hidden');
      else tr.removeClass('hidden');
    });
  });
  $('#chain-rating').on('click', e => {
    e.preventDefault();
    chained = !chained;
    $('#chain-rating').toggleClass('chained');
    updateLinkHints();
  });
  $('#player input[name="rating"]').on('input', e=>{
    updateLinkHints();
  });
  $('#player select[name="rank"]').on('input', e=>{
    let rankValue = e.target.value;
    let ratingCtl = $('#player input[name="rating"]')[0];
    if (chained) {
      if (isProFormValue(rankValue)) {
        ratingCtl.value = proLevelToRating(proLevelOf(rankValue));
      } else if (rankValue !== '') {
        ratingCtl.value = rankIntToRating(parseInt(rankValue));
      }
    }
    updateLinkHints();
  });
  $('#filter-box i').on('click', e => {
    $('#filter')[0].value = '';
    $('tbody > tr').removeClass('hidden');
  });
  $('#edit-macmahon-groups').on('click', e => {
    modal('macmahon-groups');
    store('macmahonGroups', true);
  });
  if (store('addingPlayers')) {
    addPlayers();
    if (store('registrationSuccess')) {
      $('#player').addClass('successful');
      setTimeout(() => $('#player .success-feedback').addClass('done'), 0);
    }
  }
  store.remove('registrationSuccess');
  let scrollIntoView = store('scrollIntoView');
  if (scrollIntoView) {
    let row = $(`tr[data-id="${scrollIntoView}"`);
    if (row.length !== 0) {
      row.addClass('highlighted');
      store.remove('scroll');
      setTimeout(()=>{
        row[0].scrollIntoView({ behavior: "smooth", block: "center" });
      }, 100);
    }
  }
  store.remove('scrollIntoView');
  let refreshReport = store('refreshReport');
  if (refreshReport) {
    if (refreshReport.error) showError(refreshReport.msg);
    else showSuccess(refreshReport.msg, true);
    store.remove('refreshReport');
  }
  if (store('macmahonGroups')) {
    modal('macmahon-groups');
  }
  // mac mahon groups...
  $('#under-to-top').on('click', e => {
    let players = $('#under-group .selected').map(item => (
    {
      id: parseInt(item.data("id")),
      mmsCorrection: parseInt(item.data("correction")) + 1
    }));
    bulkUpdate(players);
  });
  $('#top-to-under').on('click', e => {
    let players = $('#top-group .selected').map(item => (
      {
        id: parseInt(item.data("id")),
        mmsCorrection: parseInt(item.data("correction")) - 1
      }));
    bulkUpdate(players);
  });
  $('#top-to-super').on('click', e => {
    let players = $('#top-group .selected').map(item => (
      {
        id: parseInt(item.data("id")),
        mmsCorrection: parseInt(item.data("correction")) + 1
      }));
    bulkUpdate(players);
  });
  $('#super-to-top').on('click', e => {
    let players = $('#super-group .selected').map(item => (
      {
        id: parseInt(item.data("id")),
        mmsCorrection: parseInt(item.data("correction")) - 1
      }));
    bulkUpdate(players);
  });
  $('#reset-macmahon-groups').on('click', e => {
    let players = $('#macmahon-groups .listitem').map(item => (
      {
        id: parseInt(item.data("id")),
        mmsCorrection: 0
      }));
    bulkUpdate(players);
  });
  $('.player-fields').on('change input', e => {
    $('#register').removeClass('disabled');
  });
  $('.participation label').on('click', e => {
    let part = e.target;
    let id = part.closest('tr').data('id');
    let round = parseInt(part.text());
    let skip = new Set(part.closest('.participation').find('label.red').map(it => parseInt(it.innerText)));
    if (skip.has(round)) skip.delete(round);
    else skip.add(round);
    api.putJson(`tour/${tour_id}/part/${id}`, {
      id: id,
      skip: Array.from(skip)
    }).then(player => {
      if (player !== 'error') {
        part.toggleClass('red');
        part.toggleClass('green');
        standingsUpToDate = false;
        pairablesUpToDate = false;
      }
    });
    e.preventDefault();
    return false;
  });
  $('#rating').on('input', e => {
    if (chained) {
      let rating = parseInt(e.target.value);
      if (isNaN(rating)) return true;
      let current = $('#rank')[0].value;
      let inProDomain = isProFormValue(current);
      // domain switch only when the rating exits the active domain's bounds.
      // amateur -> pro when rating reaches above 9d (>= 2950);
      // pro -> amateur when rating falls below 1p (< 2700).
      // Inside the [2700, 2950) overlap, keep current domain.
      if (inProDomain && rating < PRO_BASE_RATING) inProDomain = false;
      else if (!inProDomain && rating >= PRO_BASE_RATING + PRO_STEP * MAX_PRO + Math.ceil(PRO_STEP / 2)) inProDomain = true;
      $('#rank')[0].value = inProDomain
        ? `p${ratingToProLevel(rating)}`
        : `${ratingToRankInt(rating)}`;
    }
    return true;
  });

  // Webhook - sync players from the website. Inserts new players and updates existing ones
  // matched by external ids (EXT > EGF/FFG/AGA, mirroring Tournament.findPlayerByExternalIds).
  // Last-wins on rank/rating/club/country/skip/etc. — operator is expected to keep both sides
  // consistent. The server guards round-drops against pairings; those rejections are surfaced
  // as a distinct "blocked, already paired" line so the operator can spot a misordered flow
  // (correct procedure: freeze the round on the website *first*, then resync).
  $('#sync-website').on('click', async e => {
    let form = $('#tournament-infos')[0];
    let code = form.val('shortName');
    if (!code) {
      showError('Tournament short name is required for sync');
      return;
    }
    let data = await api.getJson(`webhook/players/${code}`);
    if (data === 'error') return;
    if (!data.status || !data.players) {
      showError(data.message || 'Invalid response from website');
      return;
    }
    let existing = await api.getJson(`tour/${tour_id}/part`);
    if (existing === 'error' || !Array.isArray(existing)) return;

    let byExt = {}, byEgf = {}, byFfg = {}, byAga = {};
    for (let p of existing) {
      if (p.ext) byExt[p.ext] = p;
      if (p.egf) byEgf[p.egf] = p;
      if (p.ffg) byFfg[p.ffg] = p;
      if (p.aga) byAga[p.aga] = p;
    }
    let findMatch = pl =>
      (pl.ext && byExt[pl.ext]) ||
      (pl.egf && byEgf[pl.egf]) ||
      (pl.ffg && byFfg[pl.ffg]) ||
      (pl.aga && byAga[pl.aga]) ||
      null;

    function buildPayload(wp) {
      // Wire format: `rank` is a string ("10k", "2d", "1p"). 1p..9p doubles as the pro flag.
      let parsed = parseRankAndPro(wp.rank);
      let rank = parsed?.rank ?? -20; // 20k fallback on garbage / missing
      let pro = parsed?.pro ?? 0;
      let rating = (wp.rating != null && !isNaN(parseInt(wp.rating)))
        ? parseInt(wp.rating)
        : (pro > 0 ? proLevelToRating(pro) : rankIntToRating(rank));
      let skip = [];
      if (wp.rounds) {
        for (let i = 0; i < wp.rounds.length; i++) {
          if (wp.rounds[i] === '0') skip.push(i + 1);
        }
      }
      return {
        name: wp.lastname, firstname: wp.firstname,
        country: (wp.country || '').toLowerCase(),
        club: wp.club || '',
        rank, rating, pro,
        egf: wp.pin || null, ffg: wp.ffg || null, aga: wp.aga || null,
        ext: wp.id != null ? String(wp.id) : null,
        final: true, skip
      };
    }

    let norm = s => (s == null ? '' : String(s)).trim().toLowerCase();
    // Server upcases country and rewrites GB→UK on store; align here so re-sync of a UK player
    // doesn't show as "updated" every time.
    let cnorm = c => { let s = norm(c); return s === 'gb' ? 'uk' : s; };
    let normSkip = a => Array.isArray(a) ? Array.from(new Set(a.map(Number))).sort((x, y) => x - y).join(',') : '';
    function isUnchanged(payload, current) {
      return norm(payload.name) === norm(current.name)
          && norm(payload.firstname) === norm(current.firstname)
          && cnorm(payload.country) === cnorm(current.country)
          && norm(payload.club) === norm(current.club)
          && parseInt(payload.rank) === parseInt(current.rank)
          && parseInt(payload.rating) === parseInt(current.rating)
          && (parseInt(payload.pro) || 0) === (parseInt(current.pro) || 0)
          && normSkip(payload.skip) === normSkip(current.skip || [])
          && norm(payload.egf) === norm(current.egf)
          && norm(payload.ffg) === norm(current.ffg)
          && norm(payload.aga) === norm(current.aga)
          && norm(payload.ext) === norm(current.ext);
    }

    let added = 0, updated = 0, unchanged = 0, failed = 0, lastError = null;
    let blocked = []; // [{ label, round }] — paired in the round being dropped
    for (let wp of data.players) {
      let payload = buildPayload(wp);
      let label = `${payload.name} ${payload.firstname || ''}`.trim();
      let match = findMatch(payload);
      if (!match) {
        let resp = await api.post(`tour/${tour_id}/part`, payload);
        if (resp.ok) {
          added++;
        } else {
          let body = await resp.json().catch(() => ({}));
          failed++;
          lastError = body.error || `HTTP ${resp.status}`;
        }
        continue;
      }
      if (isUnchanged(payload, match)) {
        unchanged++;
        continue;
      }
      payload.id = match.id;
      let resp = await api.put(`tour/${tour_id}/part/${match.id}`, payload);
      if (resp.ok) {
        updated++;
      } else {
        let body = await resp.json().catch(() => ({}));
        let err = body.error || `HTTP ${resp.status}`;
        failed++;
        let m = err.match(/player is playing in round #(\d+)/);
        if (m) blocked.push({ label, round: parseInt(m[1]) });
        else lastError = err;
      }
    }

    let lines = [];
    if (added > 0) lines.push(`  ${added} added`);
    if (updated > 0) lines.push(`  ${updated} updated`);
    if (unchanged > 0) lines.push(`  ${unchanged} unchanged`);
    if (blocked.length > 0) {
      let names = blocked.map(x => `${x.label} (round ${x.round})`).join(', ');
      lines.push(`  ${blocked.length} blocked — already paired: ${names}`);
    }
    let other = failed - blocked.length;
    if (other > 0) lines.push(`  ${other} other failed${lastError ? ` — last: ${lastError}` : ''}`);
    let msg = lines.length === 0 ? 'Sync: nothing to do' : 'Sync results:\n' + lines.join('\n');
    let hasError = failed > 0;
    if (added > 0 || updated > 0) {
      // Stash and reload so the table reflects the new state. The on-load handler re-shows it.
      store('refreshReport', { msg, error: hasError });
      setTimeout(() => window.location.reload(), 200);
    } else if (hasError) {
      showError(msg);
    } else {
      showSuccess(msg, true);
    }
  });

  // Refresh ratings — pulls latest rating/rank/pro for already-registered players from EGD/FFG.
  // Source priority when a player has multiple external IDs: EGF > FFG > AGA. Rank is updated
  // only when the player's current (rating, rank) are chained; rating and pro are always
  // updated. The summary toast lists rank-skipped players so the organiser can spot manual
  // overrides that would otherwise be silently kept.
  $('#refresh-ratings').on('click', async e => {
    e.preventDefault();
    let players = await api.getJson(`tour/${tour_id}/part`);
    if (players === 'error' || !Array.isArray(players)) return;
    let registered = players.filter(p => p.egf || p.ffg || p.aga);
    if (registered.length === 0) {
      showError('No registered player has an external ID (EGF/FFG/AGA) to refresh from.');
      return;
    }
    if (!confirm(`Refresh ratings for ${registered.length} registered player(s) from EGD/FFG?`)) return;

    // Bulk lookup
    let want = { egf: [], ffg: [], aga: [] };
    for (let p of registered) {
      if (p.egf) want.egf.push(p.egf);
      if (p.ffg) want.ffg.push(p.ffg);
      if (p.aga) want.aga.push(p.aga);
    }
    let lookup = await api.postJson('ratings-lookup', want);
    if (lookup === 'error' || typeof lookup !== 'object') {
      showError('ratings-lookup failed');
      return;
    }

    // Chain test: are (rating, rank, pro) internally consistent? Pros chain on pro level,
    // amateurs on rank. Used to decide whether to propagate rank/pro from the refreshed
    // state or keep the player's manual override.
    function isChained(rating, rank, pro) {
      if (isNaN(rating)) return false;
      if (pro > 0) return ratingToProLevel(rating) === pro;
      return !isNaN(rank) && ratingToRankInt(rating) === rank;
    }

    let updated = 0, unchanged = 0, rankSkipped = [], mmsCorrected = [], notFound = [], failed = 0, lastError = null;
    for (let p of registered) {
      // Pick the first source with a hit, in priority order.
      let hit = null;
      for (let src of ['egf', 'ffg', 'aga']) {
        let id = p[src];
        if (id && lookup[src] && lookup[src][id]) { hit = lookup[src][id]; break; }
      }
      if (!hit || hit.rating == null) {
        notFound.push(`${p.name} ${p.firstname || ''}`.trim());
        continue;
      }
      let newRating = parseInt(hit.rating);
      let newPro = hit.pro ? parseInt(hit.pro) : 0;
      let oldRating = parseInt(p.rating);
      let oldRank = parseInt(p.rank);
      let oldPro = p.pro ? parseInt(p.pro) : 0;
      let wasChained = isChained(oldRating, oldRank, oldPro);
      // When chained, propagate everything; when not, keep rank/pro and only refresh rating.
      let newRank = wasChained ? ratingToRankInt(newRating) : oldRank;
      let proToApply = wasChained ? newPro : oldPro;
      if (newRating === oldRating && newRank === oldRank && proToApply === oldPro) {
        unchanged++;
        continue;
      }
      try {
        let resp = await api.putJson(`tour/${tour_id}/part/${p.id}`, {
          id: p.id,
          rating: newRating,
          rank: newRank,
          pro: proToApply
        });
        if (resp === 'error') {
          failed++; lastError = `PUT failed for ${p.name}`;
          continue;
        }
        updated++;
        let label = `${p.name} ${p.firstname || ''}`.trim();
        if (!wasChained && newRating !== oldRating) rankSkipped.push(label);
        // Only flag mmsCorrection when the rank actually shifted (chained + new rank ≠ old rank).
        // That's when the player's effective MM bracket has moved and the manual correction
        // may no longer be appropriate. Untouched rank → MM bracket unchanged → no warning.
        let mc = parseInt(p.mmsCorrection || 0);
        if (mc !== 0 && wasChained && newRank !== oldRank) {
          mmsCorrected.push(`${label} (${mc > 0 ? '+' : ''}${mc})`);
        }
      } catch (err) {
        failed++; lastError = err.message || String(err);
      }
    }

    let parts = [];
    parts.push(`${updated} updated`);
    if (unchanged > 0) parts.push(`${unchanged} unchanged`);
    if (rankSkipped.length > 0) parts.push(`${rankSkipped.length} rank kept (unchained: ${rankSkipped.join(', ')})`);
    if (mmsCorrected.length > 0) parts.push(`${mmsCorrected.length} with MMS correction — review: ${mmsCorrected.join(', ')}`);
    if (notFound.length > 0) parts.push(`${notFound.length} not found in ratings DB${notFound.length <= 5 ? ` (${notFound.join(', ')})` : ''}`);
    if (failed > 0) parts.push(`${failed} failed${lastError ? ` (last: ${lastError})` : ''}`);
    let msg = parts.join('; ');
    // Stash the report and reload so the table reflects new rating/rank/pro values.
    // The on-load handler below re-shows it as a sticky toast.
    store('refreshReport', { msg: msg, error: failed > 0 });
    if (updated > 0) setTimeout(() => window.location.reload(), 200);
    else if (failed > 0) showError(msg); else showSuccess(msg, true);
  });
});
