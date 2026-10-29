package com.cramdown.sorta.map

// The design preview's store as iOS NeuronHierarchyTests.swift builds it (lines 54-213: GraphPreview.makeStore's
// notes, with the same copy CosmicHierarchyTests uses), carried over by a script so the text is exact.
object NeuronPreviewData {
    class Draft(val title: String, val page: Boolean, val folder: String?, val body: String)

    val heartFailureBody: String = "Heart failure is a syndrome, not a diagnosis: the heart cannot pump enough blood for the body's needs, or manages only at raised filling pressures. It is common, and most admissions are older people with other illnesses too.\n\nTypes\n- HFrEF: ejection fraction 40% or less.\n- HFpEF: ejection fraction 50% or more, with raised filling pressures.\n- Mildly reduced: 41 to 49%.\n\nCauses\n- Ischaemic heart disease, the commonest; hypertension; valve disease.\n- Cardiomyopathy, arrhythmia, alcohol, anaemia, thyroid disease and some drugs.\n\nSymptoms and signs\n- Breathlessness on exertion, orthopnoea, waking breathless at night, fatigue.\n- Ankle swelling, raised JVP, basal crackles, a third heart sound, a displaced apex.\n\nInvestigations\n- [[BNP]]: a normal level makes heart failure unlikely.\n- Echocardiogram: ejection fraction, valves and wall motion.\n- ECG and chest X-ray: cardiomegaly, upper lobe diversion, effusions.\n- Bloods: renal function, electrolytes, full blood count, thyroid, iron studies.\n\nTreatment\n- [[Loop diuretics]] for congestion: they ease symptoms but do not prolong life.\n- [[ACE inhibitors]] (or an ARNI), a beta-blocker, an MRA and an SGLT2 inhibitor lower mortality in HFrEF.\n- Start low and titrate; check K+ and renal function after each change.\n- An ICD or CRT for selected patients with a low ejection fraction or a wide QRS.\n\nAcute decompensation\n- Sit the patient up, give oxygen if hypoxic and IV furosemide, and treat the trigger: ischaemia, arrhythmia, infection or missed tablets.\n\nFollow-up\n- Daily weights: a gain of 2 kg in 3 days means fluid.\n- A salt and fluid plan, flu and pneumococcal vaccines, cardiac rehabilitation, and advance care planning.\n- Review within two weeks of discharge."

    val drafts: List<Draft> = listOf(
        Draft("Groin hernia", true, "Examples", "Three to know: [[Indirect inguinal hernia]], [[Direct inguinal hernia]] and [[Femoral hernia]].\n\n- The anatomy first: [[Inguinal canal]]\n- A groin swelling with an expansile impulse on cough is a hernia until shown otherwise.\n- A hernia is a surgical disease - the only treatment is surgery. See [[Hernia repair]]."),
        Draft("Inguinal canal", true, "Inguinal", "Develops by the descent of the testis. Runs obliquely from the deep ring to the superficial ring.\n\nHow long is the inguinal canal? | 4 cm, running obliquely\nWhat does it contain in males? | Spermatic cord; ilio-inguinal nerve\nWhat does it contain in females? | Round ligament of the uterus; ilio-inguinal nerve\n\nWalls: [[Canal boundaries]]. Coverings of the cord: [[Spermatic cord coverings]]."),
        Draft("Canal boundaries", false, "Anatomy", "- Anterior: external oblique all through, internal oblique laterally\n- Posterior: fascia transversalis all through, internal oblique medially\n- Roof: conjoined muscles (internal oblique and transversus)\n- Floor: inguinal ligament and lacunar ligament\n\nRemember it with [[LA/PM mnemonic]]."),
        Draft("LA/PM mnemonic", false, "Anatomy", "Internal oblique sits in the Anterior wall Laterally, and in the Posterior wall Medially. The rest of [[Canal boundaries]] follows."),
        Draft("Spermatic cord coverings", false, "Anatomy", "Internal spermatic fascia | From transversalis fascia, at the internal ring\nCremasteric muscle and fascia | From internal oblique\nExternal spermatic fascia | From external oblique aponeurosis, at the external ring"),
        Draft("Indirect inguinal hernia", true, "Inguinal", "- 70% of all hernias; 30% bilateral; males 20 times more often\n- The defect is the stretched deep inguinal ring\n- The sac lies inside the cord and may reach the scrotum\n- Neck of the sac lateral to the [[Inferior epigastric vessels]]"),
        Draft("Direct inguinal hernia", true, "Inguinal", "- Through the posterior wall of the canal\n- Adult or elderly, more often bilateral, hemispherical\n- Reduces backwards; complications uncommon\n- Sac medial to the [[Inferior epigastric vessels]]"),
        Draft("Inferior epigastric vessels", false, "Inguinal", "The landmark that tells them apart: sac lateral = [[Indirect inguinal hernia]], sac medial = [[Direct inguinal hernia]]."),
        Draft("Internal ring test", false, "Inguinal", "Positive (swelling controlled) = oblique, [[Indirect inguinal hernia]]. Negative = direct. The finger invagination test is obsolete."),
        Draft("Femoral hernia", true, "Femoral", "- Parietal peritoneum down through the [[Femoral canal]]\n- More common in women\n- Neck below and lateral to the pubic tubercle\n- Femoral ring: inguinal ligament in front, pectineal ligament behind, femoral vein laterally, lacunar ligament medially\n\nThe great mimic: [[Saphena varix]]."),
        Draft("Femoral canal", false, "Femoral", "Medial to the femoral vein (artery lateral to the vein). 1.25 cm long, cone shaped."),
        Draft("Saphena varix", false, "Femoral", "Mimics a [[Femoral hernia]]: disappears completely lying flat, fluid thrill on cough, venous hum - usually with other varicose veins."),
        Draft("Hernia repair", false, "Examples", "- TAPP: transabdominal preperitoneal\n- TEP: totally extraperitoneal\n- Robotic-assisted repair"),
        Draft("Heart failure", true, "Cardiology", heartFailureBody),
        Draft("BNP", false, "Cardiology", "Raised in [[Heart failure]]; a normal level makes it unlikely."),
        Draft("Loop diuretics", false, "Cardiology", "Furosemide for congestion in [[Heart failure]]. Watch [[Hypokalaemia]]."),
        Draft("ACE inhibitors", false, "Cardiology", "Prognostic in [[Heart failure]]. Cough; check [[Hypokalaemia]] the other way."),
        Draft("Hypokalaemia", false, "Cardiology", "Flat T waves, U waves. Risk with [[Loop diuretics]]."),
        Draft("Acute coronary syndrome", true, "Cardiology", "[[STEMI]], [[NSTEMI]] and unstable angina. [[Troponin]] tells them apart."),
        Draft("STEMI", false, "Cardiology", "ST elevation: primary PCI. Part of [[Acute coronary syndrome]]."),
        Draft("NSTEMI", false, "Cardiology", "Raised [[Troponin]] without ST elevation."),
        Draft("Troponin", false, "Cardiology", "Rises in 3 hours, peaks at 24. [[Acute coronary syndrome]]."),
        Draft("Atrial fibrillation", true, "Cardiology", "Irregularly irregular. Rate or rhythm control; [[CHA2DS2-VASc]] for anticoagulation."),
        Draft("CHA2DS2-VASc", false, "Cardiology", "Stroke risk in [[Atrial fibrillation]]."),
        Draft("Murmurs", true, "Cardiology", "Aortic stenosis, mitral regurgitation - and [[Heart failure]] as the end point."),
        Draft("Heart sounds", false, "Cardiology", "S1 and S2; listen at the apex for S3."),
        Draft("Syncope", false, "Cardiology", "Cardiac or not? Exertional syncope needs an echo."),
        Draft("Expansile cough impulse", false, "Examples", "Felt over an [[Indirect inguinal hernia]], a [[Direct inguinal hernia]] and a [[Femoral hernia]]. A groin lump with a cough impulse is a hernia until shown otherwise."),
        Draft("Richter's hernia", false, null, "Only part of the bowel wall is trapped, so it can strangulate without obstructing. Most often through the femoral ring: see [[Femoral hernia]]."),
        Draft("Pericarditis", false, null, "Sharp chest pain, better sitting forward; saddle-shaped ST elevation in many leads and PR depression. Not a [[STEMI]]."),
    )

    val handLinks: List<Pair<String, String>> = listOf(
        "Internal ring test" to "Direct inguinal hernia",
        "Spermatic cord coverings" to "Indirect inguinal hernia",
        "Femoral hernia" to "Inguinal canal",
        "Acute coronary syndrome" to "Heart failure",
        "Atrial fibrillation" to "Heart failure",
        "Murmurs" to "Atrial fibrillation",
        "Groin hernia" to "Atrial fibrillation",
    )
}
