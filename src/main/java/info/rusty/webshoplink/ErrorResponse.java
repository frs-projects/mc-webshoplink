package info.rusty.webshoplink;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * Represents an error response from the API
 */
public class ErrorResponse extends RuntimeException {
    private final String errorMessage;
    private final int statusCode;

    public ErrorResponse(String errorMessage, int statusCode) {
        super(errorMessage);
        this.errorMessage = errorMessage;
        this.statusCode = statusCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isAlreadyOpenInstance() {
        return errorMessage != null && (
            errorMessage.toLowerCase().contains("already has an open shop instance")
        );
    }

    public boolean shopNotFound() {
        return errorMessage != null && (
            errorMessage.toLowerCase().contains("shop not found")
        );
    }

    /**
     * Parses error message for specific conditions
     * @return true if this is a specific type of error
     */
    public boolean isShopSessionNotFound() {
        return errorMessage != null && (
            errorMessage.toLowerCase().contains("instance not found")
        );
    }   
    
    /**
     * Checks if error is related to session expiration
     */
    public boolean isSessionExpired() {
        return errorMessage != null && (
            errorMessage.toLowerCase().contains("expired")
        );
    }
    
    /**
     * Checks if error is related to empty cart
     */
    public boolean isEmptyCart() {
        return errorMessage != null && (
            errorMessage.toLowerCase().contains("empty cart") || 
            errorMessage.toLowerCase().contains("no items") ||
            errorMessage.toLowerCase().contains("nothing to purchase")
        );
    }
    
    /**
     * Checks if error is related to insufficient permissions
     */
    public boolean isPermissionError() {
        return errorMessage != null && (
            errorMessage.toLowerCase().contains("permission") || 
            errorMessage.toLowerCase().contains("not allowed") ||
            errorMessage.toLowerCase().contains("unauthorized")
        );
    }
    
    /**
     * Checks if error is related to server maintenance
     */
    public boolean isMaintenanceError() {
        return errorMessage != null && (
            errorMessage.toLowerCase().contains("maintenance") || 
            errorMessage.toLowerCase().contains("temporarily unavailable") ||
            errorMessage.toLowerCase().contains("down for maintenance")
        );
    }
    
    /**
     * Checks if error is related to rate limiting
     */
    public boolean isRateLimitError() {
        return errorMessage != null && (
            errorMessage.toLowerCase().contains("rate limit") || 
            errorMessage.toLowerCase().contains("too many requests") ||
            errorMessage.toLowerCase().contains("try again later")
        );
    }
    
    /**
     * Checks if error is related to invalid items or invalid purchase
     */
    public boolean isInvalidPurchaseError() {
        return errorMessage != null && (
            errorMessage.toLowerCase().contains("invalid purchase") || 
            errorMessage.toLowerCase().contains("invalid item") ||
            errorMessage.toLowerCase().contains("item not available")
        );
    }
    
    /**
     * Checks if error is related to payment issues
     */
    public boolean isPaymentError() {
        return errorMessage != null && (
            errorMessage.toLowerCase().contains("payment") || 
            errorMessage.toLowerCase().contains("transaction failed") ||
            errorMessage.toLowerCase().contains("insufficient funds")
        );
    }
    
    /**
     * Checks if error is related to connection timeout
     */
    public boolean isTimeoutError() {
        return errorMessage != null && (
            errorMessage.toLowerCase().contains("timeout") || 
            errorMessage.toLowerCase().contains("timed out") ||
            errorMessage.toLowerCase().contains("connection timed out")
        );
    }
    
    /**
     * Checks if error is related to network issues
     */
    public boolean isNetworkError() {
        return errorMessage != null && (
            errorMessage.toLowerCase().contains("network") || 
            errorMessage.toLowerCase().contains("connection") ||
            errorMessage.toLowerCase().contains("unreachable")
        );
    }
    
    /**
     * Checks if error is related to the TFA (Two-Factor Authentication) code
     */
    public boolean isTfaError() {
        return errorMessage != null && (
            errorMessage.toLowerCase().contains("tfa") || 
            errorMessage.toLowerCase().contains("two-factor") ||
            errorMessage.toLowerCase().contains("verification code")
        );
    }
    
    /**
     * Gets user-friendly error title based on error type
     * @return A title for the error message
     */
    public String getUserFriendlyTitle() {
        if (isAlreadyOpenInstance()) {
            return "Active Session Exists";
        } else if (shopNotFound()) {
            return "Shop Not Found";
        } else if (isShopSessionNotFound()) {
            return "Session Not Found";
        } else if (isSessionExpired()) {
            return "Session Expired";
        } else if (isEmptyCart()) {
            return "Empty Cart";
        } else if (isPermissionError()) {
            return "Permission Denied";
        } else if (isMaintenanceError()) {
            return "Server Maintenance";
        } else if (isRateLimitError()) {
            return "Rate Limited";
        } else if (isInvalidPurchaseError()) {
            return "Invalid Purchase";
        } else if (isPaymentError()) {
            return "Payment Error";
        } else if (isTimeoutError()) {
            return "Connection Timeout";
        } else if (isNetworkError()) {
            return "Network Error";
        } else if (isTfaError()) {
            return "Verification Code Error";
        } else {
            return "Shop Error";
        }
    }
    
    /**
     * Gets user-friendly error message based on error type
     * @return A friendly error message for the user
     */
    public Component getUserFriendlyMessage() {
        if (isAlreadyOpenInstance()) {
            return Component.literal("You already have an active shop session. Please finish or cancel your current session before starting a new one.")
                   .withStyle(Style.EMPTY.withColor(ChatFormatting.RED));
        } else if (shopNotFound()) {
            return Component.literal("The requested shop could not be found. Please check the shop name and try again.")
                   .withStyle(Style.EMPTY.withColor(ChatFormatting.RED));
        } else if (isShopSessionNotFound()) {
            return Component.literal("Your shop session was not found. It may have expired or been closed.")
                   .withStyle(Style.EMPTY.withColor(ChatFormatting.RED));
        } else if (isSessionExpired()) {
            return Component.literal("Your shop session has expired. Please start a new session.")
                   .withStyle(Style.EMPTY.withColor(ChatFormatting.RED));
        } else if (isEmptyCart()) {
            return Component.literal("Your shopping cart is empty. You need to add items to your cart before checking out.")
                   .withStyle(Style.EMPTY.withColor(ChatFormatting.RED));
        } else if (isPermissionError()) {
            return Component.literal("You don't have permission to perform this action.")
                   .withStyle(Style.EMPTY.withColor(ChatFormatting.RED));
        } else if (isMaintenanceError()) {
            return Component.literal("The shop server is currently under maintenance. Please try again later.")
                   .withStyle(Style.EMPTY.withColor(ChatFormatting.RED));
        } else if (isRateLimitError()) {
            return Component.literal("You've made too many requests. Please wait a moment before trying again.")
                   .withStyle(Style.EMPTY.withColor(ChatFormatting.RED));
        } else if (isInvalidPurchaseError()) {
            return Component.literal("Your purchase contains invalid or unavailable items.")
                   .withStyle(Style.EMPTY.withColor(ChatFormatting.RED));
        } else if (isPaymentError()) {
            return Component.literal("There was an issue processing your payment. The transaction could not be completed.")
                   .withStyle(Style.EMPTY.withColor(ChatFormatting.RED));
        } else if (isTimeoutError()) {
            return Component.literal("The request timed out while connecting to the shop server.")
                   .withStyle(Style.EMPTY.withColor(ChatFormatting.RED));
        } else if (isNetworkError()) {
            return Component.literal("A network error occurred while communicating with the shop server.")
                   .withStyle(Style.EMPTY.withColor(ChatFormatting.RED));
        } else if (isTfaError()) {
            return Component.literal("The verification code provided is incorrect or has expired.")
                   .withStyle(Style.EMPTY.withColor(ChatFormatting.RED));
        } else {
            return Component.literal("Shop error: " + errorMessage)
                   .withStyle(Style.EMPTY.withColor(ChatFormatting.RED));
        }
    }
    
    /**
     * Gets help text to accompany the error message
     * @return Help text component
     */    public Component getHelpText() {
        if (isAlreadyOpenInstance()) {
            return Component.literal("If you can't find your active session, for it to expire automatically, which takes at most 15 minutes.")
                   .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW));
        } else if (shopNotFound()) {
            return Component.literal("Check the shop name and make sure it exists.")
                   .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW));
        } else if (isShopSessionNotFound()) {
            return Component.literal("Please start a new shop session.")
                   .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW));
        } else if (isSessionExpired()) {
            return Component.literal("Shop sessions expire after a period of inactivity.")
                   .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW));
        } else if (isEmptyCart()) {
            return Component.literal("Return to the shop website and add some items to your cart before checking out.")
                   .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW));
        } else if (isPermissionError()) {
            return Component.literal("Contact a server administrator if you believe you should have access.")
                   .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW));
        } else if (isMaintenanceError()) {
            return Component.literal("The shop server is being updated. Please check back in a few minutes.")
                   .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW));
        } else if (isRateLimitError()) {
            return Component.literal("Rate limits help protect the server. Wait a minute before trying again.")
                   .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW));
        } else if (isInvalidPurchaseError()) {
            return Component.literal("Some items may be out of stock or no longer available. Try adjusting your cart.")
                   .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW));
        } else if (isPaymentError()) {
            return Component.literal("Check your payment details or try using a different payment method.")
                   .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW));
        } else if (isTimeoutError()) {
            return Component.literal("The shop server may be experiencing high traffic. Please try again later.")
                   .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW));
        } else if (isNetworkError()) {
            return Component.literal("Check your internet connection and try again. If the problem persists, the server may be down.")
                   .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW));
        } else if (isTfaError()) {
            return Component.literal("Make sure you're using the most recent verification code. Try starting a new shop session.")
                   .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW));
        } else {
            return Component.literal("Please try again later or contact an administrator.")
                   .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW));
        }
    }
}
