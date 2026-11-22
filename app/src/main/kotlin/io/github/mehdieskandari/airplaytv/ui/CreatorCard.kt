package io.github.mehdieskandari.airplaytv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mehdieskandari.airplaytv.R
import io.github.mehdieskandari.airplaytv.ui.theme.TvColors

@Composable
fun CreatorCard(modifier: Modifier = Modifier) {
    TvCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Image(
                painterResource(R.drawable.creator_mehdi),
                contentDescription = stringResource(R.string.creator_avatar),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(CircleShape)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.creator_name), style = MaterialTheme.typography.titleMedium, color = TvColors.Label)
                Text(stringResource(R.string.creator_role), style = MaterialTheme.typography.bodyMedium, color = TvColors.SecondaryLabel)
            }
        }
    }
}
