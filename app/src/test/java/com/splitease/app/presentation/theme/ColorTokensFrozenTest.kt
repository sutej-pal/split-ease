package com.splitease.app.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Locks every brand token to the hex captured from the old wash/shade/lerp expressions.
 * A mismatch means a colour changed.
 */
class ColorTokensFrozenTest {
    @Test
    fun tokens_match_resolved_hex() {
        assertHex(0xFF4F46E5, IndigoLight)
        assertHex(0xFFFFA008, AmberLight)
        assertHex(0xFFFBFCFF, BackgroundLight)
        assertHex(0xFFE6EAFF, PrimaryContainerLight)
        assertHex(0xFFFFFFFF, SurfaceLight)
        assertHex(0xFFF1F3FF, SurfaceMutedLight)
        assertHex(0xFFC3CBFC, BannerFriendsLight)
        assertHex(0xFFFFE9D1, BannerHomeLight)
        assertHex(0xFFC3CBFC, BannerOtherLight)
        assertHex(0xFF7781F1, IndigoDark)
        assertHex(0xFFFFAE4C, AmberDark)
        assertHex(0xFF000004, BackgroundDark)
        assertHex(0xFF030217, SurfaceDark)
        assertHex(0xFF3D36B7, BannerFriendsDark)
        assertHex(0xFFC37D2B, BannerHomeDark)
        assertHex(0xFF49509E, BannerOtherDark)
        assertHex(0xFF747CF0, FriendDetailBannerDark)
        assertHex(0xFF332C9B, NonGroupBannerDark)
        assertHex(0xFFC43D5A, OweRed)
        assertHex(0xFFFDE8EC, OweContainer)
        assertHex(0xFF1B8A6B, OwedTeal)
        assertHex(0xFFDDF6EE, OwedContainer)
        assertHex(0xFF060424, TextPrimaryLight)
        assertHex(0xFF575F9E, TextSecondaryLight)
        assertHex(0xFFEFF1FF, TextPrimaryDark)
        assertHex(0xFFBFC8FC, TextSecondaryDark)
        assertHex(0xFFDAE0FE, OutlineLight)
        assertHex(0xFFEBEEFF, OutlineVariantLight)
        assertHex(0xFF4B465C, OutlineDark)
        assertHex(0xFF3A3552, OutlineVariantDark)
        assertHex(0xFF5F68C5, SplitEaseColors.IconOther)
        assertHex(0xFF4F46E5, SplitEaseColors.IconFriends)
        assertHex(0xFFFFA008, SplitEaseColors.IconHome)
        assertEquals(BannerFriendsDark, SplitEaseColors.BannerFriendsDark)
        assertEquals(BannerHomeDark, SplitEaseColors.BannerHomeDark)
        assertEquals(BannerOtherDark, SplitEaseColors.BannerOtherDark)
        assertEquals(BannerFriendsLight, SplitEaseColors.BannerFriends)
        assertEquals(BannerHomeLight, SplitEaseColors.BannerHome)
        assertEquals(BannerOtherLight, SplitEaseColors.BannerOther)
    }

    private fun assertHex(argb: Long, color: Color) {
        assertEquals(argb.toInt(), color.toArgb())
    }
}
