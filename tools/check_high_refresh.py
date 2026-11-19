"""Checks Sorta's sources ask for 120 frames a second while something moves,
on both platforms (perf, 15 Nov: the owner's "since most phones now are
120hz make it 120 fps not 60").

    python3 tools/check_high_refresh.py [--self-test]

iOS:
  I1 ios/project.yml sets CADisableMinimumFrameDurationOnPhone (without it an
     iPhone holds every display link and view to 60), and
     tools/check_ios_app.py wants it in the built app's Info.plist.
  I2 MotionRate.moving is 80 to 120, preferred 120; MotionRate.calm 60;
     MotionRate.range(fast:) picks between them.
  I3 every CADisplayLink made in ios/Sorta: the link it was made into (by
     name, or the property it is kept in) asks for a preferredFrameRateRange
     that reaches MotionRate, in its file or through the function it calls.
     The map's ring link follows the map's pace (the review, 19 Nov): its
     range is set only in paceRings(), from GraphPace.ringsFast of the rate
     the map's view asks for and Reduce Motion read then, and paceRings()
     runs after each pace() and on each ring tick.
  I4 every SceneKit or Metal view's preferredFramesPerSecond comes from the
     screen's own rate (GraphQuality.frameRate, GraphPace, applyRate), never
     a number, and no Graphics tier caps it below the screen's.
  I5 GraphPace's rest rule (the review, 19 Nov): under a sheet or covered,
     60 at once; else the fast rate while a body is held or within 3 s
     (still) or 20 s (lively) of the last touch, then 60; never "lively
     means fast" for good. GraphPace.ringsFast: fast only above the floor
     and without Reduce Motion.
  I6 the map's work keeps to a little over half a refresh (GraphPerfFrameBudget).
Android:
  A1 the map's GL surface asks for the display's top rate while its frame loop
     runs, 60 while it rests, and for the system's choice once it stops
     (Surface.setFrameRate, seamless).
  A2 the window asks for the fastest mode while something moves: the map's
     loop while it is in use (MotionVote.loop) and every touch (MainActivity
     -> MotionRefresh), through WindowManager.LayoutParams.preferredDisplayModeId.
  A3 nothing pins a rate: no continuous GL render mode, no setFrameRate,
     requestFrameRate or preferred refresh rate of 60 or 30, as a number or
     as a constant that holds one.
  A4 the map's work keeps to a little over half a refresh (FrameBudget).
  A5 the rest rule, as iOS's (the review, 19 Nov): a running map loop rests
     at once while covered (another window has the focus: a sheet, dialog
     or menu; read on attach and on each change, waking when it goes), else
     20 s after the last touch or change with no body held, at min(60, top),
     its window vote let go, drawing a frame a 60th of a second; with
     animations off (animator duration scale 0) Space's bodies and beams do
     not keep it drawing, and the demo neither turns nor draws on.

Comments, // and /* */, are left out before any rule reads the sources.
--self-test breaks each opt-in in memory and checks it is caught. Exit 1 on
any problem. No network, no key.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
IOS = "ios/Sorta"
AND = "android/app/src/main/java/com/cramdown/sorta"

PLIST = "ios/project.yml"
APP_CHECK = "tools/check_ios_app.py"
MOTION_RATE = f"{IOS}/Shared/MotionRate.swift"
QUALITY = f"{IOS}/Shared/Space/GraphicsQuality.swift"
BUDGET = f"{IOS}/Maps/GraphPerfBudget.swift"
MAP3D = f"{AND}/ui/map/Map3DView.kt"
REFRESH = f"{AND}/ui/MotionRefresh.kt"
ACTIVITY = f"{AND}/MainActivity.kt"
FRAME_BUDGET = f"{AND}/map/FrameBudget.kt"
VOTE = f"{AND}/map/MotionVote.kt"
RENDERER = f"{AND}/ui/map/GlMapRenderer.kt"
GRAPH3D = f"{IOS}/Maps/Graph3DView.swift"


def load():
    files = {}
    for base, ext in ((IOS, ".swift"), (AND, ".kt")):
        for p in sorted((ROOT / base).rglob("*" + ext)):
            files[p.relative_to(ROOT).as_posix()] = p.read_text(encoding="utf-8")
    for p in (PLIST, APP_CHECK):
        files[p] = (ROOT / p).read_text(encoding="utf-8")
    return files


def code(text):
    """The text without /* */ comments (KDoc and Swift's nested ones too) and
    // comments (a URL's // inside a string is rare here)."""
    out, depth, k = [], 0, 0
    while k < len(text):
        two = text[k:k + 2]
        if two == "/*":
            depth += 1
            k += 2
        elif two == "*/" and depth:
            depth -= 1
            k += 2
        else:
            if not depth:
                out.append(text[k])
            elif text[k] == "\n":
                out.append("\n")
            k += 1
    return "\n".join(re.sub(r"(^|\s)//.*$", "", line) for line in "".join(out).splitlines())


def reaches_motion_rate(expr, src, files):
    """Whether a frame rate range `expr` comes from MotionRate: named there, or
    the result of a function (in `src`'s file or any iOS file) whose body is."""
    if "MotionRate." in expr:
        return True
    for name in re.findall(r"(\w+)\(\)", expr):
        for text in [src] + [code(t) for p, t in files.items() if p.startswith(IOS) and p.endswith(".swift")]:
            m = re.search(r"func " + name + r"\(\)[^{]*\{([^}]*)\}", text)
            if m:
                return "MotionRate." in m.group(1)
    return False


def check(files):
    bad = []

    def need(ok, what):
        if not ok:
            bad.append(what)

    def has(path, pattern):
        return re.search(pattern, code(files.get(path, ""))) is not None

    # I1
    need(re.search(r"^\s+CADisableMinimumFrameDurationOnPhone:\s*true\s*$", files.get(PLIST, ""), re.M),
         f"I1 {PLIST}: CADisableMinimumFrameDurationOnPhone: true")
    need(has(APP_CHECK, r'"CADisableMinimumFrameDurationOnPhone":\s*True'),
         f"I1 {APP_CHECK}: the built app must carry CADisableMinimumFrameDurationOnPhone")
    # I2
    need(has(MOTION_RATE, r"static let moving = CAFrameRateRange\(minimum: 80, maximum: 120, preferred: 120\)"),
         f"I2 {MOTION_RATE}: moving = CAFrameRateRange(minimum: 80, maximum: 120, preferred: 120)")
    need(has(MOTION_RATE, r"static let calm = CAFrameRateRange\(minimum: 60, maximum: 60, preferred: 60\)"),
         f"I2 {MOTION_RATE}: calm = CAFrameRateRange(minimum: 60, maximum: 60, preferred: 60), for Reduce Motion")
    need(has(MOTION_RATE, r"static func range\(fast: Bool\) -> CAFrameRateRange \{\s*fast \? moving : calm\s*\}"),
         f"I2 {MOTION_RATE}: range(fast:) is moving when fast, else calm")
    # I3 and I4
    links = 0
    for path, text in files.items():
        if not (path.startswith(IOS) and path.endswith(".swift")):
            continue
        src = code(text)
        # each link by the name it was made into, and that name's own range
        for m in re.finditer(r"\b(\w+)\s*=\s*CADisplayLink\(target:", src):
            links += 1
            name = m.group(1)
            # the link itself, or the property it is kept in (ringLink = link)
            kept = re.search(r"\b(\w+)\s*=\s*" + name + r"\s*$", src[m.end():], re.M)
            names = name + ("|" + kept.group(1) if kept else "")
            asks = re.search(r"\b(?:" + names + r")\??\.preferredFrameRateRange\s*=\s*([^\n]+)", src[m.end():]) or (
                re.search(r"\b(?:" + kept.group(1) + r")\??\.preferredFrameRateRange\s*=\s*([^\n]+)", src) if kept else None)
            need(asks is not None, f"I3 {path}: display link '{name}' asks for no preferredFrameRateRange")
            if asks:
                need(reaches_motion_rate(asks.group(1), src, files),
                     f"I3 {path}: display link '{name}' asks for {asks.group(1).strip()}, not MotionRate's")
        need(not re.search(r"\.preferredFramesPerSecond = \d", src) and not re.search(r"link\w*\.preferredFramesPerSecond", src),
             f"I4 {path}: a rate set to a number, or a display link's old preferredFramesPerSecond")
        for m in re.finditer(r"\.preferredFramesPerSecond = ([\w.]+)", src):
            source = m.group(1).strip()
            ok = source == "GraphQuality.frameRate" or (
                source == "rate" and ("GraphPace.rate(" in src or "screenRate(" in src))
            need(ok, f"I4 {path}: preferredFramesPerSecond = {source} (the screen's own rate, through GraphQuality or GraphPace)")
    need(links >= 4, f"I3 the display links were not found ({links})")
    # the ring link follows the map's pace, and Reduce Motion read each time
    g3 = code(files.get(GRAPH3D, ""))

    def body(src, head):
        """The body of the function whose declaration matches `head` (by its braces)."""
        m = re.search(head + r"[^{]*\{", src)
        if not m:
            return ""
        depth, i = 1, m.end()
        while i < len(src) and depth:
            depth += {"{": 1, "}": -1}.get(src[i], 0)
            i += 1
        return src[m.end():i - 1]

    rings = body(g3, r"private func paceRings\(\)")
    need(re.search(r"let fast: Bool = GraphPace\.ringsFast\(rate: view\.preferredFramesPerSecond,\s*"
                   r"reduceMotion: UIAccessibility\.isReduceMotionEnabled\)", rings)
         and re.search(r"ringLink\.preferredFrameRateRange = MotionRate\.range\(fast: fast\)", rings),
         f"I3 {GRAPH3D}: paceRings() sets the ring link's range from GraphPace.ringsFast(rate: view.preferredFramesPerSecond, "
         f"reduceMotion: UIAccessibility.isReduceMotionEnabled)")
    need(len(re.findall(r"\.preferredFrameRateRange\s*=", g3)) == 1,
         f"I3 {GRAPH3D}: the ring link's range is set in paceRings() only")
    need(re.search(r"view\.preferredFramesPerSecond = rate \}\s*paceRings\(\)", body(g3, r"private func pace\(\)")),
         f"I3 {GRAPH3D}: pace() hands its rate on to the rings (paceRings())")
    need(re.search(r"\bpaceRings\(\)", body(g3, r"func drawRings\(\)")),
         f"I3 {GRAPH3D}: each ring tick reads the pace and Reduce Motion again (paceRings() in drawRings())")
    need(has(QUALITY, r"static let screenRate: Int = 240"), f"I4 {QUALITY}: screenRate 240, above any screen")
    tiers = re.findall(r"GraphicsBudget\(tier: \.\w+, linkSamples: \d+, maxFPS: (\w+)", code(files.get(QUALITY, "")))
    need(len(tiers) >= 2 and all(t == "screenRate" for t in tiers), f"I4 {QUALITY}: every tier's maxFPS is screenRate ({tiers})")
    # I5
    pace = code(files.get(QUALITY, ""))
    need(re.search(r"static let settleAfter: Double = 3\b", pace) and re.search(r"static let restAfter: Double = 20\b", pace)
         and re.search(r"static let settled: Int = 60\b", pace), f"I5 {QUALITY}: settleAfter 3, restAfter 20, settled 60")
    rate = re.search(r"static func rate\(fast: Int, lively: Bool, covered: Bool, quiet: Double, busy: Bool\) -> Int \{(.*?)\n    \}", pace, re.S)
    body = rate.group(1) if rate else ""
    need(re.search(r"let floor: Int = min\(settled, fast\)\s*if covered \{ return floor \}\s*if busy \{ return fast \}\s*"
                   r"return quiet < after\(lively\) \? fast : floor\s*$", body),
         f"I5 {QUALITY}: GraphPace.rate: covered rests at once, held is fast, else fast until after(lively), then the floor")
    need(re.search(r"static func after\(_ lively: Bool\) -> Double \{ lively \? restAfter : settleAfter \}", pace),
         f"I5 {QUALITY}: after(lively) is restAfter for a lively map, settleAfter for a still one")
    need(not re.search(r"lively\s*\{\s*return fast", pace), f"I5 {QUALITY}: a lively map held at the fast rate for good")
    need(re.search(r"static func ringsFast\(rate: Int, reduceMotion: Bool\) -> Bool \{ !reduceMotion && rate > settled \}", pace),
         f"I5 {QUALITY}: ringsFast: fast only above the floor, and never with Reduce Motion")
    # I6
    m = re.search(r"var share: Double = ([0-9.]+)", code(files.get(BUDGET, "")))
    need(m and 0.4 <= float(m.group(1)) <= 0.6, f"I6 {BUDGET}: share {m and m.group(1)}, at most 0.6 of a refresh")

    # A1
    need(has(MAP3D, r"surface\.setFrameRate\(hz, Surface\.FRAME_RATE_COMPATIBILITY_DEFAULT, Surface\.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS\)"),
         f"A1 {MAP3D}: Surface.setFrameRate on the GL surface, seamless")
    need(has(MAP3D, r"fun surfaceHz\(\) = RefreshRate\.surfaceVote\(running, maxHz, resting\)"),
         f"A1 {MAP3D}: the surface's rate follows the loop and its rest (RefreshRate.surfaceVote)")
    need(len(re.findall(r"requestFrameRate\(h\.surface, loop\.surfaceHz\(\)\)", code(files.get(MAP3D, "")))) == 2,
         f"A1 {MAP3D}: surfaceCreated and surfaceChanged ask for the loop's rate")
    need(has(MAP3D, r"val hz = surfaceHz\(\)\s*if \(hz != surfaceAsked\) \{ surfaceAsked = hz; view\?\.holder\?\.surface\?\.let \{ requestFrameRate\(it, hz\) \} \}"),
         f"A1 {MAP3D}: the loop's start, rest and stop change the surface's rate")
    need(has(FRAME_BUDGET, r"fun surfaceVote\(running: Boolean, maxHz: Float, resting: Boolean = false\): Float =\s*"
                           r"if \(!running\) 0f else if \(resting\) restHz\(maxHz\) else maxHz"),
         f"A1 {FRAME_BUDGET}: surfaceVote: none stopped, restHz resting, the top in use")
    # A2
    need(has(MAP3D, r"val wants = running && !resting\s*if \(wants != voting\) \{ voting = wants; motion\?\.loop\(wants, nowMs\) \}"),
         f"A2 {MAP3D}: the map's loop votes while in use (MotionVote.loop), not at rest")
    need(has(REFRESH, r"preferredDisplayModeId = id"), f"A2 {REFRESH}: the window's preferredDisplayModeId")
    need(has(ACTIVITY, r"override fun dispatchTouchEvent\(ev: MotionEvent\): Boolean \{\s*MotionRefresh\.touch\(this, ev\)"),
         f"A2 {ACTIVITY}: every touch votes (MotionRefresh.touch)")
    need(has(FRAME_BUDGET, r"fun windowVote\(moving: Boolean, fastest: DisplayModeInfo\?\): Int = if \(moving\) fastest\?\.id \?: 0 else 0"),
         f"A2 {FRAME_BUDGET}: windowVote")
    need(VOTE in files, f"A2 {VOTE}: the window's vote")
    # A3: constants that hold 60 or 30, anywhere in the app
    pins = {name for path, text in files.items() if path.endswith(".kt")
            for name in re.findall(r"\b(?:const )?val (\w+)(?:: \w+)? = (?:60|30)(?:\.0)?[fFdD]?\b(?![.\w])", code(text))}
    # a literal (60, 60f, 60.0f) or a constant holding one, by itself or qualified (RefreshRate.REST_HZ)
    pinned = r"(?:60|30)(?:\.0)?[fFdD]?" + "".join("|" + re.escape(n) for n in sorted(pins))
    for path, text in files.items():
        if not path.endswith(".kt"):
            continue
        src = code(text)
        need("RENDERMODE_CONTINUOUSLY" not in src, f"A3 {path}: a GL view drawn continuously")
        need(not re.search(r"setFrameRate\(\s*(?:\w+\.)?(" + pinned + r")\b", src), f"A3 {path}: a surface pinned to 60 or 30")
        need(not re.search(r"requestFrameRate\([^,()]+,\s*(?:\w+\.)?(" + pinned + r")\b", src), f"A3 {path}: a surface pinned to 60 or 30")
        need(not re.search(r"preferredRefreshRate\s*=\s*(?:\w+\.)?(" + pinned + r")\b", src), f"A3 {path}: a window pinned to 60 or 30")
    # A4
    m = re.search(r"const val HEAVY_SHARE = ([0-9.]+)", code(files.get(FRAME_BUDGET, "")))
    need(m and 0.4 <= float(m.group(1)) <= 0.6, f"A4 {FRAME_BUDGET}: HEAVY_SHARE {m and m.group(1)}, at most 0.6 of a refresh")
    need(has(FRAME_BUDGET, r"workMs > target \* HEAVY_SHARE"), f"A4 {FRAME_BUDGET}: onFrame judges work by HEAVY_SHARE")
    # A5
    need(has(FRAME_BUDGET, r"const val REST_AFTER_MS = 20_000L") and has(FRAME_BUDGET, r"const val REST_HZ = 60f"),
         f"A5 {FRAME_BUDGET}: REST_AFTER_MS 20 s and REST_HZ 60, iOS's restAfter and settled")
    need(has(FRAME_BUDGET, r"fun resting\(running: Boolean, held: Boolean, covered: Boolean, sinceWakeMs: Long\): Boolean =\s*"
                           r"running && \(covered \|\| \(!held && sinceWakeMs >= REST_AFTER_MS\)\)"),
         f"A5 {FRAME_BUDGET}: resting: covered at once, held or not; else 20 s untouched with no body held")
    need(has(MAP3D, r"fun cover\(now: Boolean\) \{\s*if \(now == covered\) return\s*covered = now\s*"
                    r"val nowMs = SystemClock\.uptimeMillis\(\)\s*if \(!now\) lastWake = nowMs\s*if \(running\) vote\(nowMs\)\s*\}"),
         f"A5 {MAP3D}: FrameLoop.cover votes at once, and uncovered wakes the map")
    need(has(MAP3D, r"OnWindowFocusChangeListener \{ focused -> loop\.cover\(!focused\) \}")
         and has(MAP3D, r"addOnWindowFocusChangeListener\(focus\)\s*loop\.cover\(!v\.hasWindowFocus\(\)\)")
         and has(MAP3D, r"removeOnWindowFocusChangeListener\(focus\)"),
         f"A5 {MAP3D}: the GL view covers the loop while another window has the focus, from attach to detach")
    need(has(FRAME_BUDGET, r"fun restHz\(maxHz: Float\): Float = min\(REST_HZ, maxHz\)"), f"A5 {FRAME_BUDGET}: restHz, never above the top")
    need(has(MAP3D, r"resting = RefreshRate\.resting\(running, renderer\.holding, covered, nowMs - lastWake\)"),
         f"A5 {MAP3D}: the loop rests by RefreshRate.resting")
    need(has(MAP3D, r"if \(resting && lastFrame != 0L && !RefreshRate\.drawsAtRest\(frameTimeNanos - lastFrame, vsync\)\)"),
         f"A5 {MAP3D}: a resting loop draws a frame a 60th of a second (RefreshRate.drawsAtRest)")
    need(len(re.findall(r"lastWake = SystemClock\.uptimeMillis\(\)", code(files.get(MAP3D, "")))) >= 2,
         f"A5 {MAP3D}: a start and each touch or change wake the loop")
    need(has(MAP3D, r"private val continuous = continuous && renderer\.lively")
         and has(MAP3D, r"private var autoOrbit = autoOrbit && renderer\.lively"),
         f"A5 {MAP3D}: with animations off the loop neither turns the camera nor draws on by itself")
    need(has(RENDERER, r"animating = \(lively && \(bodyCount > 0 \|\| \(bodies\?\.beamCount \?: 0\) > 0\)\)"),
         f"A5 {RENDERER}: Space's bodies and beams keep the loop drawing only with animations on")
    return bad


def self_test(files):
    """Each break must be caught, and the sources as they are must pass."""
    def broken(path, old, new, *more):
        """`files` with `old` made `new` in `path`, then each further
        (path, old, new) in `more`."""
        f = dict(files)
        for p, o, n in ((path, old, new),) + more:
            assert o in f[p], (p, o)
            f[p] = f[p].replace(o, n, 1)
        return f

    ward = f"{IOS}/Ward/WardTokens.swift"
    meter = f"{IOS}/Shared/FrameMeter.swift"
    breaks = {
        "no Info.plist key": broken(PLIST, "CADisableMinimumFrameDurationOnPhone: true", "CADisableMinimumFrameDurationOnPhone: false"),
        "MotionRate at 60": broken(MOTION_RATE, "maximum: 120, preferred: 120", "maximum: 60, preferred: 60"),
        "a calm range under 60": broken(MOTION_RATE, "calm = CAFrameRateRange(minimum: 60, maximum: 60, preferred: 60)",
                                        "calm = CAFrameRateRange(minimum: 30, maximum: 30, preferred: 30)"),
        "a display link with no range": broken(ward, "link.preferredFrameRateRange = MotionRate.moving", "_ = 0"),
        "a range set on another link than the one made": broken(meter, "link.preferredFrameRateRange = MotionRate.moving",
                                                                "other.preferredFrameRateRange = MotionRate.moving"),
        "a display link at a fixed 60": broken(ward, "link.preferredFrameRateRange = MotionRate.moving",
                                               "link.preferredFrameRateRange = CAFrameRateRange(minimum: 60, maximum: 60, preferred: 60)"),
        "a range from a function that is not MotionRate's": broken(f"{IOS}/Shared/PopOutMotion.swift", "private static func fullRate() -> CAFrameRateRange {\n        MotionRate.moving",
                                                                    "private static func fullRate() -> CAFrameRateRange {\n        idleRate"),
        "a ring link that ignores Reduce Motion": broken(GRAPH3D, "reduceMotion: UIAccessibility.isReduceMotionEnabled)", "reduceMotion: false)"),
        "a ring link that ignores the map's pace (fast while rings show)": broken(
            GRAPH3D, "GraphPace.ringsFast(rate: view.preferredFramesPerSecond,", "GraphPace.ringsFast(rate: GraphQuality.frameRate,"),
        "a ring link pinned fast when made": broken(GRAPH3D, "ringsFast = nil\n",
                                                   "ringsFast = nil\n                link.preferredFrameRateRange = MotionRate.moving\n"),
        "pace() leaving the rings at their old rate": broken(GRAPH3D, "view.preferredFramesPerSecond = rate }\n            paceRings()\n",
                                                            "view.preferredFramesPerSecond = rate }\n"),
        "Reduce Motion read only when the ring link is made": broken(GRAPH3D, "            // each tick: Reduce Motion turned on or off while the rings show is followed\n            paceRings()\n", ""),
        "rings fast at rest": broken(QUALITY, "{ !reduceMotion && rate > settled }", "{ !reduceMotion }"),
        "MotionRate.range(fast:) fast always": broken(MOTION_RATE, "fast ? moving : calm", "moving"),
        "a SceneKit view at 30": broken(GRAPH3D, "view.preferredFramesPerSecond = GraphQuality.frameRate",
                                         "view.preferredFramesPerSecond = 30"),
        "a tier capped at 60": broken(QUALITY, "linkSamples: 20, maxFPS: screenRate", "linkSamples: 20, maxFPS: 60"),
        "a lively map fast for good (the old rule)": broken(QUALITY, "        if busy { return fast }\n", "        if busy || lively { return fast }\n"),
        "a lively map that never rests": broken(QUALITY, "lively ? restAfter : settleAfter", "lively ? .infinity : settleAfter"),
        "a held body fast under a sheet": broken(QUALITY, "if covered { return floor }\n        if busy { return fast }",
                                                 "if busy { return fast }\n        if covered { return floor }"),
        "a rest after a minute": broken(QUALITY, "static let restAfter: Double = 20", "static let restAfter: Double = 60"),
        "iOS share back at 0.75": broken(BUDGET, "var share: Double = 0.55", "var share: Double = 0.75"),
        "no surface vote": broken(MAP3D, "surface.setFrameRate(hz, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT, Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS)",
                                  "Unit"),
        "a surface at the top rate always": broken(MAP3D, "fun surfaceHz() = RefreshRate.surfaceVote(running, maxHz, resting)", "fun surfaceHz() = maxHz"),
        "a surface that never rests": broken(FRAME_BUDGET, "if (!running) 0f else if (resting) restHz(maxHz) else maxHz", "if (!running) 0f else maxHz"),
        "a loop that never rests": broken(MAP3D, "resting = RefreshRate.resting(running, renderer.holding, covered, nowMs - lastWake)", "resting = false"),
        "a covered map held at the top rate (no covered rule)": broken(FRAME_BUDGET, "running && (covered || (!held && sinceWakeMs >= REST_AFTER_MS))",
                                                                       "running && !held && sinceWakeMs >= REST_AFTER_MS"),
        "a held body fast under a sheet on Android": broken(FRAME_BUDGET, "running && (covered || (!held && sinceWakeMs >= REST_AFTER_MS))",
                                                            "running && !held && (covered || sinceWakeMs >= REST_AFTER_MS)"),
        "the loop never told it is covered": broken(MAP3D, "{ focused -> loop.cover(!focused) }", "{ focused -> Unit }"),
        "covered not read on attach": broken(MAP3D, "loop.cover(!v.hasWindowFocus())", "Unit"),
        "a focus listener left on the window": broken(MAP3D, "v.viewTreeObserver.removeOnWindowFocusChangeListener(focus)", "Unit"),
        "a cover that waits for the next frame to rest": broken(MAP3D, "if (running) vote(nowMs)\n    }", "\n    }"),
        "a window vote held at rest": broken(MAP3D, "val wants = running && !resting", "val wants = running"),
        "a resting loop drawing every vsync": broken(MAP3D, "if (resting && lastFrame != 0L && !RefreshRate.drawsAtRest(frameTimeNanos - lastFrame, vsync))",
                                                     "if (false)"),
        "Space's bodies drawing on with animations off": broken(RENDERER, "animating = (lively && (bodyCount > 0 || (bodies?.beamCount ?: 0) > 0))",
                                                                "animating = ((bodyCount > 0 || (bodies?.beamCount ?: 0) > 0))"),
        "the demo turning with animations off": broken(MAP3D, "private var autoOrbit = autoOrbit && renderer.lively", "private var autoOrbit = autoOrbit"),
        "touches do not vote": broken(ACTIVITY, "MotionRefresh.touch(this, ev)\n        return super.dispatchTouchEvent(ev)",
                                      "return super.dispatchTouchEvent(ev)"),
        "a window pinned to 60": broken(REFRESH, "it.preferredDisplayModeId = id", "it.preferredRefreshRate = 60f"),
        "a window pinned to a constant of 60": broken(REFRESH, "it.preferredDisplayModeId = id", "it.preferredDisplayModeId = id; it.preferredRefreshRate = PIN_HZ",
                                                      (FRAME_BUDGET, "const val LINGER_MS = 1_500L", "const val LINGER_MS = 1_500L\n    const val PIN_HZ = 60f")),
        "a surface pinned to a constant of 60": broken(MAP3D, "surface.setFrameRate(hz, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)\n",
                                                       "surface.setFrameRate(RefreshRate.REST_HZ, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)\n"),
        "a surface asked for 60 through requestFrameRate": broken(MAP3D, "requestFrameRate(it, hz)", "requestFrameRate(it, 60f)"),
        "a GL view drawn continuously": broken(MAP3D, "GLSurfaceView.RENDERMODE_WHEN_DIRTY", "GLSurfaceView.RENDERMODE_CONTINUOUSLY"),
        "Android share back at 0.75": broken(FRAME_BUDGET, "const val HEAVY_SHARE = 0.55", "const val HEAVY_SHARE = 0.75"),
        "a share kept only in a block comment": broken(FRAME_BUDGET, "const val HEAVY_SHARE = 0.55",
                                                       "/* const val HEAVY_SHARE = 0.55 */ const val HEAVY_SHARE = 0.75"),
        "a rate range kept only in a doc comment": broken(MOTION_RATE, "static let moving = CAFrameRateRange(minimum: 80, maximum: 120, preferred: 120)",
                                                          "/** static let moving = CAFrameRateRange(minimum: 80, maximum: 120, preferred: 120) */ "
                                                          "static let moving = CAFrameRateRange(minimum: 60, maximum: 60, preferred: 60)"),
    }
    failed = [name for name, f in breaks.items() if not check(f)]
    clean = check(files)
    for name in failed:
        print(f"FAIL self-test: '{name}' was not caught")
    for b in clean:
        print(f"FAIL self-test: the sources as they are: {b}")
    if not failed and not clean:
        print(f"ok   self-test: all {len(breaks)} breaks caught, the sources pass")
    return not failed and not clean


def main():
    files = load()
    if "--self-test" in sys.argv[1:]:
        sys.exit(0 if self_test(files) else 1)
    bad = check(files)
    for b in bad:
        print("FAIL " + b)
    if bad:
        sys.exit(1)
    print("ok   120 Hz: the Info.plist key, every display link's and view's rate, the rest rule, the GL surface's and the window's votes")


if __name__ == "__main__":
    main()
