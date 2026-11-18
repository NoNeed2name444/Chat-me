// The 3D Fractal's fixture (S12): the 3D layout's rules in JavaScript, apart from both apps.
//
//   node tools/fractal_fixture3d.mjs [--write]
//
// Lays out the concept's sample tree (tools/fractal_fixture.json's "tree") and a made-up tree that
// reaches every rule (a hub of one, a hub of many, ideas in no folder, a deep chain) by the rules
// Android's FractalLayout3D.kt and iOS's FractalLayout3D.swift keep, and compares the result with
// tools/fractal_fixture3d.json, which both platforms' tests read (FractalLayout3DTest,
// FractalLayout3DTests.swift). It prints the largest difference and fails above 1e-9. With --write it
// writes the file afresh.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const flat = JSON.parse(fs.readFileSync(path.join(here, 'fractal_fixture.json'), 'utf8'));
const outPath = path.join(here, 'fractal_fixture3d.json');

// FractalRules
const SEED = 91, LEAF_DOT = 0.045, HUB_DOT = 0.1, HUB_DOT_PARENT = 0.05;
// Fractal3DRules
const GOLDEN = Math.PI * (3 - Math.sqrt(5));
const CAP_LEAST = 0.65, CAP_EACH = 0.275, CAP_MOST = Math.PI - 0.65, STRAY = 0.35, GLOW = 3.0;

// the concept's mulberry32, as FractalRules.Random
function rng(a) {
  return function () {
    a |= 0; a = (a + 0x6D2B79F5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const clamp = (x, a, b) => (x < a ? a : x > b ? b : x);

/** FractalTree's children (heaviest first, ties in list order) and weights (leaves to the 0.7). */
function treeOf(parent) {
  const n = parent.length;
  const kids = Array.from({ length: n }, () => []);
  for (let i = 1; i < n; i++) kids[parent[i]].push(i);
  const leaves = new Array(n).fill(0);
  for (let i = n - 1; i >= 0; i--) { if (kids[i].length === 0) leaves[i] = 1; if (i > 0) leaves[parent[i]] += leaves[i]; }
  const weight = leaves.map((l) => Math.pow(l, 0.7));
  for (const k of kids) k.sort((a, b) => (weight[b] - weight[a]) || (a - b));
  return { n, parent, kids, weight };
}

function layout3d(parent) {
  const t = treeOf(parent);
  const n = t.n;
  const random = rng(SEED);
  const pos = new Array(3 * n).fill(0), out = new Array(3 * n).fill(0), across = new Array(3 * n).fill(0);
  const spacing = new Array(n).fill(0), dot = new Array(n).fill(0), phi0 = new Array(n).fill(0);
  const stack = [];
  function enter(i, x, y, z, dx, dy, dz, ux, uy, uz, sp, psp) {
    pos[3 * i] = x; pos[3 * i + 1] = y; pos[3 * i + 2] = z;
    out[3 * i] = dx; out[3 * i + 1] = dy; out[3 * i + 2] = dz;
    across[3 * i] = ux; across[3 * i + 1] = uy; across[3 * i + 2] = uz;
    spacing[i] = sp;
    if (t.kids[i].length === 0) return;
    dot[i] = Math.max(sp * HUB_DOT, psp * HUB_DOT_PARENT);
    phi0[i] = random() * 2 * Math.PI;
    stack.push([i, 0]);
  }
  enter(0, 0, 0, 0, 0, 1, 0, 1, 0, 0, 1, 1);
  while (stack.length > 0) {
    const top = stack[stack.length - 1];
    const i = top[0], k = top[1], count = t.kids[i].length;
    if (k >= count) { stack.pop(); continue; }
    top[1] = k + 1;
    const sp = spacing[i];
    const whole = i === 0;
    const half = whole ? Math.PI : Math.min(CAP_MOST, CAP_LEAST + count * CAP_EACH);
    const cap = 1 - Math.cos(half);
    const room = Math.min(Math.PI, 2 * Math.sqrt(2 * cap / count));
    const c = t.kids[i][k];
    const wmax = t.weight[t.kids[i][0]];
    const cosT = whole && count === 1 ? 0 : 1 - cap * (k + 0.5) / count;
    const sinT = Math.sqrt(Math.max(0, 1 - cosT * cosT));
    const phi = phi0[i] + k * GOLDEN + (random() - 0.5) * room * STRAY;
    const dist = count > 8 ? sp * ((k % 2 === 1 ? 0.64 : 0.98) + random() * 0.1) : sp * (0.74 + random() * 0.28);
    const csp = sp * clamp(0.9 * Math.sin(room / 2), 0.2, 0.42) * (0.62 + 0.38 * Math.sqrt(t.weight[c] / wmax));
    if (t.kids[c].length === 0) dot[c] = sp * LEAF_DOT;
    const dx = out[3 * i], dy = out[3 * i + 1], dz = out[3 * i + 2];
    const ux = across[3 * i], uy = across[3 * i + 1], uz = across[3 * i + 2];
    const vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;
    const cp = Math.cos(phi), sn = Math.sin(phi);
    const ex = dx * cosT + (ux * cp + vx * sn) * sinT;
    const ey = dy * cosT + (uy * cp + vy * sn) * sinT;
    const ez = dz * cosT + (uz * cp + vz * sn) * sinT;
    let along = ux * ex + uy * ey + uz * ez;
    let ax = ux - ex * along, ay = uy - ey * along, az = uz - ez * along;
    let m = Math.sqrt(ax * ax + ay * ay + az * az);
    if (m < 1e-6) {
      along = vx * ex + vy * ey + vz * ez;
      ax = vx - ex * along; ay = vy - ey * along; az = vz - ez * along;
      m = Math.sqrt(ax * ax + ay * ay + az * az);
    }
    enter(c, pos[3 * i] + ex * dist, pos[3 * i + 1] + ey * dist, pos[3 * i + 2] + ez * dist, ex, ey, ez, ax / m, ay / m, az / m, csp, sp);
  }
  // the clusters' balls
  const mark = (i) => (t.kids[i].length > 0 ? dot[i] * GLOW : dot[i]);
  const lo = new Array(3 * n), hi = new Array(3 * n);
  for (let i = 0; i < n; i++) for (let a = 0; a < 3; a++) { lo[3 * i + a] = pos[3 * i + a] - mark(i); hi[3 * i + a] = pos[3 * i + a] + mark(i); }
  for (let i = n - 1; i >= 1; i--) {
    const p = parent[i];
    for (let a = 0; a < 3; a++) { lo[3 * p + a] = Math.min(lo[3 * p + a], lo[3 * i + a]); hi[3 * p + a] = Math.max(hi[3 * p + a], hi[3 * i + a]); }
  }
  const centre = lo.map((v, k) => (v + hi[k]) / 2);
  const reach = new Array(n).fill(0);
  for (let j = 0; j < n; j++) {
    for (let a = j; a >= 0; a = parent[a]) {
      const x = pos[3 * j] - centre[3 * a], y = pos[3 * j + 1] - centre[3 * a + 1], z = pos[3 * j + 2] - centre[3 * a + 2];
      const e = Math.sqrt(x * x + y * y + z * z) + mark(j);
      if (e > reach[a]) reach[a] = e;
    }
  }
  return { pos, spacing, dot, centre, reach };
}

/** A made-up tree: 3 top folders (one of 12 ideas, one holding a folder of one idea, one a chain 5 deep), 4 loose ideas. */
function madeUp() {
  const parent = [-1];
  const add = (p) => { parent.push(p); return parent.length - 1; };
  const a = add(0); for (let k = 0; k < 12; k++) add(a);
  const b = add(0); const b1 = add(b); add(b1); add(b); add(b);
  let c = add(0); for (let d = 0; d < 5; d++) { add(c); c = add(c); } add(c);
  for (let k = 0; k < 4; k++) add(0);
  return parent;
}

const trees = [
  { name: 'concept sample', parent: flat.tree.parent },
  { name: 'made up', parent: madeUp() },
].map((t) => ({ ...t, layout: layout3d(t.parent) }));

if (process.argv[2] === '--write') {
  const about = "The 3D Fractal's shared fixture (S12): each tree's parents (node 0 All ideas, each parent before its part) and " +
    'the 3D layout tools/fractal_fixture3d.mjs makes of it: pos (x y z by node), spacing, dot, centre (x y z by node), reach. ' +
    "Android's FractalLayout3DTest and iOS's FractalLayout3DTests.swift must give these back within 1e-9.";
  fs.writeFileSync(outPath, JSON.stringify({ about, trees }) + '\n');
  console.log(`wrote ${outPath}`);
  process.exit(0);
}

const fx = JSON.parse(fs.readFileSync(outPath, 'utf8'));
let worst = 0, where = '';
const problems = [];
for (const [k, want] of trees.entries()) {
  const got = fx.trees[k];
  if (!got || JSON.stringify(got.parent) !== JSON.stringify(want.parent)) { problems.push(`${want.name}: parents differ`); continue; }
  for (const key of ['pos', 'spacing', 'dot', 'centre', 'reach']) {
    const a = got.layout[key], b = want.layout[key];
    if (!a || a.length !== b.length) { problems.push(`${want.name}.${key}: wrong length`); continue; }
    for (let i = 0; i < b.length; i++) { const d = Math.abs(a[i] - b[i]); if (!(d <= worst)) { worst = d; where = `${want.name}.${key}[${i}]`; } }
  }
}
console.log(`${trees.length} trees; largest difference ${worst.toExponential(3)}${where ? ' at ' + where : ''}`);
for (const p of problems) console.log(`problem: ${p}`);
process.exit(problems.length === 0 && worst <= 1e-9 ? 0 : 1);
