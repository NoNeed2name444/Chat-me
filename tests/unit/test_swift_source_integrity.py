from pathlib import Path


def test_swift_core_source_has_no_control_characters():
    path = (
        Path(__file__).parents[2]
        / "clients"
        / "ios"
        / "MedicalVerifierCore"
        / "Sources"
        / "MedicalVerifierCore"
        / "MedicalVerifierCore.swift"
    )
    text = path.read_text()
    offenders = [
        (index, ord(char))
        for index, char in enumerate(text)
        if ord(char) < 32 and char not in {"\n", "\r", "\t"}
    ]
    assert offenders == []
