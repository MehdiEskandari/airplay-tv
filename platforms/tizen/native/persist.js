// Pairing identity survives app restarts; the browser's main thread owns IDBFS.
Module['preRun'] = Module['preRun'] || [];
Module['preRun'].push(function () {
  FS.mkdir('/persist');
  FS.mount(IDBFS, {}, '/persist');
  addRunDependency('airplay-identity');
  FS.syncfs(true, function (error) {
    if (error) {
      Module['onAirPlayEvent']({type:'error', message:'Pairing identity storage is unavailable'});
      abort('Cannot load persistent pairing identity');
      return;
    }
    removeRunDependency('airplay-identity');
  });
});
