// Funciones modernas de JavaScript que foliate-js usa y que los WebView antiguos (Chrome < 117/119) no tienen.
// Se cargan antes que el lector; si el navegador ya las tiene, no se tocan.
(function () {
  if (!Object.groupBy) {
    Object.groupBy = function (items, fn) {
      var out = Object.create(null), i = 0;
      for (var it of items) { var k = fn(it, i++); (out[k] || (out[k] = [])).push(it); }
      return out;
    };
  }
  if (!Map.groupBy) {
    Map.groupBy = function (items, fn) {
      var out = new Map(), i = 0;
      for (var it of items) { var k = fn(it, i++); if (!out.has(k)) out.set(k, []); out.get(k).push(it); }
      return out;
    };
  }
  if (!Promise.withResolvers) {
    Promise.withResolvers = function () {
      var resolve, reject;
      var promise = new this(function (a, b) { resolve = a; reject = b; });
      return { promise: promise, resolve: resolve, reject: reject };
    };
  }
  if (!Promise.try) {
    Promise.try = function (fn) {
      var args = Array.prototype.slice.call(arguments, 1);
      return new this(function (resolve) { resolve(fn.apply(undefined, args)); });
    };
  }
  function findLastIndex(fn, thisArg) { for (var i = this.length - 1; i >= 0; i--) if (fn.call(thisArg, this[i], i, this)) return i; return -1; }
  function findLast(fn, thisArg) { var i = findLastIndex.call(this, fn, thisArg); return i < 0 ? undefined : this[i]; }
  if (!Array.prototype.findLastIndex) Object.defineProperty(Array.prototype, 'findLastIndex', { value: findLastIndex, writable: true, configurable: true });
  if (!Array.prototype.findLast) Object.defineProperty(Array.prototype, 'findLast', { value: findLast, writable: true, configurable: true });
})();
