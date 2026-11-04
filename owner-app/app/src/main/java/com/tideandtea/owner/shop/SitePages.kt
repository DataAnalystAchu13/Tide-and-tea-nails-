package com.tideandtea.owner.shop

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.ui.graphics.vector.ImageVector

/** A screen of the generic editor: which document it edits, and its fields. */
data class EditPage(
    val key: String,
    val title: String,
    val sub: String,
    val icon: ImageVector,
    /** true: the booking price list (config 'main'); false: website content (site_settings). */
    val prices: Boolean,
    val sections: List<FormSection>,
)

private val MD = "Tip: *italic* and **bold** work. Start a line with - for a bullet list."

val SITE_PAGES = listOf(
    EditPage("home", "Home page", "Headline, intro and hero photo", Icons.Outlined.Home, false, listOf(
        FormSection("Top of the page", listOf(
            F.Text("home.eyebrow", "Small line above the headline"),
            F.Text("home.title", "Headline", hint = "Wrap words in *stars* to write them in script"),
            F.Text("home.text", "Intro", lines = 3),
            F.Image("home.heroImage", "Hero photo"),
        )),
        FormSection("Offer strip", listOf(
            F.Text("home.offerText", "Message across the top of every page", hint = "Leave empty to hide. A running sale shows here on its own."),
        )),
        FormSection("Brand", listOf(
            F.Text("brand.name", "Business name"),
            F.Text("brand.tagline", "Tagline"),
        )),
    )),
    EditPage("sections", "Home page sections", "Buttons, headings, steps; show or hide", Icons.Outlined.Dashboard, false, listOf(
        FormSection("Buttons under the headline", listOf(
            F.Text("texts.home.bookButton", "First button", hint = "Book a home visit"),
            F.Text("texts.home.shopButton", "Second button", hint = "Shop press-ons"),
        )),
        FormSection("How it works", listOf(
            F.Bool("texts.show.steps", "Show the four steps", default = true),
            F.Rows("texts.steps", "Steps", listOf(Col("title", "Step"), Col("text", "What happens", lines = 2)), add = "Add a step"),
            F.Note("Up to four steps show. Remove them all to bring back the standard ones."),
        )),
        FormSection("Services", listOf(
            F.Bool("texts.show.services", "Show services", default = true),
            F.Text("texts.home.servicesTitle", "Heading", hint = "Nail extensions *at home*"),
            F.Text("texts.home.servicesLink", "Link text", hint = "Full price list"),
        )),
        FormSection("Press-on shop", listOf(
            F.Bool("texts.show.shop", "Show press-ons", default = true),
            F.Text("texts.home.shopTitle", "Heading", hint = "Press-on *drops*"),
            F.Text("texts.home.shopLink", "Link text", hint = "Shop all"),
        )),
        FormSection("Reviews", listOf(
            F.Bool("texts.show.reviews", "Show reviews", default = true),
            F.Text("texts.home.reviewsTitle", "Heading", hint = "Kind *words*"),
            F.Text("texts.home.reviewsLink", "Link text", hint = "All reviews"),
        )),
        FormSection("Blog", listOf(
            F.Bool("texts.show.blog", "Show blog posts", default = true),
            F.Text("texts.home.blogTitle", "Heading", hint = "From the *blog*"),
            F.Text("texts.home.blogLink", "Link text", hint = "All posts"),
        )),
        FormSection("Questions", listOf(
            F.Bool("texts.show.faq", "Show questions", default = true),
            F.Text("texts.home.faqTitle", "Heading", hint = "Questions, *answered*"),
        )),
        FormSection("Instagram band", listOf(
            F.Bool("texts.show.follow", "Show the Instagram band", default = true),
            F.Text("texts.home.followTitle", "Heading", hint = "Fresh sets *every week*"),
            F.Text("texts.home.followText", "Line after your handle", hint = "for new designs, offers and press-on drops."),
            F.Text("texts.home.followButton", "Button", hint = "Follow on Instagram"),
        )),
        FormSection("Areas", listOf(
            F.Bool("texts.show.areas", "Show the areas you visit", default = true),
            F.Text("texts.home.areasTitle", "Heading", hint = "Home visits across {area}"),
        )),
        FormSection("Tips", listOf(F.Note("Leave a box empty to use the standard wording shown in grey. Wrap words in *stars* to write them in script. {area} becomes your home visit area."))),
    )),
    EditPage("pagetext", "Page headings", "Top of the shop, prices, booking and other pages", Icons.Outlined.Title, false, listOf(
        pageHeading("book", "Booking page", "Book online", "Book your *set*", "Takes about two minutes. Pay a small advance by UPI to hold your slot, refunded in full if we can't do your set."),
        pageHeading("services", "Prices page", "Price list", "Services and *prices*", "Every extension is done at your home across {area}. The final price is confirmed on WhatsApp before your visit."),
        pageHeading("shop", "Shop page", "Press-on nails", "Press-ons, *made to fit*", "Reusable salon-quality press-on sets, shipped across India."),
        pageHeading("reviews", "Reviews page", "Reviews", "Kind *words*", "What our clients say about their sets."),
        pageHeading("blog", "Blog page", "Blog", "Nail *notes*", "Care tips, trends and press-on how-tos."),
        pageHeading("contact", "Contact page", "Contact", "Say *hello*", "We come to you anywhere in {area}. Message us with any question."),
        pageHeading("policies", "Policies page", "Policies", "The *small print*", ""),
        FormSection("Tips", listOf(F.Note("Leave a box empty to use the wording shown in grey. *stars* write words in script. {area} becomes your home visit area."))),
    )),
    EditPage("theme", "Colours", "Main colour, accent and background", Icons.Outlined.ColorLens, false, listOf(
        FormSection("Website colours", listOf(
            F.Color("theme.primary", "Main colour", listOf("#3a1d5c", "#5b2d8a", "#2a1824", "#1f3b4d", "#2f4a3a"), "Header, buttons, headings and prices"),
            F.Color("theme.accent", "Accent on the dark header", listOf("#ffffff", "#f4b3cb", "#e9d8f7", "#c9a35e", "#f3e3c3"), "Buttons and script words on the dark top section"),
            F.Color("theme.background", "Page background", listOf("#ffffff", "#faf7f4", "#f7f2fb", "#f6f3ef"), "Behind the cards on every page"),
            F.Note("Auto uses the standard Tide & Tea colours. Publish to see changes on the site."),
        )),
    )),
    EditPage("about", "About me", "Your photo, your story and sign-off", Icons.Outlined.Badge, false, listOf(
        FormSection("Your photo", listOf(
            F.Image("about.photo", "Photo of you"),
            F.Text("about.caption", "Handwritten caption under the photo", hint = "the girl behind the nails"),
        )),
        FormSection("Your story", listOf(
            F.Text("about.title", "Heading", hint = "hii girlies, *I'm Akshaya!* 🎀🤎"),
            F.Text("about.body", "Your story", lines = 10),
            F.Note(MD),
            F.Text("about.signoff", "Sign-off", hint = "With love,"),
            F.Text("about.signature", "Signature", hint = "Akshaya ♡"),
        )),
        FormSection("More photos (optional)", listOf(
            F.Images("about.images", "Studio or work photos shown under your story", max = 4),
        )),
    )),
    EditPage("contact", "Contact and hours", "Phone, WhatsApp, address, Google", Icons.Outlined.Call, false, listOf(
        FormSection("How to reach you", listOf(
            F.Text("contact.phone", "Phone", hint = "+91 98765 43210"),
            F.Text("contact.whatsapp", "WhatsApp number", hint = "Same as phone if empty"),
            F.Text("contact.email", "Email"),
            F.Text("contact.instagram", "Instagram handle", hint = "tideandteanails"),
        )),
        FormSection("Where you are", listOf(
            F.Bool("contact.homeVisitsOnly", "Home visits only", "Hides the street address and shows your service areas instead"),
            F.Text("contact.address", "Street address"),
            F.Text("contact.area", "Area", hint = "Anna Nagar"),
            F.Text("contact.city", "City"),
            F.Text("contact.serviceArea", "Home visit area", hint = "Chennai and Chengalpattu"),
            F.Text("contact.pincode", "PIN code"),
        )),
        FormSection("Opening hours", listOf(
            F.Rows("contact.hours", "Hours", listOf(Col("days", "Days"), Col("time", "Time")), add = "Add a line"),
        )),
        FormSection("Google", listOf(
            F.Text("contact.googleProfileUrl", "Google Business Profile link", hint = "maps.app.goo.gl/…"),
            F.Text("contact.googleReviewUrl", "Google review link", hint = "g.page/r/…/review"),
            F.Note("The review link powers the \"Review us on Google\" button. Find it in your Google Business Profile under Ask for reviews."),
        )),
    )),
    EditPage("follow", "Instagram card", "The page your shared link opens", Icons.Outlined.ThumbUp, false, listOf(
        FormSection("Instagram card", listOf(
            F.Note("Share tideandteanails.in/hi on Instagram, WhatsApp or a QR code. Visitors see this card once with a Follow button, then go straight to the website."),
            F.Bool("follow.enabled", "Show the card"),
            F.Text("follow.message", "Message", lines = 2),
            F.Num("follow.seconds", "Seconds before it moves on", hint = "3"),
            F.Text("follow.coupon", "Coupon to show (optional)", hint = "INSTA10"),
            F.Text("follow.couponNote", "Coupon note", hint = "10% off your first press-on order"),
        )),
    )),
    EditPage("shipping", "Shipping", "Charges and delivery times", Icons.Outlined.LocalShipping, false, listOf(
        FormSection("Press-on shipping", listOf(
            F.Num("shipping.flat", "Shipping charge", prefix = "₹ "),
            F.Num("shipping.freeAbove", "Free shipping above", prefix = "₹ ", hint = "0 for never"),
            F.Text("shipping.days", "Delivery time", lines = 2),
        )),
    )),
    EditPage("areas", "Chennai areas", "Area pages that help Google find you", Icons.Outlined.LocationOn, false, listOf(
        FormSection("Areas you serve", listOf(
            F.Note("Each area gets its own page, like tideandteanails.in/nail-extensions-in-anna-nagar. Write a couple of sentences that mention local landmarks, so the page isn't the same as the others."),
            F.Rows("areas", "Areas", listOf(Col("name", "Area"), Col("text", "About this area", lines = 3)), add = "Add an area"),
        )),
    )),
    EditPage("faq", "Questions (FAQ)", "Shown on the home page and in Google", Icons.Outlined.QuestionAnswer, false, listOf(
        FormSection("Questions", listOf(
            F.Rows("faq", "Questions and answers", listOf(Col("q", "Question"), Col("a", "Answer", lines = 3)), add = "Add a question"),
        )),
    )),
    EditPage("sizing", "Sizing guide", "Press-on size chart", Icons.Outlined.Straighten, false, listOf(
        FormSection("Sizing guide", listOf(F.Text("sizing", "Guide", lines = 10), F.Note(MD))),
        FormSection("Guide pictures", listOf(
            F.Images("sizingImages", "Sizing chart and how-to-apply pictures", max = 4),
            F.Note("Shown on the shop page above the written guide, with an Order press-ons button."),
        )),
        FormSection("Press-on kit", listOf(
            F.Text("kit.title", "Heading", hint = "In every press-on order"),
            F.Words("kit.items", "What's included", "Glue tabs, Liquid nail glue, Cuticle pusher, Nail buffer, Alcohol wipes"),
        )),
    )),
    EditPage("policies", "Policies", "Shipping, returns, privacy, terms", Icons.Outlined.Gavel, false, listOf(
        FormSection("Policies", listOf(
            F.Note("These are a starting draft. Read them through and change anything that doesn't match how you work."),
            F.Text("policies.shipping", "Shipping", lines = 4),
            F.Text("policies.returns", "Returns and refunds", lines = 4),
            F.Text("policies.cancellation", "Cancellations", lines = 4),
            F.Text("policies.privacy", "Privacy", lines = 4),
            F.Text("policies.terms", "Terms", lines = 4),
        )),
    )),
    EditPage("seo", "Google search", "Title and description in results", Icons.Outlined.Search, false, listOf(
        FormSection("How you appear on Google", listOf(
            F.Text("seo.title", "Title", hint = "Under 60 characters"),
            F.Text("seo.description", "Description", lines = 3, hint = "Under 160 characters"),
            F.Text("seo.siteUrl", "Website address"),
        )),
    )),
)

val PRICE_PAGES = listOf(
    EditPage("services", "Services and prices", "Extensions, lengths, add-ons", Icons.Outlined.Palette, true, listOf(
        FormSection("Extension types", listOf(
            F.Rows("extensionTypes", "Extensions", listOf(Col("name", "Name"), Col("price", "Price", num = true, prefix = "₹ "), Col("description", "Short description", lines = 2)), add = "Add an extension", idFromName = true),
        )),
        FormSection("Lengths", listOf(
            F.Rows("lengths", "Lengths", listOf(Col("name", "Name"), Col("price", "Extra charge (0 = included)", num = true, prefix = "₹ ")), add = "Add a length", idFromName = true),
        )),
        FormSection("Shapes", listOf(F.Words("shapes", "Shapes", "Almond, Coffin, Square, Oval, Stiletto"))),
        FormSection("Add-ons", listOf(
            F.Rows("addOns", "Add-ons", listOf(Col("name", "Name"), Col("price", "Price (0 = price varies)", num = true, prefix = "₹ ")), add = "Add an add-on", idFromName = true),
        )),
        FormSection("Payment", listOf(
            F.Num("advancePercent", "Advance to confirm a booking", hint = "30", prefix = "% "),
            F.Text("upiId", "UPI ID", hint = "yourname@okaxis"),
            F.Text("upiName", "Name shown on UPI"),
            F.Text("whatsapp", "WhatsApp number for clients"),
        )),
        FormSection("Home visits", listOf(
            F.Text("travel.note", "Travel note", lines = 2),
            F.Note("Leave the distances below empty to show the travel note instead of a price; you then add the travel charge when you approve."),
            F.Rows("travel.slabs", "Travel charge by distance", listOf(Col("upToKm", "Up to (km)", num = true), Col("charge", "Charge", num = true, prefix = "₹ ")), add = "Add a distance"),
            F.Num("travel.perKmBeyond", "Per km after the last distance", prefix = "₹ "),
            F.Text("studio.name", "Starting point name"),
            F.Text("studio.address", "Starting point address"),
        )),
    )),
    EditPage("schedule", "Days and slots", "When clients can book", Icons.Outlined.CalendarMonth, true, listOf(
        FormSection("Booking calendar", listOf(
            F.Days("workingDays", "Working days"),
            F.Words("slots", "Start times", "10:00, 12:30, 15:00, 17:30"),
            F.Words("blockedDates", "Days off", "2026-12-25, 2027-01-01"),
            F.Num("bookingWindowDays", "How many days ahead clients can book", hint = "30"),
        )),
    )),
)

private fun pageHeading(key: String, title: String, eyebrow: String, head: String, text: String) = FormSection(title, listOf(
    F.Text("texts.pages.$key.eyebrow", "Small line", hint = eyebrow),
    F.Text("texts.pages.$key.title", "Heading", hint = head),
    F.Text("texts.pages.$key.text", "Intro", lines = 2, hint = text.ifEmpty { "Optional" }),
))

fun editPage(key: String) = (SITE_PAGES + PRICE_PAGES).firstOrNull { it.key == key }
