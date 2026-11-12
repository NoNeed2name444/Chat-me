package com.cramdown.sorta.map

// The Neurons theme's GLSL ES 3.0 (iOS GraphNeuronShaders.swift): the soma,
// wobble, arbor, sway, membrane with its mouths, membraneSway, halo, arrival and bridge bodies
// carried over line for line, with Metal's types as GLSL's (float3 as vec3, float2 as vec2,
// float4 as vec4, float4x4 as mat4, atan2 as atan) and SceneKit's inputs as this file's:
// _surface.normal vNormal, _surface.view -vPos, _surface.position vPos (all view space),
// _surface.diffuseTexcoord vUV, scn_node.modelViewTransform uMV, modelTransform[3].xyz uWorld,
// _geometry.position aPos (into rp_out), _geometry.normal aNormal, texcoords[0] aUV.
// GraphShaderKit.noise(out, p) is the same value noise as rpNoise (out = rpNoise(p)). The
// probe uniform (rpProbe) is left out. The endings (GraphSpaceShaders.glowEnd, lines 131-134)
// leave out the pow(2.2) into SceneKit's linear space, as Space's do, and take the depth fade
// (SpaceShaders.FADE's rpFadedGlow) with the Neurons band (NeuronDepth). Pure strings, so the
// JVM tests check their shape (NeuronLookTest).
object NeuronShaders {
    // iOS GraphNeuronShaders.soma, lines 76-229
    private const val SOMA_ARGS = """
uniform float rpClock;
uniform float rpMotion;
uniform float rpDetail;
uniform float rpNucleus;
uniform float rpState;
uniform float rpOpen;
uniform float rpFill;
uniform float rpMembrane;
uniform vec3 rpTintA;
uniform vec3 rpTintB;
uniform vec3 rpTintC;
"""
    private val SOMA_BODY = """
vec3 rp_N = normalize(vNormal);
vec3 rp_V = normalize((-vPos));
float rp_mu = clamp(dot(rp_N, rp_V), 0.0, 1.0);
mat4 rp_mv = uMV;
vec3 rp_ax = rp_mv[0].xyz;
vec3 rp_ay = rp_mv[1].xyz;
vec3 rp_az = rp_mv[2].xyz;
vec3 rp_c = rp_mv[3].xyz;
float rp_R = max(length(rp_ax), 0.0001);
float rp_R2 = rp_R * rp_R;
vec3 rp_d = normalize(vPos);
float rp_tc = dot(rp_c, rp_d);
vec3 rp_off = rp_d * rp_tc - rp_c;
float rp_q = clamp(dot(rp_off, rp_off) / rp_R2, 0.0, 1.0);
float rp_thick = sqrt(1.0 - rp_q);

float rp_r = sqrt(rp_q);
float rp_t = rpClock * rpMotion;
float rp_ph = dot(uWorld, vec3(1.7, 2.3, 1.1));
float rp_pz = fract(rp_ph * 0.13) * 40.0;
float rp_px = 1.0 / max(2000.0 * rp_R / max(-rp_c.z, 0.0001), 1.0);
float rp_fine = 1.0 - smoothstep(0.025, 0.07, rp_px);
float rp_tiny = 1.0 - smoothstep(0.016, 0.04, rp_px);
vec2 rp_sd = rp_off.xy / max(length(rp_off.xy), 0.00001);
float rp_lit = 0.5 + 0.5 * dot(rp_sd, vec2(-0.6, 0.8));
float rp_a = atan(rp_sd.y, rp_sd.x);
float rp_gel = rpFill * (1.0 - 0.8 * rpOpen);
float rp_fire = step(0.5, rpState) * step(rpState, 1.5);
float rp_vesk = step(1.5, rpState) * step(rpState, 2.5);
float rp_pace = step(2.5, rpState) * step(rpState, 3.5);
float rp_eat = step(4.5, rpState);
float rp_fl = 0.5 + 0.5 * sin(rp_t * 17.0 + rp_ph * 5.0) * sin(rp_t * 7.3 + rp_ph);
float rp_beat = fract((rp_t / 1.5 + fract(rp_ph * 0.37)) * 2.0);
float rp_bp = exp(-rp_beat * 6.0) * rpMotion;

float rp_inV = 1.0 - smoothstep(0.78, 0.99, rp_r);
vec3 rp_vio = mix(vec3(0.2, 0.07, 0.46) * 0.6, vec3(0.52, 0.24, 0.98) * 0.5, 0.15);
rp_vio = mix(rp_vio, rpTintA, 0.06);

float rp_mn = rpNoise(vec3(rp_sd * (rp_r * 5.0), rp_pz + rp_t * 0.05));
vec3 rp_col = rp_vio * (0.75 * rp_inV * rp_gel * (0.5 + rp_mn));

float rp_has = step(0.01, rpNucleus);
float rp_rn = mix(0.36 + 0.25 * rpNucleus, 0.6, rpOpen);
float rp_nr = rp_r / rp_rn;
float rp_aa = max(0.03, 1.5 * rp_px / rp_rn);
float rp_inN = (1.0 - smoothstep(1.0 - rp_aa, 1.0, rp_nr)) * rp_has;
vec3 rp_nN = vec3(rp_sd * rp_nr, sqrt(max(1.0 - rp_nr * rp_nr, 0.0)));
vec3 rp_L = normalize(vec3(-0.45, 0.6, 0.66));
vec3 rp_H = normalize(rp_L + rp_V);
float rp_dif = max(dot(rp_nN, rp_L), 0.0);
vec3 rp_pv = rp_nN * (rp_rn * rp_R);
vec3 rp_lq = vec3(dot(rp_pv, rp_ax), dot(rp_pv, rp_ay), dot(rp_pv, rp_az)) / rp_R2;
float rp_c1 = rpNoise(rp_lq * 7.0 + rp_pz);
float rp_c2 = rpNoise(rp_lq * 16.0 + 3.1 + rp_pz);
float rp_chrom = 0.6 * rp_c1 + 0.4 * rp_c2;
float rp_dark = clamp(0.8 * smoothstep(0.44, 0.66, rp_chrom) + 0.7 * smoothstep(0.62, 0.97, rp_nr) * smoothstep(0.4, 0.62, rp_c2), 0.0, 1.0);
vec3 rp_ncol = mix(vec3(0.52, 0.24, 0.98) * 0.62, vec3(0.2, 0.07, 0.46) * 0.75, rp_dark) * ((0.22 + 0.78 * rp_dif) * (0.85 + 0.3 * rp_c2) * (1.0 - 0.62 * rpOpen));
vec2 rp_o = (rp_sd * rp_nr - vec2(0.3, 0.2)) / 0.32;
float rp_or = length(rp_o);
float rp_inO = 1.0 - smoothstep(1.0 - rp_aa / 0.32, 1.0, rp_or);
vec3 rp_oN = vec3(rp_o, sqrt(max(1.0 - rp_or * rp_or, 0.0)));
vec3 rp_ocol = rpTintB * (0.42 * (0.3 + 0.7 * max(dot(rp_oN, rp_L), 0.0)) * (0.8 + 0.4 * rp_c2));
rp_ncol = mix(rp_ncol, rp_ocol, rp_inO * 0.9 * (1.0 - rpOpen));
float rp_ew = max(0.035, 1.2 * rp_px / rp_rn);
float rp_env = 1.0 - smoothstep(0.0, rp_ew, abs(rp_nr - 1.0 + rp_ew));
float rp_pore = 0.55 + 0.45 * smoothstep(-0.2, 0.4, cos(rp_a * 26.0 + 2.0 * rp_c1));
rp_col = mix(rp_col, rp_ncol, rp_inN);
rp_col = rp_col + vec3(0.78, 0.66, 1.0) * (0.85 * rp_env * rp_pore * (0.55 + 0.45 * rp_lit) * rp_has);
rp_col = rp_col + vec3(0.85, 0.8, 1.0) * ((0.22 - 0.14 * rpOpen) * pow(max(dot(rp_nN, rp_H), 0.0), 40.0) * rp_inN);
float rp_cyto = 1.0 - rp_has * (1.0 - smoothstep(rp_rn + 0.01, rp_rn + 0.06, rp_r));

float rp_warm = smoothstep(0.45, 0.85, rp_r);
float rp_sa = (rp_a + 3.14159265 + rp_t * 0.02) * 9.549297;
float rp_sr = rp_r * 26.0;
vec2 rp_sc = floor(vec2(rp_sa, rp_sr));
float rp_sk = fract(sin(dot(rp_sc, vec2(12.9898, 78.233)) + rp_pz) * 43758.5453);
float rp_du = (rp_sa - rp_sc.x - fract(rp_sk * 7.13) * 0.4 - 0.3) * 0.10471976 * rp_r;
float rp_dv = (rp_sr - rp_sc.y - fract(rp_sk * 3.71) * 0.5 - 0.25) * 0.0384615;
float rp_sl = length(vec2(rp_du / max(0.028, 3.0 * rp_px), rp_dv / max(0.0065, rp_px)));
float rp_stroke = (1.0 - smoothstep(0.3, 1.0, rp_sl)) * step(0.72 - 0.3 * rp_warm, rp_sk);
rp_stroke = rp_stroke * smoothstep(0.12, 0.3, rp_r) * (1.0 - smoothstep(0.93, 0.99, rp_r)) * (0.5 + 0.5 * sin(rp_t * 1.3 + rp_sk * 50.0));
vec3 rp_stc = mix(vec3(0.7, 0.5, 1.0), vec3(1.0, 0.6, 0.22), rp_warm);
rp_col = rp_col + rp_stc * (1.1 * rp_stroke * rp_cyto * rp_gel * rp_tiny * rpDetail * (1.0 - rpOpen));

float rp_dots = 0.0;
for (int rp_k = 0; rp_k < 3; rp_k++) {
    vec3 rp_pt = rp_off + rp_d * ((float(rp_k) - 1.0) * 0.5 * rp_thick * rp_R);
    vec3 rp_lp = vec3(dot(rp_pt, rp_ax), dot(rp_pt, rp_ay), dot(rp_pt, rp_az)) / rp_R2;
    vec3 rp_g = rp_lp * 9.0 + vec3(float(rp_k) * 3.7, rp_t * 0.06, 0.0);
    vec3 rp_gi = floor(rp_g);
    float rp_gh = fract(sin(dot(rp_gi, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
    vec3 rp_gj = vec3(rp_gh, fract(rp_gh * 7.13), fract(rp_gh * 3.71)) * 0.5 + vec3(0.25, 0.25, 0.25);
    float rp_gl = length(rp_g - rp_gi - rp_gj);
    float rp_gd = 1.0 - smoothstep(0.0, 0.3, rp_gl);
    rp_gd = rp_gd * rp_gd + 2.0 * (1.0 - smoothstep(0.0, 0.07, rp_gl));
    rp_dots = rp_dots + rp_gd * step(0.68, rp_gh) * (0.6 + 0.4 * sin(rp_t * 1.7 + rp_gh * 40.0));
}
vec3 rp_spark = mix(vec3(0.88, 0.74, 1.0), vec3(1.0, 0.8, 0.5), rp_warm);
rp_col = rp_col + rp_spark * (0.9 * rp_dots * rp_cyto * rp_inV * rp_gel * (1.0 - rpOpen) * rp_tiny * rpDetail);

float rp_gn = rpNoise(vec3(rp_sd * 1.9, rp_pz + rp_t * 0.04));
float rp_fn = rpNoise(vec3(rp_sd * 2.6, rp_r * 18.0 - rp_t * 0.12 + rp_pz));
float rp_fm = rpNoise(vec3(rp_sd * 6.0 + 7.0, rp_r * 8.0 + rp_t * 0.07 + rp_pz));
float rp_ridge = 1.0 - abs(2.0 * rp_fn - 1.0);
rp_ridge = rp_ridge * rp_ridge * rp_ridge;
float rp_ridge2 = 1.0 - abs(2.0 * rp_fm - 1.0);
float rp_fib = max(rp_ridge, 0.7 * rp_ridge2 * rp_ridge2);
float rp_fib2 = rp_fib * rp_fib;
float rp_band = smoothstep(0.62, 0.97, rp_r) * (1.0 - 0.5 * smoothstep(0.985, 1.0, rp_r)) * (1.0 - rpMembrane * smoothstep(0.88, 1.0, rp_r));
float rp_lobe = smoothstep(0.25, 0.72, rp_gn);
float rp_haze = rp_band * rp_lobe;
float rp_fil = rp_band * (0.2 + 0.8 * rp_lobe) * rp_fib2 * rp_fine;
float rp_crest = rp_haze * rp_fib2 * rp_fib2 * rp_fine;
float rp_dim = (1.0 - 0.3 * rpOpen) * mix(0.6, 1.0, rpFill);
rp_col = rp_col + vec3(1.0, 0.42, 0.08) * (0.4 * rp_haze * rp_dim);
rp_col = rp_col + vec3(1.0, 0.62, 0.2) * (rp_fil * rp_dim);
rp_col = rp_col + vec3(1.0, 0.93, 0.75) * (0.75 * rp_crest * rp_dim);

float rp_pa = (rp_a + 3.14159265) * 15.278875;
float rp_pr = rp_r * 22.0;
vec2 rp_pc = floor(vec2(rp_pa, rp_pr));
float rp_pk = fract(sin(dot(rp_pc, vec2(12.9898, 78.233)) + rp_pz) * 43758.5453);
vec2 rp_pj = vec2(fract(rp_pk * 7.13), fract(rp_pk * 3.71)) * 0.5 + vec2(0.25, 0.25);
float rp_pd = 1.0 - smoothstep(0.0, 0.4, length(vec2(rp_pa, rp_pr) - rp_pc - rp_pj));
float rp_glint = rp_pd * rp_pd * step(0.72 - 0.35 * rp_vesk, rp_pk) * (0.55 + 0.45 * sin(rp_t * 2.1 + rp_pk * 60.0));
rp_col = rp_col + rpTintC * (2.4 * rp_glint * smoothstep(0.5, 0.85, rp_r) * rp_tiny);

float rp_rim = pow(1.0 - rp_mu, 7.0) * smoothstep(0.0, 0.12, rp_mu);
rp_col = rp_col + mix(vec3(0.66, 0.84, 1.0), rpTintA, 0.08) * (2.4 * rp_rim * (0.6 + 0.4 * rp_lit) * (1.0 - rpMembrane));
rp_col = rp_col + vec3(0.7, 0.45, 0.9) * (0.12 * (1.0 - rp_mu) * (1.0 - rp_mu) * (1.0 - smoothstep(0.9, 1.0, rp_r)));
rp_col = rp_col + vec3(0.8, 0.95, 1.0) * (0.4 * pow(max(dot(rp_N, rp_H), 0.0), 60.0));

rp_col = rp_col * (1.0 + rp_fire * (0.25 + 0.3 * rp_fl) + rp_pace * 0.6 * rp_bp);
rp_col = rp_col * (1.0 - rp_eat * 0.75 * rp_inV * smoothstep(0.1, 0.6, 1.0 - rp_r * 1.6));
rp_col = rp_col * (1.0 + 0.06 * sin(rp_t * 0.9 + rp_ph));
"""

    // iOS GraphNeuronShaders.wobble, lines 233-246
    private const val WOBBLE_ARGS = """
uniform float rpSway;
uniform float rpWobble;
"""
    private val WOBBLE_BODY = """
vec3 rp_p = aPos;
float rp_ph = dot(uWorld, vec3(1.3, 0.7, 1.9));
float rp_a = sin(rp_p.x * 3.1 + rpSway * 1.3 + rp_ph);
float rp_b = sin(rp_p.y * 2.7 - rpSway * 0.9 + rp_ph * 0.5);
float rp_c = sin(rp_p.z * 3.7 + rpSway * 1.1 + rp_p.x * 1.3);
float rp_w = (rp_a * rp_b * 0.6 + rp_c * 0.4) * rpWobble;
rp_out = rp_p + aNormal * rp_w;
"""

    // iOS GraphNeuronShaders.arbor, lines 260-316
    private const val ARBOR_ARGS = """
uniform float rpClock;
uniform float rpMotion;
uniform float rpDetail;
uniform vec3 rpTintA;
"""
    private val ARBOR_BODY = """
float rp_t = rpClock * rpMotion;
vec3 rp_N = normalize(vNormal);
vec3 rp_V = normalize((-vPos));
float rp_mu = abs(dot(rp_N, rp_V));
float rp_s = vUV.x;
float rp_v = vUV.y;
mat4 rp_mv = uMV;
vec3 rp_c = rp_mv[3].xyz;
float rp_R = max(length(rp_mv[0].xyz), 0.0001);
vec3 rp_d = normalize(vPos);
vec3 rp_off = rp_d * dot(rp_c, rp_d) - rp_c;
float rp_hide = smoothstep(0.8, 0.97, length(rp_off) / rp_R);
float rp_px = 1.0 / max(2000.0 * rp_R / max(-rp_c.z, 0.0001), 1.0);
float rp_tiny = 1.0 - smoothstep(0.016, 0.04, rp_px);
float rp_rest = 1.0 - rp_mu;
float rp_edge = rp_rest * rp_rest * smoothstep(0.0, 0.3, rp_mu);
vec3 rp_col = vec3(0.62, 0.82, 1.0) * (1.6 * rp_edge);
rp_col = rp_col + vec3(0.24, 0.2, 0.6) * (0.18 * rp_mu);
float rp_ang = rp_v * 6.2831853;
float rp_fn = rpNoise(vec3(cos(rp_ang) * 2.6, sin(rp_ang) * 2.6, rp_s * 2.5 - rp_t * 0.15));
float rp_fil = 1.0 - abs(2.0 * rp_fn - 1.0);
float rp_fil2 = rp_fil * rp_fil;
rp_fil = rp_fil2 * rp_fil2 * rp_fil;
float rp_warm = 1.0 - smoothstep(0.05, 0.85, rp_s);
vec3 rp_gold = mix(vec3(1.0, 0.5, 0.12), vec3(1.0, 0.86, 0.55), rp_fil);
rp_col = rp_col + rp_gold * (rp_warm * rp_mu * (0.12 + 1.3 * rp_fil * rpDetail));
float rp_pa = rp_v * 14.0;
float rp_pr = rp_s * 30.0 - rp_t * 0.6;
float rp_pcx = floor(rp_pa);
float rp_pcy = floor(rp_pr);
float rp_pk = fract(sin(rp_pcx * 12.9898 + rp_pcy * 78.233) * 43758.5453);
float rp_pjx = fract(rp_pk * 7.13) * 0.5 + 0.25;
float rp_pjy = fract(rp_pk * 3.71) * 0.5 + 0.25;
float rp_pd = 1.0 - smoothstep(0.0, 0.35, length(vec2(rp_pa - rp_pcx - rp_pjx, rp_pr - rp_pcy - rp_pjy)));
float rp_part = rp_pd * rp_pd * step(0.7, rp_pk) * rp_mu;
rp_col = rp_col + vec3(1.0, 0.62, 0.2) * (1.6 * rp_part * (1.0 - smoothstep(0.2, 0.95, rp_s)) * rpDetail * rp_tiny);
vec3 rp_rel = vPos - rp_c;
vec3 rp_lp = vec3(dot(rp_rel, rp_mv[0].xyz), dot(rp_rel, rp_mv[1].xyz), dot(rp_rel, rp_mv[2].xyz)) / (rp_R * rp_R);
vec3 rp_g = rp_lp * 12.0;
vec3 rp_gi = floor(rp_g);
float rp_gh = fract(sin(dot(rp_gi, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
vec3 rp_gj = vec3(rp_gh, fract(rp_gh * 7.13), fract(rp_gh * 3.71)) * 0.5 + vec3(0.25, 0.25, 0.25);
float rp_gd = 1.0 - smoothstep(0.0, 0.3, length(rp_g - rp_gi - rp_gj));
rp_col = rp_col + vec3(1.0, 0.9, 0.62) * (1.4 * rp_gd * rp_gd * step(0.86, rp_gh) * (1.0 - 0.6 * rp_s) * rpDetail * rp_tiny);
rp_col = rp_col + rpTintA * (0.6 * rp_mu * (1.0 - smoothstep(0.0, 0.3, rp_s)));
rp_col = rp_col * (smoothstep(0.0, 0.12, rp_s) * (1.0 - smoothstep(0.6, 1.0, rp_s)) * rp_hide);
"""

    // iOS GraphNeuronShaders.sway, lines 319-333
    private const val SWAY_ARGS = """
uniform float rpSway;
uniform float rpWobble;
"""
    private val SWAY_BODY = """
vec3 rp_p = aPos;
float rp_r = length(rp_p);
float rp_ph = dot(uWorld, vec3(1.3, 0.7, 1.9));
float rp_sx = sin(rpSway * 0.8 + rp_p.y * 1.7 + rp_ph);
float rp_sy = sin(rpSway * 0.6 + rp_p.z * 1.9 + rp_ph * 1.3);
float rp_sz = sin(rpSway * 0.7 + rp_p.x * 1.5 + rp_ph * 0.7);
vec3 rp_s = vec3(rp_sx, rp_sy, rp_sz) * (rpWobble * 2.5 * rp_r);
rp_out = rp_p + rp_s;
"""

    // iOS GraphNeuronShaders.membrane, lines 358-424
    private const val MEMBRANE_ARGS = """
uniform float rpClock;
uniform float rpMotion;
uniform float rpDetail;
uniform vec3 rpTintA;
uniform vec4 rpMouth0;
uniform vec4 rpMouth1;
uniform vec4 rpMouth2;
uniform vec4 rpMouth3;
"""
    private val MEMBRANE_BODY = """
float rp_t = rpClock * rpMotion;
vec3 rp_N = normalize(vNormal);
vec3 rp_V = normalize((-vPos));
float rp_mu = abs(dot(rp_N, rp_V));
float rp_s = vUV.x;
float rp_w = clamp(vUV.y, 0.0, 1.0);
float rp_den = 1.0 - rp_w;
mat4 rp_mv = uMV;
vec3 rp_c = rp_mv[3].xyz;
float rp_R = max(length(rp_mv[0].xyz), 0.0001);
float rp_px = 1.0 / max(2000.0 * rp_R / max(-rp_c.z, 0.0001), 1.0);
float rp_tiny = 1.0 - smoothstep(0.016, 0.04, rp_px);
vec3 rp_rel = vPos - rp_c;
vec3 rp_lp = vec3(dot(rp_rel, rp_mv[0].xyz), dot(rp_rel, rp_mv[1].xyz), dot(rp_rel, rp_mv[2].xyz)) / (rp_R * rp_R);
vec2 rp_sd = rp_rel.xy / max(length(rp_rel.xy), 0.00001);
float rp_lit = 0.5 + 0.5 * dot(rp_sd, vec2(-0.6, 0.8));
float rp_rest = 1.0 - rp_mu;
float rp_soft = 1.6 * rp_rest * rp_rest * smoothstep(0.0, 0.3, rp_mu);
float rp_crisp = 2.4 * pow(rp_rest, 7.0) * smoothstep(0.0, 0.12, rp_mu) * (0.6 + 0.4 * rp_lit);

$MOUTHS
vec3 rp_col = mix(vec3(0.62, 0.82, 1.0), vec3(0.66, 0.84, 1.0), rp_w) * (mix(rp_soft, rp_crisp, rp_w) * (1.0 - rp_open));
rp_col = rp_col + vec3(0.24, 0.2, 0.6) * (0.18 * rp_mu * rp_den);
float rp_rho = max(length(rp_lp.xy), 0.0001);
vec2 rp_az = rp_lp.xy / rp_rho;
float rp_fn = rpNoise(vec3(rp_az * 14.0, rp_s * 2.5 - rp_t * 0.15));
float rp_fil = 1.0 - abs(2.0 * rp_fn - 1.0);
float rp_fil2 = rp_fil * rp_fil;
rp_fil = rp_fil2 * rp_fil2 * rp_fil;
float rp_warm = 1.0 - smoothstep(0.05, 0.85, rp_s);
vec3 rp_gold = mix(vec3(1.0, 0.5, 0.12), vec3(1.0, 0.86, 0.55), rp_fil);
rp_col = rp_col + rp_gold * (rp_warm * rp_mu * rp_den * (0.12 + 1.3 * rp_fil * rpDetail));
float rp_pa = (atan(rp_az.y, rp_az.x) + 3.14159265) * 15.915494;
float rp_pr = rp_s * 30.0 - rp_t * 0.6;
float rp_pcx = floor(rp_pa);
float rp_pcy = floor(rp_pr);
float rp_pk = fract(sin(rp_pcx * 12.9898 + rp_pcy * 78.233) * 43758.5453);
float rp_pjx = fract(rp_pk * 7.13) * 0.5 + 0.25;
float rp_pjy = fract(rp_pk * 3.71) * 0.5 + 0.25;
float rp_pd = 1.0 - smoothstep(0.0, 0.35, length(vec2(rp_pa - rp_pcx - rp_pjx, rp_pr - rp_pcy - rp_pjy)));
float rp_part = rp_pd * rp_pd * step(0.7, rp_pk) * rp_mu * rp_den;
rp_col = rp_col + vec3(1.0, 0.62, 0.2) * (1.6 * rp_part * (1.0 - smoothstep(0.2, 0.95, rp_s)) * rpDetail * rp_tiny);
vec3 rp_g = rp_lp * 12.0;
vec3 rp_gi = floor(rp_g);
float rp_gh = fract(sin(dot(rp_gi, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
vec3 rp_gj = vec3(rp_gh, fract(rp_gh * 7.13), fract(rp_gh * 3.71)) * 0.5 + vec3(0.25, 0.25, 0.25);
float rp_gd = 1.0 - smoothstep(0.0, 0.3, length(rp_g - rp_gi - rp_gj));
rp_col = rp_col + vec3(1.0, 0.9, 0.62) * (1.4 * rp_gd * rp_gd * step(0.86, rp_gh) * (1.0 - 0.6 * rp_s) * rp_den * rpDetail * rp_tiny);
rp_col = rp_col + rpTintA * (0.6 * rp_mu * rp_den * (1.0 - smoothstep(0.0, 0.3, rp_s)));
vec3 rp_H = normalize(normalize(vec3(-0.45, 0.6, 0.66)) + rp_V);
rp_col = rp_col + vec3(0.8, 0.95, 1.0) * (0.3 * pow(max(dot(rp_N, rp_H), 0.0), 60.0) * rp_den);
rp_col = rp_col * (1.0 - smoothstep(0.6, 1.0, rp_s));
"""

    // iOS GraphNeuronShaders.membraneSway, lines 478-497
    private const val MEMBRANE_SWAY_ARGS = """
uniform float rpSway;
uniform float rpWobble;
"""
    private val MEMBRANE_SWAY_BODY = """
vec3 rp_p = aPos;
float rp_w = clamp(aUV.y, 0.0, 1.0);
float rp_r = length(rp_p);
float rp_ph = dot(uWorld, vec3(1.3, 0.7, 1.9));
float rp_a = sin(rp_p.x * 3.1 + rpSway * 1.3 + rp_ph);
float rp_b = sin(rp_p.y * 2.7 - rpSway * 0.9 + rp_ph * 0.5);
float rp_c = sin(rp_p.z * 3.7 + rpSway * 1.1 + rp_p.x * 1.3);
float rp_br = (rp_a * rp_b * 0.6 + rp_c * 0.4) * rpWobble * rp_w;
float rp_sx = sin(rpSway * 0.8 + rp_p.y * 1.7 + rp_ph);
float rp_sy = sin(rpSway * 0.6 + rp_p.z * 1.9 + rp_ph * 1.3);
float rp_sz = sin(rpSway * 0.7 + rp_p.x * 1.5 + rp_ph * 0.7);
vec3 rp_s = vec3(rp_sx, rp_sy, rp_sz) * (rpWobble * 4.0 * max(rp_r - 0.9, 0.0) * (1.0 - rp_w));
rp_out = rp_p + aNormal * rp_br + rp_s;
"""

    // iOS GraphNeuronShaders.halo, lines 519-605
    private const val HALO_ARGS = """
uniform float rpClock;
uniform float rpMotion;
uniform float rpGain;
uniform float rpState;
uniform vec3 rpTintA;
uniform vec3 rpTintC;
"""
    private val HALO_BODY = """
$PLANE
float rp_t = rpClock * rpMotion;
float rp_e = 0.4;
float rp_out = max(rp_r - rp_e, 0.0);
float rp_outside = step(rp_e, rp_r);
float rp_soft = exp(-rp_out / 0.08) * 0.13;
float rp_wide = exp(-rp_out / 0.26) * 0.07;
float rp_within = 0.2 * smoothstep(0.75 * rp_e, rp_e, rp_r);
float rp_lum = (rp_soft + rp_wide) * rp_outside + rp_within * (1.0 - rp_outside);
vec3 rp_col = rpTintA * (rp_lum * rpGain);
float rp_ph = dot(uWorld, vec3(1.7, 2.3, 1.1));
float rp_a = atan(rp_qy, rp_qx);
if (rpState > 0.5 && rpState < 1.5) {
float rp_sp = fract(rp_t * 0.9 + fract(rp_ph));
float rp_rd0 = (rp_r - rp_e - rp_sp * 0.55) / 0.025;
float rp_ring = exp(-rp_rd0 * rp_rd0) * (1.0 - rp_sp) * rp_outside;
float rp_rw = 0.5 + 0.5 * sin(rp_a * 11.0 + rp_ph * 3.0 + sin(rp_a * 3.0 + rp_t) * 1.5);
float rp_rays = rp_rw * rp_rw * rp_rw;
rp_rays = rp_rays * rp_rays;
float rp_flick = 0.75 + 0.25 * sin(rp_t * 17.0 + rp_ph * 5.0) * sin(rp_t * 7.3 + rp_ph);
rp_col = rp_col + rpTintA * (rp_rays * exp(-rp_out / 0.22) * 0.22 * rp_flick * rp_outside);
rp_col = rp_col + vec3(1.0, 0.85, 0.55) * (rp_ring * 0.55);
} else if (rpState > 1.5 && rpState < 2.5) {

float rp_cn = rpNoise(vec3(cos(rp_a) * 2.0, sin(rp_a) * 2.0, rp_r * 4.0 - rp_t * 0.25));
float rp_band = 0.5 + 0.5 * sin(rp_r * 24.0 - rp_t * 0.8 + rp_cn * 4.0);
float rp_cloud = exp(-rp_out / 0.33) * (0.18 + 0.4 * rp_band * rp_cn) * rp_outside;
float rp_vq = rp_r * 12.0 - rp_t * 0.5;
float rp_vi = floor(rp_vq);
float rp_vw = (rp_a + 3.14159) * 3.0;
float rp_vh = fract(sin(rp_vi * 12.9898 + floor(rp_vw) * 78.233 + rp_ph) * 43758.5453);
float rp_vf = fract(rp_vq) - 0.5;
float rp_vg = fract(rp_vw) - 0.5;
float rp_ves = exp(-(rp_vf * rp_vf + rp_vg * rp_vg) * 60.0) * step(0.62, rp_vh) * rp_outside;
rp_col = rp_col + mix(rpTintA, vec3(1.0, 0.78, 0.45), 0.35) * rp_cloud;
rp_col = rp_col + vec3(1.0, 0.9, 0.72) * (rp_ves * 0.45 * exp(-rp_out / 0.4));
} else if (rpState > 2.5 && rpState < 3.5) {
float rp_turn = rp_t / 1.5 + fract(rp_ph * 0.37);
float rp_bt = fract(rp_turn * 2.0);
float rp_wd = (rp_r - rp_e - rp_bt * 0.5) / 0.02;
float rp_wave = exp(-rp_wd * rp_wd) * (1.0 - rp_bt) * rp_outside;
float rp_lc = cos(rp_a - rp_turn * 6.2831853);
float rp_lobe = rp_lc * rp_lc;
rp_lobe = rp_lobe * rp_lobe * rp_lobe;
rp_lobe = rp_lobe * rp_lobe * exp(-rp_out / 0.3) * rp_outside;
rp_col = rp_col * (1.0 + 0.8 * exp(-rp_bt * 6.0) * rpMotion);
rp_col = rp_col + vec3(0.75, 0.85, 1.0) * (rp_wave * 0.65 + rp_lobe * 0.5);
} else if (rpState > 3.5 && rpState < 4.5) {
float rp_fw = 0.5 + 0.5 * sin(rp_a * 9.0 + sin(rp_t * 0.7 + rp_ph) * 2.0);
float rp_fil = rp_fw * rp_fw * rp_fw;
rp_fil = rp_fil * rp_fil;
rp_col = rp_col + rpTintA * (rp_fil * exp(-rp_out / 0.12) * 0.4 * rp_outside);
} else if (rpState > 4.5) {
float rp_lr = log(max(rp_r, 0.05));
float rp_spin = rp_a + rp_lr * 3.0 + rp_t * 0.9;
float rp_ac = 0.5 + 0.5 * cos(rp_spin * 2.0);
float rp_arm = rp_ac * rp_ac * rp_ac;
rp_arm = rp_arm * rp_arm;
float rp_fall = exp(-rp_out / 0.3) * rp_outside;
float rp_dq = rp_lr * 9.0 + rp_t * 1.3;
float rp_dg = fract(rp_dq) - 0.5;
float rp_dw = (rp_spin + 3.14159) * 2.5;
float rp_dh = fract(sin(floor(rp_dq) * 7.13 + floor(rp_dw) * 3.7) * 43758.5453);
float rp_da = fract(rp_dw) - 0.5;
float rp_deb = exp(-(rp_dg * rp_dg + rp_da * rp_da) * 40.0) * step(0.55, rp_dh) * rp_fall;
float rp_rr = (rp_r - rp_e * 1.06) / 0.012;
float rp_lip = exp(-rp_rr * rp_rr);
rp_col = rp_col + vec3(0.95, 0.6, 1.0) * (rp_arm * rp_fall * 0.3 + rp_deb * 0.6 + rp_lip * 0.45);
}
float rp_fade = clamp((1.0 - rp_r) / 0.25, 0.0, 1.0);
rp_col = rp_col * rp_fade;
float rp_rd = (rp_r - 0.47) / 0.014;
rp_col = rp_col + rpTintC * (exp(-rp_rd * rp_rd) * rp_fade);
"""

    // iOS GraphNeuronShaders.arrival, lines 611-626
    private const val ARRIVAL_ARGS = """
uniform vec3 rpTintA;
uniform vec3 rpTintB;
"""
    private val ARRIVAL_BODY = """
$PLANE
float rp_e = 0.286;
float rp_out = max(rp_r - rp_e, 0.0);
float rp_fade = clamp((1.0 - rp_r) / 0.3, 0.0, 1.0);
vec3 rp_col = rpTintA * (exp(-rp_out / 0.12) * rp_fade * 0.4);
rp_col = rp_col + rpTintB * (exp(-rp_r / 0.18) * 0.5);
"""

    // iOS GraphNeuronShaders.bridge, lines 907-1003
    private const val BRIDGE_ARGS = """
uniform float rpClock;
uniform float rpMotion;
uniform float rpDetail;
uniform float rpRate;
uniform float rpBurst;
uniform float rpHalf;
uniform vec3 rpTintA;
uniform vec3 rpTintB;
uniform vec3 rpTintC;
"""
    private val BRIDGE_BODY = """
vec2 rp_uv = vUV;
float rp_code = floor(rp_uv.x / 64.0);
float rp_along = max(rp_uv.x - rp_code * 64.0 - 1.0, 0.0);
float rp_wk = floor(rp_code / 32.0);
float rp_seed = rp_code - 32.0 * rp_wk;
float rp_hw = rpHalf * 0.12 * exp(rp_wk * 0.300105);
float rp_k = floor(rp_uv.y);
float rp_q2 = floor(rp_k * 0.5);
float rp_lit = rp_k - 2.0 * rp_q2;
float rp_len = max(rp_q2 / 16.0 + 0.03125, 0.05);
float rp_s = clamp(fract(rp_uv.y) * 2.0 - 1.0, -1.0, 1.0);
float rp_t = rpClock * rpMotion;
float rp_dE = max(rp_len - rp_along, 0.0);
float rp_end = min(rp_along, rp_dE);

float rp_mu = sqrt(max(1.0 - rp_s * rp_s, 0.0));
float rp_rest = 1.0 - rp_mu;
float rp_w = 1.0 - smoothstep(0.0, 2.0 * rp_hw, rp_end);
float rp_soft = 1.6 * rp_rest * rp_rest * smoothstep(0.0, 0.3, rp_mu);
float rp_crisp = 1.9 * pow(rp_rest, 7.0) * smoothstep(0.0, 0.12, rp_mu);
vec3 rp_col = mix(vec3(0.62, 0.82, 1.0), vec3(0.66, 0.84, 1.0), rp_w) * mix(rp_soft, rp_crisp, rp_w);
float rp_body = smoothstep(0.0, 1.2 * rp_hw, rp_end);
rp_col = rp_col + vec3(0.24, 0.2, 0.6) * (0.18 * rp_mu * rp_body);
float rp_warm = 1.0 - smoothstep(0.5 * rp_hw, 5.0 * rp_hw, rp_end);
float rp_fa = asin(rp_s);

float rp_fn = rpNoise(vec3(cos(rp_fa) * 2.6, sin(rp_fa) * 2.6, rp_along / (1.6 * rp_hw) - rp_t * 0.15));
float rp_fil = 1.0 - abs(2.0 * rp_fn - 1.0);
float rp_fil2 = rp_fil * rp_fil;
rp_fil = rp_fil2 * rp_fil2 * rp_fil;
vec3 rp_gold = mix(vec3(1.0, 0.5, 0.12), vec3(1.0, 0.86, 0.55), rp_fil);
float rp_glint = 0.02 + rp_warm * (0.1 + 1.1 * rp_fil * rpDetail) + 0.12 * rp_fil * rpDetail;
rp_col = rp_col + rp_gold * (rp_glint * rp_mu * rp_body);
vec2 rp_kc = vec2((rp_along - rp_t * 0.08) / (rp_hw * 0.8), rp_s * 2.0 + 2.0);
vec2 rp_ki = floor(rp_kc);
float rp_kh = fract(sin(dot(rp_ki, vec2(12.9898, 78.233)) + rp_seed * 3.1) * 43758.5453);
vec2 rp_kf = rp_kc - rp_ki - (vec2(fract(rp_kh * 7.13), fract(rp_kh * 3.71)) * 0.5 + vec2(0.25, 0.25));
float rp_kd = 1.0 - smoothstep(0.0, 0.3, length(rp_kf));
rp_col = rp_col + vec3(1.0, 0.88, 0.55) * (0.9 * rp_kd * rp_kd * step(0.82, rp_kh) * (0.6 + 0.4 * sin(rp_t * 2.3 + rp_kh * 50.0)) * rpDetail * rp_body);
rp_col = rp_col + rpTintA * (0.6 * rp_mu * rp_body * (1.0 - smoothstep(0.0, 3.0 * rp_hw, rp_end)));

uint rp_su = uint(rp_seed);
uint rp_h = rp_su * 747796405u + 2891336453u;
rp_h = ((rp_h >> ((rp_h >> 28u) + 4u)) ^ rp_h) * 277803737u;
rp_h = (rp_h >> 22u) ^ rp_h;
float rp_P = 0.9 + 1.5 * (float(rp_h & 255u) / 255.0);
float rp_D = 0.45 + 0.5 * (float((rp_h >> 8u) & 255u) / 255.0);
float rp_ph = (float((rp_h >> 16u) & 255u) / 255.0) * rp_P;
float rp_tt = rp_t + rp_ph;
float rp_k0 = floor(rp_tt / rp_P);
float rp_pulse = 0.0;
int rp_nb = rpBurst > 0.0 ? 3 : 1;
for (int rp_j = 0; rp_j < 3; rp_j++) {
    float rp_ks = rp_k0 - float(rp_j);
    uint rp_g = uint(max(rp_ks, 0.0)) * 2654435761u + rp_su * 40503u + 17u;
    rp_g = rp_g * 747796405u + 2891336453u;
    rp_g = ((rp_g >> ((rp_g >> 28u) + 4u)) ^ rp_g) * 277803737u;
    rp_g = (rp_g >> 22u) ^ rp_g;
    float rp_fire = 1.0 - step(rpRate, float(rp_g & 255u) / 255.0);
    float rp_at = float((rp_g >> 8u) & 255u) / 255.0;
    float rp_tf = rp_ks * rp_P + 0.5 * rp_P * rp_at;
    float rp_many = 1.0 - step(rpBurst, float((rp_g >> 16u) & 255u) / 255.0);
    for (int rp_b = 0; rp_b < rp_nb; rp_b++) {
        float rp_on = rp_b == 0 ? rp_fire : rp_fire * rp_many;
        float rp_age = rp_tt - rp_tf - float(rp_b) * 0.12;
        float rp_pos = rp_age / rp_D;
        float rp_dx = rp_along - rp_pos * rp_len;
        float rp_fr = rp_dx / 0.08;
        float rp_head = exp(-rp_fr * rp_fr);
        float rp_tail = exp(rp_dx / 0.45);
        float rp_spk = rp_dx > 0.0 ? rp_head : rp_tail;
        float rp_fly = step(0.0, rp_age) * (1.0 - smoothstep(1.0, 1.15, rp_pos));
        rp_pulse = rp_pulse + rp_on * rp_spk * rp_fly;
    }
}
float rp_still = (1.0 - rpMotion) * rp_lit;
float rp_imp = max(min(rp_pulse * rpMotion, 1.6), 0.5 * rp_still);
float rp_core = exp(-rp_s * rp_s * 1.5);
rp_col = rp_col + rpTintB * (rp_imp * (0.95 * rp_core + 0.12) * rp_body);
rp_col = rp_col + rpTintC * (rp_imp * 0.35 * rp_body);
rp_col = rp_col * (1.0 + 0.45 * rp_lit);
"""

    // iOS GraphNeuronShaders.mouths and mouth(k), lines 437-471: up to four openings where a
    // bridge joins the membrane (rpMouthK: its direction in the cell's frame, its width; 0 shut).
    private val MOUTHS: String get() = """
vec3 rp_cv = rp_c / max(length(rp_c), 0.00001);
vec3 rp_rs = rp_rel - rp_cv * dot(rp_rel, rp_cv);
float rp_open = 0.0;
vec3 rp_md = vec3(0.0, 0.0, 0.0);
vec3 rp_mp = vec3(0.0, 0.0, 0.0);
vec3 rp_mdu = vec3(0.0, 0.0, 0.0);
float rp_mst = 0.0;
float rp_mx = 0.0;
float rp_my = 0.0;
float rp_mw = 0.0;
float rp_mj = 0.0;
""" + (0 until 4).joinToString("") { mouth(it) } + """
rp_open = rp_open * rp_w;
"""

    private fun mouth(k: Int): String = """
if (rpMouth$k.w > 0.0) {
rp_md = (rp_mv * vec4(rpMouth$k.xyz, 0.0)).xyz / rp_R;
rp_mst = length(rp_md);
rp_mp = rp_md - rp_cv * dot(rp_md, rp_cv);
rp_mdu = rp_mp / max(length(rp_mp), 0.00001);
rp_mx = dot(rp_rs, rp_mdu) / rp_R;
rp_my = length(rp_rs / rp_R - rp_mdu * rp_mx);
rp_mw = max(rpMouth$k.w, 0.01);
rp_mj = sqrt(max(1.0 - rp_mw * rp_mw, 0.0));
rp_open = max(rp_open, (1.0 - smoothstep(0.82 * rp_mw, rp_mw, rp_my)) * smoothstep(rp_mj - 0.3, rp_mj - 0.08, rp_mx) * (1.0 - smoothstep(1.15, 1.35, rp_mx)) * smoothstep(0.3, 0.5, length(rp_mp) / max(rp_mst, 0.00001)) * min(rp_mst, 1.0));
}
"""

    // iOS GraphSpaceShaders.plane, lines 96-102: a quad's place, -1 to 1 each way, y up.
    private const val PLANE = """
vec2 rp_uv = vUV;
float rp_qx = rp_uv.x * 2.0 - 1.0;
float rp_qy = 1.0 - rp_uv.y * 2.0;
float rp_r = length(vec2(rp_qx, rp_qy)) + 0.00001;
"""

    /** GraphShaderKit's value noise (GraphShaderKit.swift lines 26-40) as a function. */
    const val NOISE = """
float rpHash(float n) { return fract(sin(n) * 43758.5453); }
float rpNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = p - i;
    vec3 u = f * f * (3.0 - 2.0 * f);
    float n = i.x + i.y * 57.0 + i.z * 113.0;
    float a = mix(rpHash(n), rpHash(n + 1.0), u.x);
    float b = mix(rpHash(n + 57.0), rpHash(n + 58.0), u.x);
    float c = mix(rpHash(n + 113.0), rpHash(n + 114.0), u.x);
    float d = mix(rpHash(n + 170.0), rpHash(n + 171.0), u.x);
    return mix(mix(a, b, u.y), mix(c, d, u.y), u.z);
}
"""

    private const val FRAGMENT_HEAD = """#version 300 es
precision highp float;
precision highp int;
uniform mat4 uMV;
uniform vec3 uWorld;
uniform vec4 rpFade;
uniform float uOpacity;
in vec3 vPos;
in vec3 vNormal;
in vec2 vUV;
out vec4 fragColor;
"""

    /**
     * glowEnd: the colour as it shows on screen (no pow into linear space), times uOpacity (a
     * node's opacity on iOS, which SceneKit applies in linear light, so its 1/2.2 power here),
     * faded by the view depth. Every Neurons material adds its light (blendMode add).
     */
    private const val END = """
rp_col = max(rp_col, vec3(0.0)) * pow(max(uOpacity, 0.0), 0.45454545);
float rp_dfF = rpDepthFade(-vPos.z);
fragColor = vec4(rpFadedGlow(rp_col, rp_dfF), 1.0);
"""

    private fun fragment(args: String, body: String): String =
        FRAGMENT_HEAD + args + NOISE + SpaceShaders.FADE + "void main() {\n" + body + END + "}\n"

    /** A mesh's vertex shader: aPos, aNormal, aUV in the body's frame, moved by [deform] (into rp_out). */
    private fun meshVertex(args: String, deform: String): String = """#version 300 es
precision highp float;
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aUV;
uniform mat4 uModel;
uniform mat4 uView;
uniform mat4 uProj;
uniform vec3 uWorld;
""" + args + """
out vec3 vPos;
out vec3 vNormal;
out vec2 vUV;
void main() {
vec3 rp_out = aPos;
""" + deform + """
mat4 rp_vmv = uView * uModel;
vec4 rp_vp = rp_vmv * vec4(rp_out, 1.0);
vPos = rp_vp.xyz;
vNormal = mat3(rp_vmv) * aNormal;
vUV = aUV;
gl_Position = uProj * rp_vp;
}
"""

    /** A billboard facing the camera: aCorner -1 to 1, uCentre in the world, uHalf its half width. */
    const val BILLBOARD_VS = """#version 300 es
precision highp float;
layout(location = 0) in vec2 aCorner;
uniform mat4 uView;
uniform mat4 uProj;
uniform vec3 uCentre;
uniform float uHalf;
out vec3 vPos;
out vec3 vNormal;
out vec2 vUV;
void main() {
vec4 rp_vp = uView * vec4(uCentre, 1.0) + vec4(aCorner * uHalf, 0.0, 0.0);
vPos = rp_vp.xyz;
vNormal = vec3(0.0, 0.0, 1.0);
vUV = vec2(aCorner.x * 0.5 + 0.5, 0.5 - aCorner.y * 0.5);
gl_Position = uProj * rp_vp;
}
"""

    /** A bridge's strip: world positions and the bridge coding in aUV (NeuronBridge.fill). */
    const val STRIP_VS = """#version 300 es
precision highp float;
layout(location = 0) in vec3 aPos;
layout(location = 2) in vec2 aUV;
uniform mat4 uView;
uniform mat4 uProj;
out vec3 vPos;
out vec3 vNormal;
out vec2 vUV;
void main() {
vec4 rp_vp = uView * vec4(aPos, 1.0);
vPos = rp_vp.xyz;
vNormal = vec3(0.0, 0.0, 1.0);
vUV = aUV;
gl_Position = uProj * rp_vp;
}
"""

    /**
     * The sky round the camera, at infinity (iOS keeps it round the camera): only the view's
     * turn applies, and every point sits mid-depth, so no near or far plane cuts it. aColour is
     * the dome's colour, or a quad's tint; aUV a quad's place.
     */
    const val SKY_VS = """#version 300 es
precision highp float;
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aColour;
layout(location = 2) in vec2 aUV;
uniform mat4 uView;
uniform mat4 uProj;
out vec3 vColour;
out vec2 vUV;
void main() {
vec4 rp_c = uProj * vec4(mat3(uView) * aPos, 1.0);
gl_Position = vec4(rp_c.xy, 0.0, rp_c.w);
vColour = aColour;
vUV = aUV;
}
"""

    /** The dome: its colour per vertex, opaque (iOS lightingModel constant, drawn first). */
    const val DOME_FS = """#version 300 es
precision highp float;
in vec3 vColour;
in vec2 vUV;
out vec4 fragColor;
void main() {
fragColor = vec4(vColour, 1.0);
}
"""

    /**
     * A bokeh disc (iOS NeuronArt.bokehDisc, GraphNeuronLook.swift lines 1374-1385): flat at
     * 0.62 out to 0.7 of its radius, 0.9 at 0.86, clear at the edge, straight between; tinted.
     */
    const val BOKEH_FS = """#version 300 es
precision highp float;
in vec3 vColour;
in vec2 vUV;
out vec4 fragColor;
void main() {
float rp_r = length(vUV * 2.0 - 1.0);
float rp_a = rp_r < 0.7 ? 0.62 : (rp_r < 0.86 ? mix(0.62, 0.9, (rp_r - 0.7) / 0.16) : mix(0.9, 0.0, clamp((rp_r - 0.86) / 0.14, 0.0, 1.0)));
fragColor = vec4(vColour * rp_a, 1.0);
}
"""

    /**
     * A far neuron out of focus (iOS NeuronArt.farNeuron, lines 1388-1440, drawn and blurred
     * there): its soft body, a radial fall from white through 0.6 at 0.45 to clear at 0.2 of
     * the quad, and six tapering branches at [farBranches]'s angles and lengths, softened as
     * the blur softens them. The branches run straight and do not fork here.
     */
    val FAR_FS: String get() = """#version 300 es
precision highp float;
in vec3 vColour;
in vec2 vUV;
out vec4 fragColor;
uniform vec2 uBranch[6];
void main() {
vec2 rp_q = vUV * 2.0 - 1.0;
float rp_r = length(rp_q);
float rp_s = rp_r / 0.2;
float rp_a = rp_s < 0.45 ? mix(1.0, 0.6, rp_s / 0.45) : mix(0.6, 0.0, clamp((rp_s - 0.45) / 0.55, 0.0, 1.0));
for (int rp_k = 0; rp_k < 6; rp_k++) {
vec2 rp_dir = vec2(cos(uBranch[rp_k].x), sin(uBranch[rp_k].x));
float rp_len = uBranch[rp_k].y;
float rp_t = clamp(dot(rp_q, rp_dir) / rp_len, 0.0, 1.0);
float rp_w = 0.052 * (1.0 - 0.75 * rp_t) * 0.5;
float rp_gap = length(rp_q - rp_dir * (rp_t * rp_len));
rp_a = max(rp_a, 1.0 - smoothstep(rp_w - 0.02, rp_w + 0.02, rp_gap));
}
fragColor = vec4(vColour * rp_a, 1.0);
}
"""

    /** The far neuron's six branches (angle, length in the quad's -1 to 1), as iOS draws them (UniverseRandom 0xFA2D). */
    fun farBranches(): FloatArray {
        val random = UniverseRandom(0xFA2DuL)
        val out = FloatArray(12)
        for (k in 0 until 6) {
            val angle = (k + 0.6 * random.signed()) / 6 * 2 * Math.PI
            val length = 2 * (0.26 + 0.14 * random.unit())
            out[k * 2] = angle.toFloat(); out[k * 2 + 1] = length.toFloat()
            skipBranch(random, 2)
        }
        return out
    }

    // what branch() draws next from the same random: its bend, fork place and steps, then its forks
    private fun skipBranch(random: UniverseRandom, forks: Int) {
        random.signed(); random.unit()
        repeat(10) { random.signed() }
        if (forks <= 0) return
        random.unit(); random.unit(); random.unit()
        skipBranch(random, forks - 1)
    }

    val soma: String get() = fragment(SOMA_ARGS, SOMA_BODY)
    val arbor: String get() = fragment(ARBOR_ARGS, ARBOR_BODY)
    val membrane: String get() = fragment(MEMBRANE_ARGS, MEMBRANE_BODY)
    val halo: String get() = fragment(HALO_ARGS, HALO_BODY)
    val arrival: String get() = fragment(ARRIVAL_ARGS, ARRIVAL_BODY)
    val bridge: String get() = fragment(BRIDGE_ARGS, BRIDGE_BODY)
    val somaVertex: String get() = meshVertex(WOBBLE_ARGS, WOBBLE_BODY)
    val arborVertex: String get() = meshVertex(SWAY_ARGS, SWAY_BODY)
    val membraneVertex: String get() = meshVertex(MEMBRANE_SWAY_ARGS, MEMBRANE_SWAY_BODY)

    /** The bodies carried over from iOS, for the shape tests: name to fragment source. */
    val fragments: Map<String, String> get() = mapOf("soma" to soma, "arbor" to arbor, "membrane" to membrane,
        "halo" to halo, "arrival" to arrival, "bridge" to bridge)
}
