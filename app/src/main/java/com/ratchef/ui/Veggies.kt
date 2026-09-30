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
import com.ratchef.core.Canon
import com.ratchef.core.Recipe

/** Small flat vegetable icons used as accents: recipe cards, ingredient rows, shopping items. */
enum class Veggie(@DrawableRes val res: Int) {
    TOMATO(R.drawable.ic_veg_tomato),
    POTATO(R.drawable.ic_veg_potato),
    ONION(R.drawable.ic_veg_onion),
    GARLIC(R.drawable.ic_veg_garlic),
    CARROT(R.drawable.ic_veg_carrot),
    PEPPER(R.drawable.ic_veg_pepper),
    ZUCCHINI(R.drawable.ic_veg_zucchini),
    EGGPLANT(R.drawable.ic_veg_eggplant),
    MUSHROOM(R.drawable.ic_veg_mushroom),
    LEMON(R.drawable.ic_veg_lemon),
    HERB(R.drawable.ic_veg_herb);

    companion object {
        private fun named(name: String?): Veggie? =
            name?.takeIf { it.isNotEmpty() }?.let { n -> entries.firstOrNull { it.name == n } }

        /** Uses the ingredient dictionary (EN + DE), so "Erdäpfel" and "potatoes" both get the potato. */
        fun forIngredient(name: String): Veggie? = named(Canon.match(name, "")?.entry?.veggie)

        /**
         * The recipe's vegetable: first one named in the title ("Kartoffelsalat" -> potato), else the
         * first vegetable among the ingredients, else a stable pick from its id.
         */
        fun forRecipe(r: Recipe): Veggie =
            named(Canon.veggieInText(r.title))
                ?: r.ingredients.firstNotNullOfOrNull { forIngredient(it.name) }
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
fun VeggieRow(size: Dp = 20.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Veggie.entries.forEach { Image(painterResource(it.res), contentDescription = null, Modifier.size(size)) }
    }
}
