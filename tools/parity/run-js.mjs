// Runs the browser game's override.js headless and records reference games
// for ParityTest: the board after every move, with the spawns drawn from the
// same mulberry32 the app uses.
//
//   node tools/parity/run-js.mjs path/to/override.js app/src/test/resources/parity.json.gz [games] [stress]
//
// override.js is an IIFE that draws on a canvas and listens to the keyboard.
// It runs here inside a vm context with a do-nothing canvas and document;
// the harness presses keys, steps requestAnimationFrame, and reads the board
// back from the game's own save slot (localStorage 'override-state'), which
// the game writes after every move.
//
// Math.random is split in two streams: calls from spawnTile and nexusLock
// (the only ones that change the game) get the seeded mulberry32 stream;
// everything else (sparks, static, the music) gets a separate one, so the
// visual effects do not shift the game's numbers.

import fs from 'node:fs';
import vm from 'node:vm';
import zlib from 'node:zlib';

const [, , srcPath, outPath, gamesArg, variant] = process.argv;
if (!srcPath || !outPath) {
    console.error('usage: node run-js.mjs override.js out.json [games] [stress]');
    process.exit(1);
}
const GAMES = parseInt(gamesArg || '60', 10);

/* The stress variant patches three numbers in override.js so that the rare
   paths (NEXUS-9 locks, EMP fries, the Override and endless mode) show up in
   every game. The code that runs is still the browser's; ParityTest plays the
   same rules through Engine with the matching Rules. */
const STRESS = variant === 'stress';
const PATCHES = [
    ['trace += 2 + countCorrupt();', 'trace += 14 + countCorrupt();'],
    ['countCorrupt() < 2 && Math.random() < 0.07', 'countCorrupt() < 2 && Math.random() < 0.35'],
    ['if (m.value === 2048 && !won)', 'if (m.value === 128 && !won)'],
];
let source = fs.readFileSync(srcPath, 'utf8');
if (STRESS) {
    for (const [from, to] of PATCHES) {
        if (!source.includes(from)) { console.error('patch not found: ' + from); process.exit(1); }
        source = source.replace(from, to);
    }
}

function mulberry32(a) {
    return function () {
        a |= 0; a = a + 0x6D2B79F5 | 0;
        let t = Math.imul(a ^ a >>> 15, 1 | a);
        t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t;
        return ((t ^ t >>> 14) >>> 0) / 4294967296;
    };
}

/* ---- a canvas and a document that accept everything and draw nothing ---- */
function stubContext() {
    const gradient = { addColorStop() {} };
    return new Proxy({}, {
        get(target, prop) {
            if (prop in target) return target[prop];
            if (prop === 'createLinearGradient' || prop === 'createRadialGradient') return () => gradient;
            if (prop === 'measureText') return () => ({ width: 0 });
            if (prop === 'getImageData') return () => ({ data: new Uint8ClampedArray(4) });
            return () => {};
        },
        set(target, prop, value) { target[prop] = value; return true; }
    });
}
function stubCanvas() {
    const ctx = stubContext();
    return {
        width: 0, height: 0, style: {},
        getContext: () => ctx,
        addEventListener() {}, setPointerCapture() {},
        getBoundingClientRect: () => ({ width: 400, height: 580, left: 0, top: 0 })
    };
}

const docHandlers = {};
const storage = new Map();
let rafCallback = null;
let now = 0;

const game = { rng: mulberry32(1) };
const junk = mulberry32(99);

const sandbox = {
    console,
    document: {
        getElementById: (id) => (id === 'override-screen' ? stubCanvas() : null),
        createElement: () => stubCanvas(),
        addEventListener: (type, fn) => { docHandlers[type] = fn; },
        head: { appendChild() {} },
        hidden: false
    },
    localStorage: {
        getItem: (k) => (storage.has(k) ? storage.get(k) : null),
        setItem: (k, v) => storage.set(k, String(v)),
        removeItem: (k) => storage.delete(k)
    },
    requestAnimationFrame: (cb) => { rafCallback = cb; },
    setInterval: () => 0,
    setTimeout: () => 0,
    performance: { now: () => now },
    Promise, JSON, Error, Array, Object, String, Float32Array, Uint8Array, Uint8ClampedArray, Proxy
};
sandbox.window = sandbox;
vm.createContext(sandbox);
const ctxMath = vm.runInContext('Math', sandbox);
const ctxDate = vm.runInContext('Date', sandbox);
ctxDate.now = () => now;
ctxMath.random = function () {
    const caller = (new Error().stack.split('\n')[2] || '');
    if (/\bspawnTile\b|\bnexusLock\b/.test(caller)) return game.rng();
    return junk();
};

vm.runInContext(source, sandbox, { filename: 'override.js' });

/* ---- driving the game ---- */
function frames(ms) {
    for (let t = 0; t < ms; t += 50) {
        now += 50;
        const cb = rafCallback; rafCallback = null;
        if (cb) cb(now);
    }
}
function key(k) {
    docHandlers.keydown({ key: k, repeat: false, metaKey: false, ctrlKey: false, altKey: false, target: { tagName: 'BODY' }, preventDefault() {} });
}
function board() {
    const s = storage.get('override-state');
    return s ? JSON.parse(s) : null;
}
const KEYS = ['ArrowUp', 'ArrowRight', 'ArrowDown', 'ArrowLeft'];

/* The player that drives the recording. It only chooses the moves; what the
   moves do is always measured from override.js itself. Most games use a
   small greedy search on a rough copy of the slide (empty cells, a big tile
   in a corner, smooth rows), so some reach the Override and endless mode;
   a share of random moves brings locks and corrupted nodes in quickly. */
const driver = mulberry32(2024);
function slideRough(cells, locks, d) {
    const g = Array.from({ length: 4 }, () => Array(4).fill(null));
    cells.forEach(c => { g[c[0]][c[1]] = { v: c[2], c: c[3] === 1, m: false }; });
    const locked = (x, y) => locks.some(l => l[0] === x && l[1] === y);
    const vx = [0, 1, 0, -1][d], vy = [-1, 0, 1, 0][d];
    const xs = [0, 1, 2, 3], ys = [0, 1, 2, 3];
    if (vx === 1) xs.reverse();
    if (vy === 1) ys.reverse();
    let moved = false, gain = 0;
    const free = (x, y) => x >= 0 && x < 4 && y >= 0 && y < 4 && !g[x][y] && !locked(x, y);
    for (const x of xs) for (const y of ys) {
        const t = g[x][y];
        if (!t || locked(x, y)) continue;
        let fx, fy, nx = x, ny = y;
        do { fx = nx; fy = ny; nx = fx + vx; ny = fy + vy; } while (free(nx, ny));
        const n = nx >= 0 && nx < 4 && ny >= 0 && ny < 4 ? g[nx][ny] : null;
        if (n && n.v === t.v && !n.m && !locked(nx, ny)) {
            g[x][y] = null;
            if (t.c || n.c) g[nx][ny] = null; else { g[nx][ny] = { v: t.v * 2, c: false, m: true }; gain += t.v * 2; }
            moved = true;
        } else if (fx !== x || fy !== y) { g[x][y] = null; g[fx][fy] = t; moved = true; }
    }
    return moved ? { g, gain } : null;
}
function evalBoard(g, gain) {
    let empty = 0, smooth = 0, max = 0, corner = 0;
    for (let x = 0; x < 4; x++) for (let y = 0; y < 4; y++) {
        const t = g[x][y];
        if (!t) { empty++; continue; }
        if (!t.c && t.v > max) { max = t.v; corner = (x === 0 && y === 3) ? 1 : 0; }
        if (x < 3 && g[x + 1][y]) smooth -= Math.abs(Math.log2(t.v) - Math.log2(g[x + 1][y].v));
        if (y < 3 && g[x][y + 1]) smooth -= Math.abs(Math.log2(t.v) - Math.log2(g[x][y + 1].v));
        smooth += (3 - x + y) * Math.log2(t.v) * 0.15;
    }
    return empty * 3 + smooth + corner * 6 + Math.log2(gain + 1);
}
function pickOrder(state, randomShare, stall) {
    const d = [0, 1, 2, 3];
    const empty = 16 - state.cells.length - state.locks.length;
    if (driver() < randomShare) {
        for (let i = 3; i > 0; i--) { const j = Math.floor(driver() * (i + 1)); [d[i], d[j]] = [d[j], d[i]]; }
        return d;
    }
    const scored = d.map(dir => {
        const r = slideRough(state.cells, state.locks.map(l => [l.x, l.y]), dir);
        if (!r) return { dir, s: -1e9 };
        /* the staller avoids fusions while there is room, so the trace climbs into locks */
        if (stall && empty > 4) return { dir, s: -r.gain * 10 + driver() };
        return { dir, s: evalBoard(r.g, r.gain) };
    });
    scored.sort((a, b) => b.s - a.s);
    return scored.map(s => s.dir);
}

const out = {
    source: srcPath,
    rules: STRESS ? { traceEmpty: 14, corruptChance: 0.35, target: 128 } : {},
    games: []
};
frames(200);
for (let g = 0; g < GAMES; g++) {
    const seed = (g * 7919 + 17) | 0;
    game.rng = mulberry32(seed);
    frames(2000);                       /* SIGNAL LOST waits 90 frames before a restart */
    key(' ');
    frames(200);
    const init = board();
    const rec = { seed, init, steps: [] };
    const randomShare = g % 4 === 0 ? 0.5 : 0.04;
    const stall = g % 4 === 1;
    let guard = 0;
    while (guard++ < 6000) {
        const before = board();
        if (!before) break;            /* game over: the save slot is cleared */
        if (before.state === 'won' && !before.keep) {
            frames(1600);
            key('Enter');
            frames(200);
            rec.steps.push({ cont: true, state: board() });
            continue;
        }
        let moved = false;
        for (const d of pickOrder(before, randomShare, stall)) {
            key(KEYS[d]);
            const after = board();
            if (!after || after.moves !== before.moves) {
                rec.steps.push({ dir: d, state: after });
                moved = true;
                frames(200);
                break;
            }
            rec.steps.push({ dir: d, blocked: true });
        }
        if (!moved) break;
    }
    out.games.push(rec);
    const states = rec.steps.filter(s => s.state).map(s => s.state);
    const last = states.length ? states[states.length - 1] : init;
    const max = Math.max(...last.cells.filter(c => !c[3]).map(c => c[2]));
    let locks = 0, prev = 0;
    states.forEach(st => { if (st.locks.length > prev) locks++; prev = st.locks.length; });
    console.error(`game ${g}: ${rec.steps.length} steps, score ${last.score}, max ${max}, won ${last.won}, ~locks ${locks}`);
    if (board()) key('r');             /* end the session if it is still running */
}
const json = JSON.stringify(out);
fs.writeFileSync(outPath, outPath.endsWith('.gz') ? zlib.gzipSync(json, { level: 9 }) : json);
console.error(`wrote ${out.games.length} games to ${outPath}`);
