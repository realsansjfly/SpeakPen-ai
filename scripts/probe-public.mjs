import { serviceRequest } from './tunnel-network.mjs';
try {
  let input='';for await(const part of process.stdin){input+=part;if(input.length>10000)throw Error('Probe input too large');}
  const {url,token}=JSON.parse(input);
  const reply=await serviceRequest(url+'/v1/status',{token,timeoutMs:8000});
  const result=JSON.parse(reply.body.toString('utf8'));
  if(reply.status!==200)throw Error('Service authentication failed');
  console.log(JSON.stringify({...result,_dnsFallback:reply.dnsFallback}));
} catch(error){console.log(JSON.stringify({_probeError:error.code||'Unavailable'}));process.exitCode=1;}
