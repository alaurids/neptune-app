package com.example.projectneptune

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.projectneptune.ui.theme.ProjectNeptuneTheme

data class ShellfishSpecies(
    @DrawableRes val imageRes: Int,
    @StringRes val nameRes: Int,
    @StringRes val scientificNameRes: Int,
    @StringRes val descriptionRes: Int
)

private data class ShellfishCategory(
    @StringRes val titleRes: Int,
    val species: List<ShellfishSpecies>
)

private val categorizedShellfish = listOf(
    ShellfishCategory(R.string.clams, listOf(
        ShellfishSpecies(R.mipmap.bc_foreground, R.string.bc_name, R.string.bc_scientific, R.string.bc_description),
        ShellfishSpecies(R.mipmap.g_foreground, R.string.g_name, R.string.g_scientific, R.string.g_description),
        ShellfishSpecies(R.mipmap.hc_foreground, R.string.hc_name, R.string.hc_scientific, R.string.hc_description),
        ShellfishSpecies(R.mipmap.lc_foreground, R.string.lc_name, R.string.lc_scientific, R.string.lc_description),
        ShellfishSpecies(R.mipmap.mc_foreground, R.string.mc_name, R.string.mc_scientific, R.string.mc_description),
        ShellfishSpecies(R.mipmap.rc_foreground, R.string.rc_name, R.string.rc_scientific, R.string.rc_description),
        ShellfishSpecies(R.mipmap.sc_foreground, R.string.sc_name, R.string.sc_scientific, R.string.sc_description),
        ShellfishSpecies(R.mipmap.vc_foreground, R.string.vc_name, R.string.vc_scientific, R.string.vc_description)
    )),
    ShellfishCategory(R.string.cockles, listOf(
        ShellfishSpecies(R.mipmap.nc_foreground, R.string.nc_name, R.string.nc_scientific, R.string.nc_description)
    )),
    ShellfishCategory(R.string.mussels, listOf(
        ShellfishSpecies(R.mipmap.bm_foreground, R.string.bm_name, R.string.bm_scientific, R.string.bm_description),
        ShellfishSpecies(R.mipmap.cm_foreground, R.string.cm_name, R.string.cm_scientific, R.string.cm_description)
    )),
    ShellfishCategory(R.string.oysters, listOf(
        ShellfishSpecies(R.mipmap.po_foreground, R.string.po_name, R.string.po_scientific, R.string.po_description),
        ShellfishSpecies(R.mipmap.oo_foreground, R.string.oo_name, R.string.oo_scientific, R.string.oo_description)
    )),
    ShellfishCategory(R.string.scallops, listOf(
        ShellfishSpecies(R.mipmap.ps_foreground, R.string.ps_name, R.string.ps_scientific, R.string.ps_description),
        ShellfishSpecies(R.mipmap.ss_foreground, R.string.ss_name, R.string.ss_scientific, R.string.ss_description),
        ShellfishSpecies(R.mipmap.rs_foreground, R.string.rs_name, R.string.rs_scientific, R.string.rs_description),
        ShellfishSpecies(R.mipmap.ws_foreground, R.string.ws_name, R.string.ws_scientific, R.string.ws_description)
    )),
    ShellfishCategory(R.string.abalones, listOf(
        ShellfishSpecies(R.mipmap.na_foreground, R.string.na_name, R.string.na_scientific, R.string.na_description)
    ))
)

@Composable
fun ReferenceGuideDestination(
    modifier: Modifier = Modifier
){
    ReferenceGrid(modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp)
    )
}

@Composable
fun ReferenceCard(
    species: ShellfishSpecies,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val rotationState by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f, label = "rotation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Show thumbnail ONLY when collapsed
                if (!expanded) {
                    Image(
                        painter = painterResource(species.imageRes),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(species.nameRes),
                        // Slightly larger font when expanded
                        style = if (expanded) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(species.scientificNameRes),
                        // Slightly larger font when expanded
                        style = if (expanded) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotationState)
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                // Expanded Photo
                Image(
                    painter = painterResource(species.imageRes),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.height(16.dp))
                // Description
                Text(
                    text = stringResource(species.descriptionRes),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun ReferenceGrid(
    modifier: Modifier = Modifier
)
{
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
    ) {
        categorizedShellfish.forEach { category ->
            item {
                Text(
                    text = stringResource(category.titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(category.species) { species ->
                ReferenceCard(species = species)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ReferenceGridPreview() {
    ProjectNeptuneTheme {
        ReferenceGrid()
    }
}
