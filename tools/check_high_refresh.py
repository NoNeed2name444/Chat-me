"""Checks Sorta's sources ask for 120 frames a second while something moves,
on both platforms (perf, 15 Nov: the owner's "since most phones now are
120hz make it 120 fps not 60").

    python3 tools/check_high_refresh.py [--self-test]

iOS:
  I1 ios/project.yml sets CADisableMinimumFrameDurationOnPhone (without it an
     iPhone holds every display link and view to 60), and
     tools/check_ios_app.py wants it in the built app's Info.plist.
  I2 MotionRate.moving is 80 to 120, preferred 120.
  I3 every CADisplayLink made in ios/Sorta asks for a preferredFrameRateRange,
     and its file reaches MotionRate.moving.
  I4 every SceneKit or Metal view's preferredFramesPerSecond comes from the
     screen's own rate (GraphQuality.frameRate, GraphPace, applyRate), never
     a number, and no Graphics tier caps it below the screen's.
  I5 GraphPace asks for the fast rate whenever the map moves.
  I6 the map's work keeps to a little over half a refresh (GraphPerfFrameBudget).
Android:
  A1 the map's GL surface asks for the display's top rate while its frame loop
     runs and for the system's choice at rest (Surface.setFrameRate, seamless).
  A2 the window asks for the fastest mode while something moves: the map's
     loop (MotionVote.loop) and every touch (MainActivity -> MotionRefresh),
     through WindowManager.LayoutParams.preferredDisplayModeId.
  A3 nothing pins a rate: no continuous GL render mode, no setFrameRate or
     preferred rate of 60 or 30.
  A4 the map's work keeps to a little over half a refresh (FrameBudget).

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


def load():
    files = {}
    for base, ext in ((IOS, ".swift"), (AND, ".kt")):
        for p in sorted((ROOT / base).rglob("*" + ext)):
            files[p.relative_to(ROOT).as_posix()] = p.read_text(encoding="utf-8")
    for p in (PLIST, APP_CHECK):
        files[p] = (ROOT / p).read_text(encoding="utf-8")
    return files


def code(text):
    """The text without // comments (a URL's // inside a string is rare here)."""
    return "\n".join(re.sub(r"(^|\s)//.*$", "", line) for line in text.splitlines())


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
    # I3 and I4
    links = 0
    for path, text in files.items():
        if not (path.startswith(IOS) and path.endswith(".swift")):
            continue
        src = code(text)
        made = len(re.findall(r"\bCADisplayLink\(target:", src))
        links += made
        if made:
            asks = len(re.findall(r"\.preferredFrameRateRange = ", src))
            need(asks >= made, f"I3 {path}: {made} display link(s) made, {asks} ask for a frame rate range")
            need("MotionRate.moving" in src, f"I3 {path}: a display link that moves asks for MotionRate.moving")
        need(not re.search(r"\.preferredFramesPerSecond = \d", src) and not re.search(r"link\w*\.preferredFramesPerSecond", src),
             f"I4 {path}: a rate set to a number, or a display link's old preferredFramesPerSecond")
        for m in re.finditer(r"\.preferredFramesPerSecond = ([\w.]+)", src):
            source = m.group(1).strip()
            ok = source == "GraphQuality.frameRate" or (
                source == "rate" and ("GraphPace.rate(" in src or "screenRate(" in src))
            need(ok, f"I4 {path}: preferredFramesPerSecond = {source} (the screen's own rate, through GraphQuality or GraphPace)")
    need(links >= 3, f"I3 the display links were not found ({links})")
    need(has(QUALITY, r"static let screenRate: Int = 240"), f"I4 {QUALITY}: screenRate 240, above any screen")
    tiers = re.findall(r"GraphicsBudget\(tier: \.\w+, linkSamples: \d+, maxFPS: (\w+)", code(files.get(QUALITY, "")))
    need(len(tiers) >= 2 and all(t == "screenRate" for t in tiers), f"I4 {QUALITY}: every tier's maxFPS is screenRate ({tiers})")
    # I5
    need(has(QUALITY, r"if busy \|\| lively \{ return fast \}"), f"I5 {QUALITY}: GraphPace.rate gives the fast rate while the map moves")
    # I6
    m = re.search(r"var share: Double = ([0-9.]+)", code(files.get(BUDGET, "")))
    need(m and 0.4 <= float(m.group(1)) <= 0.6, f"I6 {BUDGET}: share {m and m.group(1)}, at most 0.6 of a refresh")

    # A1
    need(has(MAP3D, r"surface\.setFrameRate\(hz, Surface\.FRAME_RATE_COMPATIBILITY_DEFAULT, Surface\.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS\)"),
         f"A1 {MAP3D}: Surface.setFrameRate on the GL surface, seamless")
    need(has(MAP3D, r"fun surfaceHz\(\) = RefreshRate\.surfaceVote\(running, maxHz\)"),
         f"A1 {MAP3D}: the surface's rate follows the loop (RefreshRate.surfaceVote)")
    need(len(re.findall(r"requestFrameRate\(h\.surface, loop\.surfaceHz\(\)\)", code(files.get(MAP3D, "")))) == 2,
         f"A1 {MAP3D}: surfaceCreated and surfaceChanged ask for the loop's rate")
    need(has(MAP3D, r"requestFrameRate\(it, surfaceHz\(\)\)"), f"A1 {MAP3D}: the loop's start and rest change the surface's rate")
    need(has(FRAME_BUDGET, r"fun surfaceVote\(running: Boolean, maxHz: Float\): Float = if \(running\) maxHz else 0f"),
         f"A1 {FRAME_BUDGET}: surfaceVote")
    # A2
    need(has(MAP3D, r"motion\?\.loop\(on, "), f"A2 {MAP3D}: the map's loop votes (MotionVote.loop)")
    need(has(REFRESH, r"preferredDisplayModeId = id"), f"A2 {REFRESH}: the window's preferredDisplayModeId")
    need(has(ACTIVITY, r"override fun dispatchTouchEvent\(ev: MotionEvent\): Boolean \{\s*MotionRefresh\.touch\(this, ev\)"),
         f"A2 {ACTIVITY}: every touch votes (MotionRefresh.touch)")
    need(has(FRAME_BUDGET, r"fun windowVote\(moving: Boolean, fastest: DisplayModeInfo\?\): Int = if \(moving\) fastest\?\.id \?: 0 else 0"),
         f"A2 {FRAME_BUDGET}: windowVote")
    need(VOTE in files, f"A2 {VOTE}: the window's vote")
    # A3
    for path, text in files.items():
        if not path.endswith(".kt"):
            continue
        src = code(text)
        need("RENDERMODE_CONTINUOUSLY" not in src, f"A3 {path}: a GL view drawn continuously")
        need(not re.search(r"setFrameRate\(\s*(60|30)", src), f"A3 {path}: a surface pinned to 60 or 30")
        need(not re.search(r"preferredRefreshRate\s*=\s*(60|30)", src), f"A3 {path}: a window pinned to 60 or 30")
    # A4
    m = re.search(r"const val HEAVY_SHARE = ([0-9.]+)", code(files.get(FRAME_BUDGET, "")))
    need(m and 0.4 <= float(m.group(1)) <= 0.6, f"A4 {FRAME_BUDGET}: HEAVY_SHARE {m and m.group(1)}, at most 0.6 of a refresh")
    need(has(FRAME_BUDGET, r"workMs > target \* HEAVY_SHARE"), f"A4 {FRAME_BUDGET}: onFrame judges work by HEAVY_SHARE")
    return bad


def self_test(files):
    """Each break must be caught, and the sources as they are must pass."""
    def broken(path, old, new):
        f = dict(files)
        assert old in f[path], (path, old)
        f[path] = f[path].replace(old, new, 1)
        return f

    breaks = {
        "no Info.plist key": broken(PLIST, "CADisableMinimumFrameDurationOnPhone: true", "CADisableMinimumFrameDurationOnPhone: false"),
        "MotionRate at 60": broken(MOTION_RATE, "maximum: 120, preferred: 120", "maximum: 60, preferred: 60"),
        "a display link with no range": broken(f"{IOS}/Ward/WardTokens.swift", "link.preferredFrameRateRange = MotionRate.moving", "_ = 0"),
        "a SceneKit view at 30": broken(f"{IOS}/Maps/Graph3DView.swift", "view.preferredFramesPerSecond = GraphQuality.frameRate",
                                         "view.preferredFramesPerSecond = 30"),
        "a tier capped at 60": broken(QUALITY, "linkSamples: 20, maxFPS: screenRate", "linkSamples: 20, maxFPS: 60"),
        "a pace that rests while lively": broken(QUALITY, "if busy || lively { return fast }", "if busy { return fast }"),
        "iOS share back at 0.75": broken(BUDGET, "var share: Double = 0.55", "var share: Double = 0.75"),
        "no surface vote": broken(MAP3D, "surface.setFrameRate(hz, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT, Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS)",
                                  "Unit"),
        "a surface at the top rate always": broken(MAP3D, "fun surfaceHz() = RefreshRate.surfaceVote(running, maxHz)", "fun surfaceHz() = maxHz"),
        "touches do not vote": broken(ACTIVITY, "MotionRefresh.touch(this, ev)\n        return super.dispatchTouchEvent(ev)",
                                      "return super.dispatchTouchEvent(ev)"),
        "a window pinned to 60": broken(REFRESH, "it.preferredDisplayModeId = id", "it.preferredRefreshRate = 60f"),
        "a GL view drawn continuously": broken(MAP3D, "GLSurfaceView.RENDERMODE_WHEN_DIRTY", "GLSurfaceView.RENDERMODE_CONTINUOUSLY"),
        "Android share back at 0.75": broken(FRAME_BUDGET, "const val HEAVY_SHARE = 0.55", "const val HEAVY_SHARE = 0.75"),
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
    print("ok   120 Hz: the Info.plist key, every display link's and view's rate, the GL surface's and the window's votes")


if __name__ == "__main__":
    main()
