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
function rankIntToRating(k) { return 2100 + 100 * k; } // band centre (EGD nominal), not the xx50 edge
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

// Derive rating from the current rank dropdown (pro -> canonical pro rating, else amateur rank rating).
function syncRatingFromRank() {
  let rankValue = $('#rank')[0].value;
  let ratingCtl = $('#player input[name="rating"]')[0];
  if (isProFormValue(rankValue)) ratingCtl.value = proLevelToRating(proLevelOf(rankValue));
  else if (rankValue !== '') ratingCtl.value = rankIntToRating(parseInt(rankValue));
}

// When rating and rank are unlinked, the "Rank" field is an honorary grade used only for display.
function updateLinkHints() {
  let ratingEl = document.getElementById('rating');
  let rankEl = document.getElementById('rank');
  let ratingInfo = ratingEl && ratingEl.closest('.field') && ratingEl.closest('.field').querySelector('.hint');
  let rankInfo = rankEl && rankEl.closest('.field') && rankEl.closest('.field').querySelector('.hint');
  if (chained) {
    if (ratingInfo) ratingInfo.innerHTML = '';
    if (rankInfo) rankInfo.innerHTML = '';
    return;
  }
  let rating = parseInt(ratingEl ? ratingEl.value : NaN);
  if (ratingInfo) ratingInfo.innerHTML = isNaN(rating) ? '' : `<i>Effective rank used for pairing: ${displayRank(ratingToRankInt(rating))}</i>`;
  if (rankInfo) rankInfo.innerHTML = '<i>Honorary rank used for display only.</i>';
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

// Clipboard fallback for non-secure (plain HTTP) contexts where navigator.clipboard is absent.
function legacyCopy(text) {
  let ta = document.createElement('textarea');
  ta.value = text;
  ta.style.position = 'fixed';
  ta.style.opacity = '0';
  document.body.appendChild(ta);
  ta.select();
  let ok = false;
  try { ok = document.execCommand('copy'); } catch (ignored) {}
  document.body.removeChild(ta);
  return ok;
}

// Fill and open the operation report modal: one collapsible <details> box per section
// ({ label, items }) — summary = the count line, body = the per-item lines. Shared by
// ratings-refresh and the EGC sync; `title` names the source.
function showReport(title, sections) {
  let header = $('#refresh-report-modal .popup-header')[0];
  if (header) header.textContent = title;
  let box = $('#refresh-report-text')[0];
  box.innerHTML = '';
  (sections || []).forEach(s => {
    let det = document.createElement('details');
    let sum = document.createElement('summary');
    sum.textContent = s.label;
    det.appendChild(sum);
    if (s.items && s.items.length) {
      let pre = document.createElement('pre');
      pre.textContent = s.items.join('\n');
      det.appendChild(pre);
    }
    box.appendChild(det);
  });
  modal('refresh-report-modal');
}

// Flatten the shown report to plain text (every section, regardless of expand state) for the clipboard.
function reportToText() {
  return $('#refresh-report-text details').map(d => {
    let sum = d.querySelector('summary').textContent;
    let body = d.querySelector('pre');
    return body && body.textContent ? `${sum}\n${body.textContent}` : sum;
  }).join('\n\n');
}

function search(needle) {
  needle = needle.trim();
  if (needle && (needle === '*' || needle.length > 2)) {
    let form = $('#search-form')[0];
    let search = { needle: needle };
    // a disabled source has no toggle in the DOM (template-gated); only read the ones present
    // (form.val() on a missing field would log an error) — EXT is off by default, so this matters.
    ['egf', 'ffg', 'ext'].forEach(src => {
      if (form.find(`[name="${src}"]`).length) search[src] = form.val(src);
    });
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
  // single bulk upsert (one event server-side) instead of a per-player PUT loop. reason=mms so the
  // undo list labels it "Mac Mahon groups" — every bulkUpdate caller is a Mac Mahon group edit.
  api.postJson(`tour/${tour_id}/part?reason=mms`, players)
    .then(rst => { if (rst !== 'error') window.location.reload(); });
}

// Split the rounds into the event's two weeks. The model has no per-round dates, so spread the
// rounds evenly across the day span and cut at the 7-day mark (a balanced two-week event then
// divides in two); fall back to plain halving when the dates are unusable. `days` is the inclusive
// span, used by the caller to decide whether the event lasts more than one week at all.
function weekSplit() {
  let R = tour_rounds;
  let start = Date.parse(tour_startDate), end = Date.parse(tour_endDate);
  let days = (!isNaN(start) && !isNaN(end)) ? Math.round((end - start) / 86400000) + 1 : 0;
  let w1 = 0;
  if (days > 7) {
    for (let r = 1; r <= R; ++r) if (Math.floor((r - 1) * days / R) + 1 <= 7) w1++;
  } else {
    w1 = Math.floor(R / 2);
  }
  w1 = Math.min(Math.max(w1, 1), R - 1);
  let week1 = [], week2 = [];
  for (let r = 1; r <= R; ++r) (r <= w1 ? week1 : week2).push(r);
  return { week1, week2, days };
}

// Keep only players present (≥1 round) in each *checked* week; both checked → present in both weeks.
// A player's present rounds are the green participation labels on their row. Uses its own class so it
// composes with the reglist-mode (.filtered) and search (.hidden) filters.
function applyWeekFilter(w1Rounds, w2Rounds) {
  $('#players tbody tr').forEach(tr => {
    let hide = false;
    if (w1Rounds || w2Rounds) {
      let present = new Set();
      tr.querySelectorAll('.participation label.green').forEach(l => present.add(parseInt(l.textContent)));
      let inWeek = rounds => rounds.some(r => present.has(r));
      if ((w1Rounds && !inWeek(w1Rounds)) || (w2Rounds && !inWeek(w2Rounds))) hide = true;
    }
    if (hide) tr.addClass('week-filtered'); else tr.removeClass('week-filtered');
  });
}

// --- Shared row-patch primitives (the direct toggle effect AND the PlayerUpdated SSE echo call these,
//     setting state from the new value so they're idempotent) ---
function setRowFinal(id, final) {
  let tr = $(`#players tr[data-id="${id}"]`);
  if (tr.length === 0 || tr.hasClass('final') === final) return;
  let cell = tr.find('td.reg-status');
  if (final) { tr.addClass('final'); cell.addClass('final'); }
  else { tr.removeClass('final'); cell.removeClass('final'); }
  let confirmed = $('#confirmed-count')[0];
  if (confirmed) confirmed.innerText = parseInt(confirmed.innerText) + (final ? 1 : -1);
}

function setRowParticipation(id, skip) {
  let tr = $(`#players tr[data-id="${id}"]`);
  if (tr.length === 0) return;
  let skipSet = new Set((skip || []).map(Number));
  tr.find('.participation label').forEach(label => {
    let playing = !skipSet.has(parseInt(label.innerText));
    label.addClass(playing ? 'green' : 'red');
    label.removeClass(playing ? 'red' : 'green');
  });
}

// The website (EGC) is the source of truth for per-round presence. Mirror a referee's per-round
// participation flip back to it. Only website-sourced players (with an ext id) are pushed — plain
// events have none, so this is a no-op for them. Returns:
//   'noop'   — no ext/code: not website-sourced, nothing to push (caller proceeds locally).
//   'ok'     — the website accepted the change.
//   { message } — the website rejected it or was unreachable; `message` is its reason.
// An *add* (red→green) is gated on 'ok' (the website owns registration choices and may refuse a
// round the player isn't registered for); a *removal* commits regardless and only soft-warns.
async function pushPresence(ext, round, present) {
  let code = $('#tournament-infos')[0].val('shortName');
  if (!ext || !code) return 'noop';
  // Mirror the website's own id type: it ships a numeric id from /players/{code}, so send a number back.
  let id = /^\d+$/.test(String(ext)) ? Number(ext) : ext;
  try {
    let resp = await api.post(`webhook/presences/${code}/${round}`, [{ id: id, present: present }]);
    let json = await resp.json().catch(() => null);
    if (!resp.ok || (json && json.status === false)) {
      return { message: (json && json.message) || `HTTP ${resp.status}` };
    }
    // 200 + status:true can still carry a per-id refusal in `rejected` (e.g. not
    // registered for this round). We push one id, so ours appearing there = refused.
    if (json && Array.isArray(json.rejected) && json.rejected.map(String).includes(String(id)))
      return { message: `player not registered for this round` };
    return 'ok';
  } catch (ignored) {
    return { message: 'website unreachable' };
  }
}

function removePlayerRow(id) {
  let tr = $(`#players tr[data-id="${id}"]`);
  if (tr.length === 0) return;
  if (tr.hasClass('final')) {
    let confirmed = $('#confirmed-count')[0];
    if (confirmed) confirmed.innerText = parseInt(confirmed.innerText) - 1;
  }
  tr[0].remove();
}

// PlayerUpdated echo: patch reg-status (bit 2) / participation (bit 4) in place; identity (bit 1) can't
// be patched (MMS, conditional columns, sort) → return falsy to fall through to reload/warn.
function patchPlayerRow(player) {
  let changes = player.changes || 0;
  if (changes & 1) return false;
  if (changes & 2) setRowFinal(player.id, player.final);
  if (changes & 4) setRowParticipation(player.id, player.skip);
  return true;
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
  // collaborative echoes: bit2/3 patch the row in place; identity/add → reload; delete → drop the row
  sseEffect('PlayerUpdated', player => patchPlayerRow(player));
  sseEffect('PlayerAdded', () => false);
  sseEffect('PlayerDeleted', data => { removePlayerRow(data.id); return true; });

  $('input.numeric').imask({
    mask: Number,
    scale: 0,
    min: rankIntToRating(MIN_RANK), // -900: EGD's hard GoR floor (30k nominal)
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
    for (let origin of ['egf', 'ffg', 'ext']) {
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
    for (let id of ["countryFilter", /* "aga", */ "egf", "ffg", "ext", "browse"]) {
      let ctl = $(`#${id}`);
      // a state predating a newly enabled source must not override its default (checked)
      if (ctl.length !== 0 && id in searchFormState) {
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
      browse: !!form.val('browse')
    };
    // only persist sources whose toggle is present (a disabled source is template-gated out)
    ['egf', 'ffg', 'ext'].forEach(src => {
      if (form.find(`[name="${src}"]`).length) searchFormState[src] = !!form.val(src);
    });
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
  // Per-week presence filter — only for a multi-week event with at least one player. Persistent,
  // unchecked by default. The chosen round ranges are surfaced in the labels' tooltips (inspectable).
  let ws = weekSplit();
  if (tour_rounds >= 2 && ws.days > 7 && $('#players tbody tr').length > 0) {
    let range = a => a.length > 1 ? `rounds ${a[0]}–${a[a.length - 1]}` : `round ${a[0]}`;
    $('#week1-filter')[0].closest('label').attr('title', range(ws.week1));
    $('#week2-filter')[0].closest('label').attr('title', range(ws.week2));
    let saved = store('reglistWeeks') || {};
    $('#week1-filter')[0].checked = !!saved.week1;
    $('#week2-filter')[0].checked = !!saved.week2;
    let apply = () => {
      let w1 = $('#week1-filter')[0].checked, w2 = $('#week2-filter')[0].checked;
      store('reglistWeeks', { week1: w1, week2: w2 });
      applyWeekFilter(w1 ? ws.week1 : null, w2 ? ws.week2 : null);
    };
    $('#week1-filter').on('change', apply);
    $('#week2-filter').on('change', apply);
    $('#week-filter').removeClass('hidden');
    apply(); // honour the restored state on load
  }
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
    mutate({
      url: `tour/${tour_id}/part/${id}`, body: { id: id, final: newStatus },
      source: 'registration', effect: () => setRowFinal(id, newStatus)
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
    // Relinking reconciles from rank to rating.
    if (chained) syncRatingFromRank();
    updateLinkHints();
  });
  $('#player input[name="rating"]').on('input', e=>{
    updateLinkHints();
  });
  $('#player select[name="rank"]').on('input', e=>{
    if (chained) syncRatingFromRank();
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
    store.remove('refreshReport');
    if (refreshReport.sections && refreshReport.sections.length) showReport(refreshReport.title, refreshReport.sections);
    else showSuccess('Ratings refreshed', true);
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
  // Mac Mahon dialog: per-list italic footer "<n> players[, <m> selected]", refreshed on selection
  // (each list box dispatches a 'listitems' event) and on filter toggle. Counts reflect what's shown:
  // when "Only players playing all rounds" is on, players with a non-empty skip set (data-skip != 0)
  // are hidden via a container class and excluded from the count.
  let mmGroups = ['under-group', 'top-group', 'super-group'].map(id => $('#' + id)[0]).filter(Boolean);
  function mmFooter(box) {
    let onlyAll = $('#macmahon-groups').hasClass('allrounds-only');
    let items = Array.from(box.querySelectorAll('.listitem'))
      .filter(i => !onlyAll || i.getAttribute('data-skip') === '0');
    let selected = items.filter(i => i.classList.contains('selected')).length;
    box.setAttribute('data-footer', selected ? `${items.length} players, ${selected} selected`
                                             : `${items.length} players`);
  }
  mmGroups.forEach(box => { box.on('listitems', () => mmFooter(box)); mmFooter(box); });
  $('#mm-allrounds-filter').on('change', e => {
    if (e.target.checked) {
      $('#macmahon-groups').addClass('allrounds-only');
      // drop selection on now-hidden players so a move never promotes someone you can't see
      $('#macmahon-groups .listitem.selected').forEach(i => {
        if (i.getAttribute('data-skip') !== '0') { i.removeClass('selected'); i.attr('draggable', false); }
      });
    } else $('#macmahon-groups').removeClass('allrounds-only');
    mmGroups.forEach(mmFooter);
  });
  $('.player-fields').on('change input', e => {
    $('#register').removeClass('disabled');
  });
  $('.participation label').on('click', async e => {
    e.preventDefault();
    let part = e.target;
    let tr = part.closest('tr');
    let id = tr.data('id');
    let ext = tr.data('ext');
    let round = parseInt(part.text());
    let skip = new Set(part.closest('.participation').find('label.red').map(it => parseInt(it.innerText)));
    if (skip.has(round)) skip.delete(round);
    else skip.add(round);
    let skipArr = Array.from(skip);
    let present = !skip.has(round);
    let commit = () => mutate({
      url: `tour/${tour_id}/part/${id}`, body: { id: id, skip: skipArr },
      source: 'registration', effect: () => setRowParticipation(id, skipArr)
    });
    // Add (red→green) of a website-sourced player on an EGC deployment: gate the local change on a
    // confirmed push — the website may refuse a round the player isn't registered for. The label only
    // flips via the effect/echo, so aborting is just not committing (nothing to revert).
    if (present && webhook && ext) {
      let pushed = await pushPresence(ext, round, true);
      if (pushed !== 'ok' && pushed !== 'noop') {
        showError(`The website refused this presence change: ${pushed.message}. Not applied.`);
        return false;
      }
      await commit();
      return false;
    }
    // Removal (or non-website player): commit locally, then mirror back best-effort.
    let rst = await commit();
    if (rst !== 'error' && rst !== 'readonly') {
      let pushed = await pushPresence(ext, round, present);
      if (pushed !== 'ok' && pushed !== 'noop')
        showError(`Presence updated locally but not pushed to the website: ${pushed.message}. Update it there by hand.`);
    }
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

    // Translate the website wire format to pairgoth player payloads; the server does the matching
    // (by external id), the merge and the journal in one shot (one event, not one per player).
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
      let payload = {
        name: wp.lastname, firstname: wp.firstname,
        country: (wp.country || '').toLowerCase(),
        club: wp.club || '',
        rank, rating, pro,
        egf: wp.pin || null, ffg: wp.ffg || null, aga: wp.aga || null,
        ext: wp.id != null ? String(wp.id) : null,
        final: true, skip
      };
      // Level lock: the website flags its rating-exception players; only pass the flag through
      // when the wire carries it — an absent flag must never silently unlock.
      if (wp.locked != null) payload.locked = !!wp.locked;
      return payload;
    }

    let report = await api.postJson(`tour/${tour_id}/part`, data.players.map(buildPayload));
    if (report === 'error') return;

    // Journal → operator report, one collapsible box per section. The server returns per-section
    // lists (names / {player, changes} / {player, reason}); paired-player rejections are split into
    // a distinct "blocked" box (correct procedure: freeze the round on the website first, then resync).
    let added = report.added || [], updated = report.updated || [], unchanged = report.unchanged || [];
    let failed = report.failed || [], missing = report.missing || [];
    let blocked = failed.filter(f => /round #\d+/.test(f.reason || ''));
    let other = failed.filter(f => !/round #\d+/.test(f.reason || ''));
    let sections = [];
    if (added.length) sections.push({ label: `${added.length} added`, items: added });
    if (updated.length) sections.push({ label: `${updated.length} updated`, items: updated.map(u => `${u.player} — ${u.changes}`) });
    if (blocked.length) sections.push({ label: `${blocked.length} blocked (already paired — freeze the round on the website first)`,
      items: blocked.map(f => `${f.player} (${(f.reason || '').match(/round #\d+/)[0]})`) });
    if (other.length) sections.push({ label: `${other.length} failed`, items: other.map(f => `${f.player || '?'}: ${f.reason}`) });
    if (missing.length) sections.push({ label: `${missing.length} removed on website (kept here)`, items: missing });
    if (unchanged.length) sections.push({ label: `${unchanged.length} unchanged`, items: unchanged });

    if (added.length || updated.length) {
      // Stash and reload so the table reflects the new state; the on-load handler re-shows the report.
      store('refreshReport', { title: 'Sync from EGC', sections });
      setTimeout(() => window.location.reload(), 200);
    } else if (blocked.length || other.length || missing.length) {
      showReport('Sync from EGC', sections); // nothing changed but problems to surface
    } else {
      // Pure no-op (at most "N unchanged"): a one-line toast, no modal.
      showSuccess(unchanged.length ? `Sync from EGC: ${unchanged.length} player(s) already up to date`
                                   : 'Sync from EGC: nothing to do', true);
    }
  });

  // Refresh ratings — pulls latest rating/rank/pro for already-registered players from EGD/FFG.
  // Source priority when a player has multiple external IDs: EGF > FFG > AGA. An honorary rank
  // (rank decoupled from rating — the edit form's "unchained" state) is kept: only the rating
  // is refreshed. The report lists those separately so the organiser can spot them.
  $('#refresh-ratings').on('click', async e => {
    e.preventDefault();
    let players = await api.getJson(`tour/${tour_id}/part`);
    if (players === 'error' || !Array.isArray(players)) return;
    let registered = players.filter(p => p.egf || p.ffg || p.aga);
    if (registered.length === 0) {
      showError('No registered player has an external ID (EGF/FFG/AGA) to refresh from.');
      return;
    }
    // Locked players (rating exceptions, flagged by the website) are skipped up front — the server
    // would preserve them anyway, but skipping keeps the change-log and the journal truthful.
    let lockedSkipped = registered.filter(p => p.locked)
      .map(p => `${p.name} ${p.firstname || ''}`.trim()).sort((a, b) => a.localeCompare(b));
    registered = registered.filter(p => !p.locked);
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

    // Overwrite everything from the official source (rating, level, pro). The FFG licence
    // snapshot is refreshed for FR tournaments only (and only where the source carries it).
    let isFR = (tour_country || '').toLowerCase() === 'fr';
    let changes = [], honoraryChanges = [], notFound = [], payloads = [];
    for (let p of registered) {
      // Pick the first source with a hit, in priority order.
      let hit = null;
      for (let src of ['egf', 'ffg', 'aga']) {
        let id = p[src];
        if (id && lookup[src] && lookup[src][id]) { hit = lookup[src][id]; break; }
      }
      if (!hit || hit.rating == null) {
        // surface the looked-up id(s) so a source mismatch (e.g. only EGF while FFG is active) is visible
        let ids = ['egf', 'ffg', 'aga'].filter(s => p[s]).map(s => `${s} ${p[s]}`).join(', ');
        notFound.push(`${p.name} ${p.firstname || ''}`.trim() + (ids ? ` (${ids})` : ''));
        continue;
      }
      let newRating = parseInt(hit.rating);
      let oldRating = parseInt(p.rating), oldRank = parseInt(p.rank), oldPro = p.pro ? parseInt(p.pro) : 0;
      // Honorary rank = rank decoupled from rating (unchained): the organiser's grade wins,
      // only the rating is refreshed — the payload omits rank/pro so the merge keeps them.
      let honorary = oldRank !== ratingToRankInt(oldRating);
      let newPro = honorary ? oldPro : (hit.pro ? parseInt(hit.pro) : 0);
      let newRank = honorary ? oldRank : ratingToRankInt(newRating);
      let oldLicensed = (typeof p.licensed === 'boolean') ? p.licensed : null;
      let newLicensed = (isFR && hit.license != null) ? (hit.license === 'L') : oldLicensed;
      let levelChanged = newRating !== oldRating || newRank !== oldRank || newPro !== oldPro;
      if (!levelChanged && newLicensed === oldLicensed) continue;
      let payload = honorary ? { id: p.id, rating: newRating }
                             : { id: p.id, rating: newRating, rank: newRank, pro: newPro };
      if (isFR && hit.license != null) payload.licensed = newLicensed;
      payloads.push(payload);
      // the change-log is computed client-side from old vs looked-up new (the server journal only counts)
      if (levelChanged) {
        let name = `${p.name} ${p.firstname || ''}`.trim();
        (honorary ? honoraryChanges : changes)
          .push(`${name} (${displayRank(oldRank, oldPro)}, ${oldRating}) => (${displayRank(newRank, newPro)}, ${newRating})`);
      }
    }

    let failed = 0, lastError = null;
    if (payloads.length) {
      let report = await api.postJson(`tour/${tour_id}/part?reason=ratings`, payloads);
      if (report === 'error') { failed = payloads.length; lastError = 'bulk update failed'; }
      else { failed = (report.failed || []).length; lastError = report.failed?.[0]?.reason || null; }
    }
    let updated = payloads.length - failed;

    changes.sort((a, b) => a.localeCompare(b));
    honoraryChanges.sort((a, b) => a.localeCompare(b));
    notFound.sort((a, b) => a.localeCompare(b));
    let sections = [];
    if (changes.length) sections.push({ label: `${changes.length} updated`, items: changes });
    if (honoraryChanges.length) sections.push({ label: `${honoraryChanges.length} rating updated, honorary rank kept`, items: honoraryChanges });
    if (lockedSkipped.length) sections.push({ label: `${lockedSkipped.length} locked (level preserved)`, items: lockedSkipped });
    if (notFound.length) sections.push({ label: `${notFound.length} not found in ratings DB`, items: notFound });
    if (failed) sections.push({ label: `${failed} failed`, items: lastError ? [lastError] : [] });
    if (updated > 0) {
      // reload so the table reflects new values (incl. licence-only changes); report survives in storage.
      // licence-only updates leave `sections` empty → the on-load handler falls back to a plain toast.
      store('refreshReport', { title: 'Ratings refresh', sections });
      setTimeout(() => window.location.reload(), 200);
    } else if (sections.length) {
      showReport('Ratings refresh', sections); // only not-found / failed: nothing changed, no reload
    } else {
      showSuccess('Ratings refresh: no changes', true);
    }
  });
  $('#copy-refresh-report').on('click', () => {
    let text = reportToText();
    // navigator.clipboard exists only in secure contexts (HTTPS / localhost); over plain HTTP it's
    // undefined, so fall back to the legacy execCommand path.
    if (navigator.clipboard) {
      navigator.clipboard.writeText(text)
        .then(() => showSuccess('Copied to clipboard', true))
        .catch(() => showError('Copy failed'));
    } else if (legacyCopy(text)) {
      showSuccess('Copied to clipboard', true);
    } else {
      showError('Copy failed');
    }
  });
});
