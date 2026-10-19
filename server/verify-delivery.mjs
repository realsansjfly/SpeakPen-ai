import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { serviceRequest } from '../scripts/tunnel-network.mjs';
const connection=JSON.parse(readFileSync(new URL('../.local/connection.json',import.meta.url),'utf8'));
const response=await serviceRequest(connection.webUrl+'/app.apk',{token:connection.accessToken,timeoutMs:45000});
if(response.status!==200)throw Error('APK download unavailable');
const local=readFileSync(new URL('../deliverables/SpeakPen-0.4.0-debug.apk',import.meta.url));
const hash=data=>createHash('sha256').update(data).digest('hex');
if(hash(response.body)!==hash(local))throw Error('Downloaded APK differs');
console.log(JSON.stringify({authenticatedApkDownload:true,bytes:response.body.length,hashMatches:true,dnsFallback:response.dnsFallback}));

