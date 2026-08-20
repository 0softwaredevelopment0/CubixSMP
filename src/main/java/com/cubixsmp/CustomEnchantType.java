package com.cubixsmp;

/**
 * Типы спец. зачарований CubixSMP.
 *
 * <p>Это НЕ ванильные зачарования: метка хранится в {@code PersistentDataContainer}
 * предмета, а название чара добавляется в описание (лор). Спец. книга выдаётся
 * командой {@code /csmp admin giveenchant}, применение — на верстаке
 * «инструмент + книга» (см. {@code CustomEnchantCraftListener}).</p>
 */
public enum CustomEnchantType {

    /** Бур — копает область 3×3 в плоскости взгляда игрока. */
    BUR("bur"),

    /** Автопереплавка — руда выпадает сразу переплавленной (слитки/скрап). */
    AUTOSMELT("autosmelt");

    private final String id;

    CustomEnchantType(String id) {
        this.id = id;
    }

    /** id зачарования (используется в PDC-ключе, команде и config.yml). */
    public String getId() {
        return id;
    }

    /** Дефолтное название чара (переопределяется в config.yml). */
    public String getDisplayName() {
        return switch (this) {
            case BUR -> "Бур";
            case AUTOSMELT -> "Автопереплавка";
        };
    }

    /** Поиск по id (регистронезависимо), null если не найдено. */
    public static CustomEnchantType fromId(String id) {
        for (CustomEnchantType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }
}
