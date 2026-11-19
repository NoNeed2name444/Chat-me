// The Tree look's layout fixture (S11): the approved concept's Tree layout (the
// nested-maps concept of 18 Nov, its makeB for the Tree), run on a few generic
// trees, written out node by node, so both apps' ports are held to the
// concept's numbers and to each other's:
//   android/app/src/test/resources/tree/tree-fixture.json
// read by Android's TreeLayoutTest and TreeLayout3DTest and iOS's suite treelayout (tools/swift_suites.txt).
//
//   node tools/tree_fixture.mjs > android/app/src/test/resources/tree/tree-fixture.json
//
// The layout below is the concept's, word for word where it can be, with only the
// Tree's branch of each choice kept. Numbers are written to 9 decimals.

const K = 9, TAU = Math.PI * 2, LEAFK = 0.5;
const P = {seed: 11, rootAng: -Math.PI / 2, rootFan: 0.85, fanMin: 0.45, fanMax: 0.85, spread: 1.05, drop: 0.42,
  ratio: 0.7, shorten: 0.22, wid: 0.075, rootWid: 0.12, bend: 0.06};

function clamp(x, a, b) { return x < a ? a : x > b ? b : x; }
function rng(seed) {
  let s = seed >>> 0;
  return function () {
    s = (s + 0x6D2B79F5) >>> 0; let t = s;
    t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// "#" marks a folder; two spaces make one level (the concept's own outline form)
function outline(lines) {
  const parent = [-1], title = ['All ideas'], folder = [1], stack = [0];
  for (const raw of lines) {
    let t = raw.replace(/^ +/, ''); const ind = (raw.length - t.length) >> 1; let f = 0;
    if (t.charAt(0) === '#') { f = 1; t = t.slice(1); }
    stack.length = ind + 1;
    parent.push(stack[ind]); title.push(t); folder.push(f); stack.push(title.length - 1);
  }
  return {parent, title, folder};
}

function layout(T) {
  const N = T.parent.length, PAR = T.parent;
  const CC = new Int32Array(N), CS = new Int32Array(N + 1), CL = new Int32Array(N);
  for (let i = 1; i < N; i++) CC[PAR[i]]++;
  for (let i = 0; i < N; i++) CS[i + 1] = CS[i] + CC[i];
  { const fill = CS.slice(0, N); for (let i = 1; i < N; i++) CL[fill[PAR[i]]++] = i; }
  const WT = new Float64Array(N), WP = new Float64Array(N);
  for (let i = N - 1; i >= 0; i--) { if (!CC[i]) WT[i] = 1; if (i > 0) WT[PAR[i]] += WT[i]; }
  for (let i = 0; i < N; i++) WP[i] = Math.pow(WT[i], 0.7);
  const TIN = new Int32Array(N), TOUT = new Int32Array(N), ORDER = new Int32Array(N);
  { let t = 0; const dfs = (i) => { TIN[i] = t; ORDER[t++] = i; for (let k = 0; k < CC[i]; k++) dfs(CL[CS[i] + k]); TOUT[i] = t; }; dfs(0); }
  const L = {bx: new Float64Array(N), by: new Float64Array(N), ang: new Float64Array(N), len: new Float64Array(N), wid: new Float64Array(N),
    px: new Float64Array(N * K), py: new Float64Array(N * K), nx: new Float64Array(N * K), ny: new Float64Array(N * K),
    bcx: new Float64Array(N), bcy: new Float64Array(N), brad: new Float64Array(N),
    u0: new Float64Array(N), u1: new Float64Array(N), v0: new Float64Array(N), v1: new Float64Array(N)};
  const rnd = rng(P.seed);
  const place = function (i, x, y, a, len, S) {
    L.bx[i] = x; L.by[i] = y; L.ang[i] = a; L.len[i] = len; L.wid[i] = len * (i === 0 ? P.rootWid : P.wid);
    const dx = Math.cos(a), dy = Math.sin(a), qx = -dy, qy = dx, o = i * K;
    const sign = rnd() < 0.5 ? -1 : 1; rnd();   // the concept draws a phase here for its other looks
    for (let j = 0; j < K; j++) {
      const u = j / (K - 1), off = sign * P.bend * len * 4 * u * (1 - u);
      L.px[o + j] = x + dx * len * u + qx * off; L.py[o + j] = y + dy * len * u + qy * off;
    }
    for (let j = 0; j < K; j++) {
      const j0 = j > 0 ? j - 1 : 0, j1 = j < K - 1 ? j + 1 : K - 1;
      const tx = L.px[o + j1] - L.px[o + j0], ty = L.py[o + j1] - L.py[o + j0], m = Math.hypot(tx, ty) || 1;
      L.nx[o + j] = -ty / m; L.ny[o + j] = tx / m;
    }
    const n = CC[i]; if (!n) return;
    const kids = [];
    for (let k = 0; k < n; k++) kids.push(CL[CS[i] + k]);
    kids.sort((p, q) => WP[q] - WP[p]);
    const left = [], right = [];
    for (let k = 1; k < n; k++) (k % 2 ? left : right).push(kids[k]);
    const arr = left.reverse().concat([kids[0]], right);
    let tot = 0, wmax = 0, cum = 0;
    for (let k = 0; k < n; k++) { tot += WP[arr[k]]; if (WP[arr[k]] > wmax) wmax = WP[arr[k]]; }
    for (let k = 0; k < n; k++) {
      const c = arr[k]; let o2 = -S + 2 * S * (cum + WP[c] / 2) / tot; cum += WP[c];
      if (n === 1) o2 = (rnd() < 0.5 ? -1 : 1) * S * 0.45;
      const rel = Math.min(1, Math.abs(o2) / S);
      const t = clamp(1 - P.drop * Math.pow(rel, 0.85) + (rnd() - 0.5) * 0.05, 1 - P.drop, 1);
      const f = t * (K - 1), jj = Math.min(K - 2, Math.floor(f)), fr = f - jj;
      const ax = L.px[o + jj] * (1 - fr) + L.px[o + jj + 1] * fr, ay = L.py[o + jj] * (1 - fr) + L.py[o + jj + 1] * fr;
      const ca = a + o2 * P.spread + (rnd() - 0.5) * 0.08;
      const cl = len * P.ratio * (0.5 + 0.5 * Math.sqrt(WP[c] / wmax)) * (1 - P.shorten * rel);
      const cS = clamp(2 * S * WP[c] / tot * 0.9 + 0.3, P.fanMin, P.fanMax);
      place(c, ax, ay, ca, cl, cS);
    }
  };
  place(0, 0, 0, P.rootAng, 1, P.rootFan);
  const mnx = new Float64Array(N), mny = new Float64Array(N), mxx = new Float64Array(N), mxy = new Float64Array(N), tip = [0, 0];
  const leafTip = (d, out) => { const o = d * K + K - 1, a = Math.atan2(L.py[o] - L.py[o - 1], L.px[o] - L.px[o - 1]), ll = L.len[d] * LEAFK; out[0] = L.px[o] + Math.cos(a) * ll; out[1] = L.py[o] + Math.sin(a) * ll; };
  for (let i = 0; i < N; i++) {
    const w = L.wid[i]; mnx[i] = mny[i] = Infinity; mxx[i] = mxy[i] = -Infinity;
    for (let j = 0; j < K; j++) { const X = L.px[i * K + j], Y = L.py[i * K + j]; mnx[i] = Math.min(mnx[i], X - w); mxx[i] = Math.max(mxx[i], X + w); mny[i] = Math.min(mny[i], Y - w); mxy[i] = Math.max(mxy[i], Y + w); }
    if (!CC[i]) { leafTip(i, tip); const e = L.len[i] * LEAFK * 0.3; mnx[i] = Math.min(mnx[i], tip[0] - e); mxx[i] = Math.max(mxx[i], tip[0] + e); mny[i] = Math.min(mny[i], tip[1] - e); mxy[i] = Math.max(mxy[i], tip[1] + e); }
  }
  for (let i = N - 1; i > 0; i--) { const p = PAR[i]; mnx[p] = Math.min(mnx[p], mnx[i]); mny[p] = Math.min(mny[p], mny[i]); mxx[p] = Math.max(mxx[p], mxx[i]); mxy[p] = Math.max(mxy[p], mxy[i]); }
  for (let i = 0; i < N; i++) { L.bcx[i] = (mnx[i] + mxx[i]) / 2; L.bcy[i] = (mny[i] + mxy[i]) / 2; L.brad[i] = Math.hypot(mxx[i] - mnx[i], mxy[i] - mny[i]) / 2; }
  for (let i = 0; i < N; i++) {
    const a = L.ang[i], ca2 = Math.cos(a), sa = Math.sin(a), bx = L.bx[i], by = L.by[i];
    let u0 = Infinity, u1 = -Infinity, v0 = Infinity, v1 = -Infinity;
    for (let k = TIN[i]; k < TOUT[i]; k++) {
      const d = ORDER[k], wd = L.wid[d];
      for (let j = 0; j <= K; j++) {
        let px, py, m2 = wd;
        if (j < K) { px = L.px[d * K + j]; py = L.py[d * K + j]; }
        else { if (CC[d]) continue; leafTip(d, tip); px = tip[0]; py = tip[1]; m2 = L.len[d] * LEAFK * 0.3; }
        const dx2 = px - bx, dy2 = py - by, uu = dx2 * ca2 + dy2 * sa, vv = -dx2 * sa + dy2 * ca2;
        if (uu - m2 < u0) u0 = uu - m2; if (uu + m2 > u1) u1 = uu + m2; if (vv - m2 < v0) v0 = vv - m2; if (vv + m2 > v1) v1 = vv + m2;
      }
    }
    L.u0[i] = u0; L.u1[i] = u1; L.v0[i] = v0; L.v1[i] = v1;
  }
  return L;
}

// The Tree in 3D, grown as a tree (S11, the owner's review of the third pass, 19 Nov: "the tree doesn't
// look realistic neither the leaves . also it's too cramped"). The trunk goes up the y axis, 1 long.
// - Thickness follows the pipe model (Shinozaki 1964, Leonardo's rule): a branch's area is the sum
//   of the leaves it carries, r0 = ROOT_R * sqrt(leaves / all leaves), never over JOIN of where it
//   joins; it tapers to its biggest child's (at most TIP of its base).
// - Length follows its share of the tree: LEN1 * (leaves / all leaves)^LEN_POW, never over RATIO of its
//   parent's nor under TWIG leaves.
// - Children leave their parent's line round it by the golden angle from a seeded start (3D
//   phyllotaxis, Vogel 1979), the heaviest the most upright: the trunk's branches RISE0 to RISE1 off
//   upright along its top DROP1, a deeper branch SPLAY0 to SPLAY1 off its parent's line along its
//   last P.drop, then lifted (LIFT); a child carrying most of its parent leans less (by LEAD of its
//   share squared), so the tree keeps a leader, as most trees do. A deeper branch's branches start
//   SPREAD further down a child after the first (at most DROP_MOST); the first child, and an only
//   child, leaves near its parent's tip. Two sibling branches are never nearer than GAP: a branch too
//   near one placed before it turns on round its parent, a TURN at a time; a branch after the first
//   leans at least 1.5 GAP off its parent's line.
// - An idea is a short stem (STEM of its leaf) at its parent's end (its last SPRAY), SPRAY0 to
//   SPRAY1 off the parent's line, its leaf past the stem's tip: a parent's ideas make one spray.
//   A leaf is LEAF_K / sqrt(all leaves) long each side, at most LEAF_MOST, varied by LEAF_VAR.
// - A branch bends: its middle is pulled down and out (BEND of its length), so it leaves its parent
//   flatter and turns up toward its tip, and swayed level to one side (up to SWAY), seeded. Its line is a
//   quadratic curve from base to tip through base + dir * len / 2 + bend; a child joins on the curve
//   and leaves it from the curve's own direction there.
// A body (the name's and the tap's place) is ANCHOR along a branch, a leaf's centre for an idea.
// Its own draw (a second rng on the same seed), so the flat numbers above stay as they were.
// Android's TreeLayout3D and iOS's TreeLayout3D.swift are the same line for line.
const GOLDEN = Math.PI * (3 - Math.sqrt(5)), DEG = Math.PI / 180;
const ROOT_R = 0.075, TIP = 0.85, JOIN = 0.95, LEN1 = 0.95, LEN_POW = 0.45, RATIO = 0.8;
const RISE0 = 40 * DEG, RISE1 = 70 * DEG, SPLAY0 = 30 * DEG, SPLAY1 = 60 * DEG, SPRAY0 = 40 * DEG, SPRAY1 = 75 * DEG;
const LEAD = 0.6, LIFT = 0.2, DROP1 = 0.3, SPREAD = 0.03, DROP_MOST = 0.75, SPRAY = 0.35, GAP = 18 * DEG, TURN = 10 * DEG, TURNS = 36;
const BEND = 0.12, SWAY = 0.08, STEM = 0.6, TWIG = 8, LEAF_K = 0.75, LEAF_MOST = 0.09, LEAF_VAR = 0.3, ANCHOR = 0.6;

// the point t along node i's curve, into out at o
function curveAt(E, i, t, out, o) {
  const u = 1 - t, len = E.len[i];
  for (let a = 0; a < 3; a++) {
    const b = E.base[3 * i + a], d = E.dir[3 * i + a];
    const c = b + d * len / 2 + E.bend[3 * i + a], e = b + d * len;
    out[o + a] = u * u * b + 2 * u * t * c + t * t * e;
  }
}
// the curve's direction at t (unit), into out
function tangentAt(E, i, t, out) {
  const len = E.len[i];
  for (let a = 0; a < 3; a++) {
    const b = E.base[3 * i + a], d = E.dir[3 * i + a];
    const c = b + d * len / 2 + E.bend[3 * i + a], e = b + d * len;
    out[a] = 2 * (1 - t) * (c - b) + 2 * t * (e - c);
  }
  const m = Math.hypot(out[0], out[1], out[2]); for (let a = 0; a < 3; a++) out[a] /= m;
}

function layout3d(T) {
  const N = T.parent.length, PAR = T.parent;
  const CC = new Int32Array(N), CS = new Int32Array(N + 1), CL = new Int32Array(N);
  for (let i = 1; i < N; i++) CC[PAR[i]]++;
  for (let i = 0; i < N; i++) CS[i + 1] = CS[i] + CC[i];
  { const fill = CS.slice(0, N); for (let i = 1; i < N; i++) CL[fill[PAR[i]]++] = i; }
  const WT = new Float64Array(N), WP = new Float64Array(N);
  for (let i = N - 1; i >= 0; i--) { if (!CC[i]) WT[i] = 1; if (i > 0) WT[PAR[i]] += WT[i]; }
  for (let i = 0; i < N; i++) WP[i] = Math.pow(WT[i], 0.7);
  const F = (n) => new Float64Array(n);
  const E = {base: F(3 * N), dir: F(3 * N), len: F(N), r0: F(N), r1: F(N), bend: F(3 * N), anchor: F(3 * N), leaf: F(N), sc: F(3 * N), sr: F(N)};
  const rnd = rng(P.seed);
  const pipe = (i) => ROOT_R * Math.sqrt(WT[i] / WT[0]);
  const leafSize = Math.min(LEAF_MOST, LEAF_K / Math.sqrt(WT[0]));
  const place = function (i, b, D, U, len, rmax, scale) {
    for (let a = 0; a < 3; a++) { E.base[3 * i + a] = b[a]; E.dir[3 * i + a] = D[a]; }
    E.len[i] = len;
    const r0 = Math.min(pipe(i) * scale, rmax), n = CC[i], k0 = r0 / pipe(i);
    E.r0[i] = r0;
    const V = [D[1] * U[2] - D[2] * U[1], D[2] * U[0] - D[0] * U[2], D[0] * U[1] - D[1] * U[0]];
    const phi0 = rnd() * 2 * Math.PI;
    if (!n && i > 0) {
      E.r1[i] = r0 * TIP;
      const lr = Math.min(leafSize * (1 - LEAF_VAR / 2 + LEAF_VAR * rnd()), LEAF_MOST); E.leaf[i] = lr;
      for (let a = 0; a < 3; a++) E.anchor[3 * i + a] = b[a] + D[a] * (len + lr);
      return;
    }
    // the bend: down and out from the line's middle (none for an upright line), then a sway to one side
    const pu = [-D[0] * D[1], 1 - D[1] * D[1], -D[2] * D[1]], pm = Math.hypot(pu[0], pu[1], pu[2]);
    const sway = (2 * rnd() - 1) * SWAY * len;
    // the sway level, square to the line (its across where the line is upright)
    const side = pm > 1e-6 ? [-D[2] / pm, 0, D[0] / pm] : V;
    for (let a = 0; a < 3; a++) E.bend[3 * i + a] = (pm > 1e-6 ? -pu[a] / pm * BEND * len : 0) + side[a] * sway;
    curveAt(E, i, ANCHOR, E.anchor, 3 * i);
    if (!n) { E.r1[i] = r0 * TIP; return; }
    const kids = [];
    for (let k = 0; k < n; k++) kids.push(CL[CS[i] + k]);
    kids.sort((p, q) => WP[q] - WP[p]);
    let most = 0;
    for (let k = 0; k < n; k++) most = Math.max(most, pipe(kids[k]));
    E.r1[i] = Math.min(most * k0, r0 * TIP);
    const trunk = i === 0, placed = [], at = [0, 0, 0], tg = [0, 0, 0];
    for (let q = 0; q < n; q++) {
      const c = kids[q], idea = CC[c] === 0;
      const rel = n === 1 ? 0.1 : q === 0 ? 0.1 : 0.45 + 0.55 * q / (n - 1);
      const s = n === 1 ? 0.5 : q / (n - 1);
      const drop = trunk ? DROP1 : idea ? SPRAY : Math.min(P.drop + SPREAD * (n - 1), DROP_MOST);
      const t = clamp(1 - drop * Math.pow(rel, 0.85) + (rnd() - 0.5) * 0.05, 1 - drop, 1);
      curveAt(E, i, t, at, 0);
      tangentAt(E, i, t, tg);
      // the parent's frame at t: its across turned square to the curve there
      let ud = U[0] * tg[0] + U[1] * tg[1] + U[2] * tg[2];
      let ut = [U[0] - tg[0] * ud, U[1] - tg[1] * ud, U[2] - tg[2] * ud], um = Math.hypot(ut[0], ut[1], ut[2]);
      for (let a = 0; a < 3; a++) ut[a] /= um;
      const vt = [tg[1] * ut[2] - tg[2] * ut[1], tg[2] * ut[0] - tg[0] * ut[2], tg[0] * ut[1] - tg[1] * ut[0]];
      const lean = (trunk ? RISE0 + (RISE1 - RISE0) * s : SPLAY0 + (SPLAY1 - SPLAY0) * s) * (1 - LEAD * (WT[c] / WT[i]) * (WT[c] / WT[i]));
      // a branch after the first leans at least 1.5 GAP off its parent's line, so turning round can clear the first
      const th = idea ? SPRAY0 + (SPRAY1 - SPRAY0) * s : q > 0 ? Math.max(lean, 1.5 * GAP) : lean;
      const ct = Math.cos(th), st = Math.sin(th), d = [0, 0, 0];
      const aim = (f) => {
        const cp = Math.cos(f), sp = Math.sin(f);
        for (let a = 0; a < 3; a++) d[a] = tg[a] * ct + (ut[a] * cp + vt[a] * sp) * st;
        if (!trunk) d[1] += LIFT;
        const dm = Math.hypot(d[0], d[1], d[2]); for (let a = 0; a < 3; a++) d[a] /= dm;
      };
      let phi = phi0 + q * GOLDEN;
      if (!idea) {
        // turned on round the parent until clear of every sibling branch, else the turn that came nearest
        let best = phi, bestNear = -1;
        for (let k = 0; k < TURNS; k++) {
          const f = phi + k * TURN; aim(f);
          let near = Math.PI;
          for (const p of placed) near = Math.min(near, Math.acos(clamp(p[0] * d[0] + p[1] * d[1] + p[2] * d[2], -1, 1)));
          if (near > bestNear) { bestNear = near; best = f; }
          if (near >= GAP) break;
        }
        phi = best;
      }
      aim(phi);
      if (!idea) placed.push([d[0], d[1], d[2]]);
      const cl = idea ? leafSize * STEM : Math.max(Math.min(LEN1 * Math.pow(WT[c] / WT[0], LEN_POW), len * RATIO), leafSize * TWIG);
      ud = ut[0] * d[0] + ut[1] * d[1] + ut[2] * d[2];
      let u = [ut[0] - d[0] * ud, ut[1] - d[1] * ud, ut[2] - d[2] * ud];
      um = Math.hypot(u[0], u[1], u[2]);
      if (um < 1e-6) { ud = vt[0] * d[0] + vt[1] * d[1] + vt[2] * d[2]; u = [vt[0] - d[0] * ud, vt[1] - d[1] * ud, vt[2] - d[2] * ud]; um = Math.hypot(u[0], u[1], u[2]); }
      for (let a = 0; a < 3; a++) u[a] /= um;
      place(c, [at[0], at[1], at[2]], d, u, cl, (r0 + (E.r1[i] - r0) * t) * JOIN, k0);
    }
  };
  place(0, [0, 0, 0], [0, 1, 0], [1, 0, 0], 1, Infinity, 1);
  // each subtree's box (its branches' curves round their radius, its leaves round theirs), as a ball: what a fly-in frames
  const lo = F(3 * N), hi = F(3 * N);
  for (let i = 0; i < N; i++) {
    for (let a = 0; a < 3; a++) {
      const b0 = E.base[3 * i + a], b1 = b0 + E.dir[3 * i + a] * E.len[i], bc = (b0 + b1) / 2 + E.bend[3 * i + a], r = E.r0[i];
      lo[3 * i + a] = Math.min(b0, b1, bc) - r; hi[3 * i + a] = Math.max(b0, b1, bc) + r;
      if (E.leaf[i] > 0) { const c = E.anchor[3 * i + a]; lo[3 * i + a] = Math.min(lo[3 * i + a], c - E.leaf[i]); hi[3 * i + a] = Math.max(hi[3 * i + a], c + E.leaf[i]); }
    }
  }
  for (let i = N - 1; i > 0; i--) { const p = PAR[i]; for (let a = 0; a < 3; a++) { lo[3 * p + a] = Math.min(lo[3 * p + a], lo[3 * i + a]); hi[3 * p + a] = Math.max(hi[3 * p + a], hi[3 * i + a]); } }
  for (let i = 0; i < N; i++) {
    for (let a = 0; a < 3; a++) E.sc[3 * i + a] = (lo[3 * i + a] + hi[3 * i + a]) / 2;
    E.sr[i] = Math.hypot(hi[3 * i] - lo[3 * i], hi[3 * i + 1] - lo[3 * i + 1], hi[3 * i + 2] - lo[3 * i + 2]) / 2;
  }
  return E;
}

// the concept's sample ideas (its SRC, word for word), the picture the owner approved
const SAMPLE = [
'#Examples',
'  #Inguinal',
'    #Anatomy',
'      Canal boundaries',
'        Anterior',
'          External oblique all through',
'          Internal oblique laterally',
'        Posterior',
'          Fascia transversalis all through',
'          Internal oblique medially',
'        Roof',
'          Conjoined muscles',
'            Internal oblique and transversus',
'        Floor',
'          Inguinal ligament',
'          Lacunar ligament',
'      LA/PM mnemonic',
'        Anterior wall Laterally',
'        Posterior wall Medially',
'      Spermatic cord coverings',
'        Internal spermatic fascia',
'        Cremasteric muscle and fascia',
'        External spermatic fascia',
'    Inguinal canal',
'      4 cm, running obliquely',
'      In males',
'        Spermatic cord',
'        Ilio-inguinal nerve',
'      In females',
'        Round ligament of the uterus',
'        Ilio-inguinal nerve',
'    Indirect inguinal hernia',
'      70% of all hernias',
'      The defect is the stretched deep inguinal ring',
'      The sac lies inside the cord',
'        May reach the scrotum',
'    Direct inguinal hernia',
'      Through the posterior wall of the canal',
'      Adult or elderly',
'      Reduces backwards',
'    Inferior epigastric vessels',
'      Sac lateral = indirect inguinal hernia',
'      Sac medial = direct inguinal hernia',
'    Internal ring test',
'      Positive (swelling controlled) = oblique',
'      Negative = direct',
'  #Femoral',
'    Femoral hernia',
'      More common in women',
'      Neck below and lateral to the pubic tubercle',
'      Femoral ring',
'        Inguinal ligament in front',
'        Pectineal ligament behind',
'        Femoral vein laterally',
'        Lacunar ligament medially',
'    Femoral canal',
'      Medial to the femoral vein',
'      1.25 cm long, cone shaped',
'    Saphena varix',
'      Disappears completely lying flat',
'      Fluid thrill on cough',
'      Venous hum',
'  Groin hernia',
'    Three to know',
'    The anatomy first',
'    The only treatment is surgery',
'  Hernia repair',
'    TAPP',
'      Transabdominal preperitoneal',
'    TEP',
'      Totally extraperitoneal',
'    Robotic-assisted repair',
'  Expansile cough impulse',
'    A hernia until shown otherwise',
'#Cardiology',
'  Heart failure',
'    Types',
'      HFrEF',
'        Ejection fraction 40% or less',
'      HFpEF',
'        Ejection fraction 50% or more',
'      Mildly reduced',
'        41 to 49%',
'    Causes',
'      Ischaemic heart disease',
'      Hypertension',
'      Valve disease',
'    Symptoms and signs',
'      Raised JVP',
'      Ankle swelling',
'      Basal crackles',
'    Treatment',
'      Loop diuretics for congestion',
'      Beta-blocker',
'      SGLT2 inhibitor',
'      Start low and titrate',
'  BNP',
'    Raised in heart failure',
'    A normal level makes it unlikely',
'  Loop diuretics',
'    Furosemide',
'      For congestion',
'    Watch hypokalaemia',
'  ACE inhibitors',
'    Prognostic in heart failure',
'    Cough',
'    Check hypokalaemia the other way',
'  Hypokalaemia',
'    Flat T waves',
'    U waves',
'    Risk with loop diuretics',
'  Acute coronary syndrome',
'    Unstable angina',
'    Troponin tells them apart',
'  STEMI',
'    ST elevation',
'    Primary PCI',
'  NSTEMI',
'    Raised troponin',
'    Without ST elevation',
'  Troponin',
'    Rises in 3 hours',
'    Peaks at 24',
'  Atrial fibrillation',
'    Irregularly irregular',
'    Rate or rhythm control',
'    CHA2DS2-VASc for anticoagulation',
'  CHA2DS2-VASc',
'    Stroke risk',
'  Murmurs',
'    Aortic stenosis',
'    Mitral regurgitation',
'    Heart failure as the end point',
'  Heart sounds',
'    S1 and S2',
'    Listen at the apex for S3',
'  Syncope',
'    Cardiac or not?',
'    Exertional syncope needs an echo',
"Richter's hernia",
'  Only part of the bowel wall is trapped',
'  Can strangulate without obstructing',
'  Most often through the femoral ring',
'Pericarditis',
'  Sharp chest pain',
'  Better sitting forward',
'  Saddle-shaped ST elevation',
'  PR depression'
];
// deeper than the store goes today: sub-ideas under ideas, a folder four down
const DEEP = ['#A', '  #B', '    #C', '      #D', '        Leaf one', '        Leaf two', '      Twig', '        Sub one', '        Sub two',
  '          Sub sub', '    Branch idea', '  Bough idea', 'Loose one', 'Loose two', 'Loose three'];
const SMALL = ['#Garden', '  Seeds', '  Fence', 'Call the plumber'];
const ONE = ['Only idea'];

const r9 = (x) => Math.round(x * 1e9) / 1e9;
const trees = [['sample', SAMPLE], ['deep', DEEP], ['small', SMALL], ['one', ONE]].map(([name, lines]) => {
  const T = outline(lines), L = layout(T), E = layout3d(T), arr = (a) => Array.from(a, r9);
  return {name, parent: T.parent, title: T.title, folder: T.folder,
    expect: {bx: arr(L.bx), by: arr(L.by), ang: arr(L.ang), len: arr(L.len), wid: arr(L.wid), px: arr(L.px), py: arr(L.py),
      nx: arr(L.nx), ny: arr(L.ny), bcx: arr(L.bcx), bcy: arr(L.bcy), brad: arr(L.brad), u0: arr(L.u0), u1: arr(L.u1), v0: arr(L.v0), v1: arr(L.v1)},
    expect3d: {base: arr(E.base), dir: arr(E.dir), len: arr(E.len), r0: arr(E.r0), r1: arr(E.r1), bend: arr(E.bend), anchor: arr(E.anchor), leaf: arr(E.leaf),
      sc: arr(E.sc), sr: arr(E.sr)}};
});
process.stdout.write(JSON.stringify({version: 1, k: K, trees}) + '\n');
