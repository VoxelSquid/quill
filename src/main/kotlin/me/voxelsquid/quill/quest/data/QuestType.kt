package me.voxelsquid.quill.quest.data

enum class QuestType(val promptConfigPath: String) {

    PROFESSION_ITEM_GATHERING("profession-quest"),
    FOOD("food-quest"),
    MUSIC_DISC("music-disc-quest"),
    BOOZE("booze-quest"),
    OMINOUS_BANNER("ominous-banner-quest"),
    SMITHING_TEMPLATE("smithing-template-quest"),
    ENCHANTED_BOOK("enchanted-book-quest"),
    TREASURE_HUNT("treasure-hunt-quest")

}