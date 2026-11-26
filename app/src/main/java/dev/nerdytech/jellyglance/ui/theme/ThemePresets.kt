package dev.nerdytech.jellyglance.ui.theme

/**
 * JellyGlance's own theme presets (apps/web/src/lib/theme.js in the JellyGlance server), so the
 * app can match whatever the web dashboard uses.
 */
data class ThemePreset(val name: String, val group: String, val primary: Long, val secondary: Long, val background: Long, val surface: Long)

object ThemePresets {
    val groups = listOf("Signature", "Cool", "Warm", "Neon", "Classic")

    val all = listOf(
        ThemePreset("JellyGlance", "Signature", 0xFF9B6AC8, 0xFF4AA8BC, 0xFF0B0D12, 0xFF131820),
        ThemePreset("Jellyfin", "Signature", 0xFF00A4DC, 0xFFAA5CC3, 0xFF0A0E14, 0xFF141B24),
        ThemePreset("OLED", "Signature", 0xFFC4B5FD, 0xFF67E8F9, 0xFF000000, 0xFF0C0C0F),
        ThemePreset("Noir", "Signature", 0xFFFAFAFA, 0xFFF43F5E, 0xFF050505, 0xFF111111),
        ThemePreset("Paper", "Signature", 0xFFF8FAFC, 0xFF94A3B8, 0xFF111318, 0xFF20242C),
        ThemePreset("Graphite", "Signature", 0xFFCBD5E1, 0xFF64748B, 0xFF0A0C10, 0xFF161A21),
        ThemePreset("Slate", "Signature", 0xFF94A3B8, 0xFF38BDF8, 0xFF080B10, 0xFF151B24),
        ThemePreset("Mono", "Signature", 0xFFE5E7EB, 0xFF94A3B8, 0xFF07080A, 0xFF15171C),
        ThemePreset("Ocean", "Cool", 0xFF2DD4BF, 0xFF38BDF8, 0xFF071015, 0xFF10212A),
        ThemePreset("Arctic", "Cool", 0xFF93C5FD, 0xFF67E8F9, 0xFF06101A, 0xFF101D2B),
        ThemePreset("Lagoon", "Cool", 0xFF06B6D4, 0xFF14B8A6, 0xFF041115, 0xFF0D2226),
        ThemePreset("Iceberg", "Cool", 0xFF7DD3FC, 0xFFC4B5FD, 0xFF071018, 0xFF121D29),
        ThemePreset("Deep Sea", "Cool", 0xFF38BDF8, 0xFF2DD4BF, 0xFF031018, 0xFF0B202B),
        ThemePreset("Electric Blue", "Cool", 0xFF2563EB, 0xFF67E8F9, 0xFF050A18, 0xFF101B35),
        ThemePreset("Midnight", "Cool", 0xFF60A5FA, 0xFFF472B6, 0xFF050816, 0xFF101827),
        ThemePreset("Peacock", "Cool", 0xFF14B8A6, 0xFFA3E635, 0xFF031014, 0xFF0C2024),
        ThemePreset("Nord", "Cool", 0xFF88C0D0, 0xFFB48EAD, 0xFF0B1118, 0xFF17202B),
        ThemePreset("Ember", "Warm", 0xFFF97316, 0xFFF43F5E, 0xFF120B08, 0xFF1F1512),
        ThemePreset("Forest", "Warm", 0xFF22C55E, 0xFFEAB308, 0xFF08110D, 0xFF111D17),
        ThemePreset("Solar", "Warm", 0xFFFACC15, 0xFFFB923C, 0xFF100D05, 0xFF201809),
        ThemePreset("Mint", "Warm", 0xFF34D399, 0xFFA3E635, 0xFF06110C, 0xFF102017),
        ThemePreset("Copper", "Warm", 0xFFD97706, 0xFFF59E0B, 0xFF110B05, 0xFF21160C),
        ThemePreset("Citrus", "Warm", 0xFF84CC16, 0xFFFACC15, 0xFF080F05, 0xFF14200D),
        ThemePreset("Crimson", "Warm", 0xFFDC2626, 0xFFFBBF24, 0xFF100405, 0xFF1F0D0F),
        ThemePreset("Volcanic", "Warm", 0xFFFB923C, 0xFFFEF08A, 0xFF120805, 0xFF26150D),
        ThemePreset("Honey", "Warm", 0xFFFBBF24, 0xFFFEF3C7, 0xFF120D03, 0xFF251B08),
        ThemePreset("Moss", "Warm", 0xFFA3E635, 0xFF84CC16, 0xFF091006, 0xFF17210D),
        ThemePreset("Tangerine", "Warm", 0xFFFB923C, 0xFFF472B6, 0xFF130905, 0xFF26140F),
        ThemePreset("Sandstone", "Warm", 0xFFD6A36A, 0xFFE7C9A0, 0xFF120E09, 0xFF221A12),
        ThemePreset("Ruby", "Warm", 0xFFEF4444, 0xFFF97316, 0xFF120707, 0xFF211010),
        ThemePreset("Aurora", "Neon", 0xFFA78BFA, 0xFF22D3EE, 0xFF090814, 0xFF17142A),
        ThemePreset("Synth", "Neon", 0xFFFF2BD6, 0xFF00E5FF, 0xFF080510, 0xFF171024),
        ThemePreset("Terminal", "Neon", 0xFF4ADE80, 0xFFF8FAFC, 0xFF030604, 0xFF0B120D),
        ThemePreset("Matrix", "Neon", 0xFF22C55E, 0xFF86EFAC, 0xFF020806, 0xFF08140F),
        ThemePreset("Coral", "Neon", 0xFFFB7185, 0xFF2DD4BF, 0xFF10090B, 0xFF201316),
        ThemePreset("Limewire", "Neon", 0xFFBEF264, 0xFF22D3EE, 0xFF050B08, 0xFF101A14),
        ThemePreset("Plasma", "Neon", 0xFFE879F9, 0xFF818CF8, 0xFF0D0614, 0xFF1D1029),
        ThemePreset("Cyberpunk", "Neon", 0xFFFCEE0A, 0xFFFF2A6D, 0xFF0A0610, 0xFF1A1024),
        ThemePreset("Flamingo", "Neon", 0xFFFB7185, 0xFFF472B6, 0xFF140810, 0xFF26141E),
        ThemePreset("Grape", "Classic", 0xFFC084FC, 0xFF818CF8, 0xFF0D0718, 0xFF1B102C),
        ThemePreset("Rose", "Classic", 0xFFFB7185, 0xFFF9A8D4, 0xFF130910, 0xFF23111C),
        ThemePreset("Sakura", "Classic", 0xFFFDA4AF, 0xFFF0ABFC, 0xFF120910, 0xFF24131F),
        ThemePreset("Violet Night", "Classic", 0xFF8B5CF6, 0xFFC4B5FD, 0xFF090615, 0xFF17102A),
        ThemePreset("Dracula", "Classic", 0xFFBD93F9, 0xFFFF79C6, 0xFF0D0F17, 0xFF1C1E2B),
        ThemePreset("Catppuccin", "Classic", 0xFFCBA6F7, 0xFF89B4FA, 0xFF0E0F16, 0xFF1A1B26),
        ThemePreset("Gruvbox", "Classic", 0xFFFABD2F, 0xFFFE8019, 0xFF14120E, 0xFF221E16),
        ThemePreset("Tokyo Night", "Classic", 0xFF7AA2F7, 0xFFBB9AF7, 0xFF0D1017, 0xFF1A1B26),
        ThemePreset("One Dark", "Classic", 0xFF61AFEF, 0xFFC678DD, 0xFF0F1218, 0xFF1C212B),
        ThemePreset("Solarized", "Classic", 0xFF268BD2, 0xFF2AA198, 0xFF002B36, 0xFF073642),
        ThemePreset("Kanagawa", "Classic", 0xFF7E9CD8, 0xFFFFA066, 0xFF12131A, 0xFF1F1F28),
        ThemePreset("Everforest", "Classic", 0xFFA7C080, 0xFFDBBC7F, 0xFF121612, 0xFF1E2320),
        ThemePreset("Rose Pine", "Classic", 0xFFC4A7E7, 0xFFEBBCBA, 0xFF121018, 0xFF1F1D27),
        ThemePreset("Monokai", "Classic", 0xFFA6E22E, 0xFFF92672, 0xFF14140F, 0xFF22221A),
        ThemePreset("Horizon", "Classic", 0xFFE95678, 0xFF25B2BC, 0xFF120E12, 0xFF1F1A20),
        ThemePreset("Night Owl", "Classic", 0xFF82AAFF, 0xFFC792EA, 0xFF011627, 0xFF0D2237),
    )

    val default: ThemePreset get() = all.first()

    fun byName(name: String?): ThemePreset = all.firstOrNull { it.name == name } ?: default

    /** "Server" means the custom palette saved in the user's JellyGlance preferences. */
    fun resolve(name: String, server: List<Long>): ThemePreset =
        if (name == "Server" && server.size == 4) ThemePreset("Server", "Custom", server[0], server[1], server[2], server[3]) else byName(name)
}
