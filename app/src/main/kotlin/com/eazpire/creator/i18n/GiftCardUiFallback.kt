package com.eazpire.creator.i18n

/**
 * English fallback for [TranslationStore] when `creator.gift_cards.*` keys are not yet in the
 * translation DB (same source strings as [theme/locales/en.default.json] under `creator.gift_cards`).
 */
internal object GiftCardUiFallback {
    val en: Map<String, String> = mapOf(
        "creator.gift_cards.error_loading" to "Error loading gift cards.",
        "creator.gift_cards.image_read_error" to "Could not read image",
        "creator.gift_cards.image_generated" to "Image ready",
        "creator.gift_cards.limit_reached" to "Limit reached (5/5)",
        "creator.gift_cards.expires" to "Expires",
        "creator.gift_cards.recommended_products" to "Recommended products",
        "creator.gift_cards.no_products_selected" to "No products selected yet.",
        "creator.gift_cards.edit_product_selection" to "Edit product selection",
        "creator.gift_cards.send_gift_card" to "Send Gift Card",
        "creator.gift_cards.email_sent_notice" to "This gift card was already sent.",
        "creator.gift_cards.delivery_mode_label" to "Delivery mode",
        "creator.gift_cards.delivery_mode_self_print" to "Self print",
        "creator.gift_cards.delivery_mode_email" to "Email",
        "creator.gift_cards.delivery_mode_postcard" to "Postcard",
        "creator.gift_cards.sender_name_label" to "Sender name",
        "creator.gift_cards.recipient_email_label" to "Recipient email",
        "creator.gift_cards.message_label" to "Message",
        "creator.gift_cards.generate_message" to "Generate message",
        "creator.gift_cards.image_optional" to "Image (optional)",
        "creator.gift_cards.image_prompt" to "Image prompt",
        "creator.gift_cards.generate_image" to "Generate image",
        "creator.gift_cards.upload_image" to "Upload image",
        "creator.gift_cards.enter_recipient_email" to "Please enter the recipient email.",
        "creator.gift_cards.enter_valid_email" to "Please enter a valid email address.",
        "creator.gift_cards.postcard_incomplete" to "Please complete all required postcard address fields.",
        "creator.gift_cards.email_template_saved" to "Email template saved",
        "creator.gift_cards.save_template" to "Save",
        "creator.gift_cards.postcard_sent" to "Postcard sent successfully!",
        "creator.gift_cards.email_sent" to "Email successfully sent",
        "creator.gift_cards.send_postcard" to "Send postcard",
        "creator.gift_cards.send_email" to "Send email",
        "creator.gift_cards.select_products" to "Select products",
        "creator.gift_cards.selection_saved" to "Selection saved",
        "creator.gift_cards.product_load_error" to "Could not load product details.",
        "creator.gift_cards.variant_options" to "Options",
        "creator.gift_cards.no_variants" to "No variants available.",
        "creator.gift_cards.postcard_first_name" to "First name",
        "creator.gift_cards.postcard_last_name" to "Last name",
        "creator.gift_cards.postcard_address_line_1" to "Address line 1",
        "creator.gift_cards.postcard_address_line_2" to "Address line 2 (optional)",
        "creator.gift_cards.postcard_postal_code" to "Postal code",
        "creator.gift_cards.postcard_city" to "City",
        "creator.gift_cards.postcard_country" to "Country",
        "creator.gift_cards.postcard_state" to "State / Province (optional)",
        "creator.gift_cards.postcard_size" to "Postcard size",
        "creator.gift_cards.postcard_size_6x4" to "6x4",
        "creator.gift_cards.postcard_size_9x6" to "9x6",
        "creator.gift_cards.postcard_size_11x6" to "11x6"
    )
}
