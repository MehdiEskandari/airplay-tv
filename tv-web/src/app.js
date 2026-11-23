import {Preferences} from './preferences.js';
import {translator,isRtl} from './i18n.js';
import {icon} from './icons.js';
import {ReceiverEngine} from './engine.js';
import {createPlatform} from '#airplay-platform';
import {installRemote} from './remote.js';
import {HlsPlayer} from './hls.js';

let storage;try{storage=window.localStorage;}catch{storage={getItem(){throw new Error('storageUnavailable');},setItem(){throw new Error('storageUnavailable');}};}
const prefs=new Preferences(storage);
const platform=createPlatform();
const engine=new ReceiverEngine({...platform,settings:prefs});
const root=document.getElementById('app'),modalRoot=document.getElementById('modal-root');
const video=document.getElementById('receiver-video');
let t=translator(prefs.get('language')),tab='home',modal=null,languageChoice=prefs.get('language');
let approval=null,approvalTimer=null,logs=[],toastTimer=null,playing=false,controlsTimer=null;
const hls=new HlsPlayer({api:platform.avplay,onStart:()=>{document.body.classList.add('hls');setPlaying(true);},onStop:()=>{document.body.classList.remove('hls');setPlaying(false);},onError:message=>{addLog(message,'error');toast(t('engineUnavailable'));},onProgress:info=>engine.playback(info)});
const escape=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const text=(key,...args)=>escape(t(key,...args));
const button=(key,label,cls='',attrs='')=>'<button class="'+cls+'" data-key="'+key+'" '+attrs+'>'+label+'</button>';
const emblem='<img class="emblem" src="assets/icon.svg" alt="AirPlay">';
const themes=[['teli_dark','theme_teli_dark'],['teli_light','theme_teli_light'],['solid_dark','theme_solid_dark']];

function applyAppearance(){
  const language=prefs.get('language');t=translator(language);
  document.documentElement.lang=language==='mul'?'fa':language;document.documentElement.dir=isRtl(language)?'rtl':'ltr';
  document.documentElement.dataset.theme=prefs.get('appearance');document.title=t('home_headline');
}
function scale(){document.documentElement.style.setProperty('--scale',Math.min(innerWidth/960,innerHeight/540));}
function toast(message){const el=document.getElementById('toast');el.textContent=message;el.classList.add('visible');clearTimeout(toastTimer);toastTimer=setTimeout(()=>el.classList.remove('visible'),3500);}
function focus(key){const node=document.querySelector('[data-key="'+key+'"]');if(node){node.focus();node.scrollIntoView({block:'nearest',inline:'nearest'});}}
function preserveRender(html){
  const key=document.activeElement?.dataset.key,scroll=root.querySelector('.screen')?.scrollTop||0;
  root.innerHTML=html;const screen=root.querySelector('.screen');if(screen)screen.scrollTop=scroll;
  if(key&&!modalRoot.children.length)focus(key);
}
function setTab(next){tab=next;render();focus('tab-'+next);}
function languageOptions(){return [['fa','فارسی','محیط کاملا فارسی'],['en','English','All menus in English'],['mul','فارسی + English','ترکیبی · Bilingual']].map(([code,title,subtitle])=>button('language-'+code,'<span><strong>'+title+'</strong><small>'+subtitle+'</small></span>'+(languageChoice===code?'<span class="check">'+icon('check')+'</span>':''),'language-option','role="radio" aria-checked="'+(languageChoice===code)+'" data-language="'+code+'"')).join('');}
function onboarding(){return '<section class="language-screen"><div class="language-panel">'+emblem+'<h1>'+text('language_welcome')+'</h1><p>'+text('language_intro')+'</p><div role="radiogroup">'+languageOptions()+'</div>'+button('language-confirm',escape(languageChoice==='en'?'Continue':languageChoice==='mul'?'ادامه · Continue':'ادامه'),'action primary')+'<p class="hint">'+text('language_change_hint')+'</p></div></section>';}
function stateLabel(){return {listening:'ready_for_connection',connected:'session',starting:'starting',stopped:'server_stopped',unsupported:'server_stopped',error:'error_starting_server'}[engine.status];}
function home(){
  const active=['starting','listening','connected'].includes(engine.status);
  const message=platform.unavailableKey?t(platform.unavailableKey):!platform.isDevice?t('unsupported'):engine.status==='stopped'?t('startRequired'):engine.status==='error'?t('engineUnavailable'):'';
  const guides=[['mirror','home_mirror_title','home_mirror_desc'],['airplay','home_video_title','home_video_desc'],['wifi','networkGuideTitle','networkGuideDescription']];
  return '<section class="screen home" aria-label="'+text('tab_overview')+'"><div class="receiver-card"><div><div class="receiver-label">'+text('home_receiver')+'</div><div class="receiver-name" dir="auto">'+escape(prefs.get('serverName'))+'</div><div class="status '+engine.status+'">'+text(stateLabel())+'</div>'+(message?'<div class="status-message">'+escape(message)+'</div>':'')+'</div>'+button('toggle-receiver',icon(active?'stop':'play')+text(platform.receiverImplemented===false?'receiverPending':active?'btn_stop':'btn_start'),'action',platform.receiverImplemented===false?'disabled':'')+'</div><div class="guides">'+guides.map(([glyph,title,description],i)=>'<article class="guide"><div class="guide-top">'+icon(glyph)+'<span class="step">0'+(i+1)+'</span></div><h2>'+text(title)+'</h2><p>'+text(description,prefs.get('serverName'))+'</p></article>').join('')+'</div><p class="network-hint">'+icon('wifi')+text('networkHint')+'</p></section>';
}
function setting(key,title,description,value,extra=''){
  return button(key,'<span class="setting-body"><strong>'+text(title)+'</strong>'+(description?'<small>'+text(description)+'</small>':'')+'</span>'+(value===null?'':typeof value==='boolean'?'<span class="switch"></span>':'<span class="value" dir="auto">'+escape(value)+'</span>'),'setting',extra+(typeof value==='boolean'?' role="switch" aria-checked="'+value+'"':''));
}
function section(key){return '<h2 class="section-title">'+text(key)+'</h2>';}
function choices(pref,items){return '<div class="choices" role="radiogroup">'+items.map(([value,label])=>button(pref+'-'+value,escape(t(label)),'chip','role="radio" aria-checked="'+(String(prefs.get(pref))===String(value))+'" data-pref="'+pref+'" data-value="'+value+'"')).join('')+'</div>';}
function settings(){
  return '<section class="screen"><div class="settings">'+section('section_language')+setting('choose-language',{fa:'فارسی',en:'English',mul:'فارسی + English'}[prefs.get('language')],'language_description',null)+
    section('section_appearance')+'<div class="setting"><span class="setting-body"><strong>'+text('setting_theme')+'</strong><small>'+text('setting_theme_desc')+'</small></span></div>'+choices('appearance',themes)+
    (platform.receiverImplemented===false?'<p class="setting-note">'+text('webosCorePending')+'</p>':section('section_server')+setting('edit-name','setting_server_name',null,prefs.get('serverName'))+setting('auto-start','setting_auto_start','setting_auto_start_desc',prefs.get('autoStart'))+
    section('section_display')+
    section('setting_resolution')+choices('resolution',[['1280x720','1280 × 720'],['1920x1080','1920 × 1080']])+
    section('setting_max_fps')+choices('maxFps',[[30,'30 fps'],[60,'60 fps']])+
    section('section_devices')+setting('approve-devices','approve_devices','approveHint',prefs.get('approveDevices'))+
    (prefs.get('trusted').length?prefs.get('trusted').map((d,i)=>setting('forget-'+i,'device',null,d.name)).join(''):'<p class="setting-note">'+text('trustEmpty')+'</p>')+
    '<p class="setting-note">'+text('privacy')+'</p>')+section('section_creator')+'<div class="creator"><img src="assets/creator.jpg" alt="'+text('creator_avatar')+'"><div><strong>'+text('creator_name')+'</strong><p>'+text('creator_role')+'</p></div></div></div></section>';
}
function logScreen(){return '<section class="screen logs"><div class="logs-toolbar"><h1>'+text('tab_logs')+'</h1>'+button('clear-logs',text('clearLogs'),'quiet-button')+'</div><div class="log-list">'+(logs.length?logs.map(item=>'<div class="log-row '+item.level+'">'+escape(item.time+' '+item.message)+'</div>').join(''):'<div class="log-empty">'+text('noLogs')+'</div>')+'</div></section>';}
function render(){
  applyAppearance();
  if(!prefs.get('languageSelected')){preserveRender(onboarding());return;}
  preserveRender('<div class="shell"><header class="topbar"><nav class="tabs" role="tablist">'+[['home','tab_overview'],['logs','tab_logs'],['settings','tab_settings']].map(([id,label])=>button('tab-'+id,text(label),'tab','role="tab" aria-selected="'+(tab===id)+'" data-tab="'+id+'"')).join('')+'</nav></header>'+({home,settings,logs:logScreen}[tab])()+(!platform.available?'<span class="preview-label">'+text(platform.previewKey||'preview')+'</span>':'')+'</div>');
}
function closeModal(){const restore=modal?.restore;modal=null;modalRoot.innerHTML='';if(restore)focus(restore);}
function showModal(kind,data={}){
  const restore=document.activeElement?.dataset.key;modal={kind,...data,restore};renderModal();
  const first=modalRoot.querySelector(kind==='language'?'[data-language="'+languageChoice+'"]':kind==='name'?'input':'button');first?.focus();
}
function renderModal(){
  if(!modal)return;
  let html='';
  if(modal.kind==='language')html='<h2>'+text('section_language')+'</h2><div role="radiogroup">'+languageOptions()+'</div><div class="dialog-actions">'+button('language-confirm',text('save'),'action primary')+button('modal-cancel',text('cancel'),'action')+'</div>';
  if(modal.kind==='name')html='<h2>'+text('setting_server_name')+'</h2><form id="name-form"><input id="receiver-name" data-key="receiver-name-input" aria-label="'+text('setting_server_name')+'" value="'+escape(prefs.get('serverName'))+'" maxlength="63" dir="auto"><div class="dialog-actions"><button class="action primary" data-key="save-name" type="submit">'+text('save')+'</button>'+button('modal-cancel',text('cancel'),'action','type="button"')+'</div></form>';
  if(modal.kind==='exit')html='<h2>'+text('exit')+'</h2><div class="dialog-actions">'+button('modal-cancel',text('stay'),'action primary')+button('exit-app',text('exitYes'),'action')+'</div>';
  if(modal.kind==='approval')html='<h2>'+text('approval_title')+'</h2><p>'+text('approval_request',approval.name,approval.source||'AirPlay')+'</p><p id="approval-countdown">'+text('approvalCountdown',30)+'</p><div class="dialog-actions">'+(approval.canRemember?button('approve-always',text('approval_always'),'action primary'):'')+button('approve-once',text('approval_once'),'action')+button('approve-deny',text('approval_deny'),'action')+'</div>';
  if(modal.kind==='pin')html='<h2>'+text('dialog_pin_title')+'</h2><div class="pin-code">'+escape(modal.pin)+'</div>'+button('modal-cancel',text('close'),'action');
  modalRoot.innerHTML='<div class="scrim"><section class="panel" role="dialog" aria-modal="true">'+html+'</section></div>';
}
function answer(decision){if(!approval)return;engine.approve(approval.requestId,decision);clearInterval(approvalTimer);approval=null;closeModal();}
function addLog(message,level='info'){logs.push({time:new Date().toLocaleTimeString('en-GB'),message,level});if(logs.length>200)logs.shift();if(tab==='logs')render();}
function setPlaying(value){playing=value;document.body.classList.toggle('playing',value);if(!value)document.body.classList.remove('controls-visible');}
function mediaKey(key){if(!playing)return;try{if(key==='MediaStop'){engine.stop();setPlaying(false);}else if(hls.active)hls.rate(key==='MediaPause'?0:key==='MediaPlay'?1:hls.paused?1:0);else if(key==='MediaPause')video.pause();else if(key==='MediaPlay')video.play().catch(()=>{});else video.paused?video.play().catch(()=>{}):video.pause();}catch(error){addLog(error.message,'error');}}
function showControls(){document.body.classList.add('controls-visible');clearTimeout(controlsTimer);controlsTimer=setTimeout(()=>document.body.classList.remove('controls-visible'),5000);}
function onBack(){if(approval){answer(0);return;}if(modal){closeModal();return;}if(!prefs.get('languageSelected'))return;if(playing){showControls();return;}if(tab!=='home'){setTab('home');return;}showModal('exit');}

engine.on(event=>{
  if(event.type==='state'){if(event.status==='error')addLog(event.detail,'error');if(['stopped','error','unsupported'].includes(event.status)){hls.stop();setPlaying(false);}render();}
  if(event.type==='hls-play')hls.play(event.url,event.position);
  if(event.type==='hls-seek')hls.seek(event.position);
  if(event.type==='hls-rate')hls.rate(event.rate);
  if(event.type==='hls-stop')hls.stop();
  if(event.type==='log')addLog(event.message,event.level);
  if(event.type==='video-start')setPlaying(true);
  if(event.type==='video-stop'||event.type==='disconnected')setPlaying(false);
  if(event.type==='approval'){
    if(approval)answer(0);approval=event;showModal('approval');let remaining=30;clearInterval(approvalTimer);
    approvalTimer=setInterval(()=>{remaining--;const label=document.getElementById('approval-countdown');if(label)label.textContent=t('approvalCountdown',remaining);if(remaining<=0){answer(0);toast(t('approvalTimeout'));}},1000);
  }
  if(event.type==='approval-closed'){clearInterval(approvalTimer);approval=null;if(modal?.kind==='approval')closeModal();}
  if(event.type==='pin')showModal('pin',{pin:event.pin});
  if(event.type==='trusted'){
    try{const entries=prefs.get('trusted').filter(d=>d.key!==event.key);entries.push({key:event.key,id:event.id,name:event.name});prefs.set('trusted',entries.slice(-128));if(tab==='settings')render();}catch(error){addLog(error.message,'error');}
  }
});

document.addEventListener('click',event=>{
  const target=event.target.closest('button');if(!target||target.disabled)return;
  const key=target.dataset.key;
  if(target.dataset.tab){setTab(target.dataset.tab);return;}
  if(target.dataset.language){languageChoice=target.dataset.language;modal?renderModal():render();focus('language-'+languageChoice);return;}
  if(target.dataset.pref){const pref=target.dataset.pref,value=target.dataset.value;prefs.set(pref,pref==='maxFps'?Number(value):value);render();if(pref!=='appearance')toast(t('restart'));return;}
  if(key==='language-confirm'){const first=!prefs.get('languageSelected');prefs.set('language',languageChoice);prefs.set('languageSelected',true);closeModal();render();focus(first?'tab-home':'choose-language');if(first&&prefs.get('autoStart'))engine.start();return;}
  if(key==='toggle-receiver'){['starting','listening','connected'].includes(engine.status)?engine.stop():engine.start();return;}
  if(key==='choose-language'){languageChoice=prefs.get('language');showModal('language');return;}
  if(key==='edit-name'){showModal('name');return;}
  if(key==='modal-cancel'){closeModal();return;}
  if(key==='clear-logs'){logs=[];render();return;}
  if(key==='auto-start'||key==='auto-fullscreen'||key==='approve-devices'){const pref={'auto-start':'autoStart','auto-fullscreen':'autoFullscreen','approve-devices':'approveDevices'}[key];prefs.set(pref,!prefs.get(pref));render();if(pref==='approveDevices')toast(t('restart'));return;}
  if(key?.startsWith('forget-')){const index=Number(key.slice(7));prefs.set('trusted',prefs.get('trusted').filter((_,i)=>i!==index));render();toast(t('restart'));return;}
  if(key?.startsWith('approve-')){answer({'approve-deny':0,'approve-once':1,'approve-always':2}[key]);return;}
  if(key==='exit-app'){engine.stop();closeModal();if(platform.isDevice)platform.exit();}
});
document.addEventListener('input',event=>{if(event.target.id==='receiver-name')event.target.setCustomValidity('');});
document.addEventListener('submit',event=>{
  if(event.target.id!=='name-form')return;event.preventDefault();
  const input=document.getElementById('receiver-name');try{prefs.set('serverName',input.value);closeModal();render();toast(t('restart'));}catch{input.setCustomValidity(t('setting_server_name'));input.reportValidity();}
});
window.addEventListener('resize',scale);window.addEventListener('pagehide',()=>engine.stop());
installRemote({onBack,onMedia:mediaKey});scale();applyAppearance();render();
requestAnimationFrame(()=>focus(prefs.get('languageSelected')?'tab-home':'language-fa'));
if(prefs.get('languageSelected')&&prefs.get('autoStart'))engine.start();
if(!prefs.persistent)toast(t('storageUnavailable'));
