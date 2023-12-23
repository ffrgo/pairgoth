// Utilities

const characters ='ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
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
  }
  let tag = ctl.tagName;
  let type = tag === 'INPUT' ? ctl.attr('type') : undefined;
  if (
    (tag === 'INPUT' && ['text', 'number', 'hidden'].includes(ctl.attr('type'))) ||
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
  $('body').removeClass('dimmed');
  $(`.popup`).removeClass('shown');
}

onLoad(() => {
  $('button.close').on('click', e => {
    let modal = e.target.closest('.popup');
    if (modal) {
      modal.removeClass('shown');
      $('body').removeClass('dimmed');
    }
  });

  /* commented for now - do we want this?
  $('#dimmer').on('click', e => $('.popup').removeClass('shown');
   */

  // keyboard handling
  document.on('keyup', e => {
    switch (e.key) {
      case 'Escape': {
        if ($('#player').hasClass('shown')) {
          if ($('#needle')[0].value) {
            $('#needle')[0].value = '';
            initSearch();
          } else {
            close_modal();
          }
        }
        break;
      }
      case 'ArrowDown': {
        if (searchResultShown()) {
          let lines = $('.result-line');
          if (typeof (searchHighlight) === 'undefined') searchHighlight = 0;
          else ++searchHighlight;
          searchHighlight = Math.min(searchHighlight, lines.length - 1);
          lines.removeClass('highlighted');
          lines[searchHighlight].addClass('highlighted');
        }
        break;
      }
      case 'ArrowUp': {
        if (searchResultShown()) {
          let lines = $('.result-line');
          if (typeof (searchHighlight) === 'undefined') searchHighlight = 0;
          else --searchHighlight;
          searchHighlight = Math.max(searchHighlight, 0);
          lines.removeClass('highlighted');
          lines[searchHighlight].addClass('highlighted');
        }
        break;
      }
      case 'Enter': {
        if (searchResultShown()) {
          fillPlayer(searchResult[searchHighlight]);
        } else {
          $('#register')[0].click();
        }
        break;
      }
    }
  });

});

