export const defaults = Object.freeze({language:'fa',languageSelected:false,appearance:'teli_dark',serverName:'AirPlay TV',autoStart:true,approveDevices:true,autoFullscreen:true,resolution:'1920x1080',maxFps:30,volume:80,trusted:[],deviceId:''});
const valid = {
  language:v=>['fa','en','mul'].includes(v), languageSelected:v=>typeof v==='boolean',
  appearance:v=>['teli_dark','teli_light','solid_dark'].includes(v),
  serverName:v=>typeof v==='string' && v.trim().length>0 && new TextEncoder().encode(v.trim()).length<=63 && !/[\x00-\x1f]/.test(v),
  autoStart:v=>typeof v==='boolean',approveDevices:v=>typeof v==='boolean',autoFullscreen:v=>typeof v==='boolean',
  resolution:v=>['1280x720','1920x1080'].includes(v),maxFps:v=>[30,60].includes(v),volume:v=>Number.isInteger(v)&&v>=0&&v<=100,
  trusted:v=>Array.isArray(v)&&v.length<=128&&v.every(x=>x&&typeof x.key==='string'&&/^[A-Za-z0-9+/]{43}=$/.test(x.key)&&typeof x.id==='string'&&x.id.length<=128&&typeof x.name==='string'&&x.name.length<=128),
  deviceId:v=>typeof v==='string'&&/^[0-9a-f]{12}$/i.test(v)
};
export class Preferences {
  constructor(storage) {
    this.storage=storage;this.values={...defaults,trusted:[]};this.persistent=true;
    try {const parsed=JSON.parse(storage.getItem('airplay-tv-v1')||'{}');for(const key of Object.keys(defaults)) if(valid[key](parsed[key])) this.values[key]=parsed[key];}catch{this.persistent=false;}
    if(!this.values.deviceId){const bytes=new Uint8Array(6);crypto.getRandomValues(bytes);bytes[0]=(bytes[0]|2)&254;this.values.deviceId=Array.from(bytes,x=>x.toString(16).padStart(2,'0')).join('');this.save();}
  }
  get(key){return this.values[key];}
  set(key,value){if(!valid[key]||!valid[key](value))throw new TypeError('Invalid preference: '+key);this.values[key]=key==='serverName'?value.trim():value;this.save();}
  save(){try{this.storage.setItem('airplay-tv-v1',JSON.stringify(this.values));}catch{this.persistent=false;}}
}
