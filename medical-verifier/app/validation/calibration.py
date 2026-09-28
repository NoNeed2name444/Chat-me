class IsotonicCalibrator:
    """
    Pure-Python pool-adjacent-violators isotonic calibrator.

    It maps raw verifier confidence scores to monotonically increasing
    calibrated probabilities using clinician-labeled ground truth.
    """

    def __init__(self):
        self._thresholds = []
        self._values = []

    def fit(self, scores, truths):
        if len(scores) != len(truths):
            raise ValueError("scores and truths must have equal length")
        if not scores:
            raise ValueError("at least one calibration example is required")

        pairs = sorted(
            (float(score), float(truth))
            for score, truth in zip(scores, truths)
        )

        blocks = [
            {
                "lo": score,
                "hi": score,
                "weight": 1.0,
                "sum": truth,
            }
            for score, truth in pairs
        ]

        index = 0

        while index < len(blocks) - 1:
            left = blocks[index]
            right = blocks[index + 1]

            left_mean = left["sum"] / left["weight"]
            right_mean = right["sum"] / right["weight"]

            if left_mean <= right_mean:
                index += 1
                continue

            merged = {
                "lo": left["lo"],
                "hi": right["hi"],
                "weight": left["weight"] + right["weight"],
                "sum": left["sum"] + right["sum"],
            }

            blocks[index:index + 2] = [merged]

            if index > 0:
                index -= 1

        self._thresholds = [
            block["hi"]
            for block in blocks
        ]

        self._values = [
            block["sum"] / block["weight"]
            for block in blocks
        ]

        return self

    def predict(self, score):
        if not self._thresholds:
            raise RuntimeError("calibrator has not been fit")

        score = float(score)

        for threshold, value in zip(
            self._thresholds,
            self._values,
        ):
            if score <= threshold:
                return value

        return self._values[-1]
