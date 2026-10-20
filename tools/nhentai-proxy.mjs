#!/usr/bin/env node
/**
 * nhentai 反代（本地开发 / 自托管）
 *
 * 为什么需要它：
 *  1) nhentai v2 API 不返回 CORS 头，浏览器里的网页不能直接 fetch；
 *  2) 部分网络的 DNS 把 nhentai.net 解析到错误地址（污染），需要按已知的 Cloudflare 入口 IP 直连；
 *  3) 在本机实测中，DoH（cloudflare-dns.com / dns.google）不可达，因此默认使用静态 IP 列表 + 系统 DNS 兜底。
 *
 * 用法：
 *   node proxy/index.mjs                 # 监听 127.0.0.1:8787
 *   PORT=9000 NHENTAI_IPS=104.26.4.188 node proxy/index.mjs
 *
 * 路由：
 *   /api/v2/...            → https://nhentai.net/api/v2/...
 *   /img?url=<urlencode>   → 图片（nhentai.net / i1-i4 / t1-t4 白名单），长缓存
 *   /health                → {"ok":true}
 *
 * 只做转发，不修改、不缓存、不存储任何内容；不提供任何绕过验证码的能力。
 */
import http from 'node:http';
import https from 'node:https';

const PORT = Number(process.env.PORT || 8787);
const BIND_HOST = process.env.HOST || '127.0.0.1';


/** 默认入口 IP（实测可用的 Cloudflare 地址，2026-10 验证）。可用 NHENTAI_IPS 覆盖。 */
const DEFAULT_IPS = ['104.26.4.188', '104.26.5.188', '172.67.74.203'];
const IP_OVERRIDE = (process.env.NHENTAI_IPS || '')
  .split(',')
  .map((s) => s.trim())
  .filter(Boolean);
const STATIC_IPS = IP_OVERRIDE.length ? IP_OVERRIDE : DEFAULT_IPS;

/**
 * 安卓客户端身份识别：WebView 请求可带自定义头 x-client，/health 据此告知调用方。
 * 只做能力声明，不改变转发行为。
 */
const ANDROID_UA_HINT = 'MangaViewerAndroid';
/** 允许转发的目标域名；防止被当成任意 URL 的开放代理 */
const ALLOWED_HOSTS = [
  'nhentai.net',
  'www.nhentai.net',
  ...Array.from({ length: 4 }, (_, i) => `i${i + 1}.nhentai.net`),
  ...Array.from({ length: 4 }, (_, i) => `t${i + 1}.nhentai.net`),
];

const CORS = {
  'access-control-allow-origin': '*',
  'access-control-allow-methods': 'GET,POST,PUT,DELETE,OPTIONS',
  'access-control-allow-headers': 'authorization,content-type,accept,x-requested-with',
  'access-control-max-age': '86400',
};

/**
 * 发起 HTTPS 请求，可指定直连 IP（保持 Host / SNI 为原域名以保证证书校验通过）。
 * @param {{url: URL, ip?: string, method: string, headers: Record<string,string>, body?: Buffer}} opt
 * @returns {Promise<import('node:http').IncomingMessage>}
 */
function request(opt) {
  return new Promise((resolve, reject) => {
    const { url, ip, method, headers, body } = opt;
    let settled = false;
    const req = https.request(
      {
        host: ip || url.hostname,
        port: url.port ? Number(url.port) : 443,
        path: url.pathname + url.search,
        method,
        headers: { ...headers, host: url.hostname },
        servername: url.hostname,
        rejectUnauthorized: true,
      },
      (res) => {
        settled = true;
        resolve(res);
      },
    );
    req.setTimeout(45_000, () => req.destroy(new Error('upstream timeout')));
    req.on('error', (e) => {
      if (!settled) reject(e);
    });
    if (body && body.length) req.write(body);
    req.end();
  });
}

/**
 * 依次尝试静态 IP，再回退系统 DNS；全部失败抛出带尝试记录的异常。
 */
async function requestWithFallback(url, method, headers, body) {
  const tried = [];
  for (const ip of STATIC_IPS) {
    try {
      return await request({ url, ip, method, headers, body });
    } catch (e) {
      tried.push(`${ip}: ${e.code || e.message}`);
    }
  }
  try {
    return await request({ url, method, headers, body });
  } catch (e) {
    tried.push(`sysdns: ${e.code || e.message}`);
    throw new Error(`upstream failed (${tried.join('; ')})`);
  }
}

function isAllowed(target) {
  return ALLOWED_HOSTS.includes(target.hostname);
}

function send(res, status, body, headers = {}) {
  res.writeHead(status, { ...CORS, ...headers });
  res.end(body);
}

function jsonBody(res, status, obj) {
  send(res, status, JSON.stringify(obj), { 'content-type': 'application/json; charset=utf-8' });
}

async function handle(req, res) {
  if (req.method === 'OPTIONS') return send(res, 204, '');

  const u = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);

  if (u.pathname === '/health') {
    return jsonBody(res, 200, {
      ok: true,
      app: 'nhentai-proxy',
      version: 2,
      ips: STATIC_IPS,
      hosts: ALLOWED_HOSTS.length,
      android: ((req.headers['user-agent'] || '') + (req.headers['x-client'] || '')).includes(ANDROID_UA_HINT),
    });
  }

  /** @type {URL} */
  let target;
  if (u.pathname.startsWith('/api/')) {
    target = new URL(`https://nhentai.net${u.pathname}${u.search}`);
  } else if (u.pathname === '/img') {
    const raw = u.searchParams.get('url');
    if (!raw) return jsonBody(res, 400, { error: 'missing url' });
    try {
      target = new URL(raw);
    } catch {
      return jsonBody(res, 400, { error: 'bad url' });
    }
    if (!isAllowed(target) || target.protocol !== 'https:') {
      return jsonBody(res, 403, { error: 'host not allowed' });
    }
  } else if (u.pathname === '/') {
    return jsonBody(res, 200, { app: 'nhentai-proxy', routes: ['/api/v2/*', '/img?url=', '/health'] });
  } else {
    return jsonBody(res, 404, { error: 'not found' });
  }

  // 透传客户端头（认证、语言、UA），补上来源信息
  const fwd = {
    accept: req.headers.accept || '*/*',
    'accept-language': req.headers['accept-language'] || 'zh-CN,zh;q=0.9,en;q=0.8,ja;q=0.7',
    'accept-encoding': req.headers['accept-encoding'] || 'gzip, deflate, br, identity',
    'user-agent':
      req.headers['user-agent'] ||
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36',
    referer: 'https://nhentai.net/',
  };
  if (req.headers.authorization) fwd.authorization = String(req.headers.authorization);
  if (req.headers['content-type']) fwd['content-type'] = String(req.headers['content-type']);

  let body;
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    const chunks = [];
    for await (const c of req) chunks.push(c);
    body = Buffer.concat(chunks);
    if (body.length) fwd['content-length'] = String(body.length);
  }

  try {
    const upstream = await requestWithFallback(target, req.method || 'GET', fwd, body);
    const outHeaders = { ...CORS };
    for (const h of ['content-type', 'content-encoding', 'cache-control', 'etag', 'last-modified', 'retry-after', 'content-length']) {
      const v = upstream.headers[h];
      if (v) outHeaders[h] = Array.isArray(v) ? v.join(', ') : v;
    }
    if (u.pathname === '/img' && !outHeaders['cache-control']) {
      outHeaders['cache-control'] = 'public, max-age=604800, immutable';
    }
    outHeaders['x-proxied-by'] = 'nhentai-proxy';
    res.writeHead(upstream.statusCode || 502, outHeaders);
    upstream.pipe(res);
    upstream.on('error', () => res.destroy());
  } catch (e) {
    jsonBody(res, 502, { error: 'proxy_error', message: String((e && e.message) || e) });
  }
}

export function createProxyServer() {
  return http.createServer((req, res) => {
    handle(req, res).catch((e) => {
      try {
        jsonBody(res, 500, { error: String(e && e.message) });
      } catch {
        res.destroy();
      }
    });
  });
}

const isMain =
  process.argv[1] && /(?:nhentai-proxy|index)\.mjs$/.test(process.argv[1]);

if (isMain) {
  createProxyServer().listen(PORT, BIND_HOST, () => {
    console.log(`[nhentai-proxy] listening on http://${BIND_HOST}:${PORT}`);
    console.log('[nhentai-proxy] upstream IPs:', STATIC_IPS.join(', '));
    console.log('[nhentai-proxy] GET /api/v2/*  → https://nhentai.net/api/v2/*');
    console.log('[nhentai-proxy] GET /img?url=  → 图片（i1-i4 / t1-t4 / nhentai.net）');
  });
}