#!/usr/bin/env node
'use strict';
/*
 * Mock JellyGlance server for trying the app in an emulator without a real install.
 * Response shapes follow apps/api/classes/command-center.js in the JellyGlance server.
 *
 *   node tools/mock-server.js [port] [posterDir]
 *
 * In the emulator, connect to http://10.0.2.2:<port> with any API key. A key containing
 * "widget" behaves like a widgets-only key (proxy and full routes return 403).
 */
const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = Number(process.argv[2] || 7788);
const POSTERS = process.argv[3] || path.join(__dirname, 'mock-posters');
// Real responses saved by tools/capture.sh take priority over the synthetic data below.
const FIXTURES = path.join(__dirname, 'fixtures');
const REAL_BASE = fs.existsSync(path.join(FIXTURES, '_base_url')) ? fs.readFileSync(path.join(FIXTURES, '_base_url'), 'utf8').trim() : '';

function fixture(route) {
  const file = path.join(FIXTURES, route.replace(/^\//, '').replace(/\//g, '__') + '.json');
  if (!fs.existsSync(file)) return null;
  try { return JSON.parse(fs.readFileSync(file, 'utf8')); } catch { return null; }
}

const now = Date.now();
const iso = (offsetMs) => new Date(now + offsetMs).toISOString();
const H = 3600e3;
const D = 24 * H;
const TICKS = 10_000_000;

const library = [
  { id: 'm1', title: 'Dune: Part Two', type: 'Movie', year: 2024 },
  { id: 'm2', title: 'The Wild Robot', type: 'Movie', year: 2024 },
  { id: 's1', title: 'Andor', type: 'Series', year: 2022 },
  { id: 'm3', title: 'Conclave', type: 'Movie', year: 2024 },
  { id: 's2', title: 'The Grand Tour', type: 'Series', year: 2016 },
  { id: 'm4', title: 'Flow', type: 'Movie', year: 2024 },
  { id: 's3', title: 'Severance', type: 'Series', year: 2022 },
  { id: 'm5', title: 'Anora', type: 'Movie', year: 2024 },
];

const sessions = [
  {
    Id: 'sess-1', UserName: 'Roffo', UserId: 'u1', Client: 'Jellyfin Web', DeviceName: 'Chrome',
    NowPlayingItem: { Id: 'ep1', Type: 'Episode', SeriesName: 'The Grand Tour', SeriesId: 's2', Name: 'Last Car Standing', ParentIndexNumber: 4, IndexNumber: 1, RunTimeTicks: 62 * 60 * TICKS, MediaStreams: [{ Type: 'Video', Height: 1080 }] },
    PlayState: { PositionTicks: 23 * 60 * TICKS, IsPaused: false, PlayMethod: 'DirectPlay' },
  },
  {
    Id: 'sess-2', UserName: 'Sam', UserId: 'u2', Client: 'Android TV', DeviceName: 'Living Room Shield',
    NowPlayingItem: { Id: 'm1', Type: 'Movie', Name: 'Dune: Part Two', ProductionYear: 2024, RunTimeTicks: 166 * 60 * TICKS, MediaStreams: [{ Type: 'Video', Height: 2160 }] },
    PlayState: { PositionTicks: 104 * 60 * TICKS, IsPaused: true, PlayMethod: 'Transcode' },
    TranscodingInfo: { TranscodeReasons: ['AudioCodecNotSupported'] },
  },
];

const seerr = [
  { requestId: 41, title: 'Gladiator II', status: 'Pending', mediaType: 'movie', requestedBy: 'Sam', createdAt: iso(-3 * H), posterKey: 'gladiator', overview: 'Years after witnessing the death of Maximus, Lucius is forced to enter the Colosseum.', genres: ['Action', 'Drama'], rating: 6.8, runtime: 148 },
  { requestId: 40, title: 'Slow Horses', status: 'Pending', mediaType: 'tv', requestedBy: 'Kim', createdAt: iso(-20 * H), posterKey: 'slow', overview: 'A team of British intelligence agents who serve in a dumping ground department of MI5.', genres: ['Drama', 'Thriller'], rating: 8.1 },
  { requestId: 39, title: 'Wicked', status: 'Approved', mediaType: 'movie', requestedBy: 'Roffo', createdAt: iso(-2 * D), posterKey: 'wicked', genres: ['Fantasy', 'Musical'], rating: 7.4, runtime: 160 },
  { requestId: 38, title: 'The Penguin', status: 'Available', mediaType: 'tv', requestedBy: 'Roffo', createdAt: iso(-4 * D), posterKey: 'penguin', jellyfinItemId: 's3' },
  { requestId: 37, title: 'Nosferatu', status: 'Failed', mediaType: 'movie', requestedBy: 'Sam', createdAt: iso(-6 * D), posterKey: 'nosferatu' },
  { requestId: 36, title: 'Flow', status: 'Available', mediaType: 'movie', requestedBy: 'Kim', createdAt: iso(-9 * D), posterKey: 'flow', jellyfinItemId: 'm4' },
];

const releases = [
  { title: 'Klara and the Sun', service: 'Radarr', date: iso(-36 * D), hasFile: false },
  { title: 'Clayface', service: 'Radarr', date: iso(-30 * D), hasFile: false },
  { title: 'Severance', subtitle: 'S02E07 - Chikhai Bardo', service: 'Sonarr', date: iso(-2 * D), hasFile: true },
  { title: 'Andor', subtitle: 'S02E04 - Ever Been to Ghorman?', service: 'Sonarr', date: iso(2 * H), hasFile: false, posterUrl: '/proxy/Items/Images/Primary?id=s1' },
  { title: 'Slow Horses', subtitle: 'S05E01 - Bad Ideas', service: 'Sonarr', date: iso(1 * D + 3 * H), hasFile: false },
  { title: 'Wicked', subtitle: 'Movie release', service: 'Radarr', date: iso(3 * D), hasFile: false },
  { title: 'The Last of Us', subtitle: 'S02E03 - The Path', service: 'Sonarr', date: iso(5 * D), hasFile: false },
  { title: 'Mickey 17', subtitle: 'Movie release', service: 'Radarr', date: iso(12 * D), hasFile: false },
];

const downloads = [
  { id: 'h1', name: 'Gladiator.II.2024.2160p.WEB-DL.DDP5.1.HDR.H.265', state: 'downloading', progress: 62.4, stalled: false },
  { id: 'h2', name: 'Andor.S02E03.1080p.DSNP.WEB-DL', state: 'paused', progress: 18, stalled: false },
  { id: 'h3', name: 'Nosferatu.2024.1080p.AMZN.WEB-DL', state: 'stalledDL', progress: 3.1, stalled: true },
];

const w = (extra) => ({ jellyglance: true, ...extra, updatedAt: new Date().toISOString() });
const playingItems = () => sessions.filter((s) => s.NowPlayingItem);

const routes = {
  '/api/jellyfin/status': () => ({ ok: true, name: 'Roffo Media', version: '10.11.2' }),
  '/api/widgets/nowplaying': () => w({
    playing: playingItems().length,
    transcoding: playingItems().filter((s) => s.PlayState.PlayMethod === 'Transcode').length,
    items: playingItems().map((s) => ({
      sessionId: s.Id, viewer: s.UserName,
      title: s.NowPlayingItem.SeriesName ? `${s.NowPlayingItem.SeriesName} · ${s.NowPlayingItem.Name}` : s.NowPlayingItem.Name,
      client: s.Client, device: s.DeviceName, method: s.PlayState.PlayMethod,
    })),
  }),
  '/api/widgets/sessions': () => w({ recent: 2, today: 14, last24h: 21, viewersToday: 4, users: 9 }),
  '/api/widgets/viewers': () => w({ viewersToday: 4, users: 9, today: 14 }),
  '/api/widgets/watch': () => w({ hoursToday: 6.2, hoursWeek: 41.5, hoursAll: 9120.3, secondsToday: 22320, secondsWeek: 149400, secondsAll: 32833080 }),
  '/api/widgets/catalog': () => w({ movies: 7159, shows: 412, episodes: 18233, libraries: 6, addedWeek: 37 }),
  '/api/widgets/storage': () => w({ totalLabel: '41.2 TB', totalBytes: 45299999999999, upcomingCount: 6, upcomingEstimate: '84 GB' }),
  '/api/widgets/libraries': () => w({ totalLabel: '41.2 TB', totalBytes: 45299999999999, upcomingCount: 6, upcomingEstimate: '84 GB', libraries: [{ name: 'Movies', size: '28.1 TB' }, { name: 'Shows', size: '12.9 TB' }] }),
  '/api/widgets/downloads': () => w({ active: downloads.filter((d) => d.progress < 100).length, stalled: downloads.filter((d) => d.stalled).length, total: downloads.length, items: downloads }),
  '/api/widgets/stalled': () => w({ stalled: 1, items: downloads.filter((d) => d.stalled) }),
  '/api/widgets/requests': () => w({
    total: seerr.length, pending: seerr.filter((r) => r.status === 'Pending').length, approved: 1, available: 2, other: 1,
    items: seerr.map((r) => ({ id: r.requestId, sourceId: 'seerr-1', title: 'Request', status: { Pending: 1, Approved: 2, Failed: 4, Available: 5 }[r.status], source: 'Jellyseerr', requestedBy: r.requestedBy })),
  }),
  '/api/widgets/issues': () => w({ total: 3, open: 1, resolved: 2, ok: false, items: [{ title: 'Andor', status: 'Open', type: 'Audio', source: 'Jellyseerr' }] }),
  '/api/widgets/calendar': () => {
    const upcoming = releases.filter((r) => !r.hasFile).sort((a, b) => new Date(a.date) - new Date(b.date));
    return w({ upcoming: upcoming.length, today: 1, estimate: '84 GB', items: upcoming.slice(0, 8).map(({ title, service, date, hasFile }) => ({ title, service, date, hasFile })) });
  },
  '/api/widgets/health': () => w({ digestOk: false, digest: 2, items: [{ type: 'download', label: 'Nosferatu.2024.1080p is stalledDL' }, { type: 'webhook', label: 'Discord delivery failed' }], jellyfinOk: true, jellyfinName: 'Roffo Media', jellyfinVersion: '10.11.2', backupAt: iso(-26 * H) }),
  '/api/widgets/recent': () => w({ count: library.length, items: library.map((l, i) => ({ id: l.id, title: l.title, type: l.type, year: l.year, added: iso(-(i + 1) * 7 * H) })) }),
  '/api/widgets/activity': () => w({ count: 6, items: [
    { viewer: 'Roffo', title: 'The Grand Tour · Last Car Standing', method: 'DirectPlay', client: 'Jellyfin Web', playedAt: iso(-25 * 60e3) },
    { viewer: 'Sam', title: 'Dune: Part Two', method: 'Transcode', client: 'Android TV', playedAt: iso(-2 * H) },
    { viewer: 'Kim', title: 'Severance · Chikhai Bardo', method: 'DirectPlay', client: 'Infuse', playedAt: iso(-5 * H) },
    { viewer: 'Kim', title: 'Severance · Woe’s Hollow', method: 'DirectPlay', client: 'Infuse', playedAt: iso(-6 * H) },
    { viewer: 'Alex', title: 'Flow', method: 'DirectStream', client: 'Swiftfin', playedAt: iso(-26 * H) },
  ] }),
  '/api/widgets/statistics': () => w({ titles: 5, users: 4, topTitle: 'Severance', topUser: 'Kim',
    items: [{ title: 'Severance', viewers: 5 }, { title: 'Dune: Part Two', viewers: 4 }, { title: 'Andor', viewers: 3 }, { title: 'Flow', viewers: 2 }, { title: 'Conclave', viewers: 1 }],
    people: [{ name: 'Kim', plays: 23 }, { name: 'Roffo', plays: 17 }, { name: 'Sam', plays: 9 }, { name: 'Alex', plays: 4 }] }),
  '/api/widgets/users': () => w({ users: 9, admins: 1, activeToday: 4, items: [
    { name: 'Roffo', lastAt: iso(-20 * 60e3), admin: true }, { name: 'Sam', lastAt: iso(-2 * H), admin: false },
    { name: 'Kim', lastAt: iso(-5 * H), admin: false }, { name: 'Alex', lastAt: iso(-26 * H), admin: false }, { name: 'Guest', lastAt: null, admin: false },
  ] }),
  '/api/widgets/transcodes': () => w({ connected: true, status: 'Online', ok: true, name: 'Tdarr', active: 1, queued: 14, errored: 0, items: [{ name: 'Wicked.2024.mkv', node: 'node-1' }] }),
  '/api/widgets/digest': () => w({ digestOk: false, digest: 2, backupAt: iso(-26 * H), items: [{ type: 'download', label: 'Nosferatu stalled' }, { type: 'webhook', label: 'Discord delivery failed' }] }),
  '/api/widgets/homepage': () => w({ sessionsToday: 14, viewersToday: 4, movies: 7159, shows: 412, storage: '41.2 TB', downloads: 2, stalled: 1, jellyfinOk: true }),
  // Full-scope routes
  '/proxy/getSessions': () => sessions.concat([{ Id: 'idle', UserName: 'Kim', Client: 'Infuse', DeviceName: 'iPad' }]),
  '/api/requests': () => ({
    requests: seerr.map((r) => ({
      id: `seerr-1-${r.requestId}`, requestId: r.requestId, sourceId: 'seerr-1', source: 'Jellyseerr', title: r.title, mediaType: r.mediaType,
      status: r.status, requestedBy: r.requestedBy, createdAt: r.createdAt, overview: r.overview || '', genres: r.genres || [], rating: r.rating || null,
      runtime: r.runtime || null, jellyfinItemId: r.jellyfinItemId || null,
      posterUrl: `http://${'HOST'}/poster/${r.posterKey}.png`, backdropUrl: `http://${'HOST'}/backdrop/${r.posterKey}.png`,
    })),
    stats: {},
  }),
  '/api/integrations/calendar': () => ({ releases, sources: [], syncedAt: iso(-10 * 60e3) }),
};

function itemGlance(id) {
  const item = library.find((l) => l.id === id) || { id, title: 'The Grand Tour', type: 'Episode', year: 2016 };
  return {
    item: { id: item.id, name: item.title, type: item.type, year: item.year, library: item.type === 'Movie' ? 'Movies' : 'Shows' },
    jellyfin: { available: true, id: item.id },
    request: id === 'm4' ? { id: 36, title: 'Flow', status: 5, source: 'Jellyseerr', mediaType: 'movie' } : null,
    download: id === 's1' ? { id: 'h2', name: 'Andor.S02E03.1080p.DSNP.WEB-DL', client: 'qBittorrent', state: 'paused', progress: 18 } : null,
    arr: id === 's1' ? { title: 'Andor', service: 'Sonarr', date: iso(2 * H), hasFile: false } : null,
    tdarr: { connected: true, name: 'Tdarr' }, maintainerr: null, kometa: null, unpackerr: null,
  };
}

function sendPoster(res, key) {
  const file = path.join(POSTERS, `${String(key).replace(/[^a-z0-9_-]/gi, '')}.png`);
  const fallback = path.join(POSTERS, 'default.png');
  const chosen = fs.existsSync(file) ? file : fallback;
  if (!fs.existsSync(chosen)) { res.writeHead(404); return res.end(); }
  res.writeHead(200, { 'Content-Type': 'image/png' });
  fs.createReadStream(chosen).pipe(res);
}

function readBody(req) {
  return new Promise((resolve) => {
    let raw = '';
    req.on('data', (c) => { raw += c; });
    req.on('end', () => { try { resolve(JSON.parse(raw || '{}')); } catch { resolve({}); } });
  });
}

http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);
  const p = url.pathname;
  const key = String(req.headers['x-api-token'] || '');
  const widgetOnly = key.toLowerCase().includes('widget');
  const json = (code, body) => {
    const text = JSON.stringify(body).split('http://HOST').join(`http://${req.headers.host}`);
    res.writeHead(code, { 'Content-Type': 'application/json' });
    res.end(text);
  };
  console.log(req.method, p, widgetOnly ? '(widget key)' : '');

  // Image proxy routes are public on JellyGlance, so with captured data send the emulator to the real ones.
  if (REAL_BASE && (p.startsWith('/proxy/Items/Images/') || p.startsWith('/proxy/Arr/Images/') || p.startsWith('/proxy/Users/Images/'))) {
    res.writeHead(302, { Location: REAL_BASE + req.url });
    return res.end();
  }
  if (p.startsWith('/proxy/Items/Images/')) return sendPoster(res, url.searchParams.get('id'));
  if (p.startsWith('/proxy/Arr/Images/')) return sendPoster(res, 'default');
  if (p.startsWith('/poster/') || p.startsWith('/backdrop/')) return sendPoster(res, path.basename(p, '.png'));
  if (!key) return json(401, { error: 'Unauthorized' });

  if (req.method === 'POST') {
    if (widgetOnly) return json(403, { error: 'widgets-write key or admin session required' });
    const body = await readBody(req);
    if (p === '/api/widgets/sessions/stop') {
      const i = sessions.findIndex((s) => s.Id === body.sessionId);
      if (i >= 0) sessions.splice(i, 1);
      return json(200, { ok: true });
    }
    if (p === '/api/widgets/downloads/pause') {
      const d = downloads.find((x) => x.id === body.id);
      if (d) d.state = body.paused ? 'paused' : 'downloading';
      return json(200, { ok: true });
    }
    if (p === '/api/widgets/requests/actions') {
      const r = seerr.find((x) => String(x.requestId) === String(body.requestId));
      if (r) r.status = body.action === 'approve' ? 'Approved' : body.action === 'decline' ? 'Declined' : 'Pending';
      return json(200, { ok: true });
    }
    if (p === '/api/widgets/repair/refresh') return json(200, { ok: true });
    return json(404, { error: 'Not found' });
  }

  if ((p.startsWith('/proxy/') || p === '/api/requests' || p.startsWith('/api/integrations/')) && widgetOnly) {
    return json(403, { message: 'This API key is widgets-only' });
  }
  if (p.startsWith('/api/item-glance/')) {
    const id = decodeURIComponent(p.split('/').pop());
    return json(200, fixture(`item-glance/${id}`) || itemGlance(id));
  }
  // DEMO_STREAMS=1 keeps the synthetic sessions even when replaying an idle server.
  const demoStreams = process.env.DEMO_STREAMS === '1' && (p === '/proxy/getSessions' || p === '/api/widgets/nowplaying');
  const captured = demoStreams ? null : fixture(p);
  if (captured) return json(200, captured);
  const handler = routes[p];
  if (!handler) return json(404, { error: 'Not found' });
  return json(200, handler());
}).listen(PORT, '0.0.0.0', () => console.log(`Mock JellyGlance on http://0.0.0.0:${PORT} (emulator: http://10.0.2.2:${PORT})${REAL_BASE ? ' replaying ' + REAL_BASE : ''}`));
