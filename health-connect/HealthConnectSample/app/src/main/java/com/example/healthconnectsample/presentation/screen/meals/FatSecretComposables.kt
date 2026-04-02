package com.example.healthconnectsample.presentation.screen.meals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import java.util.Locale

// FatSecret UI composables used by the meals screen.

@Composable
fun MealFatSecretSearchInput(
    onSearch: (String) -> Unit,
    onClose: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Search FatSecret",
                    style = MaterialTheme.typography.h5,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close")
                }
            }

            Text(
                text = "Enter a food name to search",
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Food name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = {
                    Icon(Icons.Outlined.Restaurant, contentDescription = null)
                }
            )

            Button(
                onClick = { if (searchQuery.isNotBlank()) onSearch(searchQuery) },
                modifier = Modifier.fillMaxWidth(),
                enabled = searchQuery.isNotBlank()
            ) {
                Icon(Icons.Outlined.Bolt, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Search")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FatSecret Autocomplete Results Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
@OptIn(ExperimentalMaterialApi::class)
fun MealFatSecretAutocompleteResults(
    query: String,
    suggestions: List<String>,
    isLoading: Boolean,
    errorMessage: String?,
    suppressAutoSearch: Boolean = false,
    onAutocomplete: (String) -> Unit,
    onSelectSuggestion: (String) -> Unit,
    onClose: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf(query) }
    var skipNextAutocomplete by remember(query, suppressAutoSearch) { mutableStateOf(suppressAutoSearch) }
    val trimmedQuery = searchQuery.trim()

    LaunchedEffect(query) {
        if (query != searchQuery) searchQuery = query
        skipNextAutocomplete = suppressAutoSearch
    }

    LaunchedEffect(searchQuery) {
        if (skipNextAutocomplete) {
            skipNextAutocomplete = false
            return@LaunchedEffect
        }

        val normalizedQuery = searchQuery.trim()
        if (normalizedQuery.length < 2) {
            onAutocomplete(normalizedQuery)
            return@LaunchedEffect
        }

        delay(500)
        if (normalizedQuery == searchQuery.trim()) {
            onAutocomplete(normalizedQuery)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "Back")
                }
                Column(Modifier.weight(1f)) {
                    Text("Search FatSecret", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                    if (trimmedQuery.isBlank()) {
                        Text("Type at least 2 letters", style = MaterialTheme.typography.caption)
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                label = { Text("Food name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(12.dp))

            when {
                trimmedQuery.isBlank() || trimmedQuery.length < 2 -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Type at least 2 letters", style = MaterialTheme.typography.body1)
                    }
                }
                !errorMessage.isNullOrBlank() && suggestions.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(errorMessage, style = MaterialTheme.typography.body1)
                    }
                }
                suggestions.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(Modifier.height(12.dp))
                            }
                            Text("No suggestions yet", style = MaterialTheme.typography.body1)
                        }
                    }
                }
                else -> {
                    Column(modifier = Modifier.weight(1f)) {
                        if (isLoading) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(
                                    text = "Updating suggestions...",
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            itemsIndexed(suggestions) { _, suggestion ->
                                Card(
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = 2.dp,
                                    onClick = { onSelectSuggestion(suggestion) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = suggestion,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 16.sp
                                        )
                                        Icon(
                                            imageVector = Icons.Outlined.ChevronRight,
                                            contentDescription = "Open suggestion",
                                            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FatSecret Search Results Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
@OptIn(ExperimentalMaterialApi::class)
fun MealFatSecretSearchResults(
    query: String,
    foods: List<com.example.healthconnectsample.data.api.FatSecretFood>,
    isLoading: Boolean,
    errorMessage: String?,
    onSelectFood: (com.example.healthconnectsample.data.api.FatSecretFood) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val trimmedQuery = query.trim()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "Back")
                }
                Column(Modifier.weight(1f)) {
                    Text("Search results for \"$trimmedQuery\"", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (isLoading) "Searching..." else "${foods.size} found",
                        style = MaterialTheme.typography.caption
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            when {
                trimmedQuery.isBlank() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Go back to search again", style = MaterialTheme.typography.body1)
                    }
                }
                trimmedQuery.length < 2 -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Go back to search again", style = MaterialTheme.typography.body1)
                    }
                }
                !errorMessage.isNullOrBlank() && foods.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(errorMessage, style = MaterialTheme.typography.body1)
                    }
                }
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Loading results...", style = MaterialTheme.typography.body1)
                        }
                    }
                }
                foods.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.Restaurant,
                                contentDescription = null,
                                tint = MaterialTheme.colors.surface,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text("No results found", style = MaterialTheme.typography.body1)
                        }
                    }
                }
                else -> {
                    Column(modifier = Modifier.weight(1f)) {
                        if (isLoading) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(
                                    text = "Updating...",
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            itemsIndexed(foods) { _, food ->
                                Card(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = 2.dp,
                                    onClick = { onSelectFood(food) }
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = food.foodName,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 16.sp
                                        )
                                        if (!food.brandName.isNullOrEmpty()) {
                                            Text(
                                                text = food.brandName,
                                                style = MaterialTheme.typography.caption,
                                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                        val servingSummary = formatFatSecretServingSummary(food.servings)
                                        if (servingSummary.isNotBlank()) {
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                text = servingSummary,
                                                style = MaterialTheme.typography.caption,
                                                fontSize = 12.sp,
                                                color = Color(0xFF4CAF50)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FatSecret Food Detail & Serving Selection
// ─────────────────────────────────────────────────────────────────────────────

@Composable
@OptIn(ExperimentalMaterialApi::class)
fun MealFatSecretFoodDetail(
    food: com.example.healthconnectsample.data.api.FatSecretFood,
    selectedServingId: Int,
    quantity: Double,
    isLoading: Boolean,
    errorMessage: String?,
    onServingChange: (Int, Double) -> Unit,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    var currentQuantity by remember { mutableStateOf(quantity.toString()) }
    val uriHandler = LocalUriHandler.current
    val servings = food.servings
    val effectiveSelectedServingId = selectedServingId.takeIf { it in servings.indices } ?: servings.indices.firstOrNull()
    val selectedServing = effectiveSelectedServingId?.let { servings.getOrNull(it) }
    val imageUrl = food.foodImages?.foodImage?.firstOrNull { !it.imageUrl.isNullOrBlank() }?.imageUrl
    val allergens = food.allergens?.allergen.orEmpty().filter { !it.name.isNullOrBlank() }
    val preferences = food.preferences?.preference.orEmpty().filter { !it.name.isNullOrBlank() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "Back")
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(food.foodName, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                }
            }

            if (isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Loading food details...",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = 2.dp
                ) {
                    Text(
                        text = errorMessage,
                        modifier = Modifier.padding(12.dp),
                        color = Color(0xFFD32F2F)
                    )
                }
            }

            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = food.foodName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            }

            if (!food.brandName.isNullOrBlank()) {
                Text(
                    text = "Brand: ${food.brandName}",
                    style = MaterialTheme.typography.body2
                )
            }

            if (!food.foodUrl.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Source: ",
                        style = MaterialTheme.typography.body2
                    )
                    Text(
                        text = food.foodUrl,
                        modifier = Modifier.clickable { uriHandler.openUri(food.foodUrl) },
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (preferences.isNotEmpty() || allergens.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (preferences.isNotEmpty()) {
                            Text("Preferences", fontWeight = FontWeight.SemiBold)
                            preferences.forEach { preference ->
                                Text(
                                    text = formatAttributeValue(preference.name.orEmpty(), preference.value),
                                    style = MaterialTheme.typography.body2
                                )
                            }
                        }
                        if (allergens.isNotEmpty()) {
                            Text("Allergens", fontWeight = FontWeight.SemiBold)
                            allergens.forEach { allergen ->
                                Text(
                                    text = formatAttributeValue(allergen.name.orEmpty(), allergen.value),
                                    style = MaterialTheme.typography.body2
                                )
                            }
                        }
                    }
                }
            }

            // Serving size selector
            Text("Select serving size:", style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.SemiBold)
            if (servings.isEmpty()) {
                Text(
                    text = "No serving details available",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            } else {
                servings.forEachIndexed { index, serving ->
                    val isSelected = index == effectiveSelectedServingId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                        backgroundColor = if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.10f) else MaterialTheme.colors.surface,
                        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colors.primary) else null,
                        elevation = if (isSelected) 4.dp else 1.dp,
                        onClick = { onServingChange(index, quantity) }
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = serving.servingDescription,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            if (isSelected) {
                                Text(
                                    text = "Selected",
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            val servingMeta = buildServingMeta(serving)
                            if (servingMeta.isNotEmpty()) {
                                Text(
                                    text = servingMeta,
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.65f)
                                )
                            }
                        }
                    }
                }
            }

            if (selectedServing != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Nutrition facts (${selectedServing.servingDescription})",
                            fontWeight = FontWeight.SemiBold
                        )
                        NutrientRows(selectedServing)
                    }
                }
            }

            // Quantity
            Text("Quantity:", style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = currentQuantity,
                onValueChange = {
                    currentQuantity = it
                    it.toDoubleOrNull()?.let { qty ->
                        onServingChange(selectedServingId, qty)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Number of servings") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                shape = RoundedCornerShape(8.dp)
            )
        }

        Button(
            onClick = onConfirm,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            enabled = selectedServing != null && currentQuantity.toDoubleOrNull() != null && !isLoading,
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add Meal")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FatSecret Meal Preview Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MealFatSecretMealPreview(
    foodName: String,
    servingDescription: String,
    quantity: Double,
    totals: Map<String, Double>?,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Nutrition Summary", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close")
                }
            }

            // Meal info
            Card(modifier = Modifier.fillMaxWidth(), elevation = 2.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(foodName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("$servingDescription × $quantity", style = MaterialTheme.typography.caption)
                }
            }

            // Nutrition grid
            Text("Calculated Nutrients:", fontWeight = FontWeight.SemiBold)
            if (totals != null) {
                GridNutrients(totals)
            }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Back")
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add to Log")
                }
            }
        }
    }
}

@Composable
private fun GridNutrients(totals: Map<String, Double>) {
    val displayItems = listOf(
        ("calories" to "Energy"),
        ("protein" to "Protein"),
        ("carbohydrate" to "Carbs"),
        ("fat" to "Fat"),
        ("fiber" to "Fiber"),
        ("sugar" to "Sugar"),
        ("sodium" to "Sodium"),
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        displayItems.forEach { (key, label) ->
            val value = totals[key]
            if (value != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colors.surface, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, fontWeight = FontWeight.Medium)
                    Text(
                        text = "${value.toInt()} g",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colors.primary
                    )
                }
            }
        }
    }
}

private fun formatFatSecretServingSummary(
    servings: List<com.example.healthconnectsample.data.api.FatSecretServing>,
): String {
    if (servings.isEmpty()) return ""
    val descriptions = servings.map { it.servingDescription.trim() }.filter { it.isNotEmpty() }
    if (descriptions.isEmpty()) return ""
    val label = if (descriptions.size == 1) "serving" else "servings"
    return "${descriptions.size} $label: ${descriptions.joinToString(" | ")}"
}

private fun buildServingMeta(serving: com.example.healthconnectsample.data.api.FatSecretServing): String {
    val meta = mutableListOf<String>()
    val amount = serving.metricServingAmount
    val unit = serving.metricServingUnit
    if (amount != null && !unit.isNullOrBlank()) {
        meta.add("${formatNumber(amount)} $unit")
    }
    if (serving.numberOfUnits != null) {
        meta.add("${formatNumber(serving.numberOfUnits)} unit(s)")
    }
    if (!serving.measurementDescription.isNullOrBlank()) {
        meta.add(serving.measurementDescription)
    }
    return meta.joinToString(" • ")
}

@Composable
private fun NutrientRows(serving: com.example.healthconnectsample.data.api.FatSecretServing) {
    val rows = listOfNotNull(
        serving.calories?.let { "Calories" to formatNumber(it, 0) + " kcal" },
        serving.protein?.let { "Protein" to formatNumber(it) + " g" },
        serving.carbohydrate?.let { "Carbs" to formatNumber(it) + " g" },
        serving.fat?.let { "Fat" to formatNumber(it) + " g" },
        serving.saturatedFat?.let { "Saturated fat" to formatNumber(it) + " g" },
        serving.polyunsaturatedFat?.let { "Polyunsaturated fat" to formatNumber(it) + " g" },
        serving.monounsaturatedFat?.let { "Monounsaturated fat" to formatNumber(it) + " g" },
        serving.fiber?.let { "Fiber" to formatNumber(it) + " g" },
        serving.sugar?.let { "Sugar" to formatNumber(it) + " g" },
        serving.transFat?.let { "Trans fat" to formatNumber(it) + " g" },
        serving.addedSugars?.let { "Added sugars" to formatNumber(it) + " g" },
        serving.sodium?.let { "Sodium" to formatNumber(it) + " mg" },
        serving.potassium?.let { "Potassium" to formatNumber(it) + " mg" },
        serving.cholesterol?.let { "Cholesterol" to formatNumber(it) + " mg" },
        serving.calcium?.let { "Calcium" to formatNumber(it) + " mg" },
        serving.iron?.let { "Iron" to formatNumber(it) + " mg" },
        serving.vitaminA?.let { "Vitamin A" to formatNumber(it) + " mcg" },
        serving.vitaminC?.let { "Vitamin C" to formatNumber(it) + " mg" },
        serving.vitaminD?.let { "Vitamin D" to formatNumber(it) + " mcg" },
    )

    if (rows.isEmpty()) {
        Text(
            text = "No nutrition data available for this serving",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colors.surface, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, fontWeight = FontWeight.Medium)
                Text(value, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.primary)
            }
        }
    }
}

private fun formatAttributeValue(name: String, value: Int?): String {
    val status = when (value) {
        1 -> "Contains"
        0 -> "Does not contain"
        -1, null -> "Unknown"
        else -> "Unknown"
    }
    return "$name: $status"
}

private fun formatNumber(value: Double, digits: Int = 1): String {
    return if (digits == 0) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.${digits}f", value).trimEnd('0').trimEnd('.')
    }
}
