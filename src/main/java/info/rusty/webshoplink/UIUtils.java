package info.rusty.webshoplink;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

/**
 * Contains utility methods for creating UI components
 */
public class UIUtils {
    /**
     * Creates a border component for shop UI
     */
    public static Component createShopBorder(String title, boolean isHeader) {
        StringBuilder border = new StringBuilder();
        int totalLength = 50;
        
        if (isHeader) {
            // Ensure title length won't cause underflow
            if (title.length() > totalLength - 4) {
                title = title.substring(0, totalLength - 4);
            }
            
            // Calculate padding for centered title
            int titleLength = title.length();
            int padding = (totalLength - titleLength - 2) / 2;
            
            border.append("=".repeat(padding));
            if (!title.isEmpty()) {
                border.append(" ").append(title).append(" ");
            }
            border.append("=".repeat(Math.max(0, totalLength - padding - titleLength - 2)));
        } else {
            // Simple footer border
            border.append("=".repeat(totalLength));
        }
        
        return Component.literal(border.toString()).withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD));
    }
    
    /**
     * Displays a formatted error message to the player
     * @param player The player to send the message to
     * @param error The ErrorResponse containing error information
     */
    public static void displayErrorMessage(ServerPlayer player, ErrorResponse error) {
        Component spacerComponent = Component.literal("");
        Component headerComponent = createShopBorder(error.getUserFriendlyTitle(), true);
        Component errorComponent = error.getUserFriendlyMessage();
        Component helpComponent = error.getHelpText();
        Component footerComponent = createShopBorder("", false);
        
        player.sendSystemMessage(spacerComponent);
        player.sendSystemMessage(headerComponent);
        player.sendSystemMessage(errorComponent);
        player.sendSystemMessage(helpComponent);
        player.sendSystemMessage(footerComponent);
        player.sendSystemMessage(spacerComponent);
    }
    
    /**
     * Displays a generic connection error message to the player
     * @param player The player to send the message to
     * @param errorMessage The error message to display
     */
    public static void displayConnectionError(ServerPlayer player, String errorMessage) {
        Component spacerComponent = Component.literal("");
        Component headerComponent = createShopBorder("Connection Error", true);
        Component errorComponent = Component.literal(errorMessage)
                .withStyle(Style.EMPTY.withColor(ChatFormatting.RED));
        Component helpComponent = Component.literal("Please check your connection and try again later.")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW));
        Component footerComponent = createShopBorder("", false);
        
        player.sendSystemMessage(spacerComponent);
        player.sendSystemMessage(headerComponent);
        player.sendSystemMessage(errorComponent);
        player.sendSystemMessage(helpComponent);
        player.sendSystemMessage(footerComponent);
        player.sendSystemMessage(spacerComponent);
    }
    
    /**
     * Displays a success message to the player
     * @param player The player to send the message to
     * @param title The title for the success message
     * @param message The success message to display
     */
    public static void displaySuccessMessage(ServerPlayer player, String title, String message) {
        Component spacerComponent = Component.literal("");
        Component headerComponent = createShopBorder(title, true);
        Component successComponent = Component.literal(message)
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN));
        Component footerComponent = createShopBorder("", false);
        
        player.sendSystemMessage(spacerComponent);
        player.sendSystemMessage(headerComponent);
        player.sendSystemMessage(successComponent);
        player.sendSystemMessage(footerComponent);
        player.sendSystemMessage(spacerComponent);
    }
    
    /**
     * Displays a warning message to the player
     * @param player The player to send the message to
     * @param title The title for the warning message
     * @param message The warning message to display
     * @param helpText The help text to display with the warning
     */
    public static void displayWarningMessage(ServerPlayer player, String title, String message, String helpText) {
        Component spacerComponent = Component.literal("");
        Component headerComponent = createShopBorder(title, true);
        Component warningComponent = Component.literal(message)
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD));
        Component helpComponent = Component.literal(helpText)
                .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW));
        Component footerComponent = createShopBorder("", false);
        
        player.sendSystemMessage(spacerComponent);
        player.sendSystemMessage(headerComponent);
        player.sendSystemMessage(warningComponent);
        player.sendSystemMessage(helpComponent);
        player.sendSystemMessage(footerComponent);
        player.sendSystemMessage(spacerComponent);
    }
}
