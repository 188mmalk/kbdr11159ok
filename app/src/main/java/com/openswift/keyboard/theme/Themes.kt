package com.openswift.keyboard.theme

import androidx.compose.ui.graphics.Color

data class KbTheme(
    val id: String,
    val name: String,
    val background: Int,
    val keyBackground: Int,
    val keyModifierBackground: Int,
    val keyText: Int,
    val keyAccent: Int,
    val suggestionBg: Int,
    val suggestionText: Int,
    val gestureTrail: Int
) {
    val displayName: String
        get() = when (id) {
            "amoled" -> "أسود AMOLED (الحالي)"
            "honey_3d" -> "عسلي ثلاثي الأبعاد 3D"
            "retro_1995" -> "كلاسيك 1995 ثريدي"
            "ios_style" -> "نمط آيفون ثريدي (iOS)"
            "mocha" -> "كاتبوشين موكا"
            "github_dark" -> "جيت هب داكن"
            "nord" -> "نورد الشتوي"
            "dracula" -> "دراكولا"
            "solarized_light" -> "سولاريزد الفاتح"
            "tokyo_night" -> "ليل طوكيو"
            "rose_pine" -> "روز باين"
            "gruvbox" -> "جروف بوكس"
            "high_contrast" -> "تباين عالي"
            else -> name
        }

    companion object {
        fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        fun rgba(r: Int, g: Int, b: Int, a: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}

object Themes {

    val Amoled = KbTheme(
        id = "amoled",
        name = "Cyan Slate",
        background = KbTheme.rgb(0x05, 0x0B, 0x14),
        keyBackground = KbTheme.rgb(0x18, 0x26, 0x3A),
        keyModifierBackground = KbTheme.rgb(0x12, 0x1C, 0x2B),
        keyText = KbTheme.rgb(0xFF, 0xFF, 0xFF),
        keyAccent = KbTheme.rgb(0x00, 0xE5, 0xFF),
        suggestionBg = KbTheme.rgb(0x05, 0x0B, 0x14),
        suggestionText = KbTheme.rgb(0xFF, 0xFF, 0xFF),
        gestureTrail = KbTheme.rgb(0x00, 0xE5, 0xFF)
    )

    val Honey3D = KbTheme(
        id = "honey_3d",
        name = "Honey Amber 3D",
        background = KbTheme.rgb(0x1D, 0x11, 0x05),
        keyBackground = KbTheme.rgb(0x6B, 0x43, 0x18),
        keyModifierBackground = KbTheme.rgb(0x4A, 0x2B, 0x0E),
        keyText = KbTheme.rgb(0xFF, 0xF6, 0xDF),
        keyAccent = KbTheme.rgb(0xF5, 0x9E, 0x0B),
        suggestionBg = KbTheme.rgb(0x1D, 0x11, 0x05),
        suggestionText = KbTheme.rgb(0xFF, 0xF6, 0xDF),
        gestureTrail = KbTheme.rgb(0xF5, 0x9E, 0x0B)
    )

    val Retro1995 = KbTheme(
        id = "retro_1995",
        name = "Classic 1995 (Win95/IBM)",
        background = KbTheme.rgb(0xC3, 0xC7, 0xCB),
        keyBackground = KbTheme.rgb(0xEC, 0xE9, 0xD8),
        keyModifierBackground = KbTheme.rgb(0xD4, 0xD0, 0xC8),
        keyText = KbTheme.rgb(0x11, 0x11, 0x11),
        keyAccent = KbTheme.rgb(0x00, 0x00, 0x80),
        suggestionBg = KbTheme.rgb(0xC3, 0xC7, 0xCB),
        suggestionText = KbTheme.rgb(0x11, 0x11, 0x11),
        gestureTrail = KbTheme.rgb(0x00, 0x00, 0x80)
    )

    val IosStyle = KbTheme(
        id = "ios_style",
        name = "iPhone iOS 3D",
        background = KbTheme.rgb(0xD1, 0xD5, 0xDB),
        keyBackground = KbTheme.rgb(0xFF, 0xFF, 0xFF),
        keyModifierBackground = KbTheme.rgb(0xB0, 0xB6, 0xC2),
        keyText = KbTheme.rgb(0x00, 0x00, 0x00),
        keyAccent = KbTheme.rgb(0x00, 0x7A, 0xFF),
        suggestionBg = KbTheme.rgb(0xD1, 0xD5, 0xDB),
        suggestionText = KbTheme.rgb(0x00, 0x00, 0x00),
        gestureTrail = KbTheme.rgb(0x00, 0x7A, 0xFF)
    )

    val Mocha = KbTheme(
        id = "mocha",
        name = "Catppuccin Mocha",
        background = KbTheme.rgb(30, 30, 46),
        keyBackground = KbTheme.rgb(49, 50, 68),
        keyModifierBackground = KbTheme.rgb(24, 24, 37),
        keyText = KbTheme.rgb(205, 214, 244),
        keyAccent = KbTheme.rgb(137, 180, 250),
        suggestionBg = KbTheme.rgb(24, 24, 37),
        suggestionText = KbTheme.rgb(205, 214, 244),
        gestureTrail = KbTheme.rgb(203, 166, 247)
    )

    val GithubDark = KbTheme(
        id = "github_dark",
        name = "GitHub Dark",
        background = KbTheme.rgb(13, 17, 23),
        keyBackground = KbTheme.rgb(33, 38, 45),
        keyModifierBackground = KbTheme.rgb(22, 27, 34),
        keyText = KbTheme.rgb(201, 209, 217),
        keyAccent = KbTheme.rgb(88, 166, 255),
        suggestionBg = KbTheme.rgb(13, 17, 23),
        suggestionText = KbTheme.rgb(201, 209, 217),
        gestureTrail = KbTheme.rgb(125, 191, 255)
    )

    val SwiftDark = KbTheme(
        id = "swift_dark",
        name = "Swift Dark",
        background = KbTheme.rgb(0x05, 0x0B, 0x14),
        keyBackground = KbTheme.rgb(0x18, 0x26, 0x3A),
        keyModifierBackground = KbTheme.rgb(0x12, 0x1C, 0x2B),
        keyText = KbTheme.rgb(0xFF, 0xFF, 0xFF),
        keyAccent = KbTheme.rgb(0x00, 0xE5, 0xFF),
        suggestionBg = KbTheme.rgb(0x05, 0x0B, 0x14),
        suggestionText = KbTheme.rgb(0xFF, 0xFF, 0xFF),
        gestureTrail = KbTheme.rgb(0x00, 0xE5, 0xFF)
    )

    val MaterialLight = KbTheme(
        id = "material_light",
        name = "Material Light",
        background = KbTheme.rgb(220, 226, 234),
        keyBackground = KbTheme.rgb(255, 255, 255),
        keyModifierBackground = KbTheme.rgb(192, 200, 212),
        keyText = KbTheme.rgb(20, 20, 20),
        keyAccent = KbTheme.rgb(98, 0, 238),
        suggestionBg = KbTheme.rgb(220, 226, 234),
        suggestionText = KbTheme.rgb(20, 20, 20),
        gestureTrail = KbTheme.rgb(98, 0, 238)
    )

    val Pixel = KbTheme(
        id = "pixel",
        name = "Pixel",
        background = KbTheme.rgb(15, 16, 17),
        keyBackground = KbTheme.rgb(15, 16, 17),
        keyModifierBackground = KbTheme.rgb(15, 16, 17),
        keyText = KbTheme.rgb(232, 234, 237),
        keyAccent = KbTheme.rgb(138, 180, 248),
        suggestionBg = KbTheme.rgb(15, 16, 17),
        suggestionText = KbTheme.rgb(232, 234, 237),
        gestureTrail = KbTheme.rgb(138, 180, 248)
    )

    val Nord = KbTheme(
        id = "nord",
        name = "Nord",
        background = KbTheme.rgb(46, 52, 64),
        keyBackground = KbTheme.rgb(59, 66, 82),
        keyModifierBackground = KbTheme.rgb(36, 41, 51),
        keyText = KbTheme.rgb(236, 239, 244),
        keyAccent = KbTheme.rgb(136, 192, 208),
        suggestionBg = KbTheme.rgb(36, 41, 51),
        suggestionText = KbTheme.rgb(216, 222, 233),
        gestureTrail = KbTheme.rgb(143, 188, 187)
    )

    val Dracula = KbTheme(
        id = "dracula",
        name = "Dracula",
        background = KbTheme.rgb(40, 42, 54),
        keyBackground = KbTheme.rgb(68, 71, 90),
        keyModifierBackground = KbTheme.rgb(40, 42, 54),
        keyText = KbTheme.rgb(248, 248, 242),
        keyAccent = KbTheme.rgb(189, 147, 249),
        suggestionBg = KbTheme.rgb(40, 42, 54),
        suggestionText = KbTheme.rgb(248, 248, 242),
        gestureTrail = KbTheme.rgb(139, 233, 253)
    )

    val Tokyonight = KbTheme(
        id = "tokyonight",
        name = "Tokyo Night",
        background = KbTheme.rgb(26, 27, 38),
        keyBackground = KbTheme.rgb(41, 44, 60),
        keyModifierBackground = KbTheme.rgb(26, 27, 38),
        keyText = KbTheme.rgb(192, 202, 245),
        keyAccent = KbTheme.rgb(122, 162, 247),
        suggestionBg = KbTheme.rgb(26, 27, 38),
        suggestionText = KbTheme.rgb(173, 187, 230),
        gestureTrail = KbTheme.rgb(158, 206, 106)
    )

    val HighContrast = KbTheme(
        id = "high_contrast",
        name = "High Contrast (WCAG AAA)",
        background = KbTheme.rgb(255, 255, 255),     // White
        keyBackground = KbTheme.rgb(240, 240, 240),  // Very light gray
        keyModifierBackground = KbTheme.rgb(224, 224, 224), // Light gray
        keyText = KbTheme.rgb(0, 0, 0),              // Black (21:1 contrast)
        keyAccent = KbTheme.rgb(0, 0, 170),          // Dark blue (8.6:1 on white, 9.1:1 on gray)
        suggestionBg = KbTheme.rgb(0, 0, 0),         // Black
        suggestionText = KbTheme.rgb(255, 255, 255), // White (21:1 contrast)
        gestureTrail = KbTheme.rgb(0, 0, 255)        // Bright blue (5.2:1 on white)
    )

    val all: List<KbTheme> = listOf(
        Amoled,
        Honey3D,
        Retro1995,
        IosStyle,
        Mocha,
        GithubDark,
        SwiftDark,
        MaterialLight,
        Pixel,
        Nord,
        Dracula,
        Tokyonight,
        HighContrast
    )

    fun byId(id: String): KbTheme = all.firstOrNull { it.id == id } ?: Amoled
}
