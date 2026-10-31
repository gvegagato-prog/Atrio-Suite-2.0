package com.gonzalo.gamerate

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont

/*
 * Estilo de la app: paleta de colores (cada una con versión clara y oscura), letra de títulos, letra del texto y tamaño.
 * Las letras que no vienen con la app se bajan de Google Fonts la primera vez (las sirve Android, sin pesar en la app);
 * si no se pueden bajar, se ve la letra del sistema.
 */

/**
 * Tonos de una paleta. paper: fondo · crudo: tarjetas · ink: texto · los cinco de los criterios (general, gráficos,
 * historia, música, jugabilidad) y gold (estrellas y avisos). sand/line/edge/muted: 0 = se calculan entre fondo y tinta.
 */
data class Tones(
    val paper: Long, val crudo: Long, val ink: Long,
    val general: Long, val gfx: Long, val story: Long, val music: Long, val gameplay: Long, val gold: Long,
    val sand: Long = 0, val line: Long = 0, val edge: Long = 0, val muted: Long = 0
) {
    private fun mix(a: Long, b: Long, t: Float) = lerp(Color(a), Color(b), t)
    val cPaper get() = Color(paper)
    val cCrudo get() = Color(crudo)
    val cInk get() = Color(ink)
    val cSand get() = if (sand != 0L) Color(sand) else mix(paper, ink, 0.07f)
    val cLine get() = if (line != 0L) Color(line) else mix(paper, ink, 0.13f)
    val cEdge get() = if (edge != 0L) Color(edge) else mix(paper, ink, 0.30f)
    val cMuted get() = if (muted != 0L) Color(muted) else mix(paper, ink, 0.60f)
    /** ¿Fondo oscuro? (para la barra del sistema) */
    val darkBg: Boolean get() = Color(paper).let { 0.299f * it.red + 0.587f * it.green + 0.114f * it.blue } < 0.5f
}

/** El color de acento de unos tonos. */
fun Tones.cGeneral(): Color = Color(general)

/** Una paleta: nombre, de dónde viene (para agruparlas) y sus dos versiones. */
data class Palette(val id: String, val name: String, val group: String, val light: Tones, val dark: Tones)

/** Una familia de letra de Google Fonts y los gruesos que tiene. */
data class FontChoice(val name: String, val weights: IntArray)

/** Combinación lista: paleta + letra de títulos + letra del texto. */
data class SkinStyle(val id: String, val name: String, val desc: String, val palette: String, val display: String, val body: String, val form: String = "clasico")

internal fun t(
    paper: Long, crudo: Long, ink: Long, general: Long, gfx: Long, story: Long, music: Long, gameplay: Long, gold: Long,
    sand: Long = 0, line: Long = 0, edge: Long = 0, muted: Long = 0
) = Tones(paper or 0xFF000000, crudo or 0xFF000000, ink or 0xFF000000, general or 0xFF000000, gfx or 0xFF000000, story or 0xFF000000,
    music or 0xFF000000, gameplay or 0xFF000000, gold or 0xFF000000,
    if (sand == 0L) 0 else sand or 0xFF000000, if (line == 0L) 0 else line or 0xFF000000,
    if (edge == 0L) 0 else edge or 0xFF000000, if (muted == 0L) 0 else muted or 0xFF000000)

/** Las paletas. Las de editores conocidos usan sus colores oficiales; las de Atrio son propias. */
val PALETTES: List<Palette> = listOf(
    // Interfaz Revista: papel y tinta, con rojo de imprenta (de noche, papel oscuro y tinta clara).
    Palette("revista", "Revista", "Atrio",
        // Los criterios, en tinta, grises y granate (nada de azules ni verdes: es papel impreso)
        t(0xF3EEE4, 0xFAF6EE, 0x15120F, 0xC8372D, 0x15120F, 0x5A524A, 0x968C80, 0x8E241C, 0xB8861B, 0xE9E2D5, 0xD9D0C1, 0xBDB2A0, 0x7C746A),
        t(0x141210, 0x1C1916, 0xF1EBE0, 0xE0533F, 0xF1EBE0, 0xBDB2A0, 0x847B70, 0xF08A78, 0xE0B456, 0x221E1A, 0x2E2924, 0x4A433B, 0x9C9387)),
    // Atrio 6: la de la app nueva. Oscura de verdad, con un coral cálido; la clara, de papel blanco roto.
    Palette("vitrina", "Vitrina", "Atrio",
        t(0xF5F3EF, 0xFFFFFF, 0x14151A, 0xE85A2E, 0x3E6BE0, 0x8B5CF6, 0x1F9D72, 0xC98A00, 0xD99A00, 0xECE9E3, 0xE4E0D9, 0xC9C4BB, 0x6D6A64),
        t(0x0B0C10, 0x16181E, 0xF3F1EC, 0xFF7A50, 0x7AA2FF, 0xC59BFF, 0x5FD4A8, 0xFFC857, 0xFFC857, 0x1D2028, 0x252932, 0x3A3F4B, 0x9AA0AD)),
    Palette("atrio", "Atrio", "Atrio",
        t(0xF4F2ED, 0xFBFAF7, 0x1E1D22, 0xC4473F, 0x4E78B8, 0xBF8E34, 0x3B9273, 0x7763B8, 0xC99A22, 0xECE9E2, 0xE2DED5, 0xC9C4BA, 0x86827A),
        t(0x121214, 0x1B1B1E, 0xEDEBE6, 0xE2665D, 0x7EA3DE, 0xDDB061, 0x5EBA96, 0xA18FDF, 0xE2B947, 0x26262A, 0x303035, 0x4E4E55, 0x9D988F)),
    Palette("medianoche", "Medianoche", "Atrio",
        t(0xEEF1F6, 0xFAFBFD, 0x18202E, 0xD0505A, 0x3C6FD1, 0xB98A2E, 0x2E8B74, 0x6E5AC8, 0xC39A2B),
        t(0x0D1220, 0x151C2E, 0xE4E9F2, 0xF06A72, 0x6D9BFF, 0xE6BE62, 0x56C7A4, 0xA28BFF, 0xF2C14E)),
    Palette("oled", "OLED", "Atrio",
        t(0xFFFFFF, 0xF6F6F6, 0x0A0A0A, 0xD7263D, 0x2F6FEB, 0xC08A00, 0x1E9E6A, 0x7B4DE0, 0xC99A00),
        t(0x000000, 0x0E0E10, 0xF2F2F2, 0xFF5C6C, 0x6EA8FF, 0xFFC94A, 0x3EE0A0, 0xB592FF, 0xFFD24A)),
    Palette("papel", "Papel", "Atrio",
        t(0xF3EAD7, 0xFBF5E8, 0x2B2118, 0xB0442F, 0x3F6D8E, 0xA77B22, 0x4F7D3A, 0x7A4F8A, 0xB8861B),
        t(0x1E1A14, 0x29231B, 0xEDE3CF, 0xE07A5F, 0x7FA7C4, 0xD8B25C, 0x8FB873, 0xB48BC4, 0xE0B456)),
    Palette("bosque", "Bosque", "Atrio",
        t(0xEEF2EC, 0xF8FAF6, 0x17241C, 0xB5523B, 0x3E7397, 0xA88A2B, 0x2F7D4F, 0x6C5A9E, 0xB59324),
        t(0x0F1A14, 0x16241C, 0xE1EBE3, 0xE87F64, 0x7CB2D6, 0xD9BC5C, 0x63C48B, 0xA894D8, 0xE5C454)),
    Palette("oceano", "Océano", "Atrio",
        t(0xEAF3F6, 0xF7FBFC, 0x0F2530, 0xD1495B, 0x1F78B4, 0xC99B2F, 0x1E9A8A, 0x5F5FC4, 0xD4A72C),
        t(0x07161D, 0x0E222B, 0xDDEEF3, 0xFF7085, 0x4FB3FF, 0xF2C766, 0x3FD6C1, 0x9A9BFF, 0xFFD166)),
    Palette("lavanda", "Lavanda", "Atrio",
        t(0xF2EFF8, 0xFBFAFE, 0x251E36, 0xC2486B, 0x4A6FC2, 0xB88B2E, 0x3A8C78, 0x7B55D3, 0xC29A2A),
        t(0x15121F, 0x1E1A2C, 0xEAE4F7, 0xF07B9E, 0x86A6F2, 0xE5C06A, 0x6CCDB3, 0xB79CFF, 0xF0C860)),
    Palette("vino", "Vino", "Atrio",
        t(0xF6EEEE, 0xFDF9F8, 0x2C1418, 0xA3243B, 0x46679E, 0xA97D2C, 0x3F7F5F, 0x7A4C8F, 0xB98C2B),
        t(0x1A0C10, 0x251216, 0xF2E3E5, 0xE5566F, 0x86A2D6, 0xDDB565, 0x6FBF94, 0xB487CC, 0xE7BF5A)),
    Palette("neon", "Neón", "Atrio",
        t(0xF3F0FA, 0xFFFFFF, 0x1A1030, 0xE0006F, 0x0077FF, 0xD99A00, 0x00A57A, 0x8A2BE2, 0xE0A800),
        t(0x0B0614, 0x140B24, 0xF1E9FF, 0xFF2E97, 0x00D1FF, 0xFFE14D, 0x39FF88, 0xB967FF, 0xFFD000)),
    Palette("sakura", "Sakura", "Atrio",
        t(0xFBF0F3, 0xFFFAFB, 0x3A1E28, 0xD94A73, 0x5B7CC9, 0xC4932F, 0x4C9A7A, 0x9A5FC0, 0xD1A032),
        t(0x1C1216, 0x28181F, 0xF7E6EC, 0xFF8AAE, 0x9DB5FF, 0xF0CB79, 0x84D6B4, 0xD3A2F2, 0xF5CF6A)),
    Palette("matcha", "Matcha", "Atrio",
        t(0xF0F2E6, 0xFAFBF4, 0x23281A, 0xB9533F, 0x4F7896, 0xA88B33, 0x5E8A2E, 0x7A6297, 0xB59A2C),
        t(0x161A10, 0x1F2417, 0xE7EBD9, 0xE58A72, 0x8EB3CF, 0xDCC071, 0x9CC860, 0xB9A1D4, 0xE3C860)),
    Palette("cafe", "Café", "Atrio",
        t(0xF2EBE3, 0xFAF6F1, 0x2B1F17, 0xB24A36, 0x4F6F8F, 0xA0752A, 0x5A7D47, 0x7D5A86, 0xB07F24),
        t(0x17110D, 0x221914, 0xEEE2D6, 0xE07C62, 0x8EAACB, 0xD7AE66, 0x95BC7E, 0xB996C2, 0xE2B45C)),
    Palette("hielo", "Hielo", "Atrio",
        t(0xF1F5F9, 0xFFFFFF, 0x1A2533, 0xD64550, 0x2E7DD7, 0xC0912C, 0x2C9C83, 0x6A63D6, 0xC9A12D),
        t(0x10161D, 0x18202A, 0xE6EDF5, 0xFF7A84, 0x7DB8FF, 0xF0CC70, 0x62D6BA, 0xA9A3FF, 0xF5D06A)),
    Palette("atardecer", "Atardecer", "Atrio",
        t(0xFBF1E8, 0xFFFAF5, 0x33201A, 0xD8453A, 0x4C6FB5, 0xD08A1E, 0x3F8F6B, 0x8A52B5, 0xDE9A1C),
        t(0x1C120F, 0x2A1A15, 0xF8E8DD, 0xFF6F5E, 0x8DA9F5, 0xFFB648, 0x6CCB9C, 0xC48BF0, 0xFFC247)),
    Palette("ambar", "Terminal ámbar", "Atrio",
        t(0xF7F1E3, 0xFFFBF1, 0x2E2410, 0xB3402A, 0x5B6F8E, 0xB07800, 0x5E7D2C, 0x7E5E8E, 0xB88000),
        t(0x0E0B05, 0x17120A, 0xFFB000, 0xFF6A3D, 0xFFCF66, 0xFFD27A, 0xD9C45A, 0xFF9F40, 0xFFC233, muted = 0xB07A00)),
    Palette("carmesi", "Carmesí", "Atrio",
        t(0xF7EEEE, 0xFFFAFA, 0x2A1214, 0xC0152F, 0x4A64A8, 0xB2852A, 0x3E7D5E, 0x7C4B9A, 0xBF8F27),
        t(0x120707, 0x1D0C0D, 0xF5E3E3, 0xFF4058, 0x8199E0, 0xE3B65E, 0x6EC098, 0xB884E0, 0xEDC05A)),
    Palette("cobre", "Cobre", "Atrio",
        t(0xF4EEE8, 0xFCF8F4, 0x2A1E16, 0xB8502E, 0x4E6E8C, 0xB07B2A, 0x4F7E5C, 0x7B5D8F, 0xB98228),
        t(0x15100C, 0x201812, 0xF1E3D5, 0xE88355, 0x8FAFCF, 0xE2B062, 0x88BE96, 0xB89ACB, 0xE9B85E)),
    Palette("pizarra", "Pizarra", "Atrio",
        t(0xEEF0F2, 0xF9FAFB, 0x1F2429, 0xC9483F, 0x3F6FB0, 0xB78C2E, 0x34886A, 0x6D5DB0, 0xBE962B),
        t(0x15181B, 0x1D2125, 0xE3E7EA, 0xF0716A, 0x7FA9E6, 0xE3C06E, 0x5FC4A0, 0xA899EA, 0xEDC860)),
    Palette("ciruela", "Ciruela", "Atrio",
        t(0xF5EFF4, 0xFDF9FC, 0x2B1830, 0xC03A62, 0x4D68B5, 0xB0862D, 0x3C8670, 0x8A3FA8, 0xBC8F29),
        t(0x170E1A, 0x221426, 0xF2E3F4, 0xF0709A, 0x8FA6F2, 0xE5BE6A, 0x6FCAAD, 0xD08BF0, 0xF0C55E)),
    Palette("gameboy", "Game Boy", "Clásicos",
        t(0x9BBC0F, 0x8BAC0F, 0x0F380F, 0x0F380F, 0x306230, 0x306230, 0x0F380F, 0x306230, 0x306230, line = 0x306230, edge = 0x306230, muted = 0x306230),
        t(0x0F380F, 0x184A18, 0x9BBC0F, 0x9BBC0F, 0x8BAC0F, 0x8BAC0F, 0x9BBC0F, 0x8BAC0F, 0x9BBC0F, line = 0x306230, edge = 0x306230, muted = 0x8BAC0F)),
    Palette("dracula", "Dracula", "Editores",
        t(0xFFFBEB, 0xFFFFFF, 0x1F1F1F, 0xCB3A2A, 0x036A96, 0x846E15, 0x14710A, 0x644AC9, 0xA34D14, sand = 0xE2DECA, line = 0xE2DECA, edge = 0xCFCFDE, muted = 0x6C664B),
        t(0x282A36, 0x353747, 0xF8F8F2, 0xFF5555, 0x8BE9FD, 0xF1FA8C, 0x50FA7B, 0xBD93F9, 0xFFB86C, sand = 0x44475A, line = 0x44475A, edge = 0x6272A4)),
    Palette("nord", "Nord", "Editores",
        t(0xE5E9F0, 0xECEFF4, 0x2E3440, 0xBF616A, 0x5E81AC, 0xD08770, 0xA3BE8C, 0xB48EAD, 0xD08770, sand = 0xD8DEE9, line = 0xD8DEE9),
        t(0x2E3440, 0x3B4252, 0xECEFF4, 0xBF616A, 0x88C0D0, 0xEBCB8B, 0xA3BE8C, 0xB48EAD, 0xD08770, sand = 0x434C5E, line = 0x434C5E, edge = 0x4C566A)),
    Palette("catppuccin", "Catppuccin", "Editores",
        t(0xE6E9EF, 0xEFF1F5, 0x4C4F69, 0xD20F39, 0x1E66F5, 0xDF8E1D, 0x40A02B, 0x8839EF, 0xFE640B, sand = 0xDCE0E8, line = 0xCCD0DA, edge = 0x9CA0B0, muted = 0x6C6F85),
        t(0x181825, 0x1E1E2E, 0xCDD6F4, 0xF38BA8, 0x89B4FA, 0xF9E2AF, 0xA6E3A1, 0xCBA6F7, 0xFAB387, sand = 0x313244, line = 0x313244, edge = 0x45475A, muted = 0xA6ADC8)),
    Palette("gruvbox", "Gruvbox", "Editores",
        t(0xFBF1C7, 0xF9F5D7, 0x3C3836, 0x9D0006, 0x076678, 0xB57614, 0x79740E, 0x8F3F71, 0xAF3A03, sand = 0xEBDBB2, line = 0xD5C4A1, edge = 0xBDAE93, muted = 0x7C6F64),
        t(0x282828, 0x32302F, 0xEBDBB2, 0xFB4934, 0x83A598, 0xFABD2F, 0xB8BB26, 0xD3869B, 0xFE8019, sand = 0x3C3836, line = 0x3C3836, edge = 0x504945, muted = 0xA89984)),
    Palette("solarized", "Solarized", "Editores",
        t(0xEEE8D5, 0xFDF6E3, 0x586E75, 0xDC322F, 0x268BD2, 0xB58900, 0x859900, 0x6C71C4, 0xCB4B16),
        t(0x002B36, 0x073642, 0x93A1A1, 0xDC322F, 0x268BD2, 0xB58900, 0x859900, 0x6C71C4, 0xCB4B16, muted = 0x839496)),
    Palette("tokyonight", "Tokyo Night", "Editores",
        t(0xE1E2E7, 0xEDEEF2, 0x3760BF, 0xF52A65, 0x2E7DE9, 0x8C6C3E, 0x587539, 0x9854F1, 0xB15C00, line = 0xB7C1E3),
        t(0x1A1B26, 0x24283B, 0xC0CAF5, 0xF7768E, 0x7AA2F7, 0xE0AF68, 0x9ECE6A, 0xBB9AF7, 0xFF9E64, sand = 0x292E42, line = 0x3B4261, edge = 0x545C7E, muted = 0x737AA2)),
    Palette("rosepine", "Rosé Pine", "Editores",
        t(0xFAF4ED, 0xFFFAF3, 0x464261, 0xB4637A, 0x56949F, 0xEA9D34, 0x286983, 0x907AA9, 0xD7827E, sand = 0xF2E9E1, line = 0xF2E9E1, edge = 0x9893A5, muted = 0x797593),
        t(0x191724, 0x1F1D2E, 0xE0DEF4, 0xEB6F92, 0x9CCFD8, 0xF6C177, 0x31748F, 0xC4A7E7, 0xEBBCBA, sand = 0x26233A, line = 0x26233A, edge = 0x6E6A86, muted = 0x908CAA)),
    Palette("everforest", "Everforest", "Editores",
        t(0xEFEBD4, 0xFDF6E3, 0x5C6A72, 0xF85552, 0x3A94C5, 0xDFA000, 0x8DA101, 0xDF69BA, 0xF57D26, sand = 0xE6E2CC, line = 0xE6E2CC, edge = 0x939F91, muted = 0x939F91),
        t(0x2D353B, 0x343F44, 0xD3C6AA, 0xE67E80, 0x7FBBB3, 0xDBBC7F, 0xA7C080, 0xD699B6, 0xE69875, sand = 0x3D484D, line = 0x3D484D, edge = 0x475258, muted = 0x859289)),
)

/** Letras para títulos (Google Fonts, con los gruesos que tiene cada una). */
val DISPLAY_FONTS: List<FontChoice> = listOf(
    FontChoice("Fraunces", intArrayOf(100, 200, 300, 400, 500, 600, 700, 800, 900)),
    FontChoice("Playfair Display", intArrayOf(400, 500, 600, 700, 800, 900)),
    FontChoice("DM Serif Display", intArrayOf(400)),
    FontChoice("Cormorant Garamond", intArrayOf(300, 400, 500, 600, 700)),
    FontChoice("EB Garamond", intArrayOf(400, 500, 600, 700, 800)),
    FontChoice("Libre Baskerville", intArrayOf(400, 500, 600, 700)),
    FontChoice("Lora", intArrayOf(400, 500, 600, 700)),
    FontChoice("Cinzel", intArrayOf(400, 500, 600, 700, 800, 900)),
    FontChoice("Bebas Neue", intArrayOf(400)),
    FontChoice("Oswald", intArrayOf(200, 300, 400, 500, 600, 700)),
    FontChoice("Anton", intArrayOf(400)),
    FontChoice("Archivo Black", intArrayOf(400)),
    FontChoice("Space Grotesk", intArrayOf(300, 400, 500, 600, 700)),
    FontChoice("Press Start 2P", intArrayOf(400)),
    FontChoice("VT323", intArrayOf(400)),
    FontChoice("Silkscreen", intArrayOf(400, 700)),
    FontChoice("Pixelify Sans", intArrayOf(400, 500, 600, 700)),
    FontChoice("Orbitron", intArrayOf(400, 500, 600, 700, 800, 900)),
    FontChoice("Audiowide", intArrayOf(400)),
    FontChoice("Righteous", intArrayOf(400)),
    FontChoice("Bungee", intArrayOf(400)),
    FontChoice("Bangers", intArrayOf(400)),
    FontChoice("Unbounded", intArrayOf(200, 300, 400, 500, 600, 700, 800, 900)),
    FontChoice("Syne", intArrayOf(400, 500, 600, 700, 800)),
    FontChoice("Chakra Petch", intArrayOf(300, 400, 500, 600, 700)),
    FontChoice("Teko", intArrayOf(300, 400, 500, 600, 700)),
    FontChoice("Abril Fatface", intArrayOf(400)),
    FontChoice("Bodoni Moda", intArrayOf(400, 500, 600, 700, 800, 900)),
    FontChoice("Young Serif", intArrayOf(400)),
    FontChoice("Instrument Serif", intArrayOf(400)),
    FontChoice("Gloock", intArrayOf(400)),
    FontChoice("Marcellus", intArrayOf(400)),
    FontChoice("Uncial Antiqua", intArrayOf(400)),
    FontChoice("MedievalSharp", intArrayOf(400)),
    FontChoice("Creepster", intArrayOf(400)),
    FontChoice("Black Ops One", intArrayOf(400)),
    FontChoice("Russo One", intArrayOf(400)),
    FontChoice("Shrikhand", intArrayOf(400)),
    FontChoice("Lobster", intArrayOf(400)),
    FontChoice("Pacifico", intArrayOf(400)),
    FontChoice("Permanent Marker", intArrayOf(400)),
    FontChoice("Caveat", intArrayOf(400, 500, 600, 700)),
    FontChoice("Major Mono Display", intArrayOf(400)),
    FontChoice("Rampart One", intArrayOf(400)),
    FontChoice("Mochiy Pop One", intArrayOf(400)),
    FontChoice("Fredoka", intArrayOf(300, 400, 500, 600, 700)),
    FontChoice("Bricolage Grotesque", intArrayOf(200, 300, 400, 500, 600, 700, 800)),
    FontChoice("Zilla Slab", intArrayOf(300, 400, 500, 600, 700)),
)

/** Letras para el texto. */
val BODY_FONTS: List<FontChoice> = listOf(
    FontChoice("Inter", intArrayOf(100, 200, 300, 400, 500, 600, 700, 800, 900)),
    FontChoice("DM Sans", intArrayOf(100, 200, 300, 400, 500, 600, 700, 800, 900)),
    FontChoice("Manrope", intArrayOf(200, 300, 400, 500, 600, 700, 800)),
    FontChoice("Outfit", intArrayOf(100, 200, 300, 400, 500, 600, 700, 800, 900)),
    FontChoice("Sora", intArrayOf(100, 200, 300, 400, 500, 600, 700, 800)),
    FontChoice("Lexend", intArrayOf(100, 200, 300, 400, 500, 600, 700, 800, 900)),
    FontChoice("Plus Jakarta Sans", intArrayOf(200, 300, 400, 500, 600, 700, 800)),
    FontChoice("Figtree", intArrayOf(300, 400, 500, 600, 700, 800, 900)),
    FontChoice("Rubik", intArrayOf(300, 400, 500, 600, 700, 800, 900)),
    FontChoice("Karla", intArrayOf(200, 300, 400, 500, 600, 700, 800)),
    FontChoice("Work Sans", intArrayOf(100, 200, 300, 400, 500, 600, 700, 800, 900)),
    FontChoice("Poppins", intArrayOf(100, 200, 300, 400, 500, 600, 700, 800, 900)),
    FontChoice("Montserrat", intArrayOf(100, 200, 300, 400, 500, 600, 700, 800, 900)),
    FontChoice("Nunito", intArrayOf(200, 300, 400, 500, 600, 700, 800, 900)),
    FontChoice("Quicksand", intArrayOf(300, 400, 500, 600, 700)),
    FontChoice("Comfortaa", intArrayOf(300, 400, 500, 600, 700)),
    FontChoice("IBM Plex Sans", intArrayOf(100, 200, 300, 400, 500, 600, 700)),
    FontChoice("Space Grotesk", intArrayOf(300, 400, 500, 600, 700)),
    FontChoice("Space Mono", intArrayOf(400, 700)),
    FontChoice("JetBrains Mono", intArrayOf(100, 200, 300, 400, 500, 600, 700, 800)),
    FontChoice("IBM Plex Mono", intArrayOf(100, 200, 300, 400, 500, 600, 700)),
    FontChoice("Share Tech Mono", intArrayOf(400)),
    FontChoice("Rajdhani", intArrayOf(300, 400, 500, 600, 700)),
    FontChoice("Exo 2", intArrayOf(100, 200, 300, 400, 500, 600, 700, 800, 900)),
    FontChoice("Titillium Web", intArrayOf(200, 300, 400, 600, 700, 900)),
    FontChoice("Barlow", intArrayOf(100, 200, 300, 400, 500, 600, 700, 800, 900)),
    FontChoice("Josefin Sans", intArrayOf(100, 200, 300, 400, 500, 600, 700)),
    FontChoice("Atkinson Hyperlegible", intArrayOf(400, 700)),
    FontChoice("Merriweather", intArrayOf(300, 400, 500, 600, 700, 800, 900)),
    FontChoice("Source Serif 4", intArrayOf(200, 300, 400, 500, 600, 700, 800, 900)),
    FontChoice("Crimson Pro", intArrayOf(200, 300, 400, 500, 600, 700, 800, 900)),
    FontChoice("Spectral", intArrayOf(200, 300, 400, 500, 600, 700, 800)),
    FontChoice("Lora", intArrayOf(400, 500, 600, 700)),
    FontChoice("EB Garamond", intArrayOf(400, 500, 600, 700, 800)),
    FontChoice("Red Hat Display", intArrayOf(300, 400, 500, 600, 700, 800, 900)),
    FontChoice("Chivo", intArrayOf(100, 200, 300, 400, 500, 600, 700, 800, 900)),
    FontChoice("Kanit", intArrayOf(100, 200, 300, 400, 500, 600, 700, 800, 900)),
    FontChoice("Saira", intArrayOf(100, 200, 300, 400, 500, 600, 700, 800, 900)),
    FontChoice("Zen Maru Gothic", intArrayOf(300, 400, 500, 700, 900)),
    FontChoice("M PLUS Rounded 1c", intArrayOf(100, 300, 400, 500, 700, 800, 900)),
)

/** Combinaciones listas (paleta + letras). «Atrio» en una letra = la que viene con la app. */
val STYLES: List<SkinStyle> = listOf(
    SkinStyle("vitrina", "Vitrina", "Atrio 6: portadas a lo grande, letra con carácter y coral", "vitrina", "Vitrina", "Atrio", "vitrina"),
    SkinStyle("clasico", "Atrio", "El de siempre: serif cálida y texto limpio", "atrio", "Atrio", "Atrio", "clasico"),
    SkinStyle("revista", "Revista", "Portadas de papel y titulares con carácter", "papel", "Playfair Display", "Source Serif 4", "editorial"),
    SkinStyle("nocturno", "Editorial nocturno", "Serif elegante sobre azul noche", "medianoche", "DM Serif Display", "DM Sans", "editorial"),
    SkinStyle("arcade", "Arcade", "Píxeles y verde de Game Boy", "gameboy", "Press Start 2P", "Space Mono", "pixel"),
    SkinStyle("pixel", "Píxel neón", "Recreativa de los 80", "neon", "Silkscreen", "JetBrains Mono", "pixel"),
    SkinStyle("cyberpunk", "Cyberpunk", "Neón y letras de cabina", "neon", "Orbitron", "Rajdhani", "neon"),
    SkinStyle("scifi", "Ciencia ficción", "Interfaz de nave", "oceano", "Audiowide", "Exo 2", "neon"),
    SkinStyle("terminal", "Terminal", "Fósforo ámbar", "ambar", "VT323", "IBM Plex Mono", "terminal"),
    SkinStyle("fantasia", "Fantasía", "Pergamino y piedra grabada", "vino", "Cinzel", "EB Garamond", "editorial"),
    SkinStyle("medieval", "Medieval", "Tinta de monasterio", "cafe", "MedievalSharp", "Crimson Pro", "editorial"),
    SkinStyle("terror", "Terror", "Para Resident Evil y compañía", "carmesi", "Creepster", "Spectral", "editorial"),
    SkinStyle("comic", "Cómic", "Viñetas y onomatopeyas", "atardecer", "Bangers", "Nunito", "comic"),
    SkinStyle("redondo", "Redondito", "Suave y amable", "sakura", "Fredoka", "Quicksand", "suave"),
    SkinStyle("japon", "Japón", "Té verde y letras redondas", "matcha", "Mochiy Pop One", "Zen Maru Gothic", "suave"),
    SkinStyle("nordico", "Nórdico", "Frío, limpio y tranquilo", "nord", "Bricolage Grotesque", "Manrope", "minimal"),
    SkinStyle("dracula", "Dracula", "El tema oscuro más famoso", "dracula", "Unbounded", "Figtree", "neon"),
    SkinStyle("catppuccin", "Catppuccin", "Pastel cremoso", "catppuccin", "Righteous", "Lexend", "suave"),
    SkinStyle("gruvbox", "Gruvbox", "Retro cálido", "gruvbox", "Zilla Slab", "IBM Plex Sans", "terminal"),
    SkinStyle("solarized", "Solarized", "El clásico de los programadores", "solarized", "Space Grotesk", "Work Sans", "minimal"),
    SkinStyle("tokyo", "Tokyo Night", "Luces de ciudad", "tokyonight", "Syne", "Outfit", "neon"),
    SkinStyle("rosepine", "Rosé Pine", "Rosa empolvado y pino", "rosepine", "Instrument Serif", "Karla", "suave"),
    SkinStyle("everforest", "Everforest", "Bosque en calma", "everforest", "Young Serif", "Atkinson Hyperlegible", "minimal"),
    SkinStyle("brutalista", "Brutalista", "Negro puro y letra gorda", "oled", "Archivo Black", "Space Grotesk", "brutal"),
    SkinStyle("deportivo", "Deportivo", "Marcador de estadio", "pizarra", "Bebas Neue", "Barlow", "brutal"),
    SkinStyle("lujo", "Lujo", "Cobre y Bodoni", "cobre", "Bodoni Moda", "Montserrat", "editorial"),
    SkinStyle("minimal", "Minimal", "Nada que sobre", "hielo", "Inter", "Inter", "minimal"),
    SkinStyle("cuaderno", "Cuaderno", "Apuntes a mano", "lavanda", "Caveat", "Karla", "suave"),
    SkinStyle("militar", "Militar", "Caqui y plantilla", "bosque", "Black Ops One", "Saira", "militar"),
    SkinStyle("ciruela", "Ciruela", "Morado profundo y geometría", "ciruela", "Sora", "Plus Jakarta Sans", "clasico"),
    SkinStyle("atardecer", "Atardecer", "Naranja de verano", "atardecer", "Shrikhand", "Poppins", "comic"),
)

/** Un color de acento de la revista, con su versión para el modo oscuro (más clara, para que se lea sobre papel oscuro). */
data class Accent(val id: String, val name: String, val light: Long, val dark: Long)

/** Los acentos que se pueden elegir (el rojo de imprenta, el de siempre, el primero). */
val ACCENT_COLORS = listOf(
    Accent("rojo", "Rojo", 0xFFC8372D, 0xFFE0533F),
    Accent("azul", "Azul", 0xFF2F5D8A, 0xFF7FA7C4),
    Accent("verde", "Verde", 0xFF3F7A4A, 0xFF8FB873),
    Accent("mostaza", "Mostaza", 0xFFB8861B, 0xFFE0B456),
    Accent("naranja", "Naranja", 0xFFC8642D, 0xFFE8894F),
    Accent("rosa", "Rosa", 0xFFB83A6B, 0xFFE07AA1),
    Accent("morado", "Morado", 0xFF7A4E8C, 0xFFB48BC4),
    Accent("tinta", "Tinta", 0xFF15120F, 0xFFF1EBE0),
    Accent("electrico", "Eléctrico", 0xFF1F4BFF, 0xFF7D95FF),
    Accent("fucsia", "Fucsia", 0xFFE6007E, 0xFFFF4DA6),
    Accent("amarillo", "Amarillo", 0xFFA87600, 0xFFFFCE00),
    Accent("turquesa", "Turquesa", 0xFF0F7C80, 0xFF5CC8C9),
    Accent("granate", "Granate", 0xFF7B1E2B, 0xFFD9707F),
    Accent("burdeos", "Burdeos", 0xFF5C1A33, 0xFFC56B8A),
    Accent("cereza", "Cereza", 0xFFB3122E, 0xFFF0566E),
    Accent("coral", "Coral", 0xFFD2574A, 0xFFF08A7C),
    Accent("terracota", "Terracota", 0xFFB5562F, 0xFFE0936A),
    Accent("cobre", "Cobre", 0xFF9A5B2E, 0xFFD7A06B),
    Accent("marron", "Marrón", 0xFF5B3A1E, 0xFFC9A27A),
    Accent("chocolate", "Chocolate", 0xFF3E2A1F, 0xFFB79B88),
    Accent("ocre", "Ocre", 0xFF9C7A12, 0xFFD9B646),
    Accent("oliva", "Oliva", 0xFF6B6B1E, 0xFFB9B864),
    Accent("verdeingles", "Inglés", 0xFF1F4D3A, 0xFF86C1A3),
    Accent("bosque", "Bosque", 0xFF2F5A2E, 0xFF8DC68A),
    Accent("esmeralda", "Esmeralda", 0xFF0E7A55, 0xFF5CCBA1),
    Accent("petroleo", "Petróleo", 0xFF17525C, 0xFF72B7C2),
    Accent("marino", "Marino", 0xFF1B2A41, 0xFF9DB4D3),
    Accent("cobalto", "Cobalto", 0xFF2548A8, 0xFF7F9CE8),
    Accent("indigo", "Índigo", 0xFF3B3A8C, 0xFF9E9CE6),
    Accent("ciruela", "Ciruela", 0xFF5B2A59, 0xFFC08ABD),
    Accent("lavanda", "Lavanda", 0xFF7C6BB0, 0xFFBDB0E6),
    Accent("rosapalo", "Rosa palo", 0xFFB5707E, 0xFFE8AAB5),
    Accent("pizarra", "Pizarra", 0xFF4A5560, 0xFFA9B4BF),
    Accent("grafito", "Grafito", 0xFF2B2E33, 0xFFC5C9CF),
    Accent("salvia", "Salvia", 0xFF6F8A6B, 0xFFB5CDB0),
)

/** El acento en claro y en oscuro: el elegido o, con «edicion», el de la edición. */
fun acentoDe(e: Edicion, id: String?): Pair<Long, Long> =
    ACCENT_COLORS.firstOrNull { it.id == id }?.let { it.light to it.dark } ?: ((e.paleta.light.general or 0xFF000000) to (e.paleta.dark.general or 0xFF000000))

/**
 * Fondos de color para toda la app (Apariencia → Fondo): colores hondos y sobrios, con fuerza, en lugar de solo papel claro u
 * oscuro. Cada uno trae sus tonos completos (fondo, tarjetas, líneas, tinta y un acento propio que va con él) y fija el
 * modo claro u oscuro. El acento que elijas en «Acento» manda sobre el suyo (salvo el rojo de siempre, que sobre un fondo
 * rojizo no se vería).
 */
class Fondo(val id: String, val name: String, val oscuro: Boolean, val tones: Tones)

val FONDOS: List<Fondo> = listOf(
    // Papel de verdad: blanco roto, algo beige, con tinta parda casi negra y rojo de sello
    Fondo("papelreal", "Papel de verdad", false, t(0xF8F2DF, 0xFCF8EA, 0x2B2418, 0x8C2A22, 0x2B2418, 0x5A4E33, 0x8A7B58, 0x8C2A22, 0x9A6B12,
        sand = 0xEFE6CC, line = 0xDDD0AE, edge = 0xBFAE84, muted = 0x5E5337)),
    // Granate: vino hondo, con tinta crema y oro viejo
    Fondo("granate", "Granate", true, t(0x5A1424, 0x6C1C30, 0xF8EDE8, 0xF0C26A, 0xF8EDE8, 0xDDBFC3, 0xB88A92, 0xF0C26A, 0xE9B94F,
        sand = 0x7A2639, line = 0x8F3D52, edge = 0xA85A6E, muted = 0xD3AAB2)),
    Fondo("bosque", "Verde bosque", true, t(0x12382B, 0x1B4837, 0xEDF3E8, 0xE2C86C, 0xEDF3E8, 0xBFD2BE, 0x8FB09A, 0xE2C86C, 0xDDBF58,
        sand = 0x25573F, line = 0x37705A, edge = 0x4F8A73, muted = 0xA9C7B3)),
    Fondo("marino", "Azul marino", true, t(0x10264A, 0x193560, 0xE9EEF7, 0xE6BA5E, 0xE9EEF7, 0xBCC9E0, 0x8DA2C6, 0xE6BA5E, 0xE0B050,
        sand = 0x234474, line = 0x38598C, edge = 0x5273A6, muted = 0xA3B4D2)),
    Fondo("petroleo", "Petróleo", true, t(0x0F3D44, 0x174F58, 0xE7F2F1, 0xF2C46D, 0xE7F2F1, 0xB5D3D2, 0x86B3B3, 0xF2C46D, 0xEDBB5E,
        sand = 0x1F6370, line = 0x317B86, edge = 0x4C98A2, muted = 0x9CC6C6)),
    Fondo("berenjena", "Berenjena", true, t(0x3C1A4A, 0x4D235F, 0xF6EAF4, 0xF2A9C2, 0xF6EAF4, 0xD6B8D8, 0xA98AB4, 0xF2A9C2, 0xEDB66C,
        sand = 0x5E2E75, line = 0x74428B, edge = 0x8E5DA5, muted = 0xC7A8CE)),
    Fondo("chocolate", "Chocolate", true, t(0x3A2218, 0x4A2E22, 0xF4E8DB, 0xE9A860, 0xF4E8DB, 0xD3BBA3, 0xA88B72, 0xE9A860, 0xE2A04E,
        sand = 0x5A3A2B, line = 0x704B3A, edge = 0x8B6650, muted = 0xC2A78E)),
    Fondo("pizarra", "Pizarra", true, t(0x232D38, 0x2D3A48, 0xE8EDF2, 0xE9B867, 0xE8EDF2, 0xBAC5D1, 0x8A9AAB, 0xE9B867, 0xE2B05A,
        sand = 0x3B4B5D, line = 0x4F6176, edge = 0x6A7F96, muted = 0xA2B0BF)),
    // Claro también: mostaza hondo, con tinta casi negra
    Fondo("mostaza", "Mostaza", false, t(0xD9A53A, 0xE6B954, 0x2A1E08, 0x6E1424, 0x2A1E08, 0x5A4412, 0x8A6A1E, 0x6E1424, 0x6E1424,
        sand = 0xCB962A, line = 0xB98521, edge = 0x8F6713, muted = 0x47340B)),
)

/** Tamaños del texto. */
val TEXT_SCALES = listOf(0.9f to "Pequeño", 1f to "Normal", 1.12f to "Grande", 1.25f to "Enorme")

object Skin {
    var palette by mutableStateOf(PALETTES[0])
    var display by mutableStateOf("Atrio")
    var body by mutableStateOf("Atrio")
    var textScale by mutableStateOf(1f)
    /** Forma: cómo se dibujan esquinas, notas, botones, portadas y el fondo. */
    var form by mutableStateOf(FORMS.first { it.id == "vitrina" })
    /** Cómo se ven las notas, aparte de la forma (null = las de la forma). */
    var gauge by mutableStateOf<GaugeKind?>(null)
    /** Camaleón: cada ficha se tiñe con los colores de su portada. */
    var chameleon by mutableStateOf(true)
    /** Movimiento: 0 vivo (con muelles), 1 sobrio, 2 mínimo. */
    var motion by mutableStateOf(0)
    /** Color de acento propio (0 = el de la paleta). Solo para la interfaz antigua; en la revista va [accentId]. */
    var accent by mutableStateOf(0L)
    /** Acento de la revista (uno de [ACCENT_COLORS], o «edicion»: el de la edición). */
    var accentId by mutableStateOf("edicion")
    /** Fondo de color elegido (uno de [FONDOS]; "" = papel claro u oscuro, según el tema). */
    var fondoId by mutableStateOf("")
    /** El fondo que se ve ahora: el elegido, salvo en lo de tu pareja (que va con el tema contrario, sin fondo de color). */
    val fondoActual: Fondo? get() = if (revista && !Mode.partnerShown) FONDOS.firstOrNull { it.id == fondoId } else null
    /** Edición de la revista (Ediciones.kt): su papel, sus letras, su cabecera… */
    var edicion by mutableStateOf("atrio")
    /** Colores a mano (Apariencia → A tu manera), 0 = sin elegir: mandan sobre el tema, el fondo y el acento. */
    var fondoPropio by mutableStateOf(0L)
    var tintaPropia by mutableStateOf(0L)
    var acentoPropio by mutableStateOf(0L)
    /** Letras a mano (de Google Fonts), "" = las de la edición. */
    var letraTitulos by mutableStateOf("")
    var letraTexto by mutableStateOf("")
    /** ¿El fondo propio es oscuro? (fija el modo, como los fondos de color); null sin fondo propio o en lo de tu pareja. */
    val fondoPropioOscuro: Boolean? get() = if (revista && !Mode.partnerShown && fondoPropio != 0L) Color(fondoPropio).luminance() < 0.4f else null

    /** Pone el papel de la edición (las letras las da Theme.kt según la edición). */
    fun ponEdicion() {
        palette = Ediciones.actual.paleta; display = "Atrio"; body = "Atrio"
        form = FORMS.first { it.id == "revista" }; gauge = null; chameleon = false; textScale = 1f
    }
    /** Tamaño de las portadas en las bibliotecas: 0 normal, 1 grandes, 2 pequeñas. */
    var covers by mutableStateOf(0)
    /** Revista: las listas con la portada al lado del título (como las sagas), en lugar de solo títulos. */
    var listCovers by mutableStateOf(false)
    /** Dock con el nombre del destino elegido (si no, solo iconos). */
    var dockLabels by mutableStateOf(true)
    /** La interfaz: solo hay una, la revista (la antigua, con dock, ya no existe). */
    var shell by mutableStateOf(2)
    val revista: Boolean get() = true

    /** Cambia de interfaz y le pone su estilo (colores, letra y forma); luego se puede retocar en Estilo. */
    fun applyShell(i: Int, ctx: Context) {
        shell = i
        when (i) {
            2 -> ponEdicion()
        }
        save(ctx)
    }
    /** Tonos de la ficha abierta (camaleón), por encima de la paleta. */
    var override by mutableStateOf<Tones?>(null)

    /** Pantalla de Estilo abierta (se pinta encima de toda la app). */
    var open by mutableStateOf(false)

    /** Tonos que se ven ahora (según el modo claro u oscuro). */
    val tones: Tones get() = override ?: propios(conAcento)
    private val conAcento: Tones get() = base.let { b ->
        // Con un fondo de color, el acento por defecto («edición» o el rojo de siempre) es el suyo; los demás, los tuyos
        if (revista && fondoActual != null && (accentId == "edicion" || accentId == "rojo")) b
        else if (revista) ACCENT_COLORS.firstOrNull { it.id == accentId }?.let { a -> b.copy(general = if (Mode.dark) a.dark else a.light) } ?: b
        else if (accent != 0L) b.copy(general = accent) else b
    }
    /** Los colores a mano por encima de todo: el fondo (las tarjetas, líneas y grises salen de él y del texto), el texto y el acento. */
    private fun propios(t: Tones): Tones =
        if (!revista || Mode.partnerShown) t else conPropios(t, fondoPropio, tintaPropia, acentoPropio)
    /** (también para los widgets, que leen lo guardado) */
    fun conPropios(t: Tones, fondo: Long, tintaP: Long, acentoP: Long): Tones {
        var r = t
        if (fondo != 0L || tintaP != 0L) {
            val papel = if (fondo != 0L) fondo else r.paper
            val tinta = when {
                tintaP != 0L -> tintaP
                fondo != 0L -> if (Color(papel).luminance() < 0.4f) 0xFFF1EBE0 else 0xFF15120F
                else -> r.ink
            }
            val crudo = lerp(Color(papel), Color(tinta), 0.04f).toArgb().toLong() and 0xFFFFFFFFL
            r = r.copy(paper = papel, crudo = crudo, ink = tinta, gfx = tinta, sand = 0, line = 0, edge = 0, muted = 0)
        }
        if (acentoP != 0L) r = r.copy(general = acentoP)
        return r
    }
    /** Tonos de la paleta elegida, sin camaleón. */
    val base: Tones get() = fondoActual?.tones ?: if (Mode.dark) palette.dark else palette.light

    private val provider = GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs
    )
    private val cache = HashMap<String, FontFamily>()

    /** La familia de letra de Google Fonts por su nombre (con sus gruesos reales), o null si es la de la app. */
    fun family(name: String): FontFamily? {
        // (sin nombre no hay letra: Google Fonts no acepta un nombre vacío y cerraría la app)
        if (name.isBlank() || name == "Atrio") return null
        if (name == "Vitrina") return VitrinaDisplay
        return cache.getOrPut(name) {
            val c = (DISPLAY_FONTS + BODY_FONTS).firstOrNull { it.name == name }
            val ws = c?.weights?.takeIf { it.isNotEmpty() } ?: intArrayOf(400, 700)
            FontFamily(ws.map { Font(GoogleFont(name), provider, FontWeight(it)) })
        }
    }

    fun apply(s: SkinStyle) {
        palette = PALETTES.firstOrNull { it.id == s.palette } ?: palette
        display = s.display
        body = s.body
        form = FORMS.firstOrNull { it.id == s.form } ?: form
        gauge = null
    }

    /** Sorpréndeme: paleta, letras, forma y notas al azar (con criterio: letras de títulos y de texto distintas). */
    fun surprise() {
        val rnd = kotlin.random.Random(System.nanoTime())
        palette = PALETTES.filter { it.id != palette.id }.random(rnd)
        display = DISPLAY_FONTS.random(rnd).name
        body = BODY_FONTS.filter { it.name != display }.random(rnd).name
        form = FORMS.filter { it.id != form.id }.random(rnd)
        gauge = if (rnd.nextBoolean()) null else GaugeKind.entries.random(rnd)
    }

    /** La combinación que coincide con lo elegido ahora (si es una de las listas). */
    fun currentStyle(): SkinStyle? = STYLES.firstOrNull { it.palette == palette.id && it.display == display && it.body == body && it.form == form.id && gauge == null }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("gamerate", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        val p = prefs(ctx)
        // Atrio 6: la primera vez, el estilo nuevo (después, lo que elijas)
        if (!p.getBoolean("skin_v6", false)) {
            p.edit().putBoolean("skin_v6", true).putString("skin_palette", "vitrina").putString("skin_display", "Vitrina")
                .putString("skin_body", "Atrio").putString("skin_form", "vitrina").remove("skin_gauge").apply()
        }
        palette = PALETTES.firstOrNull { it.id == p.getString("skin_palette", "vitrina") } ?: PALETTES.first { it.id == "vitrina" }
        display = p.getString("skin_display", "Vitrina") ?: "Vitrina"
        body = p.getString("skin_body", "Atrio") ?: "Atrio"
        textScale = p.getFloat("skin_scale", 1f)
        form = FORMS.firstOrNull { it.id == p.getString("skin_form", "vitrina") } ?: FORMS.first { it.id == "vitrina" }
        gauge = GaugeKind.entries.firstOrNull { it.name == p.getString("skin_gauge", "") }
        chameleon = p.getBoolean("skin_chameleon", true)
        motion = p.getInt("skin_motion", 0)
        accent = p.getLong("skin_accent", 0L)
        fondoId = p.getString("skin_fondo", "")?.takeIf { id -> FONDOS.any { it.id == id } } ?: ""
        accentId = p.getString("skin_accent_id", "edicion")?.takeIf { id -> id == "edicion" || ACCENT_COLORS.any { it.id == id } } ?: "edicion"
        edicion = Ediciones.porId(p.getString("skin_edicion", "atrio")).id
        fondoPropio = p.getLong("skin_fondo_propio", 0L); tintaPropia = p.getLong("skin_tinta_propia", 0L); acentoPropio = p.getLong("skin_acento_propio", 0L)
        letraTitulos = p.getString("skin_letra_titulos", "").orEmpty(); letraTexto = p.getString("skin_letra_texto", "").orEmpty()
        covers = p.getInt("skin_covers", 0)
        listCovers = p.getBoolean("skin_listcovers", false)
        dockLabels = p.getBoolean("skin_docklabels", true)
        // La interfaz Feed (1) ya no existe: pasa a Revista
        shell = 2
        if (!p.getBoolean("shell_v3", false)) {
            p.edit().putBoolean("shell_v3", true).apply()
            applyShell(shell, ctx)
        }
        // La revista: el papel, las letras y la forma de su edición (más claro/oscuro y el acento)
        if (shell == 2) ponEdicion()
        // El tamaño del texto de la app (Apariencia), en tanto por ciento
        if (shell == 2) textScale = p.getInt("skin_escala", 100).coerceIn(80, 140) / 100f
    }

    /** Tamaños del texto de la app que se pueden elegir (por ciento). */
    val ESCALAS = listOf(90 to "Pequeño", 100 to "Normal", 112 to "Grande", 125 to "Muy grande")
    fun ponEscala(ctx: Context, pct: Int) {
        textScale = pct.coerceIn(80, 140) / 100f
        prefs(ctx).edit().putInt("skin_escala", pct.coerceIn(80, 140)).apply()
        save(ctx)
    }

    fun save(ctx: Context) {
        prefs(ctx).edit().putString("skin_palette", palette.id).putString("skin_display", display)
            .putString("skin_body", body).putFloat("skin_scale", textScale).putString("skin_form", form.id)
            .putString("skin_gauge", gauge?.name ?: "").putBoolean("skin_chameleon", chameleon).putInt("skin_motion", motion)
            .putLong("skin_accent", accent).putString("skin_accent_id", accentId).putString("skin_fondo", fondoId).putString("skin_edicion", edicion)
            .putLong("skin_fondo_propio", fondoPropio).putLong("skin_tinta_propia", tintaPropia).putLong("skin_acento_propio", acentoPropio)
            .putString("skin_letra_titulos", letraTitulos).putString("skin_letra_texto", letraTexto).putInt("skin_covers", covers).putBoolean("skin_listcovers", listCovers).putBoolean("skin_docklabels", dockLabels).putInt("skin_shell", shell).apply()
        ajustesCambiados(ctx)
        Widgets.pedir(ctx)
    }
}
