package com.cramdown.sorta.ideas

import com.cramdown.sorta.data.Note
import java.util.Locale

/**
 * The design preview's sample ideas, the same as iOS's (Stethoscore's NoteExamples.seed and
 * GraphPreview.makeStore, ios/RedPen/Features/Notes/NoteExamples.swift:26 and
 * GraphPreview.swift:166-271): the groin hernia examples in Examples, Inguinal with Anatomy
 * inside it, and Femoral; a Cardiology folder of 14 more; a bridging idea; and two ideas in
 * no folder. Stethoscore's medical examples, kept as test data: only the instrumented tests
 * and a launch under MainActivity's map-preview extra read them, never a screen a user reaches.
 * The board places past the examples' are Sorta's (Stethoscore left Cardiology in one pile in
 * the middle), and so are three of the examples' (Direct inguinal hernia, Internal ring test
 * and Femoral hernia, which iOS's makeStore moves): no card on another, and every link, curved
 * or straight, at least 12 points clear of each card it doesn't join. The ids are a count, in
 * the order the ideas and folders are made, as iOS's makeStore gives them, so every launch
 * draws the maps alike and each link bows the same way on both platforms.
 */
object SampleIdeas {
    /** A new book holding every sample idea, as iOS's makeStore() fills its store. */
    fun book(): IdeaBook {
        var made = 0
        val book = IdeaBook(emptyList(), emptyList(), makeId = { "00000000-0000-4000-8000-%012x".format(Locale.ROOT, ++made) })
        examples(book)
        anatomy(book)
        cardiology(book)
        bridgeAndLoose(book)
        return book
    }

    private fun examples(book: IdeaBook) {
        fun add(title: String, kind: NoteKind, folder: NoteFolder, x: Double, y: Double, body: String): Note =
            book.create(title, body, kind, folder.id, BoardPoint(x, y))

        val examples = book.createFolder("Examples")
        val inguinal = book.createFolder("Inguinal", examples.id)
        val femoral = book.createFolder("Femoral", examples.id)


        // the hub everything hangs from
        add("Groin hernia", NoteKind.PAGE, examples, 0.0, 0.0, """
        Three to know: [[Indirect inguinal hernia]], [[Direct inguinal hernia]] and [[Femoral hernia]].

        - The anatomy first: [[Inguinal canal]]
        - A groin swelling with an expansile impulse on cough is a hernia until shown otherwise.
        - A hernia is a surgical disease - the only treatment is surgery. See [[Hernia repair]].
        """.trimIndent())

        // the anatomy
        val canal = add("Inguinal canal", NoteKind.PAGE, inguinal, -240.0, -150.0, """
        Develops by the descent of the testis. Runs obliquely from the deep ring to the superficial ring.

        How long is the inguinal canal? | 4 cm, running obliquely
        What does it contain in males? | Spermatic cord; ilio-inguinal nerve
        What does it contain in females? | Round ligament of the uterus; ilio-inguinal nerve

        Walls: [[Canal boundaries]]. Coverings of the cord: [[Spermatic cord coverings]].
        """.trimIndent())
        add("Canal boundaries", NoteKind.IDEA, inguinal, -470.0, -60.0, """
        - Anterior: external oblique all through, internal oblique laterally
        - Posterior: fascia transversalis all through, internal oblique medially
        - Roof: conjoined muscles (internal oblique and transversus)
        - Floor: inguinal ligament and lacunar ligament

        Remember it with [[LA/PM mnemonic]].
        """.trimIndent())
        add("LA/PM mnemonic", NoteKind.IDEA, inguinal, -470.0, -240.0, """
        Internal oblique sits in the Anterior wall Laterally, and in the Posterior wall Medially. The rest of [[Canal boundaries]] follows.
        """.trimIndent())
        val cord = add("Spermatic cord coverings", NoteKind.IDEA, inguinal, -240.0, -330.0, """
        Internal spermatic fascia | From transversalis fascia, at the internal ring
        Cremasteric muscle and fascia | From internal oblique
        External spermatic fascia | From external oblique aponeurosis, at the external ring
        """.trimIndent())

        // the two inguinal hernias, and how to tell them apart
        val indirect = add("Indirect inguinal hernia", NoteKind.PAGE, inguinal, 240.0, -150.0, """
        - 70% of all hernias; 30% bilateral; males 20 times more often
        - The defect is the stretched deep inguinal ring
        - The sac lies inside the cord and may reach the scrotum
        - Neck of the sac lateral to the [[Inferior epigastric vessels]]
        """.trimIndent())
        val direct = add("Direct inguinal hernia", NoteKind.PAGE, inguinal, 180.0, 60.0, """
        - Through the posterior wall of the canal
        - Adult or elderly, more often bilateral, hemispherical
        - Reduces backwards; complications uncommon
        - Sac medial to the [[Inferior epigastric vessels]]
        """.trimIndent())
        add("Inferior epigastric vessels", NoteKind.IDEA, inguinal, 470.0, -60.0, """
        The landmark that tells them apart: sac lateral = [[Indirect inguinal hernia]], sac medial = [[Direct inguinal hernia]].
        """.trimIndent())
        val ringTest = add("Internal ring test", NoteKind.IDEA, inguinal, 480.0, 120.0, """
        Positive (swelling controlled) = oblique, [[Indirect inguinal hernia]]. Negative = direct. The finger invagination test is obsolete.
        """.trimIndent())

        // the femoral side
        val femoralHernia = add("Femoral hernia", NoteKind.PAGE, femoral, -240.0, 150.0, """
        - Parietal peritoneum down through the [[Femoral canal]]
        - More common in women
        - Neck below and lateral to the pubic tubercle
        - Femoral ring: inguinal ligament in front, pectineal ligament behind, femoral vein laterally, lacunar ligament medially

        The great mimic: [[Saphena varix]].
        """.trimIndent())
        add("Femoral canal", NoteKind.IDEA, femoral, -430.0, 200.0, """
        Medial to the femoral vein (artery lateral to the vein). 1.25 cm long, cone shaped.
        """.trimIndent())
        add("Saphena varix", NoteKind.IDEA, femoral, -200.0, 380.0, """
        Mimics a [[Femoral hernia]]: disappears completely lying flat, fluid thrill on cough, venous hum - usually with other varicose veins.
        """.trimIndent())
        add("Hernia repair", NoteKind.IDEA, examples, 40.0, 300.0, """
        - TAPP: transabdominal preperitoneal
        - TEP: totally extraperitoneal
        - Robotic-assisted repair
        """.trimIndent())

        // links made by hand, where the connection is an idea rather than a
        // name in the text
        book.link(ringTest.id, direct.id)
        book.link(cord.id, indirect.id)
        book.link(femoralHernia.id, canal.id)
    }

    /** A subfolder inside a subfolder: Anatomy in Inguinal (GraphPreview.addAnatomy). */
    private fun anatomy(book: IdeaBook) {
        val inguinal = book.folders.firstOrNull { it.name == "Inguinal" } ?: return
        val anatomy = book.createFolder("Anatomy", inguinal.id)
        val moved = listOf("Canal boundaries", "LA/PM mnemonic", "Spermatic cord coverings")
        for (note in book.notes.filter { it.title in moved }) book.move(note.id, anatomy.id)
    }

    /** GraphPreview.addCardiology. */
    private fun cardiology(book: IdeaBook) {
        val folder = book.createFolder("Cardiology")
        // on the board in three rows under the examples, the hub in the middle
        fun add(title: String, kind: NoteKind, x: Double, y: Double, body: String): Note =
            book.create(title, body, kind, folder.id, BoardPoint(x, y))

        // a long read: a ringed gas giant
        val heartFailure = add("Heart failure", NoteKind.PAGE, 0.0, 740.0, """
        Heart failure is a syndrome, not a diagnosis: the heart cannot pump enough blood for the body's needs, or manages only at raised filling pressures. It is common, and most admissions are older people with other illnesses too.

        Types
        - HFrEF: ejection fraction 40% or less.
        - HFpEF: ejection fraction 50% or more, with raised filling pressures.
        - Mildly reduced: 41 to 49%.

        Causes
        - Ischaemic heart disease, the commonest; hypertension; valve disease.
        - Cardiomyopathy, arrhythmia, alcohol, anaemia, thyroid disease and some drugs.

        Symptoms and signs
        - Breathlessness on exertion, orthopnoea, waking breathless at night, fatigue.
        - Ankle swelling, raised JVP, basal crackles, a third heart sound, a displaced apex.

        Investigations
        - [[BNP]]: a normal level makes heart failure unlikely.
        - Echocardiogram: ejection fraction, valves and wall motion.
        - ECG and chest X-ray: cardiomegaly, upper lobe diversion, effusions.
        - Bloods: renal function, electrolytes, full blood count, thyroid, iron studies.

        Treatment
        - [[Loop diuretics]] for congestion: they ease symptoms but do not prolong life.
        - [[ACE inhibitors]] (or an ARNI), a beta-blocker, an MRA and an SGLT2 inhibitor lower mortality in HFrEF.
        - Start low and titrate; check K+ and renal function after each change.
        - An ICD or CRT for selected patients with a low ejection fraction or a wide QRS.

        Acute decompensation
        - Sit the patient up, give oxygen if hypoxic and IV furosemide, and treat the trigger: ischaemia, arrhythmia, infection or missed tablets.

        Follow-up
        - Daily weights: a gain of 2 kg in 3 days means fluid.
        - A salt and fluid plan, flu and pneumococcal vaccines, cardiac rehabilitation, and advance care planning.
        - Review within two weeks of discharge.
        """.trimIndent())
        add("BNP", NoteKind.IDEA, 240.0, 780.0, "Raised in [[Heart failure]]; a normal level makes it unlikely.")
        add("Loop diuretics", NoteKind.IDEA, -240.0, 920.0, "Furosemide for congestion in [[Heart failure]]. Watch [[Hypokalaemia]].")
        add("ACE inhibitors", NoteKind.IDEA, 240.0, 920.0, "Prognostic in [[Heart failure]]. Cough; check [[Hypokalaemia]] the other way.")
        add("Hypokalaemia", NoteKind.IDEA, 0.0, 920.0, "Flat T waves, U waves. Risk with [[Loop diuretics]].")
        val acs = add("Acute coronary syndrome", NoteKind.PAGE, -240.0, 560.0, """
        [[STEMI]], [[NSTEMI]] and unstable angina. [[Troponin]] tells them apart.
        """.trimIndent())
        add("STEMI", NoteKind.IDEA, -470.0, 560.0, "ST elevation: primary PCI. Part of [[Acute coronary syndrome]].")
        add("NSTEMI", NoteKind.IDEA, -470.0, 740.0, "Raised [[Troponin]] without ST elevation.")
        add("Troponin", NoteKind.IDEA, -240.0, 740.0, "Rises in 3 hours, peaks at 24. [[Acute coronary syndrome]].")
        val af = add("Atrial fibrillation", NoteKind.PAGE, 470.0, 560.0, """
        Irregularly irregular. Rate or rhythm control; [[CHA2DS2-VASc]] for anticoagulation.
        """.trimIndent())
        add("CHA2DS2-VASc", NoteKind.IDEA, 470.0, 740.0, "Stroke risk in [[Atrial fibrillation]].")
        val murmurs = add("Murmurs", NoteKind.PAGE, 240.0, 560.0, "Aortic stenosis, mitral regurgitation - and [[Heart failure]] as the end point.")
        // two notes with no links yet
        add("Heart sounds", NoteKind.IDEA, -470.0, 920.0, "S1 and S2; listen at the apex for S3.")
        add("Syncope", NoteKind.IDEA, 470.0, 920.0, "Cardiac or not? Exertional syncope needs an echo.")
        book.link(acs.id, heartFailure.id)
        book.link(af.id, heartFailure.id)
        book.link(murmurs.id, af.id)
        // and one from Examples (anticoagulation round a hernia repair), so
        // the two big folders are joined by a link of their own, not only
        // through the loose notes
        book.notes.firstOrNull { it.title == "Groin hernia" }?.let { groin ->
            book.link(groin.id, af.id)
        }
    }

    /** An idea in Examples linking both its subfolders, and two in no folder (GraphPreview.addBridgeAndLoose). */
    private fun bridgeAndLoose(book: IdeaBook) {
        val examples = book.folders.firstOrNull { it.name == "Examples" && it.parentId == null } ?: return
        book.create("Expansile cough impulse", """
        Felt over an [[Indirect inguinal hernia]], a [[Direct inguinal hernia]] and a [[Femoral hernia]]. A groin lump with a cough impulse is a hernia until shown otherwise.
        """.trimIndent(), NoteKind.IDEA, examples.id, BoardPoint(0.0, -360.0))
        book.create("Richter's hernia", """
        Only part of the bowel wall is trapped, so it can strangulate without obstructing. Most often through the femoral ring: see [[Femoral hernia]].
        """.trimIndent(), NoteKind.IDEA, null, BoardPoint(300.0, 180.0))
        book.create("Pericarditis", """
        Sharp chest pain, better sitting forward; saddle-shaped ST elevation in many leads and PR depression. Not a [[STEMI]].
        """.trimIndent(), NoteKind.IDEA, null, BoardPoint(-470.0, 420.0))
    }
}
