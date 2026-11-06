#!/bin/bash
# Runs the launch tests that "Build for testing" built (ci/public/ios.yml) on
# one simulator, from ios/. Each test runs on its own, with the simulator set
# to that test's appearance first and read back. The tests also set
# XCUIDevice.shared.appearance, but in run 40714297807 that didn't take: the
# dark test found the page bone, and the simulator never went dark. One report
# on Apple's developer forums says the same of tests run through xcodebuild
# (thread 812656); nobody there confirmed it.
# Logs go to ../results/<kind>-<look>.log and result bundles to
# ../results/<kind>-<look>.xcresult. It fails unless each test passed, by name.
# The simulator is shut down afterwards, so the next one boots alone: in run
# 40718963474 the iPad booted beside the running iPhone, in 6 minutes the
# first time and in 11 the second.
#
#   UDID=<simulator> bash ../tools/run_ui_tests.sh iphone|ipad [kit|maps|ideas]
#
# kit (the default): the launch tests, the look kit's gallery and the maps'
# renders, by day and by night, and the unit tests. maps: each of the maps'
# tests (GraphPreviewUITests), one at a time; the maps are always night.
# ideas (S05, S05b): on the iPhone, the Ideas screens' behaviour tests
# (IdeasBehaviourTests and MapModeTests, each whole class in one run), the
# Sort screen's and Map mode's renders by day and by night (IdeasRenderTests,
# MapModeRenderTests), Map mode's legends read for the controls they name
# (S05f), and each one's largest text size; on the iPad, the two sets of
# renders by day and by night and the legends. A whole class passes only when
# every test in its file passed, by name, and none failed or was skipped.
set -euo pipefail
kind="$1"
set_="${2:-kit}"
: "${UDID:?the simulator to run on}"
mkdir -p ../results
booting=$SECONDS
xcrun simctl bootstatus "$UDID" -b > /dev/null
echo "$kind: the simulator booted in $((SECONDS - booting)) seconds"
# iOS's slide-to-type introduction ("Speed up your typing by sliding your
# finger…", Continue) takes the keyboard's place the first time a keyboard
# shows, and when it went the field had lost the caret: in Chat-me run
# 42086097207 the iPhone's first folder name got its "G" and no more (the
# screen recording of IdeasBehaviourTests shows the card). Marked as shown
# before any test, and read back.
xcrun simctl spawn "$UDID" defaults write com.apple.Preferences DidShowContinuousPathIntroduction -bool true
shown=$(xcrun simctl spawn "$UDID" defaults read com.apple.Preferences DidShowContinuousPathIntroduction 2>/dev/null || true)
if [ "$shown" = "1" ]; then
  echo "$kind: the slide-to-type introduction is marked as shown"
else
  echo "::warning::$kind: the slide-to-type introduction could not be marked as shown (read back: ${shown:-nothing})"
fi
# One launch of the map before any test, timed by none: just after boot the
# simulator is still busy, and the first launch after an install fills
# caches that no later one does. Without it the first map test hit the
# simulator's own timeouts after slow boots (Chat-me run 40855930427, 169
# seconds: an orientation change; run 40861178931, 88 seconds: the launch),
# and passed after a 40-second boot (run 40848934308). Stethoscore launched
# once before its films and shots for the same reason
# (reference/stethoscore-aec2af1c/.github/workflows/launch-recording.yml:119).
warming=$SECONDS
if xcrun simctl install "$UDID" build/Build/Products/Debug-iphonesimulator/Sorta.app \
   && xcrun simctl launch "$UDID" com.cramdown.sorta -graphPreview > /dev/null; then
  sleep 20
  xcrun simctl terminate "$UDID" com.cramdown.sorta || true
  echo "$kind: the warm-up launch took $((SECONDS - warming)) seconds"
else
  echo "::warning::$kind: the warm-up launch failed, after $((SECONDS - warming)) seconds"
fi
failed=0
# The ideas set types once before its counted tests (KeyboardWarmUp, in
# UITests/IdeasUITestSupport.swift): on a freshly booted iPad the first idea
# typed stalled in UIKit's keyboard code in 3 of 5 runs by day (S05c's notes
# 2 and 8: Chat-me copies 3554514 and a5c44ea), and the same test by night
# passed straight after, each time. The iPhone too since 5 Nov: its first
# key, in the first test, waited 15 s to be sent and 41 s for the app to go
# idle, and the field had lost the caret (Chat-me copy 38904fd). Its result
# is noted, not counted.
if [ "$set_" = "ideas" ]; then
  warm=$SECONDS
  xcrun simctl ui "$UDID" appearance light
  if xcodebuild -project Sorta.xcodeproj -scheme Sorta -configuration Debug \
       -destination "id=$UDID" -derivedDataPath build \
       -resultBundlePath "../results/$kind-keyboard-warm-up.xcresult" \
       "-only-testing:SortaUITests/KeyboardWarmUp/testFirstKeyboard" \
       -test-timeouts-enabled YES \
       -default-test-execution-time-allowance 180 \
       -maximum-test-execution-time-allowance 180 \
       CODE_SIGNING_ALLOWED=NO \
       test-without-building > "../results/$kind-keyboard-warm-up.log" 2>&1 \
     && grep -qE "KeyboardWarmUp testFirstKeyboard\]' passed" "../results/$kind-keyboard-warm-up.log"; then
    echo "$kind: the keyboard warm-up typed an idea in $((SECONDS - warm)) seconds"
  else
    echo "::warning::$kind: the keyboard warm-up did not pass, in $((SECONDS - warm)) seconds; the counted tests follow"
  fi
fi
# Each run is <appearance>:<target>/<class>/<test>, or <appearance>:<target>
# for a whole unit-test bundle. The gallery's screenshots are step 2's
# renders of the look kit, by day and by night.
case "$set_" in
  kit)
    runs=(light:SortaUITests/LaunchTests/testLightPageIsBone
          dark:SortaUITests/LaunchTests/testDarkPageIsAubergine
          light:SortaUITests/GalleryTests/testGalleryRenders
          dark:SortaUITests/GalleryTests/testGalleryRenders
          light:SortaUITests/MapRenderTests/testEachThemeRenders
          dark:SortaUITests/MapRenderTests/testEachThemeRenders
          light:SortaUnitTests)
    allowance=300 ;;
  maps)
    runs=()
    for t in $(grep -oE 'func test[A-Za-z0-9_]+' UITests/GraphPreviewUITests.swift | awk '{print $2}'); do
      runs+=("dark:SortaUITests/GraphPreviewUITests/$t")
    done
    # the depth tests take a still at each of many poses, so a map test may
    # take longer than a kit test; none was timed in Stethoscore's runs
    allowance=900
    echo "$kind: ${#runs[@]} map tests" ;;
  ideas)
    runs=(light:SortaUITests/IdeasRenderTests/testScreensRender
          dark:SortaUITests/IdeasRenderTests/testScreensRender
          light:SortaUITests/MapModeRenderTests/testMapRenders
          dark:SortaUITests/MapModeRenderTests/testMapRenders
          light:SortaUITests/MapModeRenderTests/testLegendsNameSortasControls)
    if [ "$kind" = "iphone" ]; then
      runs=(light:SortaUITests/IdeasBehaviourTests light:SortaUITests/MapModeTests "${runs[@]}"
            light:SortaUITests/IdeasRenderTests/testLargestTextSize
            light:SortaUITests/MapModeRenderTests/testLargestTextSizeOnTheMap)
    fi
    # one test, or one launch of the whole class's: each test starts the
    # app afresh, and the longest relaunches it once
    allowance=300 ;;
  *) echo "::error::no test set $set_"; exit 2 ;;
esac
for run in "${runs[@]}"; do
  look="${run%%:*}"
  only="${run#*:}"
  test="${only##*/}"
  name="$look-${only//\//-}"
  log="../results/$kind-$name.log"
  xcrun simctl ui "$UDID" appearance "$look"
  now=$(xcrun simctl ui "$UDID" appearance | tr -d '[:space:]')
  echo "$kind: the simulator's appearance is $now, for $test"
  if [ "$now" != "$look" ]; then
    echo "::error::$kind: the simulator is $now, not $look, so $test was not run"
    failed=1
    continue
  fi
  xcodebuild -project Sorta.xcodeproj -scheme Sorta -configuration Debug \
    -destination "id=$UDID" -derivedDataPath build \
    -resultBundlePath "../results/$kind-$name.xcresult" \
    "-only-testing:$only" \
    -test-timeouts-enabled YES \
    -default-test-execution-time-allowance "$allowance" \
    -maximum-test-execution-time-allowance "$allowance" \
    CODE_SIGNING_ALLOWED=NO \
    test-without-building 2>&1 | tee "$log" | grep --line-buffered -E "error:|Test [Cc]ase|TEST |\*\* " || true
  if [ "$only" = "$test" ]; then
    # a whole bundle: every case passed, and none failed
    passed_by_name=$(grep -cE "Test [Cc]ase .*passed" "$log" || true)
    grep -qE "Test [Cc]ase .*failed" "$log" && passed_by_name=0
  elif [ "${only#*/}" = "$test" ]; then
    # a whole class: each test in its file passed, counted once by name,
    # and none failed or was skipped
    expected=$(grep -cE '^[[:space:]]*func test[A-Za-z0-9_]+\(' "UITests/$test.swift" || true)
    passed_by_name=$( (grep -E "Test [Cc]ase .*passed" "$log" || true) \
      | grep -oE "$test[ .]test[A-Za-z0-9_]+" | sort -u | wc -l | tr -d '[:space:]')
    failed_here=$( (grep -E "Test [Cc]ase .*(failed|skipped)" "$log" || true) | wc -l | tr -d '[:space:]')
    echo "$kind: $test: $passed_by_name of $expected passed, $failed_here failed or skipped"
    if [ "$passed_by_name" -ne "$expected" ] || [ "$failed_here" -ne 0 ]; then passed_by_name=0; fi
  else
    passed_by_name=$(grep -cE "[ .]$test.*passed" "$log" || true)
  fi
  if [ "$passed_by_name" -gt 0 ] && grep -qE "\*\* TEST (EXECUTE )?SUCCEEDED \*\*" "$log"; then
    echo "$kind: $test passed"
  else
    echo "::error::$kind: $test did not pass"
    failed=1
  fi
done
xcrun simctl shutdown "$UDID" || echo "::warning::$kind: the simulator didn't shut down"
exit "$failed"
