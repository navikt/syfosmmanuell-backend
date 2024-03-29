package no.nav.syfo

import no.nav.syfo.diagnose.Kodeverk

enum class ICD10(
    override val codeValue: String,
    override val text: String,
    val icpc2: List<ICPC2>,
    override val oid: String = "2.16.578.1.12.4.1.1.7110"
) : Kodeverk {
    Z000(
        "Z000",
        "Kontakt med helsetjenesten for generell helseundersøkelse",
        listOf(ICPC2.NEGATIVE_30)
    ),
    Z019(
        "Z019",
        "Kontakt med helsetjenesten for uspesifisert befolkningsundersøkelse hos person uten symptom eller registrert diagnose",
        listOf(ICPC2.NEGATIVE_31)
    ),
    Z015(
        "Z015",
        "Kontakt med helsetjenesten for diagnostisk hud- eller sensitivitetsprøve hos person uten symptom eller registrert diagnose",
        listOf(ICPC2.NEGATIVE_32)
    ),
    Z017(
        "Z017",
        "Kontakt med helsetjenesten for laboratorieundersøkelse hos person uten symptom eller registrert diagnose",
        listOf(
            ICPC2.NEGATIVE_33,
            ICPC2.NEGATIVE_34,
            ICPC2.NEGATIVE_35,
            ICPC2.NEGATIVE_36,
            ICPC2.NEGATIVE_37,
            ICPC2.NEGATIVE_38
        )
    ),
    Z018(
        "Z018",
        "Kontakt med helsetjenesten for annen spesifisert undersøkelse hos person uten symptom eller registrert diagnose",
        listOf(ICPC2.NEGATIVE_39, ICPC2.NEGATIVE_40, ICPC2.NEGATIVE_42)
    ),
    Z016(
        "Z016",
        "Kontakt med helsetjenesten for radiologisk undersøkelse ikke klassifisert annet sted hos person uten symptom eller registrert diagnose",
        listOf(ICPC2.NEGATIVE_41)
    ),
    Z048(
        "Z048",
        "Undersøkelse eller observasjon av annen spesifisert årsak",
        listOf(
            ICPC2.NEGATIVE_43,
            ICPC2.NEGATIVE_63,
            ICPC2.NEGATIVE_66,
            ICPC2.NEGATIVE_67,
            ICPC2.NEGATIVE_68
        )
    ),
    Z299(
        "Z299",
        "Behov for ikke spesifisert forebyggende tiltak i forbindelse med smittsom sykdom",
        listOf(ICPC2.NEGATIVE_44)
    ),
    Z719(
        "Z719",
        "Kontakt med helsetjenesten for uspesifisert rådgivning eller veiledning",
        listOf(ICPC2.NEGATIVE_45, ICPC2.NEGATIVE_58)
    ),
    Z769(
        "Z769",
        "Kontakt med helsetjenesten under uspesifisert omstendighet",
        listOf(ICPC2.NEGATIVE_46, ICPC2.NEGATIVE_47, ICPC2.NEGATIVE_48, ICPC2.NEGATIVE_69)
    ),
    Z298(
        "Z298",
        "Behov for annet spesifisert forebyggende tiltak i forbindelse med smittsom sykdom",
        listOf(ICPC2.NEGATIVE_49)
    ),
    Z768(
        "Z768",
        "Kontakt med helsetjenesten under annen spesifisert omstendighet",
        listOf(ICPC2.NEGATIVE_50, ICPC2.NEGATIVE_59)
    ),
    Z518(
        "Z518",
        "Kontakt med helsetjenesten for annet spesifisert behandlingstiltak",
        listOf(
            ICPC2.NEGATIVE_51,
            ICPC2.NEGATIVE_52,
            ICPC2.NEGATIVE_53,
            ICPC2.NEGATIVE_54,
            ICPC2.NEGATIVE_56,
            ICPC2.A98
        )
    ),
    Z512("Z512", "Kontakt med helsetjenesten for annen kjemoterapi", listOf(ICPC2.NEGATIVE_55)),
    Z509(
        "Z509",
        "Kontakt med helsetjenesten for behandling som omfatter bruk av uspesifisert rehabiliteringstiltak",
        listOf(ICPC2.NEGATIVE_57)
    ),
    Z712("Z712", "Kontakt for å få forklaring på undersøkelsesresultat", listOf(ICPC2.NEGATIVE_60)),
    Z929(
        "Z929",
        "Opplysning om uspesifisert tidligere medisinsk behandling",
        listOf(ICPC2.NEGATIVE_61)
    ),
    Z029(
        "Z029",
        "Kontakt med helsetjenesten for uspesifisert undersøkelse for administrative formål",
        listOf(ICPC2.NEGATIVE_62, ICPC2.A97)
    ),
    Z099(
        "Z099",
        "Kontakt med helsetjenesten for etterunderøkelse etter uspesifisert behandling for annen tilstand",
        listOf(ICPC2.NEGATIVE_64, ICPC2.NEGATIVE_65)
    ),
    R529("R529", "Uspesifisert smerte", listOf(ICPC2.A01)),
    R688(
        "R688",
        "Annet spesifisert generelt symptom eller tegn",
        listOf(ICPC2.A02, ICPC2.A08, ICPC2.A29, ICPC2.B04, ICPC2.B29, ICPC2.W29)
    ),
    R509("R509", "Uspesifisert feber", listOf(ICPC2.A03)),
    R53("R53", "Uvelhet eller tretthet", listOf(ICPC2.A04, ICPC2.A05)),
    R55("R55", "Synkope eller kollaps", listOf(ICPC2.A06)),
    R402("R402", "Uspesifisert koma", listOf(ICPC2.A07)),
    R619("R619", "Uspesifisert hyperhidrose", listOf(ICPC2.A09)),
    R58("R58", "Blødning ikke klassifisert annet sted", listOf(ICPC2.A10)),
    R074("R074", "Uspesifisert brystsmerte", listOf(ICPC2.A11)),
    Z711(
        "Z711",
        "Frykt for lidelse der ingen diagnose er stilt",
        listOf(
            ICPC2.A13,
            ICPC2.A25,
            ICPC2.A26,
            ICPC2.A27,
            ICPC2.B25,
            ICPC2.B26,
            ICPC2.B27,
            ICPC2.D26,
            ICPC2.D27,
            ICPC2.F27,
            ICPC2.H27,
            ICPC2.K24,
            ICPC2.K25,
            ICPC2.K27,
            ICPC2.L26,
            ICPC2.L27,
            ICPC2.N26,
            ICPC2.N27,
            ICPC2.P27,
            ICPC2.R26,
            ICPC2.R27,
            ICPC2.S26,
            ICPC2.S27,
            ICPC2.T26,
            ICPC2.T27,
            ICPC2.U26,
            ICPC2.U27,
            ICPC2.W02,
            ICPC2.W27,
            ICPC2.X23,
            ICPC2.X24,
            ICPC2.X25,
            ICPC2.X26,
            ICPC2.X27,
            ICPC2.Y24,
            ICPC2.Y25,
            ICPC2.Y26,
            ICPC2.Y27,
            ICPC2.Z27
        )
    ),
    R681("R681", "Uspesifikt symptom typisk for spedbarn", listOf(ICPC2.A16)),
    R468(
        "R468",
        "Annet spesifisert symptom eller tegn med tilknytning til utseende eller atferd",
        listOf(ICPC2.A18, ICPC2.H15, ICPC2.W21, ICPC2.X22)
    ),
    Z718(
        "Z718",
        "Kontakt med helsetjenesten for annen spesifisert rådgivning eller veiledning",
        listOf(ICPC2.A20)
    ),
    Z809(
        "Z809",
        "Opplysning om uspesifisert ondartet svulst i familieanamnesen",
        listOf(ICPC2.A21)
    ),
    Z918(
        "Z918",
        "Opplysning om annen spesifisert risikofaktor i egen sykehistorie, ikke klassifisert annet sted",
        listOf(ICPC2.A23)
    ),
    Z736(
        "Z736",
        "Problem i forbindelse med begrensning i aktivitet grunnet uførhet",
        listOf(
            ICPC2.A28,
            ICPC2.B28,
            ICPC2.D28,
            ICPC2.F28,
            ICPC2.H28,
            ICPC2.K28,
            ICPC2.L28,
            ICPC2.N28,
            ICPC2.P28,
            ICPC2.R28,
            ICPC2.S28,
            ICPC2.T28,
            ICPC2.U28,
            ICPC2.W28,
            ICPC2.X28,
            ICPC2.Y28
        )
    ),
    A169(
        "A169",
        "Tuberkulose i uspesifisert åndedrettsorgan, uten nevnt bakteriologisk eller histologisk bekreftelse",
        listOf(ICPC2.A70)
    ),
    B059("B059", "Meslinger uten komplikasjoner", listOf(ICPC2.A71)),
    B019("B019", "Varicella uten komplikasjoner", listOf(ICPC2.A72)),
    B54("B54", "Uspesifisert malaria", listOf(ICPC2.A73)),
    B069("B069", "Rubella uten komplikasjoner", listOf(ICPC2.A74)),
    B279("B279", "Uspesifisert mononukleose", listOf(ICPC2.A75)),
    B09(
        "B09",
        "Uspesifisert virusinfeksjon kjennetegnet ved hud- eller slimhinnelesjon",
        listOf(ICPC2.A76)
    ),
    B349("B349", "Uspesifisert virusinfeksjon", listOf(ICPC2.A77)),
    B99("B99", "Annen eller uspesifisert infeksjonssykdom", listOf(ICPC2.A78)),
    C809("C809", "Ondartet svulst med uspesifisert utgangspunkt", listOf(ICPC2.A79)),
    T149(
        "T149",
        "Uspesifisert skade i uspesifisert kroppsregion",
        listOf(ICPC2.A80, ICPC2.B77, ICPC2.W75)
    ),
    T07("T07", "Flere uspesifiserte skader", listOf(ICPC2.A81)),
    T941("T941", "Følgetilstand etter skade i uspesifisert kroppsregion", listOf(ICPC2.A82)),
    T4n(
        "T4n",
        "Forgiftning med terapeutisk legemiddel eller biologisk substans",
        listOf(ICPC2.A84)
    ),
    T887("T887", "Annen eller ikke spesifisert bivirkning av legemiddel", listOf(ICPC2.A85)),
    T659("T659", "Toksisk virkning av uspesifisert stoff", listOf(ICPC2.A86)),
    T889(
        "T889",
        "Uspesifisert komplikasjon til kirurgisk eller medisinsk behandling",
        listOf(ICPC2.A87)
    ),
    T758("T758", "Annen spesifisert skadevirkning av ytre påvirkning", listOf(ICPC2.A88)),
    T859(
        "T859",
        "Uspesifisert komplikasjon ved innvendig protese, implantat eller transplantat",
        listOf(ICPC2.A89)
    ),
    Q899("Q899", "Uspesifisert medfødt misdannelse", listOf(ICPC2.A90)),
    R899(
        "R899",
        "Uspesifisert unormalt funn i prøve fra annet organ, system eller vev",
        listOf(ICPC2.A91)
    ),
    T784("T784", "Uspesifisert allergi", listOf(ICPC2.A92)),
    P073("P073", "Annet for tidlig født barn", listOf(ICPC2.A93)),
    P969("P969", "Uspesifisert tilstand som oppstår i perinatalperioden", listOf(ICPC2.A94)),
    P95("P95", "Fosterdød av uspesifisert årsak", listOf(ICPC2.A95)),
    R99("R99", "Annen dårlig definert eller uspesifisert dødsårsak", listOf(ICPC2.A96)),
    Z014(
        "Z014",
        "Kontakt med helsetjenesten for generell eller rutinemessig gynekologisk undersøkelse hos person uten symptom eller registrert diagnose",
        listOf(ICPC2.A981)
    ),
    R69("R69", "Ukjent eller uspesifisert sykdomsårsak", listOf(ICPC2.A99)),
    R599("R599", "Uspesifiserte forstørrede lymfeknuter", listOf(ICPC2.B02)),
    L049("L049", "Uspesifisert akutt lymfadenitt", listOf(ICPC2.B70)),
    I889("I889", "Lymfadenitt uten spesifisert varighet eller lokalisasjon", listOf(ICPC2.B71)),
    C819("C819", "Uspesifisert Hodgkins sykdom", listOf(ICPC2.B72)),
    C959("C959", "Uspesifisert leukemi", listOf(ICPC2.B73)),
    C969(
        "C969",
        "Upesifisert ondartet svulst utgått fra lymfoid, hematopoetisk eller beslektet vev",
        listOf(ICPC2.B74)
    ),
    D479(
        "D479",
        "Uspesifisert svulst med usikkert eller ukjent malignitetspotensial i lymfoid, hematopoetisk eller beslektet vev",
        listOf(ICPC2.B75)
    ),
    S360("S360", "Skade på milt", listOf(ICPC2.B76)),
    D589("D589", "Uspesifisert arvelig hemolytisk anemi", listOf(ICPC2.B78)),
    Q898("Q898", "Annen spesifisert medfødt misdannelse", listOf(ICPC2.B79)),
    D509("D509", "Uspesifisert jernmangelanemi", listOf(ICPC2.B80)),
    D539("D539", "Uspesifisert mangelanemi", listOf(ICPC2.B81)),
    D649("D649", "Uspesifisert anemi", listOf(ICPC2.B82)),
    D699("D699", "Uspesifisert blødningstilstand", listOf(ICPC2.B83)),
    R72(
        "R72",
        "Unormalt forhold ved hvite blodceller, ikke klassifisert annet sted",
        listOf(ICPC2.B84)
    ),
    R161("R161", "Splenomegali, ikke klassifisert annet sted", listOf(ICPC2.B87)),
    B24("B24", "Uspesifisert humant immunsviktvirus-sykdom", listOf(ICPC2.B90)),
    D759("D759", "Uspesifisert sykdom i blod eller bloddannende organ", listOf(ICPC2.B99)),
    R104("R104", "Annen eller uspesifisert smerte i buk eller bekken", listOf(ICPC2.D01)),
    R101("R101", "Smerte lokalisert til øvre abdomen", listOf(ICPC2.D02)),
    R12("R12", "Halsbrann", listOf(ICPC2.D03)),
    R102(
        "R102",
        "Smerte i bekken eller perineum",
        listOf(ICPC2.D04, ICPC2.X01, ICPC2.Y01, ICPC2.Y02)
    ),
    L290("L290", "Pruritus ani", listOf(ICPC2.D05)),
    R103("R103", "Smerte lokalisert til annen del av nedre abdomen", listOf(ICPC2.D06)),
    K30("K30", "Dyspepsi", listOf(ICPC2.D07)),
    R14("R14", "Flatulens eller beslektet tilstand", listOf(ICPC2.D08)),
    R11("R11", "Kvalme eller oppkast", listOf(ICPC2.D09, ICPC2.D10)),
    K591("K591", "Funksjonell diaré", listOf(ICPC2.D11)),
    K590("K590", "Forstoppelse", listOf(ICPC2.D12)),
    R17("R17", "Uspesifisert gulsott", listOf(ICPC2.D13)),
    K920("K920", "Hematemese", listOf(ICPC2.D14)),
    K921("K921", "Melena", listOf(ICPC2.D15)),
    K625("K625", "Blødning i endetarmsåpning, analkanal eller endetarm", listOf(ICPC2.D16)),
    R15("R15", "Ufrivillig avføring", listOf(ICPC2.D17)),
    R194("R194", "Forandring i avføringsvane", listOf(ICPC2.D18)),
    K089("K089", "Uspesifisert forstyrrelse i tann eller støttevev", listOf(ICPC2.D19, ICPC2.D82)),
    R198(
        "R198",
        "Annet spesifisert symptom eller tegn med tilknytning til fordøyelsessystemet eller buken",
        listOf(ICPC2.D20, ICPC2.D29)
    ),
    R13("R13", "Dysfagi", listOf(ICPC2.D21)),
    R160("R160", "Hepatomegali, ikke klassifisert annet sted", listOf(ICPC2.D23)),
    R190("R190", "Hevelse, oppfylling eller kul i buk eller bekken", listOf(ICPC2.D24, ICPC2.D25)),
    A049("A049", "Uspesifisert bakteriell tarminfeksjon", listOf(ICPC2.D70)),
    B269("B269", "Kusma uten komplikasjoner", listOf(ICPC2.D71)),
    B199("B199", "Uspesifisert virushepatitt uten leverkoma", listOf(ICPC2.D72)),
    A090(
        "A090",
        "Annen eller uspesifisert gastroenteritt eller kolitt av infeksiøs årsak",
        listOf(ICPC2.D73)
    ),
    C169("C169", "Ondartet svulst i uspesifisert del av magesekk", listOf(ICPC2.D74)),
    C189("C189", "Ondartet svulst i uspesifisert del av tykktarm", listOf(ICPC2.D75)),
    C259("C259", "Ondartet svulst i uspesifisert del av bukspyttkjertel", listOf(ICPC2.D76)),
    C269(
        "C269",
        "Ondartet svulst med ufullstendig angitt lokalisasjon i fordøyelsessystemet",
        listOf(ICPC2.D77)
    ),
    D139(
        "D139",
        "Godartet svulst i ufullstendig angitt lokalisasjon innen fordøyelsessystemet",
        listOf(ICPC2.D78)
    ),
    T189("T189", "Fremmedlegeme i uspesifisert del av fordøyelseskanal", listOf(ICPC2.D79)),
    S369("S369", "Skade på uspesifisert organ i bukhule", listOf(ICPC2.D80)),
    Q459("Q459", "Uspesifisert medfødt misdannelse i fordøyelsessystem", listOf(ICPC2.D81)),
    K137("K137", "Annen eller uspesifisert lesjon i munnslimhinne", listOf(ICPC2.D83)),
    K229("K229", "Uspesifisert sykdom i spiserør", listOf(ICPC2.D84)),
    K269(
        "K269",
        "Sår som ikke er spesifisert som akutt eller kronisk i tolvfingertarm, uten blødning eller perforasjon",
        listOf(ICPC2.D85)
    ),
    K279(
        "K279",
        "Magesår som ikke er spesifisert som akutt eller kronisk i uspesifisert lokalisasjon, uten blødning eller perforasjon",
        listOf(ICPC2.D86)
    ),
    K299("K299", "Uspesifisert gastroduodenitt", listOf(ICPC2.D87)),
    K37("K37", "Uspesifisert appendisitt", listOf(ICPC2.D88)),
    K409(
        "K409",
        "Enkeltsidig eller uspesifisert lyskebrokk uten obstruksjon eller gangren",
        listOf(ICPC2.D89)
    ),
    K449("K449", "Mellomgulvsbrokk uten obstruksjon eller gangren", listOf(ICPC2.D90)),
    K469("K469", "Uspesifisert abdominalt brokk uten obstruksjon eller gangren", listOf(ICPC2.D91)),
    K579(
        "K579",
        "Divertikkelsykdom i uspesifisert del av tarm uten perforasjon eller abscess",
        listOf(ICPC2.D92)
    ),
    K589("K589", "Irritabel tarm-syndrom uten diaré", listOf(ICPC2.D93)),
    K529("K529", "Uspesifisert ikke-infeksiøs gastroenteritt eller kolitt", listOf(ICPC2.D94)),
    K639("K639", "Uspesifisert sykdom i tarm", listOf(ICPC2.D95)),
    B839("B839", "Uspesifisert ormesykdom", listOf(ICPC2.D96)),
    K769("K769", "Uspesifisert leversykdom", listOf(ICPC2.D97)),
    K839("K839", "Uspesifisert sykdom i galleveier", listOf(ICPC2.D98)),
    K929("K929", "Uspesifisert sykdom i fordøyelsessystemet", listOf(ICPC2.D99)),
    H571("H571", "Øyesmerter", listOf(ICPC2.F01)),
    H578(
        "H578",
        "Annen spesifisert sykdom i øyet eller øyets omgivelser",
        listOf(ICPC2.F02, ICPC2.F13, ICPC2.F15, ICPC2.F99)
    ),
    H042("H042", "Tåreflod", listOf(ICPC2.F03)),
    H531("H531", "Subjektiv synsforstyrrelse", listOf(ICPC2.F04)),
    H539("H539", "Uspesifisert synsforstyrrelse", listOf(ICPC2.F05)),
    H55("H55", "Nystagmus eller andre irregulære øyebevegelser", listOf(ICPC2.F14)),
    H029("H029", "Uspesifisert tilstand i øyelokk", listOf(ICPC2.F16)),
    Z460(
        "Z460",
        "Kontakt med helsetjenesten for tilpasning eller justering av briller eller kontaktlinser",
        listOf(ICPC2.F17, ICPC2.F18)
    ),
    H579("H579", "Uspesifisert sykdom i øyet eller øyets omgivelser", listOf(ICPC2.F29, ICPC2.F73)),
    H109("H109", "Uspesifisert konjunktivitt", listOf(ICPC2.F70)),
    H101("H101", "Allergisk konjunktivitt", listOf(ICPC2.F71)),
    H019("H019", "Uspesifisert betennelse i øyelokk", listOf(ICPC2.F72)),
    D487(
        "D487",
        "Svulst med usikkert eller ukjent malignitetspotensial med annen spesifisert lokalisasjon",
        listOf(ICPC2.F74, ICPC2.K72)
    ),
    S001("S001", "Kontusjon av øyelokk og område omkring øye", listOf(ICPC2.F75)),
    T159("T159", "Fremmedlegeme i uspesifisert del av fremre del av øye", listOf(ICPC2.F76)),
    S059("S059", "Uspesifisert skade på øye og øyehule", listOf(ICPC2.F79)),
    Q105("Q105", "Medfødt stenose eller striktur i tårekanal", listOf(ICPC2.F80)),
    Q159("Q159", "Uspesifisert medfødt misdannelse i øye", listOf(ICPC2.F81)),
    H332("H332", "Serøs netthinneløsning", listOf(ICPC2.F82)),
    H350("H350", "Bakgrunnsretinopati og karforandringer i netthinne", listOf(ICPC2.F83)),
    H353("H353", "Degenerasjon av makula eller bakre pol", listOf(ICPC2.F84)),
    H160("H160", "Ulcus i hornhinne", listOf(ICPC2.F85)),
    A719("A719", "Uspesifisert trakom", listOf(ICPC2.F86)),
    H527("H527", "Uspesifisert brytningsforstyrrelse", listOf(ICPC2.F91)),
    H269("H269", "Uspesifisert grå stær", listOf(ICPC2.F92)),
    H409("H409", "Uspesifisert glaukom", listOf(ICPC2.F93)),
    H543("H543", "Mild eller ingen synssvekkelse i begge øyne", listOf(ICPC2.F94)),
    H509("H509", "Uspesifisert skjeling", listOf(ICPC2.F95)),
    H920("H920", "Øreverk", listOf(ICPC2.H01)),
    H932("H932", "Annen unormal lydoppfatning", listOf(ICPC2.H02)),
    H931("H931", "Øresus", listOf(ICPC2.H03)),
    H921("H921", "Øreflod", listOf(ICPC2.H04)),
    H922("H922", "Øreblødning", listOf(ICPC2.H05)),
    H938("H938", "Annen spesifisert lidelse i øre", listOf(ICPC2.H13, ICPC2.H99)),
    H939("H939", "Uspesifisert lidelse i øre", listOf(ICPC2.H29)),
    H609("H609", "Uspesifisert betennelse i ytre øre", listOf(ICPC2.H70)),
    H660("H660", "Akutt purulent mellomørebetennelse", listOf(ICPC2.H71)),
    H659("H659", "Uspesifisert ikke-purulent mellomørebetennelse", listOf(ICPC2.H72)),
    H681("H681", "Obstruksjon av tuba auditiva", listOf(ICPC2.H73)),
    H663("H663", "Annen kronisk purulent mellomørebetennelse", listOf(ICPC2.H74)),
    D385(
        "D385",
        "Svulst med usikkert eller ukjent malignitetspotensial i annet spesifisert åndedrettsorgan",
        listOf(ICPC2.H75)
    ),
    T16("T16", "Fremmedlegeme i øre", listOf(ICPC2.H76)),
    H729("H729", "Uspesifisert perforasjon i trommehinne", listOf(ICPC2.H77)),
    S004("S004", "Overflateskade på øre", listOf(ICPC2.H78)),
    S099("S099", "Uspesifisert hodeskade", listOf(ICPC2.H79)),
    Q179("Q179", "Uspesifisert medfødt øremisdannelse", listOf(ICPC2.H80)),
    H612("H612", "Vokspropp", listOf(ICPC2.H81)),
    H819("H819", "Uspesifisert forstyrrelse i vestibularisfunksjonen", listOf(ICPC2.H82)),
    H809("H809", "Uspesifisert otosklerose", listOf(ICPC2.H83)),
    H911("H911", "Alderdomstunghørthet", listOf(ICPC2.H84)),
    H833("H833", "Virkninger av støy på indre øre", listOf(ICPC2.H85)),
    H919("H919", "Uspesifisert hørselstap", listOf(ICPC2.H86)),
    R072("R072", "Prekordial smerte", listOf(ICPC2.K01, ICPC2.K02)),
    R098(
        "R098",
        "Annet spesifisert symptom eller tegn med tilknytning til sirkulasjons- eller åndedrettssystemet",
        listOf(ICPC2.K03, ICPC2.K29, ICPC2.R29)
    ),
    R002("R002", "Palpitasjon", listOf(ICPC2.K04)),
    R008("R008", "Andre eller uspesifiserte unormale hjerteslag", listOf(ICPC2.K05)),
    I878("I878", "Annen spesifisert forstyrrelse i vene", listOf(ICPC2.K06)),
    R609("R609", "Uspesifisert ødem", listOf(ICPC2.K07)),
    Z824(
        "Z824",
        "Opplysning om iskemisk hjertesykdom eller annen sykdom i sirkulasjonssystemet i familieanamnesen",
        listOf(ICPC2.K22)
    ),
    I409("I409", "Uspesifisert akutt myokarditt", listOf(ICPC2.K70)),
    I099("I099", "Uspesifisert revmatisk hjertesykdom", listOf(ICPC2.K71)),
    Q289("Q289", "Uspesifisert medfødt misdannelse i sirkulasjonssystemet", listOf(ICPC2.K73)),
    I209("I209", "Uspesifisert angina pectoris", listOf(ICPC2.K74)),
    I219("I219", "Uspesifisert akutt hjerteinfarkt", listOf(ICPC2.K75)),
    I259("I259", "Uspesifisert kronisk iskemisk hjertesykdom", listOf(ICPC2.K76)),
    I509("I509", "Uspesifisert hjertesvikt", listOf(ICPC2.K77)),
    I489("I489", "Uspesifisert atrieflimmer eller atrieflutter", listOf(ICPC2.K78)),
    I479("I479", "Uspesifisert paroksysmal takykardi", listOf(ICPC2.K79)),
    I499("I499", "Uspesifisert hjertearytmi", listOf(ICPC2.K80)),
    R011("R011", "Uspesifisert bilyd", listOf(ICPC2.K81)),
    I279("I279", "Uspesifisert pulmonal hjertesykdom", listOf(ICPC2.K82)),
    I089("I089", "Uspesifisert affeksjon av flere klaffer", listOf(ICPC2.K83)),
    I519("I519", "Uspesifisert hjertesykdom", listOf(ICPC2.K84)),
    R030("R030", "Forhøyet blodtrykksmåling uten hypertensjonsdiagnose", listOf(ICPC2.K85)),
    I10("I10", "Essensiell hypertensjon", listOf(ICPC2.K86)),
    I139("I139", "Uspesifisert hypertensiv hjerte- og nyresykdom", listOf(ICPC2.K87)),
    I951("I951", "Ortostatisk hypotensjon", listOf(ICPC2.K88)),
    G459("G459", "Uspesifisert forbigående cerebralt iskemisk anfall", listOf(ICPC2.K89)),
    I64("I64", "Hjerneslag som ikke er spesifisert som blødning eller infarkt", listOf(ICPC2.K90)),
    I679("I679", "Uspesifisert hjernekarsykdom", listOf(ICPC2.K91)),
    I739("I739", "Uspesifisert sykdom i perifere kar", listOf(ICPC2.K92)),
    I269("I269", "Lungeemboli uten opplysning om akutt cor pulmonale", listOf(ICPC2.K93)),
    I809("I809", "Flebitt eller tromboflebitt med uspesifisert lokalisasjon", listOf(ICPC2.K94)),
    I839("I839", "Åreknuter uten ulcus eller betennelse i underekstremitet", listOf(ICPC2.K95)),
    K649("K649", "Uspesifiserte hemoroider", listOf(ICPC2.K96)),
    I99("I99", "Annen eller uspesifisert forstyrrelse i sirkulasjonssystemet", listOf(ICPC2.K99)),
    M542("M542", "Smerte i nakke", listOf(ICPC2.L01)),
    M549("M549", "Uspesifisert ryggsmerte", listOf(ICPC2.L02)),
    M545("M545", "Lumbago", listOf(ICPC2.L03, ICPC2.L84)),
    R298(
        "R298",
        "Annet eller uspesifisert symptom eller tegn med tilknytning til nervesystemet eller muskel-skjelettsystemet",
        listOf(ICPC2.L04, ICPC2.L05, ICPC2.L29, ICPC2.N29)
    ),
    M255(
        "M255",
        "Leddsmerte",
        listOf(ICPC2.L07, ICPC2.L08, ICPC2.L10, ICPC2.L11, ICPC2.L13, ICPC2.L15, ICPC2.L16)
    ),
    M796("M796", "Smerte i ekstremitet", listOf(ICPC2.L09, ICPC2.L12, ICPC2.L14, ICPC2.L17)),
    M790("M790", "Uspesifisert revmatisme", listOf(ICPC2.L18)),
    M799("M799", "Uspesifisert bløtvevssykdom", listOf(ICPC2.L19)),
    M259("M259", "Uspesifisert leddtilstand", listOf(ICPC2.L20)),
    M009("M009", "Uspesifisert pyogen artritt", listOf(ICPC2.L70)),
    C419(
        "C419",
        "Ondartet svulst i uspesifisert del av knokkel eller leddbrusk",
        listOf(ICPC2.L71)
    ),
    S529("S529", "Brudd i uspesifisert del av underarm", listOf(ICPC2.L72)),
    S829("S829", "Brudd i uspesifisert del av legg", listOf(ICPC2.L73)),
    S628("S628", "Brudd i annen eller uspesifisert del av håndledd eller hånd", listOf(ICPC2.L74)),
    S729("S729", "Brudd i uspesifisert del av lårben", listOf(ICPC2.L75)),
    T142("T142", "Brudd i uspesifisert kroppsregion", listOf(ICPC2.L76)),
    S934("S934", "Forstuving eller forstrekking av ankelligament", listOf(ICPC2.L77)),
    S836(
        "S836",
        "Forstuving, ruptur eller forstrekking av annen eller uspesifisert del av kne",
        listOf(ICPC2.L78)
    ),
    T143(
        "T143",
        "Dislokasjon, forstuving og forstrekking i uspesifisert kroppsregion",
        listOf(ICPC2.L79, ICPC2.L80)
    ),
    T146("T146", "Skade på sene eller muskel i uspesifisert kroppsregion", listOf(ICPC2.L81)),
    Q799("Q799", "Uspesifisert medfødt misdannelse i muskel-skjelettsystem", listOf(ICPC2.L82)),
    M489("M489", "Uspesifisert lidelse i ryggsøylen", listOf(ICPC2.L83)),
    M439("M439", "Uspesifisert deformerende rygglidelse", listOf(ICPC2.L85)),
    M543("M543", "Isjialgi", listOf(ICPC2.L86)),
    M779("M779", "Uspesifisert entesopati", listOf(ICPC2.L87)),
    M069("M069", "Uspesifisert revmatoid artritt", listOf(ICPC2.L88)),
    M169("M169", "Uspesifisert hofteleddsartrose", listOf(ICPC2.L89)),
    M179("M179", "Uspesifisert kneleddsartrose", listOf(ICPC2.L90)),
    M199("M199", "Uspesifisert artrose", listOf(ICPC2.L91)),
    M759("M759", "Uspesifisert skulderlidelse", listOf(ICPC2.L92)),
    M771("M771", "Lateral epikondylitt", listOf(ICPC2.L93)),
    M939("M939", "Uspesifisert osteokondropati", listOf(ICPC2.L94)),
    M819("M819", "Uspesifisert osteoporose", listOf(ICPC2.L95)),
    S832("S832", "Fersk skade i menisk", listOf(ICPC2.L96)),
    D481(
        "D481",
        "Svulst med usikkert eller ukjent malignitetspotensial i bindevev eller annet bløtvev",
        listOf(ICPC2.L97)
    ),
    M219("M219", "Uspesifisert ervervet deformitet i ekstremitet", listOf(ICPC2.L98)),
    M999("M999", "Uspesifisert biomekanisk lesjon", listOf(ICPC2.L99)),
    R51("R51", "Hodepine", listOf(ICPC2.N01)),
    G501("G501", "Atypisk ansiktssmerte", listOf(ICPC2.N03)),
    G258(
        "G258",
        "Annen spesifisert ekstrapyramidal tilstand eller bevegelsesforstyrrelse",
        listOf(ICPC2.N04)
    ),
    R202("R202", "Parestesi i hud", listOf(ICPC2.N05)),
    R208(
        "R208",
        "Annen eller uspesifisert forstyrrelse i hudens følsomhet",
        listOf(ICPC2.N06, ICPC2.S01)
    ),
    R568("R568", "Annen eller uspesifisert krampe", listOf(ICPC2.N07)),
    R258("R258", "Andre eller uspesifiserte unormale ufrivillige bevegelser", listOf(ICPC2.N08)),
    R438("R438", "Annen eller uspesifisert forstyrrelse i lukt og smak", listOf(ICPC2.N16)),
    R42("R42", "Svimmelhet", listOf(ICPC2.N17)),
    G98(
        "G98",
        "Annen lidelse i nervesystemet, ikke klassifisert annet sted",
        listOf(ICPC2.N18, ICPC2.N99)
    ),
    R478("R478", "Annen eller uspesifisert taleforstyrrelse", listOf(ICPC2.N19)),
    A809("A809", "Uspesifisert akutt poliomyelitt", listOf(ICPC2.N70)),
    G039("G039", "Uspesifisert meningitt", listOf(ICPC2.N71)),
    A35("A35", "Annen stivkrampe", listOf(ICPC2.N72)),
    A89("A89", "Uspesifisert virusinfeksjon i sentralnervesystemet", listOf(ICPC2.N73)),
    C729(
        "C729",
        "Ondartet svulst med uspesifisert lokalisasjon i sentralnervesystem",
        listOf(ICPC2.N74)
    ),
    D339("D339", "Godartet svulst i uspesifisert del av sentralnervesystemet", listOf(ICPC2.N75)),
    D439(
        "D439",
        "Svulst med usikkert eller ukjent malignitetspotensial i uspesifisert del av sentralnervesystemet",
        listOf(ICPC2.N76)
    ),
    S060("S060", "Hjernerystelse", listOf(ICPC2.N79)),
    S069("S069", "Uspesifisert intrakraniell skade", listOf(ICPC2.N80)),
    T144("T144", "Skade på nerve i uspesifisert kroppsregion", listOf(ICPC2.N81)),
    Q079("Q079", "Uspesifisert medfødt misdannelse i nervesystemet", listOf(ICPC2.N85)),
    G35("G35", "Multippel sklerose", listOf(ICPC2.N86)),
    G20("G20", "Parkinsons sykdom", listOf(ICPC2.N87)),
    G409("G409", "Uspesifisert epilepsi", listOf(ICPC2.N88)),
    G439("G439", "Uspesifisert migrene", listOf(ICPC2.N89)),
    G440("G440", "Clusterhodepinesyndrom", listOf(ICPC2.N90)),
    G510("G510", "Bells parese", listOf(ICPC2.N91)),
    G500("G500", "Trigeminusnevralgi", listOf(ICPC2.N92)),
    G560("G560", "Karpaltunnelsyndrom", listOf(ICPC2.N93)),
    G629("G629", "Uspesifisert polynevropati", listOf(ICPC2.N94)),
    G442("G442", "Tensjonshodepine", listOf(ICPC2.N95)),
    R450("R450", "Nervøsitet", listOf(ICPC2.P01)),
    F439("F439", "Uspesifisert reaksjon på alvorlig belastning", listOf(ICPC2.P02)),
    R452("R452", "Ulykkelighet", listOf(ICPC2.P03)),
    R454("R454", "Irritabilitet eller sinne", listOf(ICPC2.P04)),
    R54("R54", "Senilitet", listOf(ICPC2.P05)),
    F519("F519", "Uspesifisert ikke-organisk søvnlidele", listOf(ICPC2.P06)),
    F520("F520", "Mangel på eller tap av seksuell lyst", listOf(ICPC2.P07)),
    F529(
        "F529",
        "Uspesifisert seksuell dysfunksjon som ikke skyldes somatisk lidelse",
        listOf(ICPC2.P08)
    ),
    F669("F669", "Uspesifisert psykoseksuell forstyrrelse", listOf(ICPC2.P09)),
    F985("F985", "Stamming", listOf(ICPC2.P10)),
    F982("F982", "Spiseforstyrrelse i barndommen", listOf(ICPC2.P11)),
    F980("F980", "Ikke-organisk enurese", listOf(ICPC2.P12)),
    F981("F981", "Ikke-organisk enkoprese", listOf(ICPC2.P13)),
    F101("F101", "Skadelig bruk av alkohol", listOf(ICPC2.P15)),
    F100("F100", "Akutt alkoholintoksikasjon", listOf(ICPC2.P16)),
    F171("F171", "Skadelig bruk av tobakk", listOf(ICPC2.P17)),
    F131("F131", "Skadelig bruk av sedativum eller hypnotikum", listOf(ICPC2.P18)),
    F191("F191", "Skadelig bruk av flere stoffer", listOf(ICPC2.P19)),
    R418(
        "R418",
        "Annet eller uspesifisert symptom eller tegn med tilknytning til kognitiv funksjon eller bevissthet",
        listOf(ICPC2.P20)
    ),
    F919("F919", "Uspesifisert atferdsforstyrrelse", listOf(ICPC2.P22, ICPC2.P23)),
    F819("F819", "Uspesifisert utviklingsforstyrrelse i skoleferdighet", listOf(ICPC2.P24)),
    Z600("Z600", "Problem med tilpasning til overgangsperiode i livssyklus", listOf(ICPC2.P25)),
    R458(
        "R458",
        "Annet spesifisert symptom eller tegn med tilknytning til emosjonell tilstand",
        listOf(ICPC2.P29)
    ),
    F03("F03", "Annen eller uspesifisert demens", listOf(ICPC2.P70)),
    F069("F069", "Uspesifisert organisk psykisk lidelse", listOf(ICPC2.P71)),
    F209("F209", "Uspesifisert schizofreni	", listOf(ICPC2.P72)),
    F319("F319", "Uspesifisert bipolar affektiv lidelse", listOf(ICPC2.P73)),
    F419("F419", "Uspesifisert angstlidelse", listOf(ICPC2.P74)),
    F459("F459", "Uspesifisert somatoform lidelse", listOf(ICPC2.P75)),
    F329("F329", "Uspesifisert depressiv episode", listOf(ICPC2.P76)),
    F99("F99", "Uspesifisert psykisk forstyrrelse eller lidelse", listOf(ICPC2.P77, ICPC2.P99)),
    F480("F480", "Nevrasteni", listOf(ICPC2.P78)),
    F409("F409", "Uspesifisert fobisk angstlidelse", listOf(ICPC2.P79)),
    F609("F609", "Uspesifisert personlighetsforstyrrelse", listOf(ICPC2.P80)),
    F909("F909", "Uspesifisert hyperkinetisk forstyrrelse", listOf(ICPC2.P81)),
    F431("F431", "Posttraumatisk stresslidelse [PTSD]", listOf(ICPC2.P82)),
    F799(
        "F799",
        "Uspesifisert psykisk utviklingshemming uten beskrivelse av atferdsproblem",
        listOf(ICPC2.P85)
    ),
    F509("F509", "Uspesifisert spiseforstyrrelse", listOf(ICPC2.P86)),
    F29("F29", "Uspesifisert ikke-organisk psykose", listOf(ICPC2.P98)),
    R071("R071", "Brystsmerte ved pusting", listOf(ICPC2.R01)),
    R060("R060", "Dyspné", listOf(ICPC2.R02)),
    R062("R062", "Hvesende respirasjon", listOf(ICPC2.R03)),
    R068("R068", "Annen eller uspesifisert åndedrettsabnormitet", listOf(ICPC2.R04)),
    R05("R05", "Hoste", listOf(ICPC2.R05)),
    R040("R040", "Epistaxis", listOf(ICPC2.R06)),
    R067("R067", "Nysing", listOf(ICPC2.R07)),
    J348(
        "J348",
        "Annen spesifisert forstyrrelse i nese eller nesebihule",
        listOf(ICPC2.R08, ICPC2.R09)
    ),
    R070("R070", "Smerte i svelg", listOf(ICPC2.R21)),
    R498("R498", "Annen eller uspesifisert stemmeforstyrrelse", listOf(ICPC2.R23)),
    R042("R042", "Hemoptyse", listOf(ICPC2.R24)),
    R093("R093", "Unormalt oppspytt", listOf(ICPC2.R25)),
    A379("A379", "Uspesifisert kikhoste", listOf(ICPC2.R71)),
    J030("J030", "Streptokokktonsillitt", listOf(ICPC2.R72)),
    J340("J340", "Abscess, furunkel eller karbunkel i nese", listOf(ICPC2.R73)),
    J069("J069", "Uspesifisert akutt infeksjon i øvre luftveier", listOf(ICPC2.R74)),
    J019("J019", "Uspesifisert akutt sinusitt", listOf(ICPC2.R75)),
    J039("J039", "Uspesifisert akutt tonsillitt", listOf(ICPC2.R76)),
    J040("J040", "Akutt laryngitt", listOf(ICPC2.R77)),
    J22("J22", "Uspesifisert akutt infeksjon i nedre luftveier", listOf(ICPC2.R78)),
    J42("J42", "Uspesifisert kronisk bronkitt", listOf(ICPC2.R79)),
    J111(
        "J111",
        "Influensa med annen luftveismanifestasjon, som skyldes uidentifisert virus",
        listOf(ICPC2.R80)
    ),
    J189("J189", "Uspesifisert pneumoni", listOf(ICPC2.R81)),
    R091("R091", "Pleuritt", listOf(ICPC2.R82)),
    J311("J311", "Kronisk nasofaryngitt", listOf(ICPC2.R83)),
    C349("C349", "Ondartet svulst i uspesifisert del av bronkie eller lunge", listOf(ICPC2.R84)),
    C399(
        "C399",
        "Ondartet svulst med ufullstendig angitt lokalisasjon i åndedrettsorgan",
        listOf(ICPC2.R85)
    ),
    D144("D144", "Godartet svulst i uspesifisert del av åndedrettsorgan", listOf(ICPC2.R86)),
    T179("T179", "Fremmedlegeme i uspesifisert del av luftveier", listOf(ICPC2.R87)),
    S279("S279", "Skade i uspesifisert intratorakalt organ", listOf(ICPC2.R88)),
    Q349("Q349", "Uspesifisert medfødt misdannelse i åndedrettssystemet", listOf(ICPC2.R89)),
    J359("J359", "Uspesifisert kronisk sykdom i mandel eller adenoid vev", listOf(ICPC2.R90)),
    D386(
        "D386",
        "Svulst med usikkert eller ukjent malignitetspotensial i uspesifisert åndedrettsorgan",
        listOf(ICPC2.R92)
    ),
    J449("J449", "Uspesifisert kronisk obstruktiv lungesykdom", listOf(ICPC2.R95)),
    J459("J459", "Uspesifisert astma", listOf(ICPC2.R96)),
    J304("J304", "Uspesifisert allergisk rinitt", listOf(ICPC2.R97)),
    R064("R064", "Hyperventilering", listOf(ICPC2.R98)),
    J989("J989", "Uspesifisert åndedrettsforstyrrelse", listOf(ICPC2.R99)),
    L299("L299", "Uspesifisert kløe", listOf(ICPC2.S02)),
    B07("B07", "Virusvorter", listOf(ICPC2.S03)),
    R229("R229", "Uspesifisert lokalisert hevelse, oppfylling eller kul", listOf(ICPC2.S04)),
    R227(
        "R227",
        "Lokalisert hevelse, oppfylling eller kul med flere lokalisasjoner",
        listOf(ICPC2.S05)
    ),
    R21("R21", "Utslett eller annet uspesifikt hudutbrudd", listOf(ICPC2.S06, ICPC2.S07)),
    R238("R238", "Annen eller uspesifisert hudforandring", listOf(ICPC2.S08, ICPC2.S29)),
    L030("L030", "Cellulitt på finger eller tå", listOf(ICPC2.S09)),
    L029("L029", "Uspesifisert kutan abscess, furunkel eller karbunkel", listOf(ICPC2.S10)),
    T793("T793", "Posttraumatisk sårinfeksjon ikke klassifisert annet sted", listOf(ICPC2.S11)),
    T140(
        "T140",
        "Overflateskade på uspesifisert kroppsregion",
        listOf(ICPC2.S12, ICPC2.S16, ICPC2.S17, ICPC2.S19)
    ),
    T141("T141", "Åpent sår på uspesifisert kroppsregion", listOf(ICPC2.S13, ICPC2.S15, ICPC2.S18)),
    T300("T300", "Brannskade av uspesifiert grad i uspesifisert kroppsregion", listOf(ICPC2.S14)),
    L84("L84", "Liktorner eller hornhud", listOf(ICPC2.S20)),
    R234("R234", "Forandring i hudstruktur", listOf(ICPC2.S21)),
    L609("L609", "Uspesifisert neglelidelse", listOf(ICPC2.S22)),
    L659("L659", "Uspesifisert hårtap uten arrdannelse", listOf(ICPC2.S23)),
    L679("L679", "Uspesifisert abnormitet ved hårfarge eller hårskaft", listOf(ICPC2.S24)),
    B029("B029", "Herpes zoster uten komplikasjoner", listOf(ICPC2.S70)),
    B009("B009", "Uspesifisert herpesvirusinfeksjon", listOf(ICPC2.S71)),
    B86("B86", "Skabb", listOf(ICPC2.S72)),
    B889("B889", "Uspesifisert infestasjon", listOf(ICPC2.S73)),
    B359("B359", "Uspesifisert hudsoppsykdom", listOf(ICPC2.S74)),
    B372("B372", "Candidainfeksjon på hud eller negl", listOf(ICPC2.S75)),
    L089("L089", "Uspesifisert lokal infeksjon i hud eller underhud", listOf(ICPC2.S76)),
    C449("C449", "Odartet svulst med uspesifisert lokalisasjon i hud ", listOf(ICPC2.S77)),
    D179("D179", "Lipom med uspesifisert lokalisasjon", listOf(ICPC2.S78)),
    D239("D239", "Godartet svulst i hud med uspesifisert lokalisasjon", listOf(ICPC2.S79)),
    L569(
        "L569",
        "Uspesifisert akutt hudforandring som skyldes ultrafiolett stråling",
        listOf(ICPC2.S80)
    ),
    D180("D180", "Hemangiom med enhver lokalisasjon", listOf(ICPC2.S81)),
    D229("D229", "Melanocyttnevus med uspesifisert lokalisasjon", listOf(ICPC2.S82)),
    Q849("Q849", "Uspesifisert medfødt misdannelse i hud eller underhud", listOf(ICPC2.S83)),
    L010("L010", "Impetigo", listOf(ICPC2.S84)),
    L059("L059", "Pilonidalcyste uten abscess", listOf(ICPC2.S85)),
    L219("L219", "Uspesifisert seboréisk dermatitt", listOf(ICPC2.S86)),
    L209("L209", "Uspesifisert atopisk dermatitt", listOf(ICPC2.S87)),
    L309("L309", "Uspesifisert dermatitt", listOf(ICPC2.S88)),
    L22("L22", "Bleiedermatitt", listOf(ICPC2.S89)),
    L42("L42", "Pityriasis rosea", listOf(ICPC2.S90)),
    L409("L409", "Uspesifisert psoriasis", listOf(ICPC2.S91)),
    L749("L749", "Uspesifisert lidelse i eksokrine svettekjertler", listOf(ICPC2.S92)),
    L721("L721", "Retensjonscyste i talgkjertel eller hårsekk", listOf(ICPC2.S93)),
    L600("L600", "Inngrodd negl", listOf(ICPC2.S94)),
    B081("B081", "Molluscum contagiosum", listOf(ICPC2.S95)),
    L709("L709", "Uspesifisert akne", listOf(ICPC2.S96)),
    L984("L984", "Kronisk hudsår, ikke klassifisert annet sted", listOf(ICPC2.S97)),
    L509("L509", "Uspesifisert urticaria", listOf(ICPC2.S98)),
    L989("L989", "Uspesifisert lidelse i hud eller underhud", listOf(ICPC2.S99)),
    R631("R631", "Polydipsi", listOf(ICPC2.T01)),
    R632("R632", "Polyfagi", listOf(ICPC2.T02)),
    R630("R630", "Anoreksi", listOf(ICPC2.T03)),
    R633("R633", "Vanskeligheter med inntak eller tilførsel av mat", listOf(ICPC2.T04, ICPC2.T05)),
    R635("R635", "Unormal vektøkning", listOf(ICPC2.T07)),
    R634("R634", "Unormalt vekttap", listOf(ICPC2.T08)),
    R629(
        "R629",
        "Uspesifisert uteblivelse av forventet normal fysiologisk utvikling",
        listOf(ICPC2.T10)
    ),
    E86("E86", "Væsketap", listOf(ICPC2.T11)),
    R638(
        "R638",
        "Annet spesifisert symptom eller tegn med tilknytning til mat- eller væskeinntak",
        listOf(ICPC2.T29)
    ),
    E060("E060", "Akutt tyreoiditt	", listOf(ICPC2.T70)),
    C73("C73", "Ondartet svulst i skjoldbruskkjertel", listOf(ICPC2.T71)),
    D34("D34", "Godartet svulst i skjoldbruskkjertel", listOf(ICPC2.T72)),
    D449(
        "D449",
        "Svulst med usikkert eller ukjent malignitetspotensial i uspesifisert endokrin kjertel",
        listOf(ICPC2.T73)
    ),
    Q892("Q892", "Medfødt misdannelse i annen endokrin kjertel", listOf(ICPC2.T78, ICPC2.T80)),
    E049("E049", "Uspesifisert ikke-toksisk struma	", listOf(ICPC2.T81)),
    E669("E669", "Uspesifisert fedme", listOf(ICPC2.T82, ICPC2.T83)),
    E059("E059", "Uspesifisert tyreotoksikose", listOf(ICPC2.T85)),
    E039("E039", "Uspesifisert hypotyreose", listOf(ICPC2.T86)),
    E162("E162", "Uspesifisert hypoglykemi	", listOf(ICPC2.T87)),
    E109("E109", "Diabetes mellitus type I uten komplikasjon", listOf(ICPC2.T89)),
    E119("E119", "Diabetes mellitus type II uten komplikasjon", listOf(ICPC2.T90)),
    E639("E639", "Uspesifisert mangelsykdom", listOf(ICPC2.T91)),
    M109("M109", "Uspesifisert urinsyregikt", listOf(ICPC2.T92)),
    E789("E789", "Uspesifisert forstyrrelse i lipoproteinmetabolismen", listOf(ICPC2.T93)),
    E349("E349", "Uspesifisert endokrin forstyrrelse", listOf(ICPC2.T99)),
    R309("R309", "Uspesifisert smertefull vannlating", listOf(ICPC2.U01)),
    R35("R35", "Polyuri", listOf(ICPC2.U02)),
    R32("R32", "Uspesifisert inkontinens", listOf(ICPC2.U04)),
    R391("R391", "Annet vannlatingsproblem", listOf(ICPC2.U05)),
    R31("R31", "Uspesifisert hematuri", listOf(ICPC2.U06)),
    R398(
        "R398",
        "Annet eller uspesifisert symptom eller tegn med tilknytning til urinveiene",
        listOf(ICPC2.U07, ICPC2.U13, ICPC2.U29)
    ),
    R33("R33", "Urinretensjon", listOf(ICPC2.U08)),
    N23("N23", "Uspesifisert nyrekolikk", listOf(ICPC2.U14)),
    N12(
        "N12",
        "Tubulointerstitiell nefritt ikke spesifisert som akutt eller kronisk",
        listOf(ICPC2.U70)
    ),
    N309("N309", "Uspesifisert cystitt", listOf(ICPC2.U71)),
    N341("N341", "Uspesifikk uretritt", listOf(ICPC2.U72)),
    C64("C64", "Ondartet svulst i nyre unntatt nyrebekken", listOf(ICPC2.U75)),
    C679("C679", "Ondartet svulst med uspesifisert lokalisasjon i urinblære", listOf(ICPC2.U76)),
    C689(
        "C689",
        "Ondartet svulst med uspesifisert lokalisasjon i urinveisorgan",
        listOf(ICPC2.U77)
    ),
    D309("D309", "Godartet svulst i uspesifisert urinveisorgan", listOf(ICPC2.U78)),
    D419(
        "D419",
        "Svulst med usikkert eller ukjent malignitetspotensial i uspesifisert urinveisorgan",
        listOf(ICPC2.U79)
    ),
    S370("S370", "Skade på nyre", listOf(ICPC2.U80)),
    Q649("Q649", "Uspesifisert medfødt misdannelse i urinsystemet", listOf(ICPC2.U85)),
    N059(
        "N059",
        "Uspesifisert nefrittisk syndrom med uspesifisert morfologisk lesjon",
        listOf(ICPC2.U88)
    ),
    N392("N392", "Uspesifisert ortostatisk proteinuri", listOf(ICPC2.U90)),
    N209("N209", "Sten i uspesifisert lokalisasjon i urinveier", listOf(ICPC2.U95)),
    R829("R829", "Annet eller uspesifisert unormalt funn i urin", listOf(ICPC2.U98)),
    N399("N399", "Uspesifisert forstyrrelse i urinsystemet", listOf(ICPC2.U99)),
    Z320("Z320", "Graviditetsundersøkelse eller test uten bekreftet graviditet", listOf(ICPC2.W01)),
    O469("O469", "Uspesifisert blødning før fødsel", listOf(ICPC2.W03)),
    O219("O219", "Uspesifiserte svangerskapsbrekninger", listOf(ICPC2.W05)),
    Z303("Z303", "Utskrapn. av endomet. v/forsinket mens (brukes ikke i Norge)", listOf(ICPC2.W10)),
    Z304(
        "Z304",
        "Kontakt med helsetjenesten for overvåkning av befruktningshindrende legemiddel",
        listOf(ICPC2.W11)
    ),
    Z301("Z301", "Kontakt med helsetjenesten for innsetting av spiral", listOf(ICPC2.W12)),
    Z302("Z302", "Kontakt med helsetjenesten for sterilisering", listOf(ICPC2.W13, ICPC2.Y13)),
    Z308(
        "Z308",
        "Kontakt med helsetjenesten for annet spesifisert prevensjonstiltak",
        listOf(ICPC2.W14, ICPC2.Y14)
    ),
    N979("N979", "Uspesifisert kvinnelig infertilitet", listOf(ICPC2.W15)),
    O721("O721", "Annen umiddelbar blødning etter fødsel", listOf(ICPC2.W17)),
    O909("O909", "Uspesifisert komplikasjon i barseltid", listOf(ICPC2.W18, ICPC2.W96)),
    O927(
        "O927",
        "Annen eller uspesifisert forstyrrelse i melkeproduksjon",
        listOf(ICPC2.W19, ICPC2.W95)
    ),
    O85("O85", "Sepsis i barseltid", listOf(ICPC2.W70)),
    O989(
        "O989",
        "Uspesifisert infeksjonssykdom eller parasittsykdom hos mor, som kompliserer svangerskap, fødsel eller barseltid",
        listOf(ICPC2.W71)
    ),
    C58("C58", "Ondartet svulst i morkake", listOf(ICPC2.W72)),
    O019("O019", "Uspesifisert blæremola", listOf(ICPC2.W73)),
    O998(
        "O998",
        "Annen spesifisert sykdom eller tilstand som kompliserer svangerskap, fødsel eller barseltid",
        listOf(ICPC2.W76)
    ),
    Z321(
        "Z321",
        "Graviditetsundersøkelse eller test med bekreftet graviditet",
        listOf(ICPC2.W78, ICPC2.W79)
    ),
    Z349(
        "Z349",
        "Kontroll av normalt svangerskap, uspesifisert om første eller senere svangerskap",
        listOf(ICPC2.W781)
    ),
    O009("O009", "Uspesifisert svangerskap utenfor livmoren", listOf(ICPC2.W80)),
    O149("O149", "Uspesifisert preeklampsi", listOf(ICPC2.W81)),
    O039("O039", "Spontan komplett eller uspesifisert abort uten komplikasjon", listOf(ICPC2.W82)),
    O049("O049", "Legal komplett eller uspesifisert abort, uten komplikasjon", listOf(ICPC2.W83)),
    Z359("Z359", "Kontroll av høyrisikosvangerskap av uspesifisert årsak", listOf(ICPC2.W84)),
    O244("O244", "Diabetes mellitus som oppstår under svangerskap", listOf(ICPC2.W85)),
    Z370("Z370", "Enkeltfødsel med levendfødt barn", listOf(ICPC2.W90)),
    Z371("Z371", "Enkeltfødsel med dødfødt barn", listOf(ICPC2.W91)),
    O759(
        "O759",
        "Uspesifisert komplikasjon ved fødsel eller forløsning",
        listOf(ICPC2.W92, ICPC2.W93)
    ),
    O912("O912", "Ikke-purulent mastitt i forbindelse med fødsel", listOf(ICPC2.W94)),
    O269("O269", "Uspesifisert svangerskapsrelatert tilstand", listOf(ICPC2.W99)),
    N946("N946", "Uspesifisert dysmenoré", listOf(ICPC2.X02)),
    N940("N940", "«Mittelschmerz»", listOf(ICPC2.X03)),
    N941("N941", "Dyspareuni", listOf(ICPC2.X04)),
    N915("N915", "Uspesifisert oligomenoré", listOf(ICPC2.X05)),
    N920("N920", "Kraftig eller hyppig menstruasjon med regelmessig syklus", listOf(ICPC2.X06)),
    N921("N921", "Kraftig eller hyppig menstruasjon med uregelmessig syklus", listOf(ICPC2.X07)),
    N923("N923", "Ovulasjonsblødning", listOf(ICPC2.X08)),
    N949(
        "N949",
        "Uspesifisert tilstand med tilknytning til kvinnelige kjønnsorganer eller menstruasjonssyklus",
        listOf(ICPC2.X09, ICPC2.X17, ICPC2.X29)
    ),
    Z309(
        "Z309",
        "Kontakt med helsetjenesten for uspesifisert prevensjonstiltak",
        listOf(ICPC2.X10)
    ),
    N959("N959", "Uspesifisert forstyrrelse i klimakterium eller senere", listOf(ICPC2.X11)),
    N950("N950", "Postmenopausal blødning", listOf(ICPC2.X12)),
    N930("N930", "Postkoital blødning eller kontaktblødning", listOf(ICPC2.X13)),
    N898("N898", "Annen spesifisert ikke-inflammatorisk lidelse i skjede", listOf(ICPC2.X14)),
    N899("N899", "Uspesifisert ikke-inflammatorisk lidelse i skjede", listOf(ICPC2.X15)),
    N909(
        "N909",
        "Uspesifisert ikke-inflammatorisk lidelse i ytre kvinnelige kjønnsorganer eller perineum",
        listOf(ICPC2.X16)
    ),
    N644("N644", "Mastodyni", listOf(ICPC2.X18)),
    N63("N63", "Uspesifisert klump i bryst", listOf(ICPC2.X19)),
    N645("N645", "Annet tegn eller symptom i bryst", listOf(ICPC2.X20, ICPC2.Y16)),
    N649("N649", "Uspesifisert tilstand i bryst", listOf(ICPC2.X21)),
    A539("A539", "Uspesifisert syfilis", listOf(ICPC2.X70, ICPC2.Y70)),
    A549("A549", "Uspesifisert gonokokkinfeksjon", listOf(ICPC2.X71, ICPC2.Y71)),
    B373(
        "B373",
        "Candidainfeksjon i ytre kvinnelige kjønnsorganer eller skjede",
        listOf(ICPC2.X72)
    ),
    A590("A590", "Urogenital trikomonasinfeksjon", listOf(ICPC2.X73)),
    N739("N739", "Uspesifisert betennelsestilstand i kvinnelig bekkenorgan", listOf(ICPC2.X74)),
    C539("C539", "Ondartet svulst med uspesifisert lokalisasjon i livmorhals", listOf(ICPC2.X75)),
    C509("C509", "Ondartet svulst med uspesifisert lokalisasjon i bryst", listOf(ICPC2.X76)),
    C577(
        "C577",
        "Ondartet svulst i annen spesifisert del av kvinnelige kjønnsorganer",
        listOf(ICPC2.X77)
    ),
    D259("D259", "Leiomyom med uspesifisert lokalisasjon i livmor", listOf(ICPC2.X78)),
    D24("D24", "Godartet svulst i bryst", listOf(ICPC2.X79)),
    D289("D289", "Godartet svulst i uspesifisert kvinnelig kjønnsorgan", listOf(ICPC2.X80)),
    D397(
        "D397",
        "Svulst med usikkert eller ukjent malignitetspotensial i annet spesifisert kvinnelig kjønnsorgan",
        listOf(ICPC2.X81)
    ),
    S314("S314", "Åpent sår i skjede eller på ytre kvinnelige kjønnsorganer", listOf(ICPC2.X82)),
    Q529("Q529", "Uspesifisert medfødt misdannelse i kvinnelige kjønnsorganer", listOf(ICPC2.X83)),
    N768(
        "N768",
        "Annen spesifisert betennelse i skjede eller ytre kvinnelige kjønnsorganer",
        listOf(ICPC2.X84)
    ),
    N889("N889", "Uspesifisert ikke-inflammatorisk lidelse i livmorhals", listOf(ICPC2.X85)),
    N879("N879", "Uspesifisert dysplasi i livmorhals", listOf(ICPC2.X86)),
    N819("N819", "Uspesifisert fremfall av kvinnelige kjønnsorganer", listOf(ICPC2.X87)),
    N609("N609", "Uspesifisert godartet mammadysplasi", listOf(ICPC2.X88)),
    N943("N943", "Premenstruelt tensjonssyndrom (PMS)", listOf(ICPC2.X89)),
    A609("A609", "Uspesifisert anogenital herpesvirusinfeksjon", listOf(ICPC2.X90, ICPC2.Y72)),
    A630("A630", "Anogenital venerisk vorte", listOf(ICPC2.X91, ICPC2.Y76)),
    A562(
        "A562",
        "Uspesifisert klamydiainfeksjon i kjønnsorganer eller urinveier",
        listOf(ICPC2.X92)
    ),
    N948(
        "N948",
        "Annen spesifisert tilstand med tilknytning til kvinnelige kjønnsorganer eller menstruasjonssyklus",
        listOf(ICPC2.X99)
    ),
    R36("R36", "Utflod fra urinrøret", listOf(ICPC2.Y03)),
    N489("N489", "Uspesifisert penisforstyrrelse", listOf(ICPC2.Y04, ICPC2.Y08)),
    N509(
        "N509",
        "Uspesifisert forstyrrelse i mannlige kjønnsorganer",
        listOf(ICPC2.Y05, ICPC2.Y29, ICPC2.Y99)
    ),
    N429("N429", "Uspesifisert forstyrrelse i blærehalskjertel", listOf(ICPC2.Y06)),
    N484("N484", "Impotens med organisk årsak", listOf(ICPC2.Y07)),
    N46("N46", "Infertilitet hos mann", listOf(ICPC2.Y10)),
    N419("N419", "Uspesifisert infeksjonssykdom i blærehalskjertel", listOf(ICPC2.Y73)),
    N459("N459", "Orkitt, epididymitt eller epididymo-orkitt uten abscess", listOf(ICPC2.Y74)),
    N481("N481", "Balanopostitt", listOf(ICPC2.Y75)),
    C61("C61", "Ondartet svulst i blærehalskjertel", listOf(ICPC2.Y77)),
    C639(
        "C639",
        "Ondartet svulst med uspesifisert lokalisasjon i mannlige kjønnsorganer",
        listOf(ICPC2.Y78)
    ),
    D409(
        "D409",
        "Svulst med usikkert eller ukjent malignitetspotensial i uspesifisert mannlig kjønnsorgan",
        listOf(ICPC2.Y79)
    ),
    S379("S379", "Skade på uspesifisert bekkenorgan", listOf(ICPC2.Y80)),
    N47("N47", "Overflødig forhud, fimose eller parafimose", listOf(ICPC2.Y81)),
    Q549("Q549", "Uspesifisert hypospadi", listOf(ICPC2.Y82)),
    Q539("Q539", "Uspesifisert ikke-descendert testikkel", listOf(ICPC2.Y83)),
    Q559("Q559", "Uspesifisert medfødt misdannelse i mannlige kjønnsorganer", listOf(ICPC2.Y84)),
    N40("N40", "Benign prostataobstruksjon", listOf(ICPC2.Y85)),
    N433("N433", "Uspesifisert hydrocele", listOf(ICPC2.Y86)),
    Z596("Z596", "Problem med lav inntekt", listOf(ICPC2.Z01)),
    Z594("Z594", "Problem med mangel på fullverdig mat", listOf(ICPC2.Z02)),
    Z599(
        "Z599",
        "Uspesifisert problem i forbindelse med boforhold eller økonomiske forhold",
        listOf(ICPC2.Z03)
    ),
    Z609("Z609", "Uspesifisert problem i forbindelse med sosialt miljø", listOf(ICPC2.Z04)),
    Z567(
        "Z567",
        "Annet eller uspesifisert problem i forbindelse med arbeidsliv",
        listOf(ICPC2.Z05)
    ),
    Z560("Z560", "Problem i forbindelse med uspesifisert arbeidsledighet", listOf(ICPC2.Z06)),
    Z559(
        "Z559",
        "Uspesifisert problem i forbindelse med utdannelse eller lese- eller skriveferdighet",
        listOf(ICPC2.Z07)
    ),
    Z597("Z597", "Problem med utilstrekkelig trygd eller stønad", listOf(ICPC2.Z08)),
    Z653("Z653", "Problem i forbindelse med annet rettslig forhold", listOf(ICPC2.Z09)),
    Z759(
        "Z759",
        "Uspesifisert problem i forbindelse med behov for helsetjeneste eller omsorgstilbud",
        listOf(ICPC2.Z10, ICPC2.Z11)
    ),
    Z630("Z630", "Problem i forhold til ektefelle eller partner", listOf(ICPC2.Z12, ICPC2.Z13)),
    Z636(
        "Z636",
        "Problem i forbindelse med hjelpeløs slektning som trenger pleie hjemme",
        listOf(ICPC2.Z14, ICPC2.Z18)
    ),
    Z634(
        "Z634",
        "Problem i forbindelse med familiemedlem som forsvinner eller dør",
        listOf(ICPC2.Z15, ICPC2.Z19, ICPC2.Z23)
    ),
    Z629("Z629", "Uspesifisert problem i forbindelse med oppfostring", listOf(ICPC2.Z16)),
    Z631("Z631", "Problem i forhold til foreldre eller svigerforeldre", listOf(ICPC2.Z20)),
    Z639(
        "Z639",
        "Uspesifisert problem i forbindelse med primærkontakt",
        listOf(ICPC2.Z21, ICPC2.Z24)
    ),
    Z637(
        "Z637",
        "Problem i forbindelse med annen stressende livsopplevelse som påvirker familie eller husstand",
        listOf(ICPC2.Z22)
    ),
    T749("T749", "Uspesifisert mishandlingssyndrom", listOf(ICPC2.Z25)),
    Z734(
        "Z734",
        "Problem i forbindelse med mangelfulle sosiale ferdigheter, ikke klassifisert annet sted",
        listOf(ICPC2.Z28)
    ),
    Z658(
        "Z658",
        "Annet spesifisert problem i forbindelse med psykososialt forhold",
        listOf(ICPC2.Z29)
    ),
}
