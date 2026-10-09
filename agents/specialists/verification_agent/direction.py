"""Which way a statement goes, and whether its evidence goes the same way.

The direction axis of the commercial-accuracy branch's evidence model
(medical-verifier/app/verification/evidence_model.py on
verification-layer-commercial-accuracy-v1, at 030ed8c), read for one atomic
claim against one atomic evidence statement, on the two axes Red Pen's claim
gate reads (server/claims.js, turnedAround): which way something moves or
compares (increases, reduces; higher, lower) and how much or how often it is
(high, low; common, rare). A negated statement says no way here: the
negation checks own it.
"""

import re

from agents.specialists.verification_agent.claim_reasoning import NEGATION_RE


def _way(words):
    # never the first half of a hyphenated name ("low-dose", "high-density");
    # the second half still says a way ("glucose-lowering")
    return re.compile(r"\b(?:" + words + r")\b(?!-)", re.I)


CHANGE, AMOUNT = 0, 1
AXES = (
    (
        _way(
            r"increas(?:e|es|ed|ing)|higher|greater|more|stronger"
            r"|rais(?:e|es|ed|ing)|elevat(?:e|es|ed|ing)|above"
        ),
        _way(
            r"decreas(?:e|es|ed|ing)|reduc(?:e|es|ed|ing|tion)"
            r"|lower(?:s|ed|ing)?|less|fewer|weaker|below"
        ),
    ),
    (
        _way(r"high(?:est)?|common(?:ly|est)?|frequent(?:ly)?"),
        _way(
            r"low(?:est)?|minimal|negligible|rare(?:ly)?|uncommon"
            r"|infrequent(?:ly)?|seldom"
        ),
    ),
)

# Where those words name a part or a thing, not a way: the lower limb, the
# greater trochanter, higher centres, the common bile duct, minimal change
# disease, "see below".
NOT_A_WAY = re.compile(
    r"\b(?:lower\s+(?:limbs?|lobes?|motor|o?esophag\w*|urinary|respiratory|gi"
    r"|gastrointestinal|abdom\w*|back|quadrants?|segment|uterine|chest|ribs?"
    r"|half|third|parts?|extremit\w*|legs?|airways?|poles?|borders?|eyelids?"
    r"|lips?|jaws?|limits?)"
    r"|greater\s+(?:trochanter|tuberc\w*|tuberos\w*|curvature|omentum|sciatic"
    r"|saphenous|petrosal|palatine|occipital|splanchnic|auricular|wings?|sac)"
    r"|higher\s+(?:centres?|centers?|cortical|mental)"
    r"|common\s+(?:bile|carotid|iliac|peroneal|fibular|femoral|hepatic|cold"
    r"|variable|pathway)"
    r"|minimal\s+change"
    r"|(?:as|see|described|shown|listed|discussed|mentioned|noted|outlined)"
    r"\s+(?:above|below))\b",
    re.I,
)

# A cut-off, not a way: "below 30", "more than 50%". The evidence may write
# it another way ("< 30"), so a claim's cut-off asks nothing of its words.
THRESHOLD = re.compile(
    r"\b(?:(?:more|less|greater|fewer|higher|lower)\s+than|above|below|over"
    r"|under)\s+(?=\d)",
    re.I,
)

# What a comparison is against: "than warfarin", "compared with placebo".
AGAINST = re.compile(
    r"\b(?:than|compared (?:with|to)|in comparison (?:with|to)|versus|vs"
    r"|relative to)\b",
    re.I,
)


def _blank(pattern, text):
    return pattern.sub(lambda m: " " * len(m.group(0)), text)


def directions(text, *, cut_offs=True):
    """Each axis's way: 1 up, -1 down, 0 both ways, None no way.

    None for a negated statement.
    """
    if NEGATION_RE.search(text):
        return None
    read = _blank(NOT_A_WAY, text)
    if not cut_offs:
        read = _blank(THRESHOLD, read)
    signs = []
    for up, down in AXES:
        ups = bool(up.search(read))
        downs = bool(down.search(read))
        signs.append(0 if ups and downs else 1 if ups else -1 if downs else None)
    return tuple(signs)


def _words(text):
    return {
        token
        for token in re.findall(r"[a-z0-9'-]+", text.lower())
        if len(token) >= 4
    }


def _sides(text):
    marker = AGAINST.search(text)
    if not marker:
        return None
    return _words(text[:marker.start()]), _words(text[marker.end():])


def _comparison_swapped(claim, evidence):
    # "warfarin has a higher risk than DOACs" says what "DOACs have a lower
    # risk than warfarin" says
    claim_sides = _sides(claim)
    evidence_sides = _sides(evidence)
    if not claim_sides or not evidence_sides:
        return False
    claim_subject, claim_against = claim_sides
    evidence_subject, evidence_against = evidence_sides
    return (
        bool(claim_against & evidence_subject)
        and bool(evidence_against & claim_subject)
        and not claim_against & evidence_against
    )


def direction_entailed(claim, evidence):
    """Whether evidence that otherwise matches the claim goes the claim's way.

    A claim that goes one way is not supported by evidence that goes the
    other ("a reduced risk" for "an increased risk"), nor by evidence that
    says no way at all ("a stronger effect" for "interacts with").
    """
    claim_ways = directions(claim)
    evidence_ways = directions(evidence)
    if claim_ways is None or evidence_ways is None:
        return True, None

    evidence_change = evidence_ways[CHANGE]
    if evidence_change and _comparison_swapped(claim, evidence):
        evidence_change = -evidence_change
    evidence_ways = (evidence_change, evidence_ways[AMOUNT])

    for claim_way, evidence_way in zip(claim_ways, evidence_ways):
        if claim_way and evidence_way and claim_way == -evidence_way:
            return False, "atomic_direction_mismatch"

    claim_stated = directions(claim, cut_offs=False)
    if any(claim_stated) and not any(
        way is not None for way in evidence_ways
    ):
        return False, "atomic_direction_not_entailed"

    return True, None
