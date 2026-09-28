import re
from dataclasses import dataclass
from app.verification.claim_reasoning import decompose_claim, relation_entailed, safety_relation_entailed, temporal_entailed
RELATION_CLASSES={"causal":("cause","causes","caused","leads to","result in","results in","prevent","prevents"),"risk_increase":("increase","increases","increased","raises","elevates","higher"),"risk_decrease":("reduce","reduces","reduced","lowers","decrease","decreases","decreased"),"association":("associated with","association","correlated with","linked to"),"safety":("safe","safely","dangerous","harmful","harm","adverse","contraindicated","contraindication"),"effectiveness":("effective","effectiveness","efficacy","works")}
POPULATION_TERMS=("adult","adults","child","children","pediatric","elderly","pregnancy","pregnant","breastfeeding","renal","kidney","hepatic","liver")
NEGATION=re.compile(r"\b(no|not|never|without|does not|doesn't|cannot|can't)\b",re.I)
DOUBLE_NEGATION_EQUIVALENTS={"not uncommon":"common","not unlikely":"likely","not impossible":"possible"}
@dataclass(frozen=True)
class IndependentEntailment:
 label:str
 reasons:tuple[str,...]
def _normalize_multilingual_terms(text):
 result=text.lower()
 for source,replacement in {"no aumenta":"does not increase","ne augmente pas":"does not increase","n'augmente pas":"does not increase","no causa":"does not cause","ne cause pas":"does not cause","n'est pas sûr":"is not safe","no es seguro":"is not safe","aumenta":"increases","augmente":"increases","reduce":"reduces","réduit":"reduces","causa":"causes","causado":"caused","asociado con":"associated with","associé à":"associated with","asociado a":"associated with","seguro":"safe","sûr":"safe","efectivo":"effective","efficace":"effective","pacientes":"patients","niños":"children","enfants":"children","adultos":"adults","adultes":"adults","glucosa":"glucose"}.items(): result=result.replace(source,replacement)
 return result
def _normalize_double_negation(text):
 result=_normalize_multilingual_terms(text)
 for source,replacement in DOUBLE_NEGATION_EQUIVALENTS.items(): result=result.replace(source,replacement)
 return result
def _tokens(text): return {t for t in re.findall(r"[a-z0-9'-]+",text.lower()) if len(t)>=4}
def _relation_class(text):
 lower=_normalize_double_negation(text); matches=[c for c,w in RELATION_CLASSES.items() if any(x in lower for x in w)]; return matches[0] if len(matches)==1 else "mixed"
def _numbers(text): return set(re.findall(r"\b\d+(?:\.\d+)?\b",text.lower()))
def _normalize_measurement(value,unit):
 unit=unit.lower()
 if unit in {"mcg","ug"}: return float(value)*.001,"mg"
 if unit=="g": return float(value)*1000,"mg"
 if unit=="kg": return float(value)*1000000,"mg"
 if unit=="l": return float(value)*1000,"ml"
 if unit in {"%","percent"}: return float(value),"percent"
 return float(value),unit
def _measurements(text):
 return {(round(v,9),u) for v,u in (_normalize_measurement(n,u) for n,u in re.findall(r"\b(\d+(?:\.\d+)?)\s*(mg|g|mcg|ug|kg|ml|l|mmol|mmhg|%|percent)\b",text.lower()))}
def _frequency_multiplier(text):
 lower=text.lower()
 for pattern,value in [(r"\b(twice|2\s+times)(?:\s+a)?\s+(?:day|daily)\b|\bbid\b",2.),(r"\bthree\s+times(?:\s+a)?\s+(?:day|daily)\b|\btid\b",3.),(r"\bfour\s+times(?:\s+a)?\s+(?:day|daily)\b|\bqid\b",4.),(r"\bonce(?:\s+a)?\s+(?:day|daily)\b|\bdaily\b|\bqd\b",1.)]:
  if re.search(pattern,lower): return value
 match=re.search(r"\bevery\s+(\d+)\s*(?:hours?|h)\b|\bq(\d+)h\b",lower)
 if match:
  hours=int(next(v for v in match.groups() if v)); return 24./hours if 0<hours<=24 else None
 if re.search(r"\b(?:once\s+a\s+week|weekly)\b",lower): return 1/7
 if re.search(r"\btwice\s+(?:a\s+)?week\b",lower): return 2/7
 return None
def _daily_mass_dose(text):
 lower=text.lower()
 if re.search(r"\b\d+(?:\.\d+)?\s*(?:mg|g|mcg|ug)\s*(?:/|per)\s*(?:ml|l)\b",lower) or re.search(r"\b\d+(?:\.\d+)?\s*(?:mg|g|mcg|ug)\s*/\s*kg\b",lower): return None
 values=re.findall(r"\b(\d+(?:\.\d+)?)\s*(mg|g|mcg|ug)\b",lower); multiplier=_frequency_multiplier(text)
 if len(values)!=1 or multiplier is None: return None
 value,unit=_normalize_measurement(*values[0]); return round(value*multiplier,9) if unit=="mg" else None
def _concentration_daily_dose(text):
 lower=text.lower(); concentration=re.findall(r"\b(\d+(?:\.\d+)?)\s*(mg|g|mcg|ug)\s*(?:/|per)\s*(ml|l)\b",lower); volume=re.findall(r"\b(\d+(?:\.\d+)?)\s*(ml|l)\b",lower); multiplier=_frequency_multiplier(text)
 if len(concentration)!=1 or len(volume)!=1 or multiplier is None: return None
 mass,mu=_normalize_measurement(concentration[0][0],concentration[0][1]); vol,vu=_normalize_measurement(volume[0][0],volume[0][1])
 return round(mass*vol*multiplier,9) if mu=="mg" and vu=="ml" else None
def _weight_based_daily_dose(text):
 lower=text.lower(); dose=re.findall(r"\b(\d+(?:\.\d+)?)\s*(mg|g|mcg|ug)\s*/\s*kg(\s*/\s*day)?\b",lower); weights=re.findall(r"\b(\d+(?:\.\d+)?)\s*kg\b",lower)
 if len(dose)!=1 or len(weights)!=1: return None
 mass,unit=_normalize_measurement(dose[0][0],dose[0][1]); per_day=1. if dose[0][2] else _frequency_multiplier(text)
 return round(mass*float(weights[0])*per_day,9) if unit=="mg" and per_day is not None else None
def _daily_dose_equivalent(text):
 for f in (_daily_mass_dose,_concentration_daily_dose,_weight_based_daily_dose):
  value=f(text)
  if value is not None: return value
 return None
def _measurement_kind(text):
 lower=text.lower()
 for phrase,kind in (("percentage point","percentage_points"),("%","percent"),("percent","percent"),("odds ratio","odds_ratio"),("hazard ratio","hazard_ratio"),("relative risk","relative_risk"),("absolute risk","absolute_risk")):
  if phrase in lower:return kind
 return None
def _populations(text): return {t for t in POPULATION_TERMS if t in text.lower()}
def _condition_signatures(text):
 lower=" ".join(text.lower().split()); patterns=(r"\bif\s+([^,.;:]+)",r"\bonly if\s+([^,.;:]+)",r"\bunless\s+([^,.;:]+)",r"\bwhen\s+([^,.;:]+)",r"\bprovided that\s+([^,.;:]+)",r"\bin patients with\s+([^,.;:]+)",r"\bfor patients with\s+([^,.;:]+)"); out=[]
 for pattern in patterns:
  for match in re.finditer(pattern,lower):
   sig={t for t in re.findall(r"[a-z0-9'-]+",match.group(1)) if len(t)>=4}
   if sig:out.append(sig)
 return out
def _scope_strength(text):
 lower=text.lower(); return {"universal":any(x in lower for x in ("all patients","all people","everyone","every patient","always","never","regardless of")),"exclusive":any(x in lower for x in ("only","exclusively","only if"))}
def _condition_supported(claim,evidence):
 cc=_condition_signatures(claim); ec=_condition_signatures(evidence)
 if not ec:return True,None
 if not cc:return False,"conditional_scope_missing"
 for source in ec:
  best=max((len(source&candidate)/max(1,len(source)) for candidate in cc),default=0.)
  if best<.7:return False,"condition_not_entrailed"
 return True,None
def _atomic_alignment(claim,evidence):
 ca=decompose_claim(claim); ea=decompose_claim(evidence)
 if not ca:return True,None
 if not ea:return False,"no_atomic_evidence_claim"
 for claim_atom in ca:
  ct=_tokens(claim_atom.text); matched=False; failures=[]
  for evidence_atom in (a for a in ea if a.text):
   if len(ct&_tokens(evidence_atom.text))/max(1,len(ct))<.55:continue
   ok,reason=relation_entailed(claim_atom,evidence_atom)
   if not ok:
    if reason:failures.append(reason)
    continue
   ok,reason=temporal_entailed(claim_atom,evidence_atom)
   if not ok:
    if reason:failures.append(reason)
    continue
   ok,reason=safety_relation_entailed(claim_atom,evidence_atom)
   if not ok:
    if reason:failures.append(reason)
    continue
   if claim_atom.polarity!=evidence_atom.polarity:return False,"atomic_polarity_mismatch"
   matched=True;break
  if not matched:return False,(failures[0] if failures else "atomic_claim_not_entailed")
 return True,None
def verify(claim,evidence):
 claim_for_logic=_normalize_double_negation(claim); evidence_for_logic=_normalize_double_negation(evidence); ct=_tokens(claim_for_logic); et=_tokens(evidence_for_logic)
 if not ct:return IndependentEntailment("UNKNOWN",("empty_claim_tokens",))
 claim_daily=_daily_dose_equivalent(claim_for_logic); evidence_daily=_daily_dose_equivalent(evidence_for_logic); daily_equivalent=claim_daily is not None and evidence_daily is not None and abs(claim_daily-evidence_daily)<1e-9
 if len(ct&et)/len(ct)<.5:
  if not daily_equivalent:return IndependentEntailment("UNKNOWN",("insufficient_semantic_overlap",))
  if not ((ct&et)-_numbers(claim_for_logic)):return IndependentEntailment("UNKNOWN",("insufficient_semantic_overlap",))
 ok,reason=_atomic_alignment(claim_for_logic,evidence_for_logic)
 if not ok:
  if not (daily_equivalent and reason in {"atomic_claim_not_entailed","atomic_object_mismatch","atomic_relation_mismatch"}):return IndependentEntailment("UNKNOWN",(reason,))
 cr=_relation_class(claim_for_logic); er=_relation_class(evidence_for_logic)
 if cr=="causal" and er=="association":return IndependentEntailment("UNKNOWN",("causal_vs_associative_mismatch",))
 if cr!="mixed" and er not in {cr,"mixed"}:return IndependentEntailment("UNKNOWN",("relation_class_mismatch",))
 ok,reason=_condition_supported(claim_for_logic,evidence_for_logic)
 if not ok:return IndependentEntailment("UNKNOWN",(reason,))
 cs=_scope_strength(claim_for_logic); es=_scope_strength(evidence_for_logic)
 if cs["universal"] and not es["universal"]:return IndependentEntailment("UNKNOWN",("universal_scope_not_entrailed",))
 if cs["exclusive"] and not es["exclusive"]:return IndependentEntailment("UNKNOWN",("exclusive_scope_not_entrailed",))
 cm=_measurement_kind(claim_for_logic); em=_measurement_kind(evidence_for_logic); cme=_measurements(claim_for_logic); eme=_measurements(evidence_for_logic)
 if cme and not cme.issubset(eme) and not daily_equivalent:return IndependentEntailment("UNKNOWN",("measurement_unit_or_value_mismatch",))
 cf=_frequency_multiplier(claim_for_logic); ef=_frequency_multiplier(evidence_for_logic)
 if cf is not None and ef is not None and abs(cf-ef)>1e-9 and not daily_equivalent:return IndependentEntailment("UNKNOWN",("dose_frequency_mismatch",))
 if cm and em and cm!=em:return IndependentEntailment("UNKNOWN",("risk_measurement_type_mismatch",))
 nums=_numbers(claim_for_logic)
 if nums and not nums.issubset(_numbers(evidence_for_logic)) and not daily_equivalent:return IndependentEntailment("UNKNOWN",("numeric_values_not_entrailed",))
 cp=_populations(claim_for_logic); ep=_populations(evidence_for_logic)
 if cp and not cp.issubset(ep):return IndependentEntailment("UNKNOWN",("population_not_entrailed",))
 if bool(NEGATION.search(claim_for_logic))!=bool(NEGATION.search(evidence_for_logic)):return IndependentEntailment("CONTRADICTS",("claim_evidence_polarity_mismatch",))
 return IndependentEntailment("SUPPORTS",("independent_structured_checks_passed",))
