package net.kravos.weather.data

/** Serbian Cyrillic -> Latin, so place names follow the app's Latin UI. */
object Translit {
    private val map = mapOf(
        'А' to "A", 'Б' to "B", 'В' to "V", 'Г' to "G", 'Д' to "D", 'Ђ' to "Đ", 'Е' to "E", 'Ж' to "Ž",
        'З' to "Z", 'И' to "I", 'Ј' to "J", 'К' to "K", 'Л' to "L", 'Љ' to "Lj", 'М' to "M", 'Н' to "N",
        'Њ' to "Nj", 'О' to "O", 'П' to "P", 'Р' to "R", 'С' to "S", 'Т' to "T", 'Ћ' to "Ć", 'У' to "U",
        'Ф' to "F", 'Х' to "H", 'Ц' to "C", 'Ч' to "Č", 'Џ' to "Dž", 'Ш' to "Š",
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'ђ' to "đ", 'е' to "e", 'ж' to "ž",
        'з' to "z", 'и' to "i", 'ј' to "j", 'к' to "k", 'л' to "l", 'љ' to "lj", 'м' to "m", 'н' to "n",
        'њ' to "nj", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'ћ' to "ć", 'у' to "u",
        'ф' to "f", 'х' to "h", 'ц' to "c", 'ч' to "č", 'џ' to "dž", 'ш' to "š",
        // a few Russian/Macedonian/Bulgarian letters seen in place names
        'Й' to "J", 'й' to "j", 'Ы' to "Y", 'ы' to "y", 'Э' to "E", 'э' to "e", 'Ю' to "Ju", 'ю' to "ju",
        'Я' to "Ja", 'я' to "ja", 'Щ' to "Šč", 'щ' to "šč", 'Ъ' to "", 'ъ' to "", 'Ь' to "", 'ь' to "",
        'Ѓ' to "Ǵ", 'ѓ' to "ǵ", 'Ќ' to "Ḱ", 'ќ' to "ḱ", 'Ѕ' to "Dz", 'ѕ' to "dz", 'Ё' to "Jo", 'ё' to "jo",
    )

    fun toLatin(s: String): String {
        if (s.none { it in 'Ѐ'..'ӿ' }) return s
        val sb = StringBuilder(s.length + 4)
        for (ch in s) sb.append(map[ch] ?: ch.toString())
        return sb.toString()
    }
}
