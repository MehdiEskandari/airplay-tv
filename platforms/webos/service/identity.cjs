'use strict';
const APP='com.mehdiskan.airplaytv',SERVICE=APP+'.receiver';
function allowedCaller(message){
  const caller=message.applicationID||message.sender;
  // Self calls carry the service's own bus identity. LG's Simulator also uses
  // that identity for app requests, so its caller isolation is not a TV test.
  return typeof caller==='string'&&[APP,SERVICE].some(id=>caller===id||caller.startsWith(id+' '));
}
module.exports={APP,SERVICE,allowedCaller};
