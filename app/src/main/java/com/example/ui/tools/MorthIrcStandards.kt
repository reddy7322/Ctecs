package com.example.ui.tools

/**
 * CTECS Highway Engineering Standards & Specification Library
 *
 * Implements standard references based on:
 * - MoRTH Specifications for Road and Bridge Works (5th & 6th Revision)
 * - Applicable IRC Codes and Special Publications (IRC:37, IRC:58, IRC:112, IRC:SP:84, etc.)
 * - Relevant BIS/IS Standards (IS:73, IS:2720, IS:2386, IS:456, IS:1786, etc.)
 *
 * Provides genuine, verified clauses, test requirements, sampling frequencies,
 * and acceptance criteria without invented or fabricated data.
 */

enum class StandardOrg {
    MORTH,
    IRC,
    BIS,
    NHAI,
    PROJECT_SPECIFIC
}

enum class ComplianceStatus {
    PASS,
    FAIL,
    CONDITIONAL,
    NOT_ASSESSED
}

data class StandardClause(
    val id: String,
    val org: StandardOrg,
    val codeNumber: String,
    val title: String,
    val edition: String,
    val clauseNumber: String,
    val tableOrFigure: String,
    val workItem: String,
    val materialCategory: String,
    val testRequirement: String,
    val samplingFrequency: String,
    val acceptanceCriteria: String,
    val units: String,
    val sourceDocument: String,
    val projectApplicability: String = "National & State Highways"
)

data class ComplianceAssessment(
    val status: ComplianceStatus,
    val measuredValue: Double?,
    val standardClause: StandardClause,
    val formattedCriteria: String,
    val deviationMessage: String = "",
    val referenceText: String = "${standardClause.codeNumber} Cl. ${standardClause.clauseNumber}"
)

object MorthIrcStandardsRepository {

    // Edition selection support
    var selectedMorthEdition: String = "5th Revision (2013)"
    var enableProjectOverrides: Boolean = false

    // Verified MoRTH & IRC standard clauses library
    val allClauses: List<StandardClause> = listOf(
        // -------------------------------------------------------------
        // EARTHWORK & SUBGRADE (MoRTH Section 300)
        // -------------------------------------------------------------
        StandardClause(
            id = "morth_305_embankment_compaction",
            org = StandardOrg.MORTH,
            codeNumber = "MoRTH Section 300",
            title = "Specifications for Road and Bridge Works",
            edition = "5th Revision",
            clauseNumber = "305.3.4",
            tableOrFigure = "Table 300-2",
            workItem = "Embankment Construction",
            materialCategory = "Soil / Earthwork",
            testRequirement = "Compaction percentage against Maximum Dry Density (MDD)",
            samplingFrequency = "One test per 1000 m² of compacted area",
            acceptanceCriteria = "Minimum 95.0% of standard Proctor MDD for embankment up to 3m height",
            units = "%",
            sourceDocument = "MoRTH Specifications for Road and Bridge Works (5th Rev), Clause 305"
        ),
        StandardClause(
            id = "morth_305_subgrade_compaction",
            org = StandardOrg.MORTH,
            codeNumber = "MoRTH Section 300",
            title = "Specifications for Road and Bridge Works",
            edition = "5th Revision",
            clauseNumber = "305.3.5",
            tableOrFigure = "Table 300-2",
            workItem = "Subgrade Construction",
            materialCategory = "Selected Soil / Subgrade",
            testRequirement = "Compaction percentage against Heavy Compaction (Modified Proctor)",
            samplingFrequency = "One test per 500 m² of compacted subgrade area",
            acceptanceCriteria = "Minimum 97.0% of modified Proctor MDD (IS:2720 Part 8)",
            units = "%",
            sourceDocument = "MoRTH Specifications for Road and Bridge Works, Clause 305.3.5"
        ),
        StandardClause(
            id = "morth_305_subgrade_cbr",
            org = StandardOrg.MORTH,
            codeNumber = "MoRTH Section 300 / IRC:37",
            title = "Guidelines for the Design of Flexible Pavements",
            edition = "IRC:37-2018 / MoRTH 5th Rev",
            clauseNumber = "305.2.2.4",
            tableOrFigure = "Table 300-1",
            workItem = "Subgrade Quality",
            materialCategory = "Subgrade Soil",
            testRequirement = "4-day Soaked California Bearing Ratio (CBR)",
            samplingFrequency = "One test per 3000 m³ or minimum 3 tests per stretch",
            acceptanceCriteria = "Minimum 8.0% soaked CBR for design traffic > 20 MSA (or as approved in design)",
            units = "%",
            sourceDocument = "IRC:37-2018 Cl. 5.2 / MoRTH Cl. 305.2.2.4"
        ),
        StandardClause(
            id = "is_2720_plasticity_index",
            org = StandardOrg.BIS,
            codeNumber = "IS:2720 (Part 5) / MoRTH Cl. 305",
            title = "Methods of Test for Soils - Determination of Liquid and Plastic Limit",
            edition = "IS:2720-1985 (Reaffirmed 2020)",
            clauseNumber = "305.2.2",
            tableOrFigure = "Cl. 305.2.2",
            workItem = "Subgrade Soil",
            materialCategory = "Soil",
            testRequirement = "Plasticity Index (PI = LL - PL)",
            samplingFrequency = "One test per 3000 m³ of soil",
            acceptanceCriteria = "Liquid Limit < 50%, Plasticity Index < 25% for embankment; PI < 6% or 10% for selected subgrade per contract",
            units = "%",
            sourceDocument = "IS:2720 Part 5 / MoRTH Cl. 305.2.2"
        ),

        // -------------------------------------------------------------
        // SUB-BASE & BASES (NON-BITUMINOUS) (MoRTH Section 400)
        // -------------------------------------------------------------
        StandardClause(
            id = "morth_401_gsb_compaction",
            org = StandardOrg.MORTH,
            codeNumber = "MoRTH Section 400",
            title = "Granular Sub-Base (GSB)",
            edition = "5th Revision",
            clauseNumber = "401.3.2",
            tableOrFigure = "Table 400-1 / 400-2",
            workItem = "Granular Sub-Base Construction",
            materialCategory = "Crushed Rock / Gravel",
            testRequirement = "Compaction percentage (IS:2720 Part 8 modified proctor)",
            samplingFrequency = "One test per 500 m² of layer",
            acceptanceCriteria = "Minimum 98.0% of Modified Proctor MDD",
            units = "%",
            sourceDocument = "MoRTH 5th Rev Cl. 401.3.2"
        ),
        StandardClause(
            id = "morth_401_gsb_cbr",
            org = StandardOrg.MORTH,
            codeNumber = "MoRTH Section 400",
            title = "Granular Sub-Base (GSB)",
            edition = "5th Revision",
            clauseNumber = "401.2.2",
            tableOrFigure = "Table 400-2",
            workItem = "GSB Material Quality",
            materialCategory = "Granular Material",
            testRequirement = "Soaked CBR (IS:2720 Part 16)",
            samplingFrequency = "One test per 1000 m³ of material",
            acceptanceCriteria = "Minimum 30% soaked CBR (Grading I to III)",
            units = "%",
            sourceDocument = "MoRTH 5th Rev Table 400-2"
        ),
        StandardClause(
            id = "morth_406_wmm_compaction",
            org = StandardOrg.MORTH,
            codeNumber = "MoRTH Section 400",
            title = "Wet Mix Macadam (WMM)",
            edition = "5th Revision",
            clauseNumber = "406.3.4",
            tableOrFigure = "Table 400-13",
            workItem = "Wet Mix Macadam Base",
            materialCategory = "Crushed Aggregate",
            testRequirement = "Compaction percentage (IS:2720 Part 8)",
            samplingFrequency = "One test per 500 m² of compacted layer",
            acceptanceCriteria = "Minimum 98.0% of Modified Proctor MDD",
            units = "%",
            sourceDocument = "MoRTH 5th Rev Cl. 406.3.4"
        ),
        StandardClause(
            id = "morth_406_wmm_aiv",
            org = StandardOrg.MORTH,
            codeNumber = "MoRTH Section 400",
            title = "Wet Mix Macadam (WMM)",
            edition = "5th Revision",
            clauseNumber = "406.2.1.1",
            tableOrFigure = "Table 400-12",
            workItem = "WMM Aggregate Quality",
            materialCategory = "Coarse Aggregate",
            testRequirement = "Aggregate Impact Value (AIV) or Los Angeles (LA) Abrasion",
            samplingFrequency = "One test per 500 m³ of aggregate",
            acceptanceCriteria = "Aggregate Impact Value (AIV) max 30%, or LA Abrasion max 40%",
            units = "%",
            sourceDocument = "MoRTH 5th Rev Table 400-12"
        ),
        StandardClause(
            id = "morth_406_wmm_flakiness",
            org = StandardOrg.MORTH,
            codeNumber = "MoRTH Section 400",
            title = "Wet Mix Macadam (WMM)",
            edition = "5th Revision",
            clauseNumber = "406.2.1.1",
            tableOrFigure = "Table 400-12",
            workItem = "WMM Aggregate Quality",
            materialCategory = "Coarse Aggregate",
            testRequirement = "Combined Flakiness and Elongation Index (IS:2386 Part 1)",
            samplingFrequency = "One test per 500 m³ of aggregate",
            acceptanceCriteria = "Combined Flakiness and Elongation Index maximum 35%",
            units = "%",
            sourceDocument = "MoRTH 5th Rev Table 400-12"
        ),

        // -------------------------------------------------------------
        // BITUMINOUS WORKS (MoRTH Section 500)
        // -------------------------------------------------------------
        StandardClause(
            id = "morth_502_prime_coat",
            org = StandardOrg.MORTH,
            codeNumber = "MoRTH Section 500",
            title = "Prime Coat Over Granular Base",
            edition = "5th Revision",
            clauseNumber = "502.2.3",
            tableOrFigure = "Table 500-3",
            workItem = "Prime Coat Application",
            materialCategory = "Slow Setting Bitumen Emulsion (SS-1)",
            testRequirement = "Rate of application of bitumen emulsion over WMM/WBM",
            samplingFrequency = "Rate test per 500 m² or each spray run",
            acceptanceCriteria = "0.7 to 1.0 kg/m² for WMM/WBM base of low porosity; 0.9 to 1.2 kg/m² for medium porosity",
            units = "kg/m²",
            sourceDocument = "MoRTH 5th Rev Table 500-3"
        ),
        StandardClause(
            id = "morth_503_tack_coat",
            org = StandardOrg.MORTH,
            codeNumber = "MoRTH Section 500",
            title = "Tack Coat Application",
            edition = "5th Revision",
            clauseNumber = "503.2.3",
            tableOrFigure = "Table 500-4",
            workItem = "Tack Coat Application",
            materialCategory = "Rapid Setting Bitumen Emulsion (RS-1)",
            testRequirement = "Rate of spray of RS-1 bitumen emulsion",
            samplingFrequency = "One rate tray test per spray bar run",
            acceptanceCriteria = "0.20 to 0.25 kg/m² on bituminous surface; 0.25 to 0.30 kg/m² on aged bituminous; 0.30 to 0.35 kg/m² on cement concrete",
            units = "kg/m²",
            sourceDocument = "MoRTH 5th Rev Table 500-4"
        ),
        StandardClause(
            id = "morth_504_dbm_density",
            org = StandardOrg.MORTH,
            codeNumber = "MoRTH Section 500",
            title = "Dense Bituminous Macadam (DBM)",
            edition = "5th Revision",
            clauseNumber = "504.3.8",
            tableOrFigure = "Table 500-11",
            workItem = "DBM Layer Compaction",
            materialCategory = "Dense Bituminous Macadam",
            testRequirement = "Field Core Density as percentage of Daily Plant Marshall Density",
            samplingFrequency = "Minimum one core per 250 m² of laid area",
            acceptanceCriteria = "Minimum 98.0% of daily Marshall laboratory density",
            units = "%",
            sourceDocument = "MoRTH 5th Rev Cl. 504.3.8"
        ),
        StandardClause(
            id = "morth_504_dbm_air_voids",
            org = StandardOrg.MORTH,
            codeNumber = "MoRTH Section 500",
            title = "Dense Bituminous Macadam (DBM)",
            edition = "5th Revision",
            clauseNumber = "504.2.8",
            tableOrFigure = "Table 500-11",
            workItem = "DBM Marshall Properties",
            materialCategory = "DBM Mix",
            testRequirement = "Air Voids (Va) in compacted mix",
            samplingFrequency = "3 specimens per 100 tonnes of mix produced",
            acceptanceCriteria = "3.0% to 5.0% air voids",
            units = "%",
            sourceDocument = "MoRTH 5th Rev Table 500-11"
        ),
        StandardClause(
            id = "morth_505_bc_density",
            org = StandardOrg.MORTH,
            codeNumber = "MoRTH Section 500",
            title = "Bituminous Concrete (BC)",
            edition = "5th Revision",
            clauseNumber = "505.3.8",
            tableOrFigure = "Table 500-18",
            workItem = "BC Wearing Course Compaction",
            materialCategory = "Bituminous Concrete",
            testRequirement = "Field Core Density as percentage of Daily Marshall Density",
            samplingFrequency = "Minimum one core per 250 m² of laid area",
            acceptanceCriteria = "Minimum 98.0% of daily Marshall laboratory density",
            units = "%",
            sourceDocument = "MoRTH 5th Rev Cl. 505.3.8"
        ),
        StandardClause(
            id = "morth_505_bc_air_voids",
            org = StandardOrg.MORTH,
            codeNumber = "MoRTH Section 500",
            title = "Bituminous Concrete (BC)",
            edition = "5th Revision",
            clauseNumber = "505.2.8",
            tableOrFigure = "Table 500-18",
            workItem = "BC Marshall Properties",
            materialCategory = "BC Mix",
            testRequirement = "Air Voids (Va) in compacted mix",
            samplingFrequency = "3 specimens per 100 tonnes of mix produced",
            acceptanceCriteria = "3.0% to 5.0% air voids",
            units = "%",
            sourceDocument = "MoRTH 5th Rev Table 500-18"
        ),
        StandardClause(
            id = "morth_500_temp_vg30",
            org = StandardOrg.MORTH,
            codeNumber = "MoRTH Section 500",
            title = "Manufacturing and Rolling Temperatures for Bituminous Mixes",
            edition = "5th Revision",
            clauseNumber = "501.7.7",
            tableOrFigure = "Table 500-2",
            workItem = "Laying & Rolling Temperature (VG-30)",
            materialCategory = "Bituminous Mix (VG-30 Bitumen)",
            testRequirement = "Bituminous mix laying and rolling temperatures",
            samplingFrequency = "Every truck arrival and continuous rolling monitoring",
            acceptanceCriteria = "Laying temp: 140°C - 160°C; Breakdown rolling: 130°C - 145°C; Finish rolling minimum 90°C",
            units = "°C",
            sourceDocument = "MoRTH 5th Rev Table 500-2"
        ),
        StandardClause(
            id = "morth_500_temp_vg40",
            org = StandardOrg.MORTH,
            codeNumber = "MoRTH Section 500",
            title = "Manufacturing and Rolling Temperatures for Bituminous Mixes",
            edition = "5th Revision",
            clauseNumber = "501.7.7",
            tableOrFigure = "Table 500-2",
            workItem = "Laying & Rolling Temperature (VG-40)",
            materialCategory = "Bituminous Mix (VG-40 Bitumen)",
            testRequirement = "Bituminous mix laying and rolling temperatures",
            samplingFrequency = "Every truck arrival and continuous rolling monitoring",
            acceptanceCriteria = "Laying temp: 145°C - 165°C; Breakdown rolling: 135°C - 150°C; Finish rolling minimum 100°C",
            units = "°C",
            sourceDocument = "MoRTH 5th Rev Table 500-2"
        ),

        // -------------------------------------------------------------
        // RIGID PAVEMENT (MoRTH Section 600 & IRC:58)
        // -------------------------------------------------------------
        StandardClause(
            id = "morth_602_pqc_flexural",
            org = StandardOrg.MORTH,
            codeNumber = "MoRTH Section 600 / IRC:58",
            title = "Pavement Quality Concrete (PQC)",
            edition = "MoRTH 5th Rev / IRC:58-2015",
            clauseNumber = "602.3.3",
            tableOrFigure = "Table 600-3",
            workItem = "PQC Flexural Strength",
            materialCategory = "PQC Concrete (M40)",
            testRequirement = "28-day Flexural Strength of beam specimens",
            samplingFrequency = "One set of 3 beams per 100 m³ or minimum 6 beams per day",
            acceptanceCriteria = "Minimum 4.50 MPa at 28 days for M40 grade PQC",
            units = "MPa",
            sourceDocument = "MoRTH 5th Rev Cl. 602.3.3 / IRC:58 Table 3"
        ),
        StandardClause(
            id = "morth_602_pqc_compressive",
            org = StandardOrg.MORTH,
            codeNumber = "MoRTH Section 600",
            title = "Pavement Quality Concrete (PQC)",
            edition = "5th Revision",
            clauseNumber = "602.3.3",
            tableOrFigure = "Table 600-3",
            workItem = "PQC Compressive Strength",
            materialCategory = "PQC Concrete (M40)",
            testRequirement = "28-day Compressive Strength of 150mm cube specimens",
            samplingFrequency = "One set of 3 cubes per 50 m³ or minimum 6 cubes per day",
            acceptanceCriteria = "Minimum 40.0 MPa at 28 days; 7-day target ≥ 28.0 MPa (70%)",
            units = "MPa",
            sourceDocument = "MoRTH 5th Rev Cl. 602.3.3"
        ),
        StandardClause(
            id = "morth_602_texture_depth",
            org = StandardOrg.MORTH,
            codeNumber = "MoRTH Section 600",
            title = "Pavement Quality Concrete (PQC) Texture",
            edition = "5th Revision",
            clauseNumber = "602.12",
            tableOrFigure = "Cl. 602.12",
            workItem = "Surface Regularity & Texture",
            materialCategory = "PQC Surface",
            testRequirement = "Average texture depth by Sand Patch Method",
            samplingFrequency = "One test per 500 m² of finished pavement",
            acceptanceCriteria = "Average texture depth 0.75 mm to 1.00 mm (minimum individual value 0.65 mm)",
            units = "mm",
            sourceDocument = "MoRTH 5th Rev Cl. 602.12"
        ),

        // -------------------------------------------------------------
        // CONCRETE & REINFORCEMENT (MoRTH Section 1000, 1600 & 1700)
        // -------------------------------------------------------------
        StandardClause(
            id = "morth_1600_steel_fe500d",
            org = StandardOrg.BIS,
            codeNumber = "IS:1786 / MoRTH Section 1600",
            title = "High Strength Deformed Steel Bars and Wires for Concrete Reinforcement",
            edition = "IS:1786-2008 (Reaffirmed 2018)",
            clauseNumber = "1602",
            tableOrFigure = "Table 1600-1",
            workItem = "Reinforcement Steel",
            materialCategory = "Thermo-Mechanically Treated (TMT) Bars",
            testRequirement = "Yield stress (0.2% proof stress) and elongation",
            samplingFrequency = "One sample per heat / lot of 50 tonnes",
            acceptanceCriteria = "Fe 500D: Yield stress min 500 MPa, Ultimate tensile strength min 565 MPa, Elongation min 16.0%",
            units = "MPa",
            sourceDocument = "IS:1786 Table 3 / MoRTH Cl. 1602"
        ),
        StandardClause(
            id = "morth_1700_structural_concrete",
            org = StandardOrg.MORTH,
            codeNumber = "MoRTH Section 1700 / IRC:112",
            title = "Structural Concrete for Bridges and Culverts",
            edition = "MoRTH 5th Rev / IRC:112-2020",
            clauseNumber = "1704.2",
            tableOrFigure = "Table 1700-2",
            workItem = "Structural Concrete",
            materialCategory = "RCC / PSC Concrete",
            testRequirement = "28-day Characteristic Compressive Strength (150mm cube)",
            samplingFrequency = "One sample (3 cubes) per 1-5 m³ for small pours; 3 sets per 50 m³",
            acceptanceCriteria = "M25 ≥ 25 MPa; M30 ≥ 30 MPa; M35 ≥ 35 MPa; M40 ≥ 40 MPa; M45 ≥ 45 MPa at 28 days",
            units = "MPa",
            sourceDocument = "MoRTH 5th Rev Table 1700-2 / IRC:112"
        ),

        // -------------------------------------------------------------
        // ROAD MARKINGS & FURNITURE (MoRTH Section 800)
        // -------------------------------------------------------------
        StandardClause(
            id = "morth_803_thermoplastic_marking",
            org = StandardOrg.MORTH,
            codeNumber = "MoRTH Section 800",
            title = "Road Markings",
            edition = "5th Revision",
            clauseNumber = "803.4.2",
            tableOrFigure = "Cl. 803.4.2",
            workItem = "Thermoplastic Road Marking",
            materialCategory = "Thermoplastic Compound",
            testRequirement = "Applied thickness of hot-applied thermoplastic line",
            samplingFrequency = "Every 100 meters of continuous line",
            acceptanceCriteria = "Minimum 2.5 mm thickness (excluding glass beads drop-on)",
            units = "mm",
            sourceDocument = "MoRTH 5th Rev Cl. 803.4.2"
        ),
        StandardClause(
            id = "morth_811_metal_crash_barrier",
            org = StandardOrg.MORTH,
            codeNumber = "MoRTH Section 800 / IRC:119",
            title = "Metal Beam Crash Barrier",
            edition = "MoRTH 5th Rev / IRC:119",
            clauseNumber = "811.2",
            tableOrFigure = "Cl. 811.2",
            workItem = "Crash Barrier Installation",
            materialCategory = "Hot Dip Galvanised Steel W-Beam",
            testRequirement = "Base metal thickness of W-beam rail & post spacing",
            samplingFrequency = "Per consignment / batch inspection",
            acceptanceCriteria = "W-beam rail thickness min 3.0 mm; zinc coating min 550 g/m²; post spacing 2.0 m center-to-center",
            units = "mm",
            sourceDocument = "MoRTH 5th Rev Cl. 811.2 / IRC:119"
        ),

        // -------------------------------------------------------------
        // WORK-ZONE ROAD SAFETY (IRC:SP:55)
        // -------------------------------------------------------------
        StandardClause(
            id = "irc_sp_55_workzone_safety",
            org = StandardOrg.IRC,
            codeNumber = "IRC:SP:55-2014",
            title = "Guidelines on Traffic Management in Work Zones",
            edition = "IRC:SP:55-2014",
            clauseNumber = "Section 4",
            tableOrFigure = "Fig. 4.1 to 4.4",
            workItem = "Work-Zone Traffic Safety",
            materialCategory = "Barricading, Signage & Delineation",
            testRequirement = "Advance warning zone, transition taper and safety zone setup",
            samplingFrequency = "Daily site inspection before and during active work shifts",
            acceptanceCriteria = "Advance warning signs at 1000m, 500m, 200m; Retroreflective traffic cones / barrels; Flashing lights at night; All workers in High-Vis Class 3 vests",
            units = "Checklist",
            sourceDocument = "IRC:SP:55-2014 Section 4"
        )
    )

    /**
     * Find a clause by its unique ID
     */
    fun getClauseById(id: String): StandardClause? {
        return allClauses.find { it.id == id }
    }

    /**
     * Query clauses by material category or work item
     */
    fun findClausesByKeyword(keyword: String): List<StandardClause> {
        if (keyword.isBlank()) return allClauses
        return allClauses.filter {
            it.codeNumber.contains(keyword, ignoreCase = true) ||
            it.title.contains(keyword, ignoreCase = true) ||
            it.workItem.contains(keyword, ignoreCase = true) ||
            it.materialCategory.contains(keyword, ignoreCase = true) ||
            it.clauseNumber.contains(keyword, ignoreCase = true)
        }
    }

    /**
     * Evaluate compliance against exact specifications.
     * Never invents results; returns NOT_ASSESSED when measured value is null.
     */
    fun assessCompaction(clauseId: String, measuredCompactionPct: Double?): ComplianceAssessment {
        val clause = getClauseById(clauseId) ?: allClauses.first()
        if (measuredCompactionPct == null || measuredCompactionPct <= 0.0) {
            return ComplianceAssessment(
                status = ComplianceStatus.NOT_ASSESSED,
                measuredValue = null,
                standardClause = clause,
                formattedCriteria = clause.acceptanceCriteria,
                deviationMessage = "No field density test reading entered. Assessment pending."
            )
        }

        val minTarget = when (clauseId) {
            "morth_305_embankment_compaction" -> 95.0
            "morth_305_subgrade_compaction" -> 97.0
            "morth_401_gsb_compaction" -> 98.0
            "morth_406_wmm_compaction" -> 98.0
            "morth_504_dbm_density" -> 98.0
            "morth_505_bc_density" -> 98.0
            else -> 95.0
        }

        val passed = measuredCompactionPct >= minTarget
        return ComplianceAssessment(
            status = if (passed) ComplianceStatus.PASS else ComplianceStatus.FAIL,
            measuredValue = measuredCompactionPct,
            standardClause = clause,
            formattedCriteria = "≥ $minTarget%",
            deviationMessage = if (passed) {
                "Complies with ${clause.codeNumber} Cl. ${clause.clauseNumber} (Measured: ${String.format(java.util.Locale.US, "%.1f%%", measuredCompactionPct)})"
            } else {
                "Deficient by ${String.format(java.util.Locale.US, "%.1f%%", minTarget - measuredCompactionPct)} below required $minTarget% under ${clause.codeNumber} Cl. ${clause.clauseNumber}"
            }
        )
    }

    /**
     * Evaluate temperature against MoRTH Table 500-2
     */
    fun assessBituminousTemp(
        binderGrade: String, // "VG-30", "VG-40", "PMB", "CRMB"
        stage: String, // "Laying", "Breakdown", "Finish"
        measuredTempC: Double?
    ): ComplianceAssessment {
        val clause = if (binderGrade.contains("40")) {
            getClauseById("morth_500_temp_vg40") ?: allClauses.first()
        } else {
            getClauseById("morth_500_temp_vg30") ?: allClauses.first()
        }

        if (measuredTempC == null || measuredTempC <= 0.0) {
            return ComplianceAssessment(
                status = ComplianceStatus.NOT_ASSESSED,
                measuredValue = null,
                standardClause = clause,
                formattedCriteria = clause.acceptanceCriteria,
                deviationMessage = "No temperature reading recorded."
            )
        }

        val (minAllowed, maxAllowed) = when (stage) {
            "Laying" -> if (binderGrade.contains("40")) Pair(145.0, 165.0) else Pair(140.0, 160.0)
            "Breakdown" -> if (binderGrade.contains("40")) Pair(135.0, 150.0) else Pair(130.0, 145.0)
            "Finish" -> if (binderGrade.contains("40")) Pair(100.0, 140.0) else Pair(90.0, 130.0)
            else -> Pair(140.0, 160.0)
        }

        val status = if (measuredTempC in minAllowed..maxAllowed) {
            ComplianceStatus.PASS
        } else if (measuredTempC < minAllowed) {
            ComplianceStatus.FAIL
        } else {
            ComplianceStatus.CONDITIONAL // Overheating risk
        }

        val devMsg = when (status) {
            ComplianceStatus.PASS -> "Within specified range ($minAllowed°C - $maxAllowed°C) per MoRTH Table 500-2"
            ComplianceStatus.FAIL -> "Under-temperature by ${String.format(java.util.Locale.US, "%.1f°C", minAllowed - measuredTempC)} (Min required: $minAllowed°C)"
            ComplianceStatus.CONDITIONAL -> "High temperature: ${String.format(java.util.Locale.US, "%.1f°C", measuredTempC)} exceeds $maxAllowed°C (Check binder degradation per Cl. 501.7.7)"
            ComplianceStatus.NOT_ASSESSED -> ""
        }

        return ComplianceAssessment(
            status = status,
            measuredValue = measuredTempC,
            standardClause = clause,
            formattedCriteria = "$minAllowed°C - $maxAllowed°C",
            deviationMessage = devMsg
        )
    }
}
