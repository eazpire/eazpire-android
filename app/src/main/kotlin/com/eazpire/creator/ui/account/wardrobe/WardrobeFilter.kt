package com.eazpire.creator.ui.account.wardrobe

/**
 * Product filtering – matches theme/assets/wardrobe.js SLOT_CATEGORY_MAP, GENDER_TAGS, AGE_TAGS
 */
object WardrobeFilter {

    val SLOT_CATEGORY_MAP = mapOf(
        "head" to SlotCategory(
            types = listOf("hat", "cap", "beanie", "headwear", "mütze", "hut", "kopfbedeckung", "head"),
            keywords = listOf("hat", "cap", "beanie", "mütze", "hut", "bandana", "headband", "stirnband", "kappe")
        ),
        "upper_body" to SlotCategory(
            types = listOf("t-shirt", "shirt", "top", "hoodie", "pullover", "sweatshirt", "blouse", "bluse", "polo", "longsleeve", "oberteil", "tshirt"),
            keywords = listOf("t-shirt", "tshirt", "shirt", "top", "hoodie", "pullover", "sweatshirt", "bluse", "polo", "oberteil", "tanktop")
        ),
        "layer" to SlotCategory(
            types = listOf("jacket", "coat", "cardigan", "blazer", "vest", "weste", "jacke", "mantel", "windbreaker", "parka", "bomber"),
            keywords = listOf("jacket", "jacke", "coat", "mantel", "cardigan", "blazer", "weste", "vest", "parka", "windbreaker", "bomber")
        ),
        "pants" to SlotCategory(
            types = listOf("pants", "jeans", "shorts", "hose", "trousers", "leggings", "jogger", "chino", "skirt", "rock", "jogginghose"),
            keywords = listOf("pants", "hose", "jeans", "shorts", "trousers", "leggings", "jogger", "chino", "rock", "skirt", "jogginghose")
        ),
        "feet" to SlotCategory(
            types = listOf("shoes", "sneakers", "boots", "sandals", "schuhe", "stiefel", "sandalen", "footwear", "slippers"),
            keywords = listOf("shoes", "schuhe", "sneaker", "boots", "stiefel", "sandalen", "sandals", "slipper", "loafer", "turnschuh")
        ),
        "socks" to SlotCategory(
            types = listOf("socks", "socken", "stockings", "strümpfe"),
            keywords = listOf("socks", "socken", "strümpfe", "stockings")
        ),
        "accessory_1" to SlotCategory(
            types = listOf("accessory", "accessories", "zubehör", "belt", "gürtel", "bag", "tasche", "sunglasses", "sonnenbrille", "watch", "uhr", "jewelry", "schmuck", "scarf", "schal"),
            keywords = listOf("accessory", "zubehör", "gürtel", "belt", "tasche", "bag", "sonnenbrille", "sunglasses", "uhr", "watch", "schmuck", "jewelry", "schal", "scarf", "kette", "armband")
        ),
        "accessory_2" to SlotCategory(
            types = listOf("accessory", "accessories", "zubehör", "belt", "gürtel", "bag", "tasche", "sunglasses", "sonnenbrille", "watch", "uhr", "jewelry", "schmuck", "scarf", "schal"),
            keywords = listOf("accessory", "zubehör", "gürtel", "belt", "tasche", "bag", "sonnenbrille", "sunglasses", "uhr", "watch", "schmuck", "jewelry", "schal", "scarf", "kette", "armband")
        ),
        "one_piece" to SlotCategory(
            types = listOf("dress", "kleid", "jumpsuit", "romper", "overall", "onesie", "bodysuit", "one-piece", "einteiler", "anzug"),
            keywords = listOf("dress", "kleid", "jumpsuit", "romper", "overall", "onesie", "bodysuit", "einteiler", "anzug", "suit")
        )
    )

    val GENDER_TAGS = mapOf(
        "male" to listOf("men", "man", "herren", "männer", "male", "mann", "jungs", "boys", "boy"),
        "female" to listOf("women", "woman", "damen", "frauen", "female", "frau", "girls", "girl", "mädchen")
    )

    val AGE_TAGS = mapOf(
        "adult" to listOf("adult", "erwachsene", "erwachsen"),
        "child" to listOf("kids", "kinder", "child", "children", "youth", "jugend", "teen", "teenager"),
        "baby" to listOf("baby", "infant", "kleinkind", "newborn", "neugeboren", "säugling")
    )

    private fun wordMatch(text: String, keyword: String): Boolean {
        val escaped = Regex.escape(keyword)
        val pattern = Regex("""(?:^|[\s,;/|\-_])$escaped(?:$|[\s,;/|\-_])""", RegexOption.IGNORE_CASE)
        return pattern.containsMatchIn(text)
    }

    fun matchesCategory(productType: String, title: String, tags: List<String>, slotKey: String): Boolean {
        val cat = SLOT_CATEGORY_MAP[slotKey] ?: return false
        val typeLower = productType.lowercase()
        val titleLower = title.lowercase()
        for (t in cat.types) {
            if (typeLower == t || wordMatch(typeLower, t)) return true
        }
        for (k in cat.keywords) {
            if (wordMatch(titleLower, k)) return true
        }
        for (k in cat.keywords) {
            for (tag in tags) {
                if (tag == k || wordMatch(tag, k)) return true
            }
        }
        return false
    }

    fun matchesGender(tags: List<String>, gender: String): Boolean {
        if (tags.isEmpty()) return true
        val allGenderTags = (GENDER_TAGS["male"] ?: emptyList()) + (GENDER_TAGS["female"] ?: emptyList())
        val hasGenderTag = tags.any { tag ->
            allGenderTags.any { g -> tag.contains(g, ignoreCase = true) }
        }
        if (!hasGenderTag) return true
        if (tags.any { it.contains("unisex", ignoreCase = true) }) return true
        val genderTags = GENDER_TAGS[gender] ?: return true
        return tags.any { tag ->
            genderTags.any { g -> tag.contains(g, ignoreCase = true) }
        }
    }

    fun matchesAge(tags: List<String>, ageGroup: String): Boolean {
        if (tags.isEmpty()) return true
        val allAgeTags = (AGE_TAGS["adult"] ?: emptyList()) + (AGE_TAGS["child"] ?: emptyList()) + (AGE_TAGS["baby"] ?: emptyList())
        val hasAgeTag = tags.any { tag ->
            allAgeTags.any { a -> tag.contains(a, ignoreCase = true) }
        }
        if (!hasAgeTag) return true
        val ageTags = AGE_TAGS[ageGroup] ?: return true
        return tags.any { tag ->
            ageTags.any { a -> tag.contains(a, ignoreCase = true) }
        }
    }
}

data class SlotCategory(val types: List<String>, val keywords: List<String>)
