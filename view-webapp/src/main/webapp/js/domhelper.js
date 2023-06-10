window.$ = document.querySelectorAll.bind(document);
Node.prototype.on = window.on = function (eventNames, fn) {
  let events = eventNames.split(' ')
  for (let i = 0; i < events.length; ++i) {
    let name = events[i];
    this.addEventListener(name, fn);
  }
};
NodeList.prototype.__proto__ = Array.prototype;
NodeList.prototype.on = NodeList.prototype.addEventListener = function (eventNames, fn) {
  this.forEach(function (elem, i) {
    elem.on(eventNames, fn);
  });
}
NodeList.prototype.addClass = function(className) {
  this.forEach(function (elem, i) {
    elem.classList.add(className);
  });
}
NodeList.prototype.removeClass = function(className) {
  this.forEach(function (elem, i) {
    elem.classList.remove(className);
  });
}
NodeList.prototype.toggleClass = function(className) {
  this.forEach(function (elem, i) {
    elem.classList.toggle(className);
  });
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
  this.item(0).offset() // CB TODO review
}
Element.prototype.attr = function (key) {
  return this.attributes[key].value
}
NodeList.prototype.attr = function(key) {
  this.item(0).attr(key) // CB TODO review
}
