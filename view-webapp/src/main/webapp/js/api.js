// API

const apiVersion = '1.0';

// Usage :
// api.put('user', {user_id: 12, first_name: 'Toto', ... })
//     .then(ret => ret.json())
//     .then(json => { ... })
//     .catch(err => { ... });

const base = '/api/';
let headers = function() {
    let ret = {
        "Content-Type": "application/json; charset=utf-8",
        "Accept-Version": apiVersion,
        "Accept": "application/json",
        "X-Browser-Key": store('browserKey')
    };
    let accessToken = store('accessToken');
    if (accessToken) {
        ret['Authorization'] = `Bearer ${accessToken}`;
    }
    return ret;
};

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

    getJson: (path) => api.get(path)
    .then(resp => {
        if (resp.ok) return resp.json();
        else throw resp.statusText;
    }),

    postJson: (path, body) => api.post(path, body)
    .then(resp => {
        if (resp.ok) return resp.json();
        else throw resp.statusText;
    }),

    putJson: (path, body) => api.put(path, body)
    .then(resp => {
        if (resp.ok) return resp.json();
        else throw resp.statusText;
    })
};

