// The Fractal map's fixture, checked against the approved concept (S12, A2).
//
//   node tools/fractal_fixture.mjs path/to/nested-maps.html [--write]
//
// Runs the concept's own code (its constants, sample ideas, tree build and
// Fractal layout, makeF, seed 91, one random stream) and compares the tree
// and the layout with tools/fractal_fixture.json, which both platforms' tests
// read: Android's FractalCoreTest and iOS's FractalCoreTests. It prints the
// largest difference and fails above 2e-9. With --write it puts the concept's
// tree, links and layout into the fixture, keeping its frames (those come
// from Android's FractalFrame; see the fixture's "about").
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const [conceptPath, flag] = process.argv.slice(2);
if (!conceptPath) {
  console.error('usage: node tools/fractal_fixture.mjs path/to/nested-maps.html [--write]');
  process.exit(2);
}
const lines = fs.readFileSync(conceptPath, 'utf8').split('\n');
// lines 132 to 473 of the approved concept: constants, the ideas, the tree, small math and the layouts
const body = lines.slice(131, 473).join('\n');
for (const marker of ['function makeF', 'function rng', 'NAMES']) {
  if (!body.includes(marker)) {
    console.error(`the concept's lines 132-473 no longer hold ${marker}: is this the approved concept?`);
    process.exit(2);
  }
}
const prelude = "var Q = new URLSearchParams(''); var DEBUG=false, COMPARE=false, FLUSH=false; var STRESS=0; var REDUCED=false;\n";
const r = new Function(prelude + body + '\nreturn {NAMES, PAR, FOLD, FR, LA, LB};')();
const concept = {
  parent: Array.from(r.PAR),
  title: r.NAMES,
  links: Array.from(r.LA).map((a, k) => [a, r.LB[k]]),
  layout: Object.fromEntries(['x', 'y', 'sp', 'dr', 'rad', 'cx', 'cy', 'hw', 'hh'].map((k) => [k, Array.from(r.FR[k])])),
};

const here = path.dirname(fileURLToPath(import.meta.url));
const fixturePath = path.join(here, 'fractal_fixture.json');
const fx = JSON.parse(fs.readFileSync(fixturePath, 'utf8'));

let worst = 0;
let where = '';
const problems = [];
if (JSON.stringify(fx.tree.parent) !== JSON.stringify(concept.parent)) problems.push('tree.parent differs');
if (JSON.stringify(fx.tree.title) !== JSON.stringify(concept.title)) problems.push('tree.title differs');
if (JSON.stringify(fx.links) !== JSON.stringify(concept.links)) problems.push('links differ');
for (const [key, want] of Object.entries(concept.layout)) {
  const got = fx.layout[key];
  if (!got || got.length !== want.length) { problems.push(`layout.${key}: ${got ? got.length : 'missing'} values, the concept has ${want.length}`); continue; }
  for (let i = 0; i < want.length; i++) {
    const d = Math.abs(got[i] - want[i]);
    if (!(d <= worst)) { worst = d; where = `layout.${key}[${i}]`; }
  }
}

if (flag === '--write') {
  fx.tree.parent = concept.parent;
  fx.tree.title = concept.title;
  fx.links = concept.links;
  for (const [key, want] of Object.entries(concept.layout)) fx.layout[key] = want;
  fs.writeFileSync(fixturePath, JSON.stringify(fx) + '\n');
  console.log(`wrote ${fixturePath}`);
  process.exit(0);
}

console.log(`${concept.parent.length} parts, ${concept.links.length} links; largest layout difference ${worst.toExponential(3)}${where ? ' at ' + where : ''}`);
for (const p of problems) console.log(`problem: ${p}`);
process.exit(problems.length === 0 && worst <= 2e-9 ? 0 : 1);
