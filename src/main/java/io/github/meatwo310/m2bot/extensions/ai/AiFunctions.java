package io.github.meatwo310.m2bot.extensions.ai;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AiFunctions {
    private static final DateTimeFormatter formatter =
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    public static String getLocalDateTime() {
        return LocalDateTime.now().format(formatter);
    }
}
