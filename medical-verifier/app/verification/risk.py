def classify_risk(claim,context):
    t=(claim+' '+str(context)).lower()
    if any(x in t for x in ('suicide','self-harm','overdose','poisoning')): return 'critical'
    if any(x in t for x in ('pregnancy','pregnant','infant','newborn','anaphylaxis','emergency','insulin','chemotherapy','anticoagulant')): return 'high'
    if any(x in t for x in ('dose','interaction','contraindication','diagnosis','child','pediatric','renal','kidney','liver')): return 'moderate'
    return 'low'

def missing_context(claim,context):
    t=claim.lower(); out=[]
    if any(x in t for x in ('dose','safe','interaction','contraindication')):
        if 'current_medications' not in context and 'medications' not in context: out.append('current_medications')
        if 'age' not in context: out.append('age')
    if 'pregnan' in t and 'gestational_age' not in context: out.append('gestational_age')
    return sorted(set(out))
