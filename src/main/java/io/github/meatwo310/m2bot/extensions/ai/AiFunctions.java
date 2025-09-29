package io.github.meatwo310.m2bot.extensions.ai;

import dev.kord.common.entity.Snowflake;
import io.github.meatwo310.m2bot.extensions.reminder.ReminderExtension;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.Dispatchers;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class AiFunctions {
    private static final DateTimeFormatter formatter =
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    // Context storage for the current AI request
    private static final ThreadLocal<MessageContext> currentContext = new ThreadLocal<>();

    public static void setMessageContext(MessageContext context) {
        currentContext.set(context);
    }

    public static void clearMessageContext() {
        currentContext.remove();
    }

    public static class MessageContext {
        public final Snowflake guildId;
        public final Snowflake channelId;
        public final Snowflake messageId;
        public final Snowflake userId;

        public MessageContext(Snowflake guildId, Snowflake channelId, Snowflake messageId, Snowflake userId) {
            this.guildId = guildId;
            this.channelId = channelId;
            this.messageId = messageId;
            this.userId = userId;
        }
    }

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

        // Get current message context
        MessageContext context = currentContext.get();
        if (context == null) {
            return "Error: No message context available for reminder registration.";
        }

        try {
            // Call the Kotlin extension to add reminder
            String result = (String) BuildersKt.runBlocking(Dispatchers.getIO(), (scope, continuation) -> {
                ReminderExtension reminderExtension = ReminderExtension.Companion.getInstance();
                if (reminderExtension != null) {
                    return reminderExtension.addReminder(
                        context.guildId,
                        context.channelId,
                        context.messageId,
                        context.userId,
                        reminderAt,
                        reminderText,
                        continuation
                    );
                } else {
                    return "Error: ReminderExtension not available";
                }
            });

            return result != null ? result : "Reminder set for " + formattedDate + ": " + reminderText;
        } catch (Exception e) {
            return "Error setting reminder: " + e.getMessage();
        }
    }

    public static List<Method> getAvailableFunctions() throws NoSuchMethodException{
        return List.of(
            AiFunctions.class.getMethod("addReminder", String.class, String.class)
        );
    }
}
