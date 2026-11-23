export function nextInDirection(current,items,direction){
  const cx=current.x+current.width/2,cy=current.y+current.height/2;
  let best=null,score=Infinity;
  for(const item of items){
    const dx=item.x+item.width/2-cx,dy=item.y+item.height/2-cy;
    const forward=direction==='left'?-dx:direction==='right'?dx:direction==='up'?-dy:dy;
    if(forward<=2)continue;
    const side=(direction==='left'||direction==='right')?Math.abs(dy):Math.abs(dx);
    const candidate=forward+side*2.5;
    if(candidate<score){score=candidate;best=item;}
  }return best;
}
export function remoteKey(event){
  const mapped={37:'ArrowLeft',38:'ArrowUp',39:'ArrowRight',40:'ArrowDown',13:'Enter',461:'Escape',10009:'Escape',415:'MediaPlay',19:'MediaPause',10252:'MediaPlayPause',413:'MediaStop'}[event.keyCode];
  return event.keyCode===461||event.keyCode===10009?mapped:event.key&&event.key!=='Unidentified'?event.key:mapped;
}
export function installRemote({onBack,onMedia}){
  try{window.tizen.tvinputdevice.registerKeyBatch(['MediaPlayPause','MediaPlay','MediaPause','MediaStop']);}catch{}
  document.addEventListener('keydown',event=>{
    const key=remoteKey(event);
    if(key==='Escape'||key==='Backspace'||event.keyCode===10009){if(document.activeElement?.tagName==='INPUT'&&key==='Backspace')return;event.preventDefault();onBack();return;}
    if(key?.startsWith('Media')){event.preventDefault();onMedia(key);return;}
    const direction={ArrowLeft:'left',ArrowRight:'right',ArrowUp:'up',ArrowDown:'down'}[key];if(!direction)return;
    if(['INPUT','SELECT','TEXTAREA'].includes(document.activeElement?.tagName))return;
    const scope=document.querySelector('#modal-root:not(:empty)')||document.querySelector('.language-screen')||document.getElementById('app');
    const nodes=Array.from(scope.querySelectorAll('button:not(:disabled),input,select,[tabindex="0"]')).filter(e=>e.getBoundingClientRect().width>0&&getComputedStyle(e).visibility!=='hidden');
    const active=document.activeElement;
    if(!nodes.includes(active)){event.preventDefault();nodes[0]?.focus();return;}
    const rect=active.getBoundingClientRect();
    const next=nextInDirection(rect,nodes.filter(e=>e!==active).map(element=>({...element.getBoundingClientRect().toJSON(),element})),direction);
    if(next){event.preventDefault();next.element.focus();next.element.scrollIntoView({block:'nearest',inline:'nearest'});}
  });
}
