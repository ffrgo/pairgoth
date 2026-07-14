// Utilities

const characters ='ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';

/* Per-user preferences — disabled. Display.pairing.blackFirst is now a server-side
   pairgoth.properties config; the per-user gear icon / settings modal are commented
   out in the layout. Kept here for potential reuse.
const prefs = {
  get: function(key) {
    return store('prefs.' + key);
  },
  set: function(key, value) {
    store('prefs.' + key, value);
  },
  getAll: function() {
    return {
      // key: value
    };
  }
};
*/
function randomString(length) {
  let result = '';
  const charactersLength = characters.length;
  for ( let i = 0; i < length; i++ ) {
    result += characters.charAt(Math.floor(Math.random() * charactersLength));
  }
  return result;
}

// serializeObject tweak to allow '.' to nest keys, and '-' in keys
/*
$.extend(FormSerializer.patterns, {
  fixed:    /^\d+$/,
  validate: /^[a-z][a-z0-9_-]*(?:\.[a-z0-9_-]+|\[[0-9]+\])*(?:\[\])?$/i,
  key:      /[a-z0-9_-]+|(?=\[\])/gi,
  named:    /^[a-z0-9_-]+$/i
});
 */

// deserializeObject
/*
jQuery.fn.populate = function (data) {
  if (!this.is('form')) throw "Error: ${this} is not a form";
  populate(this[0], data);
  return this;
};
 */

// crypto

async function digestMessage(message) {
  const msgUint8 = new TextEncoder().encode(message);                           // encode as (utf-8) Uint8Array
  const hashBuffer = await crypto.subtle.digest('SHA-256', msgUint8);           // hash the message
  const hashArray = Array.from(new Uint8Array(hashBuffer));                     // convert buffer to byte array
  const hashHex = hashArray.map((b) => b.toString(16).padStart(2, '0')).join(''); // convert bytes to hex string
  return hashHex;
}

// number formats

function setDecimals() {
  // due to a W3C decision, "number" inputs do not expose their selection, breaking inputmask library
  $('input[data-decimals="0"]').inputmask({ alias: 'integer', placeholder: '0', groupSeparator: ' ' });
  $('input[data-decimals="1"]').inputmask({ alias: 'numeric', placeholder: '0', groupSeparator: ' ', digits: 1 });
  $('input[data-decimals="2"]').inputmask({ alias: 'numeric', placeholder: '0', groupSeparator: ' ', digits: 2 });
  $('input[data-decimals="3"]').inputmask({ alias: 'numeric', placeholder: '0', groupSeparator: ' ', digits: 3 });
  $('input[data-decimals="4"]').inputmask({ alias: 'numeric', placeholder: '0', groupSeparator: ' ', digits: 4 });
  $('input.number:not([data-decimals]):not([data-digits])').inputmask({ alias: 'numeric', placeholder: '', groupSeparator: ' '});
  $('input[data-digits="2"]').inputmask({ alias: 'currency', placeholder: '0', groupSeparator: ' ', digits: 2, digitsOptional: false });
  $('input[data-digits="4"]').inputmask({ alias: 'currency', placeholder: '0', groupSeparator: ' ', digits: 4, digitsOptional: false });
}

/*
$(() => {
  setDecimals();
});
 */

function populateSelect(select, list, empty = false) {
  select.empty();
  if (empty) select.append('<option></option>');
  list.forEach(option => select.append(`<option value="${option.key}">${option.value}</option>`));
}

function spinner(show) {
  if (show) $('#backdrop').addClass('active');
  else $('#backdrop').removeClass('active');
}

function exportCSV(filename, content) {
  let body = content.map(s => [].concat(s).join(';')).join('\n');
  let blob = new Blob(['\uFEFF', body], {type: 'text/csv;charset=utf-8'});
  let link = document.createElement("a");
  let url = URL.createObjectURL(blob);
  link.setAttribute("href", url);
  link.setAttribute("download", filename);
  //link.setAttribute("target", "_blank")
  link.style.visibility = 'hidden';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}

/* modals
  NOT IN USE, see popup-related code.
NodeList.prototype.modal = function(show) {
  this.item(0).modal(show);
  return this;
}
Element.prototype.modal = function(show) {
  if (show) {
    document.body.addClass('dimmed');
    this.addClass('active');
  }
  else {
    this.removeClass('active');
    document.body.removeClass('dimmed');
  }
  return this;
}
 */

/* DOM helpers */

HTMLFormElement.prototype.val = function(name, value) {
  let hasValue = typeof(value) !== 'undefined';
  let ctl = this.find(`[name="${name}"]`)[0];
  if (!ctl) {
    console.error(`unknown input name: ${name}`)
    return undefined
  }
  let tag = ctl.tagName;
  let type = tag === 'INPUT' ? ctl.attr('type') : undefined;
  if (
    (tag === 'INPUT' && ['text', 'number', 'hidden', 'password'].includes(ctl.attr('type'))) ||
    tag === 'SELECT'
  ) {
    if (hasValue) {
      ctl.value = value;
      return;
    }
    else return ctl.value;
  } else if (tag === 'INPUT' && ctl.attr('type') === 'radio') {
    if (hasValue) {
      ctl = $(`input[name="${name}"][value="${value}"]`);
      if (ctl) ctl.checked = true;
      return;
    } else {
      ctl = $(`input[name="${name}"]:checked`);
      if (ctl) return ctl[0].value;
      else return null;
    }
  } else if (tag === 'INPUT' && ctl.attr('type') === 'checkbox') {
    if (hasValue) {
      ctl.checked = value !== 'false' && Boolean(value);
      return;
    }
    else return ctl.checked && ctl.value ? ctl.value : ctl.checked;
  }
  console.error(`unhandled input tag or type for input ${name} (tag: ${tag}, type:${type}`);
  return null;
};

function msg(id) {
  let ctl = $(`#${id}`)[0];
  return ctl.textContent;
}

function spinner(show) {
  if (show) $('#backdrop').addClass('active');
  else $('#backdrop').removeClass('active');
}

function modal(id) {
  $('body').addClass('dimmed');
  $(`#${id}.popup`).addClass('shown');
}

function close_modal() {
  // check if modal requires confirmation
  let shownPopup = $('.shown.popup');
  if (shownPopup.length == 1) {
    let id = shownPopup.attr('id');
    switch (id) {
      case 'player': {
        if (shownPopup.hasClass('edit') && !$('#register').hasClass('disabled')) {
          let confirmMessage = $('#drop-changes').text();
          if (!confirm(confirmMessage)) {
            return false;
          }
        }
        break;
      }
    }
  }
  // close modal
  $('body').removeClass('dimmed');
  $(`.popup`).removeClass('shown');
  store('addingPlayers', false);
  store('macmahonGroups', false);
}

function downloadFile(blob, filename) {
  let url = URL.createObjectURL(blob);
  let link = document.createElement("a");
  link.setAttribute("href", url);
  link.setAttribute("download", filename);
  link.style.visibility = 'hidden';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}

function isTouchDevice() {
  return (('ontouchstart' in window) ||
    (navigator.maxTouchPoints > 0) ||
    (navigator.msMaxTouchPoints > 0));
}

onLoad(() => {
  $('button.close').on('click', e => {
    close_modal();
    /* no need to be specific...
    let modal = e.target.closest('.popup');
    if (modal) {
      modal.removeClass('shown');
      $('body').removeClass('dimmed');
    }
     */
  });

  /* commented for now - do we want this?
  $('#dimmer').on('click', e => $('.popup').removeClass('shown');
   */

  /* Inhibate default behavior of page down and page up when search result is shown */
  document.addEventListener('keydown', e => {
    switch (e.key) {
      case 'PageDown':
      case 'PageUp': {
        if (document.location.hash === '#registration') {
          if (typeof (searchResultShown) === 'function' && searchResultShown()) {
            e.preventDefault();
            e.cancelBubble = true;
            e.stopPropagation();
            return false;
          }
        }
      }
    }
  }, true);

    // keyboard handling
  document.addEventListener('keyup', e => {
    let tab = document.location.hash;
    switch (e.key) {
      case 'Escape': {
        if (tab === '#registration') {
          if ($('#player').hasClass('shown') && searchResultShown()) {
            $('#needle')[0].value = '';
            initSearch();
          } else {
            close_modal();
          }
        } else if (tab === '#pairing') {
          $('#pairing-lists .selected.listitem').removeClass('selected');
        }
        break;
      }
      case 'ArrowDown': {
        if (tab === '#registration') {
          if (typeof(searchResultShown) === 'function' && searchResultShown()) {
            if (typeof (searchHighlight) === 'undefined') searchHighlight = 0;
            else ++searchHighlight;
            navigateResults(e);
          }
        }
        break;
      }
      case 'ArrowUp': {
        if (tab === '#registration') {
          if (typeof(searchResultShown) === 'function' && searchResultShown()) {
            if (typeof (searchHighlight) === 'undefined') searchHighlight = 0;
            else --searchHighlight;
            navigateResults(e);
          }
        }
        break;
      }
      case 'PageDown': {
        if (tab === '#registration') {
          if (typeof(searchResultShown) === 'function' && searchResultShown()) {
            console.log(searchHighlight)
            if (typeof (searchHighlight) === 'undefined') searchHighlight = 0;
            else searchHighlight += 12;
            navigateResults(e);
          }
        }
        break;
      }
      case 'PageUp': {
        if (tab === '#registration') {
          if (typeof(searchResultShown) === 'function' && searchResultShown()) {
            if (typeof (searchHighlight) === 'undefined') searchHighlight = 0;
            else searchHighlight -= 12;
            navigateResults(e);
          }
        }
        break;
      }
      case 'Enter': {
        if (tab === '#registration') {
          if (typeof(searchResultShown) === 'function') {
            if (searchResultShown() && typeof(searchHighlight) !== 'undefined') {
              fillPlayer(searchResult[searchHighlight]);
            } else {
              $('#register')[0].click();
            }
          }
        }
        break;
      }
      case '+': {
        if (tab === '#registration') {
          if (!$('#player').hasClass('shown')) {
            addPlayers();
          }
        }
        break;
      }
    }
  }, true);

  // disable hash scrolling
  if (window.location.hash) {
    setTimeout(function() {
      window.scrollTo(0, 0);
    }, 1);
  }

  // persistent scroll
  $('#center').on('scroll', e => {
    let scroll = $('#center')[0].scrollTop;
    store('scroll', scroll);
  });
  let persistentScroll = store('scroll');
  if (persistentScroll) {
    setTimeout(() => {
      $('#center')[0].scrollTop = persistentScroll;
      let scroll = $('#center')[0].scrollTop;
    }, 200);
  }
  $('.accordion .title').on('click', e => {
    let accordion = e.target.closest('.accordion');
    let title = e.target.closest('.title');
    let content = title.nextElementSibling;

    if (!title.hasClass('active')) {
      accordion.find('.active').removeClass('active');
    }
    title.toggleClass('active');
    content.toggleClass('active');
  });
  $('#dimmer').on('click', e => {
    let dialog = e.target.closest('.popup');
    if (!dialog && $('.shown.popup.no-dimmer-close').length === 0) close_modal();
  });

  /* Settings modal handlers - empty for now
  $('#settings').on('click', e => {
    modal('settings-modal');
  });

  $('#settings-save').on('click', e => {
    // let <setting> = <input state>;
    prefs.set('<setting>', <value>);
    // Set cookie for server-side rendering (expires in 1 year)
    document.cookie = `<key>=${value}; path=/; max-age=31536000; SameSite=Lax`;
    close_modal();
    // Reload page to apply new preference
    window.location.reload();
  });
  */

  if (isTouchDevice()) {
    $("[title]").on('click', e => {
      let item = e.target.closest('[title]');
      let title = item.getAttribute('title');
      let popup = item.find('.title-popup')
      if (popup.length === 0) {
        item.insertAdjacentHTML('beforeend', `<span class="title-popup">${title}</span>`);
      } else {
        item.removeChild(popup[0]);
      }
    });
  }
});

// --- Undo / history (header button → modal listing past actions, top-anchored multi-select) ---
// The list shows actions newest-first; clicking a row selects it and every newer one (you can only
// undo a contiguous run from "now"), then "Undo selected" restores the state before the oldest one.
let undoTour = undefined, undoCursor = null, undoDone = false, undoLoading = false, undoSelected = -1;

function loadUndoPage() {
  if (undoDone || undoLoading) return Promise.resolve();
  undoLoading = true;
  let url = `tour/${undoTour}/history?count=20` + (undoCursor ? `&before=${undoCursor}` : '');
  return api.getJson(url).then(rst => {
    if (rst === 'error' || !rst || !rst.entries) { undoDone = true; return; }
    let list = $('#undo-list')[0];
    rst.entries.forEach(e => {
      let item = document.createElement('div');
      item.className = 'listitem';
      item.setAttribute('data-restore', e.restoreKey);
      let action = document.createElement('span');
      action.className = 'action';
      action.textContent = e.label || (e.category ? e.category.replace(/-/g, ' ') : '(earlier version)');
      let when = document.createElement('span');
      when.className = 'when';
      when.textContent = e.time;
      item.appendChild(action);
      item.appendChild(when);
      list.appendChild(item);
    });
    undoCursor = rst.nextBefore;
    if (!undoCursor) undoDone = true;
  }).finally(() => { undoLoading = false; });
}

function openUndo() {
  undoTour = $('#undo')[0].getAttribute('data-tour');
  undoCursor = null; undoDone = false; undoSelected = -1;
  $('#undo-list')[0].clearChildren();
  $('#undo-confirm').addClass('disabled');
  loadUndoPage().then(() => {
    if ($('#undo-list .listitem').length === 0) {
      $('#undo-list')[0].insertAdjacentHTML('beforeend', '<div class="listitem empty">Nothing to undo</div>');
    }
    modal('undo-modal');
  });
}

function selectUndoThrough(item) {
  let children = $('#undo-list')[0].childNodes.filter('.listitem');
  let to = item.index('.listitem');
  for (let j = 0; j < children.length; ++j) {
    if (j <= to) children.item(j).addClass('selected');
    else children.item(j).removeClass('selected');
  }
  undoSelected = to;
  $('#undo-confirm').removeClass('disabled');
}

onLoad(() => {
  $('#undo').on('click', e => openUndo());
  $('#undo-list').on('click', e => {
    let item = e.target.closest('.listitem');
    if (item && !item.hasClass('empty')) selectUndoThrough(item);
  });
  $('#undo-list').on('scroll', e => {
    let el = e.target;
    if (el.scrollTop + el.clientHeight >= el.scrollHeight - 20) loadUndoPage();
  });
  $('#undo-confirm').on('click', e => {
    if (undoSelected < 0) return;
    let key = $('#undo-list')[0].childNodes.filter('.listitem').item(undoSelected).getAttribute('data-restore');
    api.postJson(`tour/${undoTour}/history`, { snapshot: key }).then(rst => {
      if (rst !== 'error') document.location.reload();
    });
  });
});

// --- Collaborative SSE client ---
// Subscribes to the tournament event stream (proxied to the api), filters to the current tournament,
// and marks the affected tabs stale. `history-gap` (jeasse) means replay was incomplete → resync by
// reloading. EventSource auto-reconnects and re-sends Last-Event-Id, so jeasse replays missed events.

// Workflow order: a change originating at tab i invalidates every tab j >= i (downstream derives
// from upstream). Each mutating event maps to its source tab. A standings-criteria change comes as a
// sparse PUT the api flags as StandingsUpdated, so it's sourced at standings (affects only standings).
const TAB_ORDER = ['information', 'registration', 'teams', 'pairing', 'results', 'standings'];
const EVENT_SOURCE_TAB = {
  TournamentUpdated: 'information',
  PlayerAdded: 'registration', PlayerUpdated: 'registration', PlayerDeleted: 'registration',
  PlayersImported: 'registration', RatingsRefreshed: 'registration', MMGroupsUpdated: 'registration',
  TeamAdded: 'teams', TeamUpdated: 'teams', TeamDeleted: 'teams',
  GamesAdded: 'pairing', GamesDeleted: 'pairing', GameUpdated: 'pairing', TablesRenumbered: 'pairing',
  ResultUpdated: 'results', ResultsCleared: 'results',
  StandingsUpdated: 'standings'
};
// Staleness is held as the '.stale' class on the menu item (single source of truth, queried by
// chooseStep, reset on reload) — no visual badge by design. The current tab is left alone here
// (its dynamic/warn handling is a later cycle).
function currentStep() {
  return (window.location.hash || '').substring(1) || $('.step.active')[0]?.attr('data-step');
}

function markStaleFrom(sourceStep) {
  let from = TAB_ORDER.indexOf(sourceStep);
  if (from < 0) return;
  let current = currentStep();
  for (let i = from; i < TAB_ORDER.length; ++i) {
    let step = TAB_ORDER[i];
    if (step === current) continue;
    $(`.step[data-step="${step}"]`).addClass('stale');
  }
}

// Single choke point for every tournament-mutating click — the collaborative/direct branch lives
// here, not at the ~30 call sites.
//   collaborative : POST only — the server's SSE echo drives the data effect for everyone, the actor
//                   included (no local effect → no double-apply, observers never stale).
//   direct        : POST, then apply the local effect on the response + mark downstream tabs stale
//                   for this single op (today's behaviour, no EventSource open).
// `effect` is the tournament-STATE change (patch a cell, add a row); it shares its DOM primitive with
// the matching SSE handler (written once, two callers). Actor-local UI (close the dialog, clear the
// form, success tick) is NOT passed here — `await mutate(...)` then do it inline; it runs in both modes.
// `source` defaults to the current tab (the actor is on the originating tab); override it for the rare
// cross-tab op (e.g. a pairable's participation changed from the pairing step → source 'registration').
async function mutate({ method = 'put', url, body = {}, source, effect }) {
  if (readOnly) return 'readonly';              // tab frozen on outdated data — no overwrite path
  let rst = await (api[`${method}Json`]).call(api, url, body);
  if (rst === 'error') return rst;              // api.js already surfaced the error
  if (!collaborative) {
    if (effect) effect(rst);                    // local state effect on the response
    markStaleFrom(source || currentStep());     // this op's own cross-tab staleness
  }
  return rst;                                   // for actor-local follow-up (both modes)
}

// --- Collaborative current-tab handling (dormant building blocks; wired in by the per-event SSE
//     handlers in later increments) ---
// A collaborative echo sourced at tab S affects every tab j >= S. Off-screen affected tabs are
// stale-marked (reload on entry). For the on-screen affected tab, finely-patchable events patch in
// place; anything else must reload — but a blind reload would discard in-progress local work, so
// busy() gates it behind a warn dialog (Reload / Read-only).

// Is the on-screen tab in a state a reload would destroy, given that `affected` (the id of the changed
// entity, when relevant) just changed? The Add dialog and results survive a reload, so they're never
// busy; registration is busy only when the Edit dialog is open on the very player that changed.
function busy(affected) {
  switch (currentStep()) {
    case 'information': return $('#tournament-infos').hasClass('edit');
    case 'teams':       return $('#teams .selected.listitem, #teamables .selected.listitem').length > 0;
    case 'pairing':     return $('#pairing-lists .selected.listitem').length > 0;
    case 'standings':   return !$('#params-submit').hasClass('hidden') || $('#publish-modal').hasClass('shown');
    case 'registration':
      return $('#player').hasClass('shown') && $('#player').hasClass('edit') &&
             affected != null && String($('#player-form')[0].val('id')) === String(affected);
    default:            return false; // results: patched, nothing fragile on screen
  }
}

// Read-only freezes the on-screen tab on its (now outdated) data with no overwrite path: the body
// class disables its controls (CSS) and reveals a reload banner; the flag makes mutate() a no-op as a
// backstop. The frozen tab is also stale-marked so returning to it later reloads fresh.
let readOnly = false;
function enterReadOnly() {
  readOnly = true;
  $('body').addClass('read-only');
  $(`.step[data-step="${currentStep()}"]`).addClass('stale');
}

// Debounced: a remote change to the on-screen tab while it's busy warns the operator — but our own
// reload-based mutation also echoes back, and would briefly flash this modal before the local reload
// fires. Delaying the modal lets that reload (page navigation) preempt it; a genuine remote change
// (no local reload) still warns, just ~300ms later. A failed mutation dispatches no echo, so the only
// echoes in flight are successes, which reload.
let warnTimer = null;
function warnReloadOrReadonly() {
  if (readOnly || warnTimer || $('#sse-warn-modal').hasClass('shown')) return;
  warnTimer = setTimeout(() => {
    warnTimer = null;
    if (!readOnly && !$('#sse-warn-modal').hasClass('shown')) modal('sse-warn-modal');
  }, 300);
}

// What an affecting echo does to the on-screen tab when no finer patch applies: reload, or — if a
// reload would lose work — warn. `affected` is the changed entity's id (for the same-player check).
// (Dormant: called by the per-event SSE handlers in later increments.)
function onCurrentTabAffected(affected) {
  if (readOnly) return;
  if (busy(affected)) warnReloadOrReadonly();
  else window.location.reload();
}

// In-place patch handlers registered per event by the tab modules (tour-results.inc.js etc.). A
// handler receives the event data, patches the on-screen (source) tab, and returns true if it fully
// handled it; returning falsy falls through to the coarse reload/warn (e.g. a player identity change
// that can't be patched in place).
const sseEffects = {};
function sseEffect(name, fn) { sseEffects[name] = fn; }

// Collaborative-mode dispatch for one event: every affected tab (j >= source) off-screen goes stale
// (reload on entry); the on-screen tab, if affected (its index >= source index), is patched in place
// when a handler is registered for (event, source tab), else coarsely reloaded/warned.
function handleCollaborativeEvent(name, source, data) {
  markStaleFrom(source);                                                   // off-screen downstream tabs
  if (TAB_ORDER.indexOf(currentStep()) < TAB_ORDER.indexOf(source)) return; // on-screen tab is upstream → unaffected
  // an in-place patch is only meaningful on the tab the change originated from (downstream tabs derive
  // from it and must reload/warn)
  let patched = currentStep() === source && sseEffects[name] && sseEffects[name](data);
  if (!patched) onCurrentTabAffected(name === 'PlayerUpdated' ? data?.id : undefined);
}

onLoad(() => {
  $('#sse-warn-reload').on('click', () => window.location.reload());
  $('#sse-warn-readonly').on('click', () => { close_modal(); enterReadOnly(); });
  $('#sse-readonly-reload').on('click', () => window.location.reload());
  // leaving a frozen tab releases its read-only freeze (the tab stays stale → reloads on return)
  $('.step').on('click', () => { if (readOnly) { readOnly = false; $('body').removeClass('read-only'); } });
});

onLoad(() => {
  if (typeof tour_id === 'undefined') return;
  // Collaborative mode only opens an SSE stream — it needs one per tab, hence HTTP/2 (or dev). In
  // direct mode we open none (HTTP/1.1's ~6-conn/origin limit would break multi-tab): mutate()'s local
  // effect + stale-marking cover the single operator; cross-browser real-time is the accepted degradation.
  if (!collaborative) return;
  // the api webapp is mounted at context /api/tour, so its SSE endpoint is /api/tour/events
  // (served directly same-origin in standalone; proxied via /api/tour/* in client mode)
  let source = new EventSource('/api/tour/events');
  source.addEventListener('history-gap', e => {
    console.warn('[sse] history gap — reloading to resync');
    document.location.reload();
  });
  Object.keys(EVENT_SOURCE_TAB).forEach(name => source.addEventListener(name, e => {
    let payload = JSON.parse(e.data);
    if (payload && payload.tournament !== tour_id) return;
    handleCollaborativeEvent(name, EVENT_SOURCE_TAB[name], payload.data);
  }));
  source.onerror = () => console.warn('[sse] disconnected (auto-reconnecting)');
  // bfcache keeps navigated-away documents alive: each parked page's open EventSource holds one of
  // HTTP/1.1's ~6 conns/origin, so a few quick round navigations stall the next page load for ~45s.
  // Release the socket on leave; on restore, reload (no Last-Event-Id → missed events are unrecoverable).
  window.on('pagehide', () => source.close());
  window.on('pageshow', e => { if (e.persisted) document.location.reload(); });
});

// Element.clearChildren method
if( typeof Element.prototype.clearChildren === 'undefined' ) {
  Object.defineProperty(Element.prototype, 'clearChildren', {
    configurable: true,
    enumerable: false,
    value: function() {
      while(this.firstChild) this.removeChild(this.lastChild);
    }
  });
}
