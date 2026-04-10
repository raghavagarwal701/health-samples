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
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.material.Slider
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
import kotlin.math.roundToInt

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
    recentSearches: List<String>,
    isLoading: Boolean,
    errorMessage: String?,
    suppressAutoSearch: Boolean = false,
    onAutocomplete: (String) -> Unit,
    onSelectSuggestion: (String) -> Unit,
    onSelectRecentSearch: (String) -> Unit,
    onDeleteRecentSearch: (String) -> Unit,
    onUseAiForMeal: (String) -> Unit,
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

            if (recentSearches.isNotEmpty() && trimmedQuery.isBlank()) {
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Recent searches",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                    recentSearches.forEach { recentQuery ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            elevation = 1.dp,
                            onClick = { onSelectRecentSearch(recentQuery) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = recentQuery,
                                    style = MaterialTheme.typography.body2,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { onDeleteRecentSearch(recentQuery) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Delete recent search",
                                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            when {
                trimmedQuery.isBlank() || trimmedQuery.length < 2 -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Type at least 2 letters", style = MaterialTheme.typography.body1)
                    }
                }
                !errorMessage.isNullOrBlank() && suggestions.isEmpty() -> {
                    Column(modifier = Modifier.weight(1f)) {
                        FatSecretAiMealOptionCard(
                            query = searchQuery,
                            onClick = onUseAiForMeal
                        )
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(errorMessage, style = MaterialTheme.typography.body1)
                        }
                    }
                }
                suggestions.isEmpty() -> {
                    Column(modifier = Modifier.weight(1f)) {
                        FatSecretAiMealOptionCard(
                            query = searchQuery,
                            onClick = onUseAiForMeal
                        )
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
                }
                else -> {
                    Column(modifier = Modifier.weight(1f)) {
                        FatSecretAiMealOptionCard(
                            query = searchQuery,
                            onClick = onUseAiForMeal
                        )
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

@Composable
@OptIn(ExperimentalMaterialApi::class)
private fun FatSecretAiMealOptionCard(
    query: String,
    onClick: (String) -> Unit,
) {
    val normalizedQuery = query.trim()
    if (normalizedQuery.length < 2) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colors.primary.copy(alpha = 0.35f)),
        onClick = { onClick(query) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Can't find your food? Let AI add it for you.",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = "Analyze with AI",
                tint = MaterialTheme.colors.primary
            )
        }
    }
}

@Composable
fun MealFatSecretAiMealInput(
    initialQuery: String,
    isLoading: Boolean,
    errorMessage: String?,
    onAnalyze: (String) -> Unit,
    onBack: (String) -> Unit,
) {
    var mealText by remember(initialQuery) { mutableStateOf(initialQuery) }

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
                .padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onBack(mealText) }) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "Back")
                }
                Text(
                    text = "Add meal with AI",
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(48.dp))
            }

            Text(
                text = "Describe your meal. We pre-filled what you typed in search, and you can edit it.",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.72f)
            )

            OutlinedTextField(
                value = mealText,
                onValueChange = { mealText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                label = { Text("Meal description") },
                placeholder = { Text("e.g. rajma chawal with salad") },
                maxLines = 8,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
            )

                if (isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Analyzing your meal...",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.72f)
                        )
                    }
                }

            if (!errorMessage.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = 1.dp
                ) {
                    Text(
                        text = errorMessage,
                        modifier = Modifier.padding(12.dp),
                        color = Color(0xFFD32F2F),
                        style = MaterialTheme.typography.body2
                    )
                }
            }
        }

        Button(
            onClick = { onAnalyze(mealText) },
            enabled = mealText.isNotBlank() && !isLoading,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Spacer(Modifier.width(8.dp))
                Text("Analyzing...")
            } else {
                Icon(Icons.Outlined.Bolt, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Analyze meal")
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
    var servingMenuExpanded by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val servings = food.servings
    val effectiveSelectedServingId = selectedServingId.takeIf { it in servings.indices } ?: servings.indices.firstOrNull()
    val selectedServing = effectiveSelectedServingId?.let { servings.getOrNull(it) }
    val quantityValue = quantity.coerceIn(0.1, 3.0)
    val dishTitle = if (!food.brandName.isNullOrBlank()) {
        "${food.foodName} (${food.brandName})"
    } else {
        food.foodName
    }
    val caloriesText = selectedServing?.calories
        ?.times(quantityValue)
        ?.roundToInt()
        ?.let { "$it kcal" }
        ?: "-- kcal"
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
                    Text(dishTitle, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                    Text(
                        text = caloriesText,
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
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
            } else if (servings.size == 1) {
                Text(
                    text = servings.first().servingDescription,
                    style = MaterialTheme.typography.body2,
                    fontWeight = FontWeight.SemiBold
                )
                val singleServingMeta = buildServingMeta(servings.first())
                if (singleServingMeta.isNotEmpty()) {
                    Text(
                        text = singleServingMeta,
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.65f)
                    )
                }
            } else {
                Box {
                    OutlinedButton(
                        onClick = { servingMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = selectedServing?.servingDescription ?: "Select serving",
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = "Open serving options"
                        )
                    }

                    DropdownMenu(
                        expanded = servingMenuExpanded,
                        onDismissRequest = { servingMenuExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.95f)
                    ) {
                        servings.forEachIndexed { index, serving ->
                            DropdownMenuItem(
                                onClick = {
                                    servingMenuExpanded = false
                                    onServingChange(index, quantityValue)
                                }
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = serving.servingDescription,
                                        fontWeight = if (index == effectiveSelectedServingId) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val meta = buildServingMeta(serving)
                                    if (meta.isNotEmpty()) {
                                        Text(
                                            text = meta,
                                            style = MaterialTheme.typography.caption,
                                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.65f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                val selectedServingMeta = selectedServing?.let(::buildServingMeta).orEmpty()
                if (selectedServingMeta.isNotEmpty()) {
                    Text(
                        text = selectedServingMeta,
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.65f)
                    )
                }
            }

            Text("Quantity", style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.SemiBold)
            Text(
                text = formatNumber(quantityValue) + " serving",
                style = MaterialTheme.typography.h5,
                fontWeight = FontWeight.SemiBold
            )

            Slider(
                value = quantityValue.toFloat(),
                onValueChange = { raw ->
                    val snapped = (raw * 10f).roundToInt() / 10.0
                    val servingIndex = effectiveSelectedServingId ?: return@Slider
                    onServingChange(servingIndex, snapped.coerceIn(0.1, 3.0))
                },
                modifier = Modifier.fillMaxWidth(),
                valueRange = 0.1f..3.0f,
                steps = 28,
                enabled = effectiveSelectedServingId != null && !isLoading
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0.1", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f))
                Text("3.0", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f))
            }

            Spacer(Modifier.height(8.dp))

            if (selectedServing != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Nutrition facts (${formatNumber(quantityValue)} x ${selectedServing.servingDescription})",
                            fontWeight = FontWeight.SemiBold
                        )
                        NutrientRows(selectedServing, quantityValue)
                    }
                }
            }
        }

        Button(
            onClick = onConfirm,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            enabled = selectedServing != null && quantityValue >= 0.1 && !isLoading,
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
        NutrientDisplay("calories", "Energy", "kcal", 0),
        NutrientDisplay("protein", "Protein", "g"),
        NutrientDisplay("carbohydrate", "Carbs", "g"),
        NutrientDisplay("fat", "Fat", "g"),
        NutrientDisplay("fiber", "Fiber", "g"),
        NutrientDisplay("sugar", "Sugar", "g"),
        NutrientDisplay("sodium", "Sodium", "mg"),
        NutrientDisplay("cholesterol", "Cholesterol", "mg"),
        NutrientDisplay("saturated_fat", "Saturated fat", "g"),
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        displayItems.forEach { nutrient ->
            val value = totals[nutrient.key]
            if (value != null) {
                val nutrientColor = getNutrientStatusColor(nutrient.key, value)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colors.surface, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(nutrient.label, fontWeight = FontWeight.Medium)
                    Text(
                        text = "${formatNumber(value, nutrient.digits)} ${nutrient.unit}",
                        fontWeight = FontWeight.SemiBold,
                        color = nutrientColor
                    )
                }
            }
        }

        NutrientLegend()
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
private fun NutrientRows(
    serving: com.example.healthconnectsample.data.api.FatSecretServing,
    quantity: Double = 1.0,
) {
    val rows = listOfNotNull(
        serving.calories?.let { NutrientDisplay("calories", "Calories", "kcal", 0, it * quantity) },
        serving.protein?.let { NutrientDisplay("protein", "Protein", "g", value = it * quantity) },
        serving.carbohydrate?.let { NutrientDisplay("carbohydrate", "Carbs", "g", value = it * quantity) },
        serving.fat?.let { NutrientDisplay("fat", "Fat", "g", value = it * quantity) },
        serving.saturatedFat?.let { NutrientDisplay("saturated_fat", "Saturated fat", "g", value = it * quantity) },
        serving.polyunsaturatedFat?.let { NutrientDisplay("polyunsaturated_fat", "Polyunsaturated fat", "g", value = it * quantity) },
        serving.monounsaturatedFat?.let { NutrientDisplay("monounsaturated_fat", "Monounsaturated fat", "g", value = it * quantity) },
        serving.fiber?.let { NutrientDisplay("fiber", "Fiber", "g", value = it * quantity) },
        serving.sugar?.let { NutrientDisplay("sugar", "Sugar", "g", value = it * quantity) },
        serving.transFat?.let { NutrientDisplay("trans_fat", "Trans fat", "g", value = it * quantity) },
        serving.addedSugars?.let { NutrientDisplay("added_sugars", "Added sugars", "g", value = it * quantity) },
        serving.sodium?.let { NutrientDisplay("sodium", "Sodium", "mg", value = it * quantity) },
        serving.potassium?.let { NutrientDisplay("potassium", "Potassium", "mg", value = it * quantity) },
        serving.cholesterol?.let { NutrientDisplay("cholesterol", "Cholesterol", "mg", value = it * quantity) },
        serving.calcium?.let { NutrientDisplay("calcium", "Calcium", "mg", value = it * quantity) },
        serving.iron?.let { NutrientDisplay("iron", "Iron", "mg", value = it * quantity) },
        serving.vitaminA?.let { NutrientDisplay("vitamin_a", "Vitamin A", "mcg", value = it * quantity) },
        serving.vitaminC?.let { NutrientDisplay("vitamin_c", "Vitamin C", "mg", value = it * quantity) },
        serving.vitaminD?.let { NutrientDisplay("vitamin_d", "Vitamin D", "mcg", value = it * quantity) },
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
        rows.forEach { nutrient ->
            val nutrientColor = getNutrientStatusColor(nutrient.key, nutrient.value)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colors.surface, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(nutrient.label, fontWeight = FontWeight.Medium)
                Text(
                    text = "${formatNumber(nutrient.value, nutrient.digits)} ${nutrient.unit}",
                    fontWeight = FontWeight.SemiBold,
                    color = nutrientColor
                )
            }
        }

        NutrientLegend()
    }
}

private data class NutrientDisplay(
    val key: String,
    val label: String,
    val unit: String,
    val digits: Int = 1,
    val value: Double = 0.0,
)

private fun getNutrientStatusColor(key: String, value: Double): Color {
    val safeValue = value.coerceAtLeast(0.0)
    val green = Color(0xFF2E7D32)
    val yellow = Color(0xFFF9A825)
    val red = Color(0xFFC62828)

    return when (key) {
        "sugar" -> when {
            safeValue < 5.0 -> green
            safeValue <= 12.5 -> yellow
            else -> red
        }

        "sodium" -> when {
            safeValue < 400.0 -> green
            safeValue <= 1000.0 -> yellow
            else -> red
        }

        "fat" -> when {
            safeValue < 14.0 -> green
            safeValue <= 35.0 -> yellow
            else -> red
        }

        "saturated_fat" -> when {
            safeValue < 4.0 -> green
            safeValue <= 10.0 -> yellow
            else -> red
        }

        "fiber" -> when {
            safeValue < 5.6 -> red
            safeValue <= 14.0 -> yellow
            else -> green
        }

        "carbohydrate" -> when {
            safeValue < 55.0 -> green
            safeValue <= 137.0 -> yellow
            else -> red
        }

        "protein" -> when {
            safeValue < 10.0 -> red
            safeValue <= 25.0 -> yellow
            else -> green
        }

        "cholesterol" -> when {
            safeValue < 60.0 -> green
            safeValue <= 150.0 -> yellow
            else -> red
        }

        else -> Color(0xFF1976D2)
    }
}

@Composable
private fun NutrientLegend() {
    Spacer(Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NutrientLegendItem(label = "Good", color = Color(0xFF2E7D32), modifier = Modifier.weight(1f))
        NutrientLegendItem(label = "Okay", color = Color(0xFFF9A825), modifier = Modifier.weight(1f))
        NutrientLegendItem(label = "Excess", color = Color(0xFFC62828), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun NutrientLegendItem(label: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.75f)
        )
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
