def deterministic_checks(claim):
    t=claim.lower(); flags=[]
    if 'ignore previous instructions' in t or 'ignore all previous' in t:
        flags.append('prompt_injection_text_detected')
    return flags
