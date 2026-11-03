/* Web MIDI para la app Android: el WebView no trae MIDI, así que lo da MainActivity (android.media.midi) a través de window.AndroidMidi */
(function(){
  if(window.__androidMidiShim||!window.AndroidMidi) return; window.__androidMidiShim=true;
  var inputs=new Map(), outputs=new Map(), access=null;
  function sync(){
    var list=[]; try{list=JSON.parse(window.AndroidMidi.list());}catch(e){}
    var seenIn={}, seenOut={};
    list.forEach(function(d){
      if(d.outs>0){var k="in-"+d.id; seenIn[k]=1; if(!inputs.has(k)) inputs.set(k,{id:k,name:d.name,manufacturer:"",type:"input",state:"connected",connection:"open",onmidimessage:null,addEventListener:function(){},removeEventListener:function(){}});}
      if(d.ins>0){var k2="out-"+d.id; seenOut[k2]=1; if(!outputs.has(k2)) outputs.set(k2,{id:k2,name:d.name,manufacturer:"",type:"output",state:"connected",connection:"open",
        send:(function(id){return function(data,ts){var arr=JSON.stringify(Array.prototype.slice.call(data)); var delay=ts?ts-performance.now():0;
          if(delay>1) setTimeout(function(){window.AndroidMidi.send(id,arr);},delay); else window.AndroidMidi.send(id,arr);};})(d.id)});}
    });
    Array.from(inputs.keys()).forEach(function(k){if(!seenIn[k]) inputs.delete(k);});
    Array.from(outputs.keys()).forEach(function(k){if(!seenOut[k]) outputs.delete(k);});
  }
  window.__androidMidiMessage=function(id,bytes){var i=inputs.get("in-"+id); if(i&&typeof i.onmidimessage==="function") i.onmidimessage({data:new Uint8Array(bytes),timeStamp:performance.now()});};
  window.__androidMidiChanged=function(){sync(); if(access&&typeof access.onstatechange==="function") access.onstatechange({});};
  try{Object.defineProperty(navigator,"requestMIDIAccess",{configurable:true,value:function(){
    window.AndroidMidi.open(); sync();
    access=access||{inputs:inputs,outputs:outputs,sysexEnabled:false,onstatechange:null};
    return Promise.resolve(access);
  }});}catch(e){}
})();
