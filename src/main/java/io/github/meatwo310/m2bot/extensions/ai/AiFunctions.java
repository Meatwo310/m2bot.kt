package io.github.meatwo310.m2bot.extensions.ai;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class AiFunctions {
    private static final DateTimeFormatter formatter =
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    /**
     * 指定された日時にリマインダーを追加します。
     *
     * @param reminderAt   リマインダーを設定する日時。
     * 必ずISO 8601形式の文字列で指定してください (例: "2025-08-18T15:30:00")
     * @param reminderText リマインダーの内容
     */
    public static String addReminder(String reminderAt, String reminderText) {
        LocalDateTime dateTime;
        try {
            dateTime = LocalDateTime.parse(reminderAt);
        } catch (Exception e) {
            return "Invalid date format. Please use ISO 8601 format.";
        }

        String formattedDate = dateTime.format(formatter);

        // TODO: 実際のリマインダー追加処理をここに実装する

        return "Reminder set for " + formattedDate + ": " + reminderText;
    }

    public static List<Method> getAvailableFunctions() throws NoSuchMethodException{
        return List.of(
            AiFunctions.class.getMethod("addReminder", String.class, String.class)
        );
    }
}
