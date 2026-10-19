import dns from 'node:dns';
import https from 'node:https';
import http from 'node:http';
import { isIP } from 'node:net';

export const isTunnelHost = hostname => /^[a-z0-9-]+\.trycloudflare\.com$/.test(hostname);
export function parsePublicIpv4(result) {
  if (result.Status !== 0) return [];
  return [...new Set((result.Answer || []).filter(item => item.type === 1 && typeof item.data === 'string').map(item => item.data))]
    .filter(ip => {
      if (isIP(ip) !== 4) return false;
      const [a,b] = ip.split('.').map(Number);
      return a !== 0 && a !== 10 && a !== 127 && a < 224 && !(a===169&&b===254) && !(a===172&&b>=16&&b<=31) && !(a===192&&b===168) && !(a===100&&b>=64&&b<=127);
    });
}
const dnsCache = new Map();
export async function resolveTunnelJson(hostname) {
  const cached=dnsCache.get(hostname);
  if(cached&&cached.expires>Date.now())return cached.result;
  const result=await Promise.any(['https://dns.alidns.com/resolve','https://doh.pub/dns-query'].map(async endpoint=>{
    const url=new URL(endpoint);url.searchParams.set('name',hostname);url.searchParams.set('type','A');
    const reply=await fetch(url,{headers:{accept:'application/dns-json'},signal:AbortSignal.timeout(3500),redirect:'error'});
    if(!reply.ok)throw Error('Encrypted DNS unavailable');
    const value=await reply.json();if(!parsePublicIpv4(value).length)throw Error('No public addresses');return value;
  }));
  const ttl=Math.min(120,...result.Answer.filter(x=>x.type===1).map(x=>Number(x.TTL)||30));
  dnsCache.set(hostname,{result,expires:Date.now()+Math.max(1,ttl)*1000});return result;
}
export function createTunnelLookup({ systemLookup=dns.lookup, resolveJson=resolveTunnelJson, onFallback=()=>{} }={}) {
  return function lookup(hostname, options, callback) {
    systemLookup(hostname, options, (error, addresses, family) => {
      if (!error || !['ENOTFOUND','EAI_AGAIN'].includes(error.code) || !isTunnelHost(hostname)) return callback(error,addresses,family);
      Promise.resolve(resolveJson(hostname)).then(result => {
        const ips=parsePublicIpv4(result);
        if (!ips.length) return callback(error);
        onFallback(hostname);
        if (typeof options==='object' && options.all) callback(null,ips.map(address=>({address,family:4})));
        else callback(null,ips[0],4);
      },()=>callback(error));
    });
  };
}
/** TLS still validates the original URL hostname. Only address lookup falls back. */
export function serviceRequest(url,{token='',method='GET',body,contentType='application/json',timeoutMs=12000,allowLocal=false}={}) {
  const endpoint=new URL(url);
  if (!(endpoint.protocol==='https:'&&isTunnelHost(endpoint.hostname)) && !(allowLocal&&endpoint.protocol==='http:'&&['127.0.0.1','localhost'].includes(endpoint.hostname))) throw new Error('Unexpected service address');
  const payload=body==null?null:Buffer.isBuffer(body)?body:Buffer.from(typeof body==='string'?body:JSON.stringify(body));
  return new Promise((resolve,reject)=>{
    let dnsFallback=false;
    const transport=endpoint.protocol==='https:'?https:http;
    const request=transport.request(endpoint,{
      method,lookup:createTunnelLookup({onFallback:()=>{dnsFallback=true;}}),
      headers:{...(token?{authorization:`Bearer ${token}`} : {}),...(payload?{'content-type':contentType,'content-length':payload.length}: {})},
    },response=>{
      const parts=[];let length=0;
      response.on('data',chunk=>{length+=chunk.length;if(length>24*1024*1024)request.destroy(new Error('Response too large'));else parts.push(chunk);});
      response.on('end',()=>{clearTimeout(timer);resolve({status:response.statusCode,headers:response.headers,body:Buffer.concat(parts),dnsFallback});});
      response.on('error',reject);
    });
    const timer=setTimeout(()=>request.destroy(Object.assign(new Error('Service request timed out'),{code:'ETIMEDOUT'})),timeoutMs);
    request.on('error',error=>{clearTimeout(timer);reject(error);});
    request.end(payload);
  });
}

