// The webOS package currently ships the interface only. Device detection must
// never enable the Samsung receiver or announce an AirPlay service on LG TVs.
export function webosPlatform(scope=window){
  const isDevice=typeof scope.PalmSystem!=='undefined';
  return {
    id:'lg-webos',isDevice,available:false,receiverImplemented:false,
    unavailableKey:'webosCorePending',previewKey:'webosPreview',
    exit:()=>{if(isDevice)scope.close();},
    getIp:()=>'',
    loadCore:async()=>{throw new Error('webosCorePending');}
  };
}
export {webosPlatform as createPlatform};
