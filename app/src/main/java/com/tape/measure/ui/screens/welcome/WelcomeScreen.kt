package com.tape.measure.ui.screens.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tape.measure.ui.theme.AmberBottom
import com.tape.measure.ui.theme.AmberTop
import com.tape.measure.ui.theme.InkBackground
import com.tape.measure.ui.theme.InkTextSecondary
import com.tape.measure.ui.theme.TapeTheme

/**
 * First screen users see.
 * Matches the prototype (docs/tape-prototype.html → Welcome state).
 *
 * Visual contract:
 * - TAPE wordmark + tick glyph at top
 * - Large warm headline ("Welcome to Tape. Let's measure something.")
 * - Short body explaining the gesture
 * - Amber gradient CTA "Continue"
 * - Footer microcopy: "Next step asks for camera access…"
 */
@Composable
fun WelcomeScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val amberGradient = Brush.horizontalGradient(listOf(AmberTop, AmberBottom))

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top: brand mark
            BrandMark()

            // Middle: headline + body
            Column {
                Text(
                    text = "Welcome to Tape.\nLet's measure something.",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Point your camera, tap two points, read the distance. No account, no ads, nothing to buy.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Bottom: CTA + footer microcopy
            Column {
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberBottom,
                        contentColor = InkBackground,
                    ),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(amberGradient),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Continue",
                            style = MaterialTheme.typography.labelLarge,
                            color = InkBackground,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Next step asks for camera access — to measure what your camera sees. Frames are processed on your phone and never uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}

@Composable
private fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "TAPE",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "│││││",
            style = MaterialTheme.typography.titleMedium,
            color = InkTextSecondary,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0E0C, widthDp = 412, heightDp = 892)
@Composable
private fun WelcomeScreenPreview() {
    TapeTheme(darkTheme = true) {
        WelcomeScreen(onContinue = {})
    }
}