// hack to replace .34 into 0.34 (CB TODO - upstream patch to inputmask)
const fixNumber = (value) => isNaN(value) || !`${value}`.startsWith('.') ? value : `0.${value}`;

class FormProxy {
    constructor(formSelector, config) {
        this.formSelector = formSelector;
        this.config = config;
        this.config.properties = () => Object.keys(this.config).filter(k => typeof(this.config[k]) !== 'function');
        this.promises = [];
        this.dirty = false;

        this.config.import = function(obj) {
            for (let key in obj) {
                if (key in this.config) {
                    this.proxy[key] = obj[key];
                } else {
                    console.warn(`ignoring property ${key}`)
                }
            }
            this.config.dirty(false);
        }.bind(this);

        this.config.export = function() {
            let ret = {}
            this.config.properties().forEach(prop => {
                ret[prop] = this.proxy[prop];
            });
            return ret;
        }.bind(this);

        this.config.dirty = function(value) {
            if (typeof(value) === 'undefined') return thisProxy.dirty;
            else thisProxy.dirty = Boolean(value);
        }.bind(this);

        this.config.valid = function() {
            return $(`${thisProxy.formSelector} [required]:invalid`).length === 0
        }.bind(this);

        this.config.reset = function() {
            this.initialize();
        }.bind(this);

        // CB TODO - needs a function to wait for promises coming from dependencies

        this.setState('loading');
        $(() => this.configure.bind(this)());

        let thisProxy = this;
        return this.proxy = new Proxy(config, {
            get(target, prop) {
                if (typeof(target[prop]) === 'function') {
                    return target[prop];
                }
                else {
                    let elem = config[prop];
                    if (typeof(elem) === 'undefined') throw `invalid property: ${prop}`
                    return elem.getter();
                }
            },
            set(target, prop, value) {
                let def = config[prop];
                if (typeof(def) === 'undefined') throw `invalid property: ${prop}`
                let depends = [].concat(def.depends ? def.depends : []);
                let proms = depends.flatMap(field => config[field].promise).filter(prom => prom);
                let operation = () => {
                    def.setter(value);
                    if (typeof(def.change) === 'function') {
                        let rst = def.change(value, def.elem);
                        if (typeof(rst?.then) === 'function') {
                            def.promise = rst;
                        }
                    }
                };
                if (proms.length) Promise.all(proms).then(() => operation());
                else operation();
                config.dirty(true);
                return true;
            }
        });
    }

    configure() {
        this.form = $(this.formSelector);
        if (!this.form.length) throw `Form not found: ${this.formSelector}`;
        this.form.on('submit', e => { e.preventDefault(); return false; });
        let controls = this.form.find('input[name],select[name],textarea[name]');
        controls.on('input change keyup', e => {
            this.setState('editing');
            this.config.dirty(true);
        });
        controls.each((i,e) => {
            let name = $(e).attr('name');
            if (!(name in this.config)) this.config[name] = {};
        });
        this.config.properties().forEach(key => {
            let def = this.config[key];
            if (!def) def = this.config[key] = {};
            else if (typeof(def) === 'function') return true; // continue foreach
            if (!def.getter) {
                let elem = def.elem;
                if (!elem || !elem.length) elem = $(`${this.formSelector} [name="${key}"]`);
                if (!elem || !elem.length) elem = $(`#${key}`);
                if (!elem || !elem.length) throw `element not found: ${key}`;
                def.elem = elem;
                def.getter = elem.is('input,select,textarea')
                    ? elem.attr('type') === 'radio'
                        ? (() => elem.filter(':checked').val())
                        : (() => elem.data('default')
                            ? elem.val()
                                ? elem.is('.number')
                                    ? elem.val().replace(/ /g, '')
                                    : elem.val()
                                : elem.data('default')
                            : elem.is('.number')
                                ? elem.val() ? elem.val().replace(/ /g, '') : elem.val()
                                : elem.val())
                    : (() => elem.text());
                def.setter = elem.is('input,select,textarea')
                    ? elem.attr('type') === 'radio'
                        ? (value => elem.filter(`[value="${value}"]`).prop('checked', true))
                        : elem.is('input.number') ? (value => elem.val(fixNumber(value))) : (value => elem.val(value))
                    : (value => elem.text(value));
                if (typeof(def.change) === 'function') {
                    elem.on('change', () => def.change(def.getter(), elem));
                }
            }
            let loading = def?.loading;
            switch (typeof(loading)) {
                case 'function':
                    let rst = loading(def.elem);
                    if (typeof(rst?.then) === 'function') {
                        this.promises.push(rst);
                    }
                    break;
            }
        });
        setTimeout(() => {
            Promise.all(this.promises).then(() => { this.promises = []; this.initialize(); });
        }, 10);
    }

    initialize() {
        this.config.properties().forEach(key => {
            let def = this.config[key];
            if (typeof(def.initial) === 'undefined') {
                this.proxy[key] = '';
            } else {
                if (typeof(def.initial) === 'function') {
                    def.initial(def.elem)
                } else if (def.initial != null) {
                    this.proxy[key] = def.initial;
                }
            }
        });
        this.config.dirty(false);
        this.setState('initial');
    }

    setState(state) {
        if (this.form && this.form.length) this.form[0].dispatchEvent(new Event(state));
    }
}
