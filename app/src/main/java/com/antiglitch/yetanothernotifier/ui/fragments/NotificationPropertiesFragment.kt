package com.antiglitch.yetanothernotifier.ui.fragments

//import androidx.compose.material3.*
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.antiglitch.yetanothernotifier.ui.components.TvFriendlyChipsSelect
import com.antiglitch.yetanothernotifier.ui.components.TvFriendlySlider
import com.antiglitch.yetanothernotifier.ui.components.TvFriendlySwitch
import com.antiglitch.yetanothernotifier.data.properties.AspectRatio
import com.antiglitch.yetanothernotifier.data.properties.Gravity
import com.antiglitch.yetanothernotifier.data.repository.NotificationVisualPropertiesRepository

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NotificationPropertiesFragment(
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onBackPressed: () -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = NotificationVisualPropertiesRepository.getInstance(context)
    val dynamicProperties by repository.dynamicPropertiesMap.collectAsState()
    val scrollState = rememberScrollState()

    // Helper to get typed value or default
    fun <T> getPropValue(key: String, default: T): T {
        return (dynamicProperties[key]?.value as? T) ?: default
    }

    // Helper to get typed range or default
    fun <R> getPropRange(key: String, default: R): R {
        return (dynamicProperties[key]?.range as? R) ?: default
    }

    // Helper to get typed step or default
    fun <S> getPropStep(key: String, default: S): S {
        return (dynamicProperties[key]?.step as? S) ?: default
    }
    
    // Helper to get enum values or default
    fun < E : Enum<E>> getEnumValues(key: String, default: Array<E>): Array<E> {
        @Suppress("UNCHECKED_CAST")
        return (dynamicProperties[key]?.enumValues as? Array<E>) ?: default
    }


    // Scroll to top and request initial focus on launch


    // Replace Card with Column
    Column(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            )
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header with back button and title (remove menu button)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    Log.d("NotificationPropertiesFragment", "Back button clicked, calling onBackPressed")
                    onBackPressed()
                },
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )

            }

            Text(
                text = "Notification Properties",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        // Duration Control
        // The Card wrapper is removed. The Column below is now a direct child.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp) // Adjust corner radius as needed
                )
                .padding(12.dp) // Inner padding for the content
        ) { // Added fillMaxWidth, kept padding
            val durationValue = getPropValue("duration", 3000L)
            val durationRange = getPropRange("duration", 1000L..10000L)
            val durationStep = getPropStep("duration", 500L)
            Text(
                text = "Duration: ${durationValue}ms",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant // Explicitly set text color
            )
            TvFriendlySlider(
                modifier = Modifier
                    .focusRequester(focusRequester),
                value = durationValue.toFloat(),
                onValueChange = { repository.updatePropertyByKey("duration", it.toLong()) },
                valueRange = durationRange.start.toFloat()..durationRange.endInclusive.toFloat(),
                stepSize = durationStep.toFloat()
            )
        }
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()

        }
        // Scale Control
        // Replaced Card with a Column having a background modifier
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp) // Adjust corner radius as needed
                )
                .padding(12.dp) // Inner padding for the content
        ) {
            val scaleValue = getPropValue("scale", 0.5f)
            val scaleRange = getPropRange("scale", 0.1f..1.0f)
            val scaleStep = getPropStep("scale", 0.05f)
            Text(
                text = "Scale: ${String.format("%.2f", scaleValue)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant // Explicitly set text color
            )
            TvFriendlySlider(
                value = scaleValue,
                onValueChange = { repository.updatePropertyByKey("scale", it) },
                valueRange = scaleRange,
                stepSize = scaleStep,
                formatValue = { String.format("%.2f", it) }
            )
        }

        // Aspect Ratio Control - Replaced Card with Column
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            val aspectValue = getPropValue("aspect", AspectRatio.WIDE)
            val aspectOptions = getEnumValues("aspect", AspectRatio.values())
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                TvFriendlyChipsSelect(
                    title = "Aspect Ratio",
                    options = aspectOptions.toList(),
                    selectedOption = aspectValue,
                    onOptionSelected = { repository.updatePropertyByKey("aspect", it) },
                    optionLabel = { it.displayName }
                )
            }
        }


        // Gravity Control - Replaced Card with Column
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            val gravityValue = getPropValue("gravity", Gravity.TOP_CENTER)
            val gravityOptions = getEnumValues("gravity", Gravity.values())
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                TvFriendlyChipsSelect(
                    title = "Gravity",
                    options = gravityOptions.toList(),
                    selectedOption = gravityValue,
                    onOptionSelected = {
                        repository.updatePropertyByKey("gravity", it)
                    },
                    optionLabel = { it.displayName }
                )
            }
        }

        // Corner Controls - Replaced Card with Column
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
        ) {
            val roundedCornersValue = getPropValue("roundedCorners", true)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rounded",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TvFriendlySwitch(
                    checked = roundedCornersValue,
                    onCheckedChange = { repository.updatePropertyByKey("roundedCorners", it) }
                )
            }

            if (roundedCornersValue) {
                val cornerRadiusValue = getPropValue("cornerRadius", 12.dp)
                val cornerRadiusRange = getPropRange("cornerRadius", 0.dp..30.dp)
                val cornerRadiusStep = getPropStep("cornerRadius", 2.dp)
                Text(
                    text = "Radius: $cornerRadiusValue",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TvFriendlySlider(
                    value = cornerRadiusValue.value,
                    onValueChange = { repository.updatePropertyByKey("cornerRadius", it.dp) },
                    valueRange = cornerRadiusRange.start.value..cornerRadiusRange.endInclusive.value,
                    stepSize = cornerRadiusStep.value,
                    formatValue = { "${it.toInt()}dp" }
                )
            }
        }

        // Margin control - Replaced Card with Column
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
        ) {
            val marginValue = getPropValue("margin", 16.dp)
            val marginRange = getPropRange("margin", 0.dp..64.dp)
            val marginStep = getPropStep("margin", 1.dp) // Assuming 1dp step if not specified, adjust if model has different
            Text(
                text = "Margin: ${marginValue.value.toInt()}dp",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TvFriendlySlider(
                value = marginValue.value,
                onValueChange = {
                    repository.updatePropertyByKey("margin", it.dp)
                },
                valueRange = marginRange.start.value..marginRange.endInclusive.value,
                stepSize = marginStep.value,
                formatValue = { "${it.toInt()}dp" }
            )
        }

        // Transparency Control
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
        ) {
            val transparencyValue = getPropValue("transparency", 1.0f)
            val transparencyRange = getPropRange("transparency", 0.0f..1.0f)
            val transparencyStep = getPropStep("transparency", 0.05f)
            Text(
                text = "Transparency: ${String.format("%.2f", transparencyValue)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TvFriendlySlider(
                value = transparencyValue,
                onValueChange = { repository.updatePropertyByKey("transparency", it) },
                valueRange = transparencyRange,
                stepSize = transparencyStep,
                formatValue = { String.format("%.2f", it) }
            )
        }
    }
}
