// API

const apiVersion = '1.0';

// Usage :
// api.put('user', {user_id: 12, first_name: 'Toto', ... })
//     .then(ret => ret.json())
//     .then(json => { ... })
//     .catch(err => { ... });

const base = '/api/';
let  headers = function(withJson) {
    let ret = {
        'Accept-Version': apiVersion,
        'Accept': 'application/json',
        'X-Browser-Key': store('browserKey')
    };
    if (typeof(withJson) === 'undefined') withJson = true;
    if (withJson) {
      ret['Content-Type'] = 'application/json';
    }
    if (typeof(apiToken) !== 'undefined') {
        ret['Authorization'] = `Bearer ${apiToken}`;
    }
    return ret;
};

function clearFeedback() {
  $('#error')[0].innerText = '';
  $('#error, #success').addClass('hidden');
}

function success() {
  $('#error')[0].innerText = '';
  $('#error').addClass('hidden');
}

function showError(message) {
  console.error(message);
  $('#error')[0].innerText = message;
  $('#error').removeClass('hidden');
}

function error(response) {
  const contentType = response.headers.get("content-type");
  let promise =
    (contentType && contentType.indexOf("application/json") !== -1)
    ? response.json().then(json => json.error || "unknown error")
    : Promise.resolve(response.statusText);
  promise.then(message => {
    message = message.replaceAll(/([a-z])([A-Z])/g,"$1 $2").toLowerCase();
    showError(message);
  });
}

let api = {
    get: (path) => fetch(base + path, {
        credentials: "same-origin",
        headers: headers()
    }),
    post: (path, body) => fetch(base + path, {
        credentials: "same-origin",
        method: 'POST',
        body: JSON.stringify(body),
        headers: headers()
    }),
    put: (path, body) => fetch(base + path, {
        credentials: "same-origin",
        method: 'PUT',
        body: JSON.stringify(body),
        headers: headers()
    }),
    delete: (path, body) => fetch(base + path, {
        credentials: "same-origin",
        method: 'DELETE',
        body: JSON.stringify(body),
        headers: headers()
    }),

    /* then, some helpers */

    getJson: (path) => {
        clearFeedback();
        return api.get(path)
          .then(resp => {
              if (resp.ok) {
                return resp.json();
              }
              else throw resp;
          })
          .catch(err => {
            error(err);
            return 'error'
          });
    },

    postJson: (path, body) => {
        clearFeedback();
        spinner(true);
        return api.post(path, body)
          .then(resp => {
              if (resp.ok) {
                success();
                return resp.json();
              }
              else {
                throw resp;
              }
          })
          .catch(err => {
            error(err);
            return 'error';
          })
          .finally(() => {
              spinner(false);
          });
    },

    putJson: (path, body) => {
        clearFeedback();
        spinner(true);
        return api.put(path, body)
          .then(resp => {
              if (resp.ok) {
                success();
                return resp.json();
              }
              else throw resp;
          })
          .catch(err => {
            error(err);
            return 'error';
          })
          .finally(() => {
             spinner(false);
          });
    },

  deleteJson: (path, body) => {
    clearFeedback();
    spinner(true);
    return api.delete(path, body)
      .then(resp => {
        if (resp.ok) {
          success();
          return resp.json();
        }
        else throw resp;
      })
      .catch(err => {
        error(err);
        return 'error';
      })
      .finally(() => {
        spinner(false);
      });
  }

};

