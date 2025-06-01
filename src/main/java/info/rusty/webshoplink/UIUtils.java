package info.rusty.webshoplink;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

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
}
