// Read-only diagnosis. Credentials are read locally and never printed.
import { readFileSync } from 'node:fs';
import { serviceRequest } from '../scripts/tunnel-network.mjs';
const connection=JSON.parse(readFileSync(new URL('../.local/connection.json',import.meta.url),'utf8').replace(/^\uFEFF/,''));
let failed=false;let localId;
for(const [name,base] of [['local','http://127.0.0.1:8787/v1'],['phone-public',connection.baseUrl]]){
 const started=performance.now();
 try{
  const response=await serviceRequest(base+'/status',{token:connection.accessToken,timeoutMs:9000,allowLocal:true});
  const result=JSON.parse(response.body.toString('utf8'));
  if(name==='local')localId=result.instanceId;
  const pass=response.status===200&&result.ready===true&&result.service==='SpeakPen'&&!!result.instanceId&&result.instanceId===localId;
  failed||=!pass;console.log(JSON.stringify({route:name,status:response.status,pass,elapsedMs:Math.round(performance.now()-started),dnsFallback:response.dnsFallback,sameLocalInstance:result.instanceId===localId}));
 }catch(error){failed=true;console.log(JSON.stringify({route:name,pass:false,code:error.code||error.name,elapsedMs:Math.round(performance.now()-started)}));}
}
process.exitCode=failed?1:0;
