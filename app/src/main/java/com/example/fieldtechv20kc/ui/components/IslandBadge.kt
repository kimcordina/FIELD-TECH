package com.example.fieldtechv20kc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Island { MALTA, GOZO }

// Gozo locality names WITHOUT the "(Gozo)" suffix, so clients saved earlier
// with hand-typed localities (e.g. "Xewkija" or "Victoria") are still
// recognised. Ambiguous names shared with Malta (Zebbug, Mgarr, Rabat)
// intentionally resolve to Malta unless the text mentions Gozo explicitly.
private val gozoLocalityNames = setOf(
    "victoria",
    "fontana",
    "ghajnsielem",
    "gharb",
    "ghasri",
    "kercem",
    "marsalforn",
    "munxar",
    "nadur",
    "qala",
    "san lawrenz",
    "sannat",
    "xaghra",
    "xewkija",
    "xlendi"
)

/**
 * Determines which island a locality belongs to.
 * Returns null when the locality is blank/unknown so no badge is shown.
 */
fun islandForLocality(locality: String?): Island? {
    val raw = locality?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val lower = raw.lowercase()
    if (lower.contains("gozo")) return Island.GOZO
    // Strip any "(...)" suffix and compare against known Gozo names
    val base = lower.substringBefore("(").trim().trimEnd('/', ',')
    if (base in gozoLocalityNames) return Island.GOZO
    return Island.MALTA
}

/**
 * Small colored chip showing MALTA (blue) or GOZO (orange) so technicians
 * immediately see which island a client is on. Renders nothing when the
 * locality is blank.
 */
@Composable
fun IslandBadge(locality: String?, modifier: Modifier = Modifier) {
    val island = islandForLocality(locality) ?: return
    val (label, background, foreground) = when (island) {
        Island.MALTA -> Triple("MALTA", Color(0xFFE3F2FD), Color(0xFF1565C0))
        Island.GOZO -> Triple("GOZO", Color(0xFFFFF3E0), Color(0xFFE65100))
    }
    Text(
        text = label,
        color = foreground,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
