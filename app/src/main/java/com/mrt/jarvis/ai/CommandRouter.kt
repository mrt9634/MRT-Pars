package com.mrt.jarvis.ai

enum class Category { LOCAL_VEHICLE, MEDIA, SYSTEM, NAVIGATION, VOICE, AI_GENERAL }

enum class Intent {
    SPEED, RPM, ENGINE_TEMP, BATTERY, FUEL, CAN_STATUS,
    MEDIA_PLAY, MEDIA_PAUSE, MEDIA_NEXT, MEDIA_PREV, VOL_UP, VOL_DOWN, MUTE,
    HOME, SETTINGS, DIAGNOSTICS, NAVIGATE, STOP_VOICE, GENERAL
}

data class Route(val category: Category, val intent: Intent, val text: String)

/** Local keyword router for Persian. Vehicle/media/system never go to the cloud. */
object CommandRouter {
    private fun norm(s: String) = s.replace('ي', 'ی').replace('ك', 'ک').replace("\u200c", " ").trim()

    private val rules: List<Triple<Category, Intent, List<String>>> = listOf(
        Triple(Category.LOCAL_VEHICLE, Intent.SPEED, listOf("سرعت")),
        Triple(Category.LOCAL_VEHICLE, Intent.RPM, listOf("دور موتور", "دور")),
        Triple(Category.LOCAL_VEHICLE, Intent.ENGINE_TEMP, listOf("دمای موتور", "دمای آب", "حرارت موتور")),
        Triple(Category.LOCAL_VEHICLE, Intent.BATTERY, listOf("باتری", "ولتاژ")),
        Triple(Category.LOCAL_VEHICLE, Intent.FUEL, listOf("سوخت", "بنزین")),
        Triple(Category.LOCAL_VEHICLE, Intent.CAN_STATUS, listOf("can", "کن باس", "کان")),
        Triple(Category.MEDIA, Intent.MEDIA_NEXT, listOf("آهنگ بعدی", "بعدی")),
        Triple(Category.MEDIA, Intent.MEDIA_PREV, listOf("آهنگ قبلی", "قبلی")),
        Triple(Category.MEDIA, Intent.VOL_DOWN, listOf("کم کن", "آرام", "یواش")),
        Triple(Category.MEDIA, Intent.VOL_UP, listOf("زیاد کن", "بلند کن")),
        Triple(Category.MEDIA, Intent.MUTE, listOf("بی صدا", "قطع صدا")),
        Triple(Category.MEDIA, Intent.MEDIA_PAUSE, listOf("متوقف", "استپ", "مکث")),
        Triple(Category.MEDIA, Intent.MEDIA_PLAY, listOf("پخش", "موزیک", "آهنگ")),
        Triple(Category.SYSTEM, Intent.HOME, listOf("صفحه اصلی", "داشبورد")),
        Triple(Category.SYSTEM, Intent.SETTINGS, listOf("تنظیمات")),
        Triple(Category.SYSTEM, Intent.DIAGNOSTICS, listOf("عیب یابی")),
        Triple(Category.NAVIGATION, Intent.NAVIGATE, listOf("مسیریابی", "برو به", "نقشه")),
        Triple(Category.VOICE, Intent.STOP_VOICE, listOf("ساکت", "بسه")),
    )

    fun route(raw: String): Route {
        val t = norm(raw).lowercase()
        for ((cat, intent, keys) in rules) if (keys.any { t.contains(it) }) return Route(cat, intent, raw)
        return Route(Category.AI_GENERAL, Intent.GENERAL, raw)
    }
}
