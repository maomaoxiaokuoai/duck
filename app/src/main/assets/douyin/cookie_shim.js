
// Preload shim: give the JSVMP a writable document.cookie, like the Node sandbox does.
(function(){
  var jar = {};
  try {
    Object.defineProperty(document, 'cookie', {
      configurable: true,
      get: function(){
        var out = [];
        for (var k in jar) out.push(k + '=' + jar[k]);
        return out.join('; ');
      },
      set: function(v){
        var s = String(v).split(';')[0];
        var i = s.indexOf('=');
        if (i > 0) jar[s.slice(0, i).trim()] = s.slice(i + 1).trim();
      }
    });
    window.__duck_cookie_ok = true;
  } catch (e) {
    window.__duck_cookie_ok = 'FAIL:' + e;
  }
})();
