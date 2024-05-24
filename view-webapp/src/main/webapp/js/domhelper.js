// This small library is meant to be a lightweight replacement of jQuery basic functions.

window.$ = document.querySelectorAll.bind(document);
Node.prototype.on = window.on = function (eventNames, fn) {
  let events = eventNames.split(' ')
  for (let i = 0; i < events.length; ++i) {
    let name = events[i];
    this.addEventListener(name, fn);
  }
  return this;
};
NodeList.prototype.__proto__ = Array.prototype;
NodeList.prototype.on = NodeList.prototype.addEventListener = function (eventNames, fn) {
  this.forEach(function (elem, i) {
    elem.on(eventNames, fn);
  });
  return this;
}
NodeList.prototype.addClass = function(className) {
  this.forEach(function (elem, i) {
    elem.classList.add(className);
  });
  return this;
}
Element.prototype.addClass = function(className) {
  this.classList.add(className);
  return this;
}
NodeList.prototype.removeClass = function(className) {
  this.forEach(function (elem, i) {
    elem.classList.remove(className);
  });
  return this;
}
Element.prototype.removeClass = function(className) {
  this.classList.remove(className);
  return this;
}
NodeList.prototype.toggleClass = function(className) {
  this.forEach(function (elem, i) {
    elem.classList.toggle(className);
  });
  return this;
}
Element.prototype.toggleClass = function(className) {
  this.classList.toggle(className);
  return this;
}
NodeList.prototype.hasClass = function(className) {
  if (this.length === 0) return false;
  return this.item(0).classList.contains(className);
}
Element.prototype.hasClass = function(className) {
  return this.classList.contains(className);
}
Node.prototype.offset = function() {
  let _x = 0;
  let _y = 0;
  let el = this;
  while( el && !isNaN( el.offsetLeft ) && !isNaN( el.offsetTop ) ) {
    _x += el.offsetLeft - el.scrollLeft;
    _y += el.offsetTop - el.scrollTop;
    el = el.offsetParent;
  }
  return { top: _y, left: _x };
}
NodeList.prototype.offset = function() {
  return this.item(0).offset();
}
Element.prototype.attr = function (key, value) {
  if (typeof(value) === 'undefined') {
    return this.attributes[key]?.value;
  } else {
    this.setAttribute(key, value);
    return this;
  }
}
NodeList.prototype.attr = function(key, value) {
  if (typeof(value) === 'undefined') {
    return this.item(0).attr(key);
  } else {
    this.forEach(elem => {
      elem.attr(key, value);
    });
    return this;
  }
}
Element.prototype.data = function (key, value) {
  if (typeof(value) === 'undefined') {
    return this.attributes[`data-${key}`]?.value
  } else {
    this.setAttribute(`data-${key}`, value);
    return this;
  }
}
NodeList.prototype.data = function(key, value) {
  if (typeof(value) === 'undefined') {
    return this.item(0).data(key);
  } else {
    this.forEach(elem => {
      elem.data(key, value);
    })
    return this;
  }
}
NodeList.prototype.show = function() {
  this.item(0).show();
  return this;
}
Element.prototype.show = function() {
  this.style.display = 'block';
  return this;
}
NodeList.prototype.hide = function() {
  this.item(0).hide();
  return this;
}
Element.prototype.hide = function() {
  this.style.display = 'none';
  return this;
}
NodeList.prototype.text = function(txt) {
  if (typeof(txt) === 'undefined') {
    return this.item(0).text();
  } else {
    this.forEach(elem => {
      elem.text(txt);
    });
    return this;
  }
}
Element.prototype.text = function(txt) {
  if (typeof(txt) === 'undefined') {
    return this.textContent;
  } else {
    this.textContent = txt;
    return this;
  }
}
NodeList.prototype.item = function (i) {
  return this[+i || 0];
};
NodeList.prototype.find = function(selector) {
  let result = [];
  this.forEach(function (elem, i) {
    let partial = elem.find(selector);
    result = result.concat([...partial]);
  });
  return Reflect.construct(Array, result, NodeList);
}
Element.prototype.find = function(selector) {
  return this.querySelectorAll(':scope ' + selector);
}

NodeList.prototype.clear = function() {
  this.forEach(function (elem, i) {
    elem.clear();
  });
  return this;
}
Element.prototype.clear = function() {
  while (this.firstChild) this.removeChild(this.lastChild);
  return this;
}

/*
 TODO - conflicts with from.val(), rename one of the two
NodeList.prototype.val = function(value) {
  this.item(0).val(value);
}
Element.prototype.val = function(value) {
  // TODO - check that "this" has the "value" property
  if (typeof(value) === 'undefined') {
    return this.value;
  } else {
    this.value = value;
  }
}
*/

NodeList.prototype.focus = function() {
  let first = this.item(0);
  if (first) first.focus();
  return this;
}

Element.prototype.index = function(selector) {
  let i = 0;
  let child = this;
  while ((child = child.previousSibling) != null) {
    if (typeof(selector) === 'undefined' || child.nodeType === Node.ELEMENT_NODE && child.matches(selector)) {
      ++i;
    }
  }
  return i;
}

NodeList.prototype.filter = function(selector) {
  let result = [];
  this.forEach(elem => {
    if (elem.nodeType === Node.ELEMENT_NODE && elem.matches(selector)) {
      result.push(elem);
    }
  });
  return Reflect.construct(Array, result, NodeList);
}

