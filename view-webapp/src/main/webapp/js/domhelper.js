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
  return this.item(0).classList.contains(className);
}
Element.prototype.toggleClass = function(className) {
  this.classList.contains(className);
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
  this.item(0).offset();
}
Element.prototype.attr = function (key) {
  return this.attributes[key].value;
}
NodeList.prototype.attr = function(key) {
  this.item(0).attr(key);
}
Element.prototype.data = function (key) {
  return this.attributes[`data-${key}`].value
}
NodeList.prototype.data = function(key) {
  this.item(0).data(key);
}
NodeList.prototype.show = function(key) {
  this.item(0).show(key);
  return this;
}
Element.prototype.show = function (key) {
  this.style.display = 'block';
}
NodeList.prototype.hide = function(key) {
  this.item(0).hide(key);
  return this;
}
Element.prototype.hide = function (key) {
  this.style.display = 'none';
}

let initFunctions = [];
function onLoad(fct) {
  if (typeof(fct) == "function") initFunctions.push(fct);
}
document.on("DOMContentLoaded", () => {
  initFunctions.forEach(fct => {
    fct();
  });
});
