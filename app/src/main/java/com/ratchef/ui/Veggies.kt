package com.ratchef.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ratchef.R
import com.ratchef.core.Recipe
import java.util.Locale

/** Small flat vegetable icons used as accents: recipe cards, ingredient rows, shopping items. */
enum class Veggie(@DrawableRes val res: Int, private val words: List<String>) {
    // Order matters: garlic before onion ("Knoblauchzehen"), specific before generic.
    GARLIC(R.drawable.ic_veg_garlic, listOf("garlic", "knoblauch")),
    ONION(R.drawable.ic_veg_onion, listOf("onion", "zwiebel", "shallot", "schalotte", "leek", "lauch", "porree")),
    TOMATO(R.drawable.ic_veg_tomato, listOf("tomat", "paradeiser", "passata", "pomodor")),
    EGGPLANT(R.drawable.ic_veg_eggplant, listOf("eggplant", "aubergine", "melanzan")),
    ZUCCHINI(R.drawable.ic_veg_zucchini, listOf("zucchin", "courgette", "cucumber", "gurke")),
    PEPPER(
        R.drawable.ic_veg_pepper,
        listOf("bell pepper", "red pepper", "green pepper", "yellow pepper", "paprika", "chili", "chilli",
            "jalape", "capsicum", "pfefferoni"),
    ),
    CARROT(R.drawable.ic_veg_carrot, listOf("carrot", "karotte", "möhre", "moehre", "rüebli")),
    HERB(
        R.drawable.ic_veg_herb,
        listOf("basil", "parsley", "petersil", "coriander", "cilantro", "koriander", "dill", "thyme", "thymian",
            "rosemary", "rosmarin", "mint", "minze", "oregano", "spinach", "spinat", "lettuce", "kale",
            "chive", "schnittlauch", "herb", "kräuter", "rucola", "arugula", "sage", "salbei", "salad", "blattsalat"),
    );

    fun matches(name: String): Boolean {
        val n = name.lowercase(Locale.ROOT)
        // "paprika powder" / "Paprikapulver" is a spice, not a vegetable
        if (this == PEPPER && (n.contains("pulver") || n.contains("powder") || n.contains("smoked"))) return false
        return words.any { n.contains(it) }
    }

    companion object {
        fun forIngredient(name: String): Veggie? = entries.firstOrNull { it.matches(name) }

        /** The recipe's most prominent vegetable, or a stable pick from its id. */
        fun forRecipe(r: Recipe): Veggie =
            r.ingredients.firstNotNullOfOrNull { forIngredient(it.name) }
                ?: entries[Math.floorMod(r.id.hashCode(), entries.size)]
    }
}

@Composable
fun VeggieIcon(veggie: Veggie?, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    if (veggie == null) {
        // Keeps rows aligned when there's no matching vegetable.
        Box(modifier.size(size), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    } else {
        Image(painterResource(veggie.res), contentDescription = null, modifier = modifier.size(size))
    }
}

/** Round badge with a vegetable, used on recipe cards. */
@Composable
fun VeggieBadge(veggie: Veggie, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(veggie.res), contentDescription = null, modifier = Modifier.size(size * 0.6f))
    }
}

/** Decorative row of all vegetables, for empty states. */
@Composable
fun VeggieRow(size: Dp = 22.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Veggie.entries.forEach { Image(painterResource(it.res), contentDescription = null, Modifier.size(size)) }
    }
}
