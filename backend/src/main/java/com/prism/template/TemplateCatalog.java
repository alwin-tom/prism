package com.prism.template;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TemplateCatalog {

    private TemplateCatalog() {
    }

    public record CategorySeed(String code, String name, String description, String thumbnail) {
    }

    public record TemplateSeed(String categoryCode, String code, String name, String description, String thumbnail,
                               Map<String, Object> schema, Map<String, Object> defaults) {
    }

    public static List<CategorySeed> categories() {
        return List.of(
                new CategorySeed("WEDDING", "Wedding", "Ceremonies, vows and the meal that follows.", "/placeholders/linen.svg"),
                new CategorySeed("ANNIVERSARY", "Anniversary", "A year, marked, with the people who were there.", "/placeholders/years.svg"),
                new CategorySeed("BIRTHDAY", "Birthday", "A day with one name on it.", "/placeholders/day.svg"),
                new CategorySeed("INVITATION", "Invitation", "A time, a place and the reason for both.", "/placeholders/card.svg"),
                new CategorySeed("CORPORATE", "Corporate events", "Offsites, supplier days and the annual meeting.", "/placeholders/agenda.svg"),
                new CategorySeed("ENGAGEMENT", "Engagement", "The announcement, then the party.", "/placeholders/card.svg"),
                new CategorySeed("BAPTISM", "Baptism", "A service, a name, and lunch after.", "/placeholders/linen.svg"),
                new CategorySeed("SPORTS", "Sports", "A start time, a field, and who is running.", "/placeholders/day.svg"),
                new CategorySeed("MEMORIAL", "Memorial", "A notice of a life, and where to gather.", "/placeholders/evening.svg")
        );
    }

    public static List<TemplateSeed> templates() {
        return List.of(
                weddingLinen(),
                weddingNight(),
                anniversaryYears(),
                anniversaryTable(),
                birthdayDay(),
                birthdayDark(),
                invitationCard(),
                invitationEvening(),
                corporateAgenda(),
                corporateHall(),
                birthdayPaper(),
                anniversarySilver(),
                corporateSummit(),
                engagementRooftop(),
                baptismParish(),
                sportsOpener(),
                memorialNotice()
        );
    }

    private static TemplateSeed weddingLinen() {
        return seed("WEDDING", "WEDDING_LINEN", "Linen vow",
                "A paper-coloured ceremony site: names, the hour, then the table plan.",
                "/placeholders/linen.svg", "#f4f1ea", "#1c1915", "#1f4d3a", "Public Sans", "Fraunces",
                "Meera Iyer & Arun Shah", "Saturday, 18 April 2027",
                "The ceremony is in the courtyard. Dinner follows in the long room.",
                "2027-04-18T11:30:00Z", "The Courtyard, Byculla", "12 Sankli Street, Byculla, Mumbai",
                "/placeholders/linen.svg", true,
                List.of(
                        hero("hero", "Meera & Arun", "Together, in the courtyard", "/placeholders/linen.svg"),
                        countdown("countdown", "Until the vows"),
                        text("note", "A note", "Phones away for the ceremony. Photographs after the register is signed."),
                        details("details", "The day"),
                        schedule("plan", "Order of the day", List.of(
                                item("16:30", "Guests arrive", "Courtyard gate"),
                                item("17:00", "Ceremony", "Under the linen canopy"),
                                item("19:30", "Dinner", "The long room")
                        )),
                        gallery("gallery", "Faces"),
                        map("place", "Byculla", "Ask the gate for the courtyard, not the office."),
                        rsvp("reply", "Will you be there?"),
                        footer("close", "Meera and Arun")
                ));
    }

    private static TemplateSeed weddingNight() {
        return seed("WEDDING", "WEDDING_NIGHT", "Night aisle",
                "A dark evening wedding. The names carry the page.",
                "/placeholders/night.svg", "#141311", "#f3efe6", "#c4a574", "Public Sans", "Fraunces",
                "Leela Menon & Kabir Das", "Saturday, 12 December 2026",
                "An evening ceremony by the water, then supper inside.",
                "2026-12-12T13:30:00Z", "Olive Hall, Alibaug", "Revas Road, Alibaug",
                "/placeholders/night.svg", true,
                List.of(
                        hero("hero", "Leela & Kabir", "After dark, by the water", "/placeholders/night.svg"),
                        countdown("countdown", "Until the evening"),
                        details("details", "The evening"),
                        schedule("plan", "The hours", List.of(
                                item("18:00", "Welcome drink", "The lawn"),
                                item("19:00", "Ceremony", "The aisle"),
                                item("21:00", "Supper", "Olive Hall")
                        )),
                        video("film", "A minute of the place"),
                        map("place", "Alibaug", "The last ferry back is not yours to catch."),
                        rsvp("reply", "Reply for supper"),
                        footer("close", "Leela and Kabir")
                ));
    }

    private static TemplateSeed anniversaryYears() {
        return seed("ANNIVERSARY", "ANNIVERSARY_YEARS", "Twenty years",
                "A large numeral and a short letter. Nothing else competing.",
                "/placeholders/years.svg", "#f7f4f2", "#241316", "#6e2430", "Public Sans", "Newsreader",
                "Nalini & Dev", "Twenty years, 2 February 2027",
                "Dinner for the people who have known them since the first flat.",
                "2027-02-02T13:30:00Z", "Their house, Matunga", "King's Circle, Matunga, Mumbai",
                "/placeholders/years.svg", true,
                List.of(
                        hero("hero", "Twenty years", "Nalini and Dev Kapoor", "/placeholders/years.svg"),
                        text("letter", "A letter", "Come as you are. The speech is short and the food is not."),
                        details("details", "The dinner"),
                        gallery("gallery", "The years"),
                        rsvp("reply", "Tell them you are coming"),
                        footer("close", "Nalini and Dev")
                ));
    }

    private static TemplateSeed anniversaryTable() {
        return seed("ANNIVERSARY", "ANNIVERSARY_TABLE", "The second sitting",
                "An anniversary told as a seating and a menu of hours.",
                "/placeholders/table.svg", "#eef1ec", "#1c241c", "#3d5c45", "Public Sans", "Newsreader",
                "Farah & Imran", "Saturday, 21 November 2026",
                "A second sitting for the people who stayed.",
                "2026-11-21T14:00:00Z", "The Table, Fort", "Rope Walk Lane, Fort, Mumbai",
                "/placeholders/table.svg", true,
                List.of(
                        hero("hero", "Farah & Imran", "The second sitting", "/placeholders/table.svg"),
                        countdown("countdown", "Until supper"),
                        schedule("plan", "The sitting", List.of(
                                item("19:30", "Doors", "The small room"),
                                item("20:00", "First course", "No speeches yet"),
                                item("22:00", "Cake", "Then you may leave")
                        )),
                        details("details", "The room"),
                        map("place", "Fort", "Rope Walk Lane, second door."),
                        rsvp("reply", "A seat"),
                        footer("close", "Farah and Imran")
                ));
    }

    private static TemplateSeed birthdayDay() {
        return seed("BIRTHDAY", "BIRTHDAY_DAY", "The day itself",
                "One age, one afternoon, a lunch that does not become a party.",
                "/placeholders/day.svg", "#fbfbf8", "#1a1a1a", "#9f2d2d", "Public Sans", "Fraunces",
                "Asha turns 30", "Saturday, 9 January 2027",
                "Lunch in the garden. Children welcome. Presents are not the point.",
                "2027-01-09T07:30:00Z", "The garden at Dadar", "Near the Portuguese Church, Dadar",
                "/placeholders/day.svg", true,
                List.of(
                        hero("hero", "Asha, 30", "Lunch in the garden", "/placeholders/day.svg"),
                        countdown("countdown", "Until lunch"),
                        details("details", "The afternoon"),
                        gallery("gallery", "Bring nothing but yourself"),
                        rsvp("reply", "Are you coming?"),
                        footer("close", "Asha")
                ));
    }

    private static TemplateSeed birthdayDark() {
        return seed("BIRTHDAY", "BIRTHDAY_DARK", "After dark",
                "A fortieth birthday that starts when the office closes.",
                "/placeholders/dark.svg", "#161616", "#f5f2ea", "#e07a5f", "Public Sans", "Public Sans",
                "Rohan at 40", "Saturday, 28 November 2026",
                "Music from nine. The cake is at eleven. No speeches.",
                "2026-11-28T15:30:00Z", "Studio B, Bandra", "Waroda Road, Bandra West",
                "/placeholders/dark.svg", true,
                List.of(
                        hero("hero", "Rohan, 40", "From nine, until late", "/placeholders/dark.svg"),
                        countdown("countdown", "Until the doors"),
                        video("film", "The room"),
                        schedule("plan", "The night", List.of(
                                item("21:00", "Doors", "Studio B"),
                                item("23:00", "Cake", "Then the second set")
                        )),
                        map("place", "Bandra", "The blue door beside the bakery."),
                        rsvp("reply", "A name at the door"),
                        footer("close", "Rohan")
                ));
    }

    private static TemplateSeed invitationCard() {
        return seed("INVITATION", "INVITATION_CARD", "Card stock",
                "The invitation as a single card: who, when, where.",
                "/placeholders/card.svg", "#f6f1e7", "#221e18", "#1a1a1a", "Public Sans", "Newsreader",
                "Dinner at the Iyer house", "Saturday, 5 December 2026",
                "Eight people, one table, please be on time.",
                "2026-12-05T14:00:00Z", "The Iyer house", "14 Canal Road, Kochi",
                "/placeholders/card.svg", true,
                List.of(
                        hero("hero", "Dinner", "At the Iyer house", "/placeholders/card.svg"),
                        text("note", "What to know", "The door is on the lane, not the main road. Shoes off."),
                        details("details", "Saturday"),
                        rsvp("reply", "Eight seats"),
                        footer("close", "The Iyers")
                ));
    }

    private static TemplateSeed invitationEvening() {
        return seed("INVITATION", "INVITATION_EVENING", "The evening",
                "A gallery opening told as a time and a street.",
                "/placeholders/evening.svg", "#14181d", "#f4efe6", "#d6a15a", "Public Sans", "Fraunces",
                "Studio Rasa, opening", "Saturday, 14 March 2027",
                "New work on the walls from six. The remarks are at seven and they are brief.",
                "2027-03-14T12:30:00Z", "Studio Rasa", "Chapel Road, Bandra West",
                "/placeholders/evening.svg", true,
                List.of(
                        hero("hero", "Studio Rasa", "The spring opening", "/placeholders/evening.svg"),
                        countdown("countdown", "Until the doors"),
                        schedule("plan", "The evening", List.of(
                                item("18:00", "Doors", "Ground floor"),
                                item("19:00", "Remarks", "Five minutes"),
                                item("19:30", "The rooms stay open", "Until nine")
                        )),
                        map("place", "Chapel Road", "Above the bookshop."),
                        rsvp("reply", "Your name"),
                        footer("close", "Studio Rasa")
                ));
    }

    private static TemplateSeed corporateAgenda() {
        return seed("CORPORATE", "CORPORATE_AGENDA", "The agenda",
                "A supplier day with the hours on the page and the room number beside them.",
                "/placeholders/agenda.svg", "#f7f8f8", "#141618", "#1b6c80", "IBM Plex Sans", "IBM Plex Sans",
                "Sable Atelier supplier day", "Monday, 19 January 2027",
                "One room, one agenda, lunch in the middle.",
                "2027-01-19T03:30:00Z", "Sable Atelier", "Ballard Estate, Mumbai",
                "/placeholders/agenda.svg", true,
                List.of(
                        hero("hero", "Supplier day", "Sable Atelier", "/placeholders/agenda.svg"),
                        details("details", "Monday"),
                        schedule("plan", "Agenda", List.of(
                                item("09:30", "Arrival", "Room 2"),
                                item("10:00", "The year ahead", "No slides before coffee"),
                                item("13:00", "Lunch", "The canteen"),
                                item("15:30", "Close", "Actions, then the door")
                        )),
                        map("place", "Ballard Estate", "Reception will have a list."),
                        rsvp("reply", "Confirm a seat"),
                        footer("close", "Sable Atelier")
                ));
    }

    private static TemplateSeed corporateHall() {
        return seed("CORPORATE", "CORPORATE_HALL", "The hall",
                "An annual meeting set like a programme, not a brochure.",
                "/placeholders/hall.svg", "#121417", "#f2f4f5", "#d7b56d", "IBM Plex Sans", "Fraunces",
                "Harbour Line annual meeting", "Wednesday, 4 February 2027",
                "The year in one sitting. Questions at the end, not during the numbers.",
                "2027-02-04T04:30:00Z", "The Hall, Fort", "DN Road, Fort, Mumbai",
                "/placeholders/hall.svg", false,
                List.of(
                        hero("hero", "The annual meeting", "Harbour Line", "/placeholders/hall.svg"),
                        video("film", "Last year's hall"),
                        schedule("plan", "Programme", List.of(
                                item("10:00", "Open", "The Hall"),
                                item("10:20", "The year", "Numbers, then what changed"),
                                item("12:00", "Questions", "One microphone")
                        )),
                        map("place", "DN Road", "Use the side entrance on the lane."),
                        text("note", "For the room", "Badges at the desk. The meeting is not recorded."),
                        footer("close", "Harbour Line")
                ));
    }

    private static TemplateSeed birthdayPaper() {
        return seed("BIRTHDAY", "BIRTHDAY_PAPER", "Elena, thirty",
                "A short birthday card: the hour, the glasshouse, what to wear.",
                "/placeholders/day.svg", "#fcf9f3", "#161613", "#b56b45", "Public Sans", "Newsreader",
                "Elena, thirty", "Saturday, 18 October 2025",
                "A private celebration. Cocktail dress, autumn colours.",
                "2025-10-19T01:30:00Z", "Glasshouse", "The Terrace, San Francisco",
                "", true, "America/Los_Angeles",
                List.of(
                        hero("hero", "Elena, thirty", "Saturday, 18 October, half past six", ""),
                        text("note", "What to wear", "Cocktail, and festive autumn tones."),
                        details("details", "The evening"),
                        map("place", "Glasshouse", "The terrace, not the street door."),
                        rsvp("reply", "Will you come?"),
                        footer("close", "Elena")
                ));
    }

    private static TemplateSeed anniversarySilver() {
        return seed("ANNIVERSARY", "ANNIVERSARY_SILVER", "Silver anniversary",
                "Twenty-five years, told as a date, a house, and three hours.",
                "/placeholders/years.svg", "#fcf9f3", "#161613", "#556254", "Public Sans", "Newsreader",
                "Claire & Marcus", "Friday, 14 November 2025",
                "Honouring twenty-five years. Formal evening. Silver and charcoal welcome.",
                "2025-11-15T03:00:00Z", "Rosewood Estate", "4200 Old Oak Lane, Somis",
                "", true, "America/Los_Angeles",
                List.of(
                        hero("hero", "Claire & Marcus", "Twenty-five years", ""),
                        schedule("plan", "The evening", List.of(
                                item("19:00", "Arrival", "Courtyard"),
                                item("20:00", "Dinner", "The conservatory"),
                                item("21:30", "Toasts", "Then dancing")
                        )),
                        map("place", "Rosewood Estate", "The glass conservatory is behind the house."),
                        rsvp("reply", "Kindly reply"),
                        footer("close", "Claire and Marcus")
                ));
    }

    private static TemplateSeed corporateSummit() {
        return seed("CORPORATE", "CORPORATE_SUMMIT", "The summit",
                "A leadership meeting with the hours and the room, and nothing else.",
                "/placeholders/agenda.svg", "#f7f8f8", "#141618", "#1b6c80", "IBM Plex Sans", "IBM Plex Sans",
                "Q4 leadership summit", "Thursday, 20 November 2025",
                "Board strategy meeting. Geneva, with a line for those joining from elsewhere.",
                "2025-11-20T08:00:00Z", "The plenary chamber", "Geneva",
                "", true, "Europe/Zurich",
                List.of(
                        hero("hero", "The summit", "Thursday, 20 November", ""),
                        schedule("plan", "The day", List.of(
                                item("09:00", "Keynote", "Plenary chamber"),
                                item("11:00", "The board", "Closed session"),
                                item("13:00", "Lunch", "Then the actions")
                        )),
                        map("place", "Geneva", "Delegates will receive the room number."),
                        rsvp("reply", "Confirm attendance"),
                        footer("close", "The board")
                ));
    }

    private static TemplateSeed engagementRooftop() {
        return seed("ENGAGEMENT", "ENGAGEMENT_ROOFTOP", "The engagement",
                "Two names, a rooftop, and the hour the light goes.",
                "/placeholders/card.svg", "#fcf9f3", "#161613", "#b56b45", "Public Sans", "Newsreader",
                "Sophia & Liam", "Saturday, 9 August 2025",
                "We are getting married. Summer cocktail. Linen and soft colours.",
                "2025-08-09T22:00:00Z", "Sunset Rooftop", "74 Wythe Avenue, Brooklyn",
                "", true, "America/New_York",
                List.of(
                        hero("hero", "Sophia & Liam", "We are getting married", ""),
                        text("note", "What to wear", "Summer cocktail. Linen, soft hues, silk."),
                        details("details", "The evening"),
                        map("place", "Williamsburg", "The waterfront rooftop."),
                        rsvp("reply", "Celebrate with us"),
                        footer("close", "Sophia and Liam")
                ));
    }

    private static TemplateSeed baptismParish() {
        return seed("BAPTISM", "BAPTISM_PARISH", "The baptism",
                "A name, a Sunday service, and lunch in the garden.",
                "/placeholders/linen.svg", "#fcf9f3", "#161613", "#556254", "Public Sans", "Newsreader",
                "Noah Alexander Smith", "Sunday, 13 July 2025",
                "The baptism of Noah, born 28 March 2025. Service at half past eleven, lunch at one.",
                "2025-07-13T15:30:00Z", "Holy Trinity Parish", "The sanctuary, then the meadow garden",
                "", true, "America/New_York",
                List.of(
                        hero("hero", "Noah Alexander", "Sunday, 13 July", ""),
                        schedule("plan", "The day", List.of(
                                item("11:30", "The baptism", "The sanctuary"),
                                item("13:00", "Lunch", "Meadow garden")
                        )),
                        map("place", "Holy Trinity", "Gather in the sanctuary."),
                        rsvp("reply", "We will be there"),
                        footer("close", "The Smith family")
                ));
    }

    private static TemplateSeed sportsOpener() {
        return seed("SPORTS", "SPORTS_OPENER", "Season opener",
                "A community 5K and a small tournament. Check-in, then the start.",
                "/placeholders/day.svg", "#f7f6f1", "#161613", "#3e4a3d", "Public Sans", "Public Sans",
                "Autumn season opener", "Sunday, 21 September 2025",
                "A 5K and a seven-a-side tournament. Check in at seven. The start is at eight.",
                "2025-09-21T15:00:00Z", "Oakridge Complex", "East meadow and the ridge trails",
                "", true, "America/Los_Angeles",
                List.of(
                        hero("hero", "Season opener", "Sunday, 21 September", ""),
                        schedule("plan", "The morning", List.of(
                                item("07:00", "Check-in", "East meadow"),
                                item("08:00", "The start", "5K, then the first match")
                        )),
                        map("place", "Oakridge", "East meadow and the ridge trails."),
                        rsvp("reply", "I will be there"),
                        footer("close", "Oakridge")
                ));
    }

    private static TemplateSeed memorialNotice() {
        return seed("MEMORIAL", "MEMORIAL_NOTICE", "A notice",
                "A name, the years, and where the service is held.",
                "/placeholders/evening.svg", "#f6f3ed", "#1c1c18", "#484740", "Public Sans", "Newsreader",
                "Arthur James Hollister", "14 November 1942 – 28 February 2025",
                "A life lived with quiet honour. The service is at half past ten.",
                "2025-03-14T14:30:00Z", "St Jude's Chapel", "412 Old Parish Lane, Newburyport",
                "", true, "America/New_York",
                List.of(
                        hero("hero", "Arthur James Hollister", "1942 – 2025", ""),
                        text("note", "In memory", "A life lived with quiet honour, and an open door."),
                        details("details", "The service"),
                        map("place", "St Jude's Chapel", "Seating begins thirty minutes before."),
                        rsvp("reply", "The service"),
                        footer("close", "The Hollister family")
                ));
    }

    private static TemplateSeed seed(
            String category, String code, String name, String description, String thumbnail,
            String background, String ink, String accent, String font, String display,
            String eventName, String subtitle, String eventDescription, String start,
            String venue, String address, String image, boolean rsvp, List<Map<String, Object>> sections
    ) {
        return seed(category, code, name, description, thumbnail, background, ink, accent, font, display,
                eventName, subtitle, eventDescription, start, venue, address, image, rsvp, "Asia/Kolkata", sections);
    }

    private static TemplateSeed seed(
            String category, String code, String name, String description, String thumbnail,
            String background, String ink, String accent, String font, String display,
            String eventName, String subtitle, String eventDescription, String start,
            String venue, String address, String image, boolean rsvp, String timezone,
            List<Map<String, Object>> sections
    ) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("theme", Map.of(
                "background", background,
                "ink", ink,
                "accent", accent,
                "font", font,
                "displayFont", display
        ));
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("name", eventName);
        event.put("subtitle", subtitle);
        event.put("description", eventDescription);
        event.put("startAt", start);
        event.put("endAt", "");
        event.put("timezone", timezone);
        event.put("venueName", venue);
        event.put("venueAddress", address);
        event.put("mapUrl", "https://www.google.com/maps/search/?api=1&query=" + venue.replace(" ", "+"));
        defaults.put("event", event);
        defaults.put("rsvp", Map.of(
                "enabled", rsvp,
                "deadline", "",
                "maxGuestsPerResponse", 4,
                "allowPlusOne", true,
                "collectPhone", true,
                "collectEmail", false,
                "confirmationMessage", "Your reply is with " + eventName + "."
        ));
        defaults.put("sections", sections);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("templateCode", code);
        schema.put("version", 1);
        schema.put("category", category);
        schema.put("fonts", com.prism.typography.FontRegistry.families());
        schema.put("sections", sections.stream().map(section -> Map.of(
                "id", section.get("id"),
                "type", section.get("type"),
                "editableFields", ((Map<?, ?>) section.get("properties")).keySet().stream().toList()
        )).toList());
        return new TemplateSeed(category, code, name, description, thumbnail, schema, defaults);
    }

    private static Map<String, Object> hero(String id, String title, String subtitle, String image) {
        return section(id, "HERO", Map.of("title", title, "subtitle", subtitle, "backgroundImage", image));
    }

    private static Map<String, Object> countdown(String id, String label) {
        return section(id, "COUNTDOWN", Map.of("label", label));
    }

    private static Map<String, Object> text(String id, String heading, String body) {
        return section(id, "TEXT_BLOCK", Map.of("heading", heading, "body", body));
    }

    private static Map<String, Object> details(String id, String heading) {
        return section(id, "EVENT_DETAILS", Map.of("heading", heading));
    }

    private static Map<String, Object> gallery(String id, String caption) {
        return section(id, "IMAGE_GALLERY", Map.of("mediaIds", List.of(), "caption", caption));
    }

    private static Map<String, Object> video(String id, String caption) {
        return section(id, "VIDEO", Map.of("mediaId", "", "caption", caption));
    }

    private static Map<String, Object> map(String id, String heading, String note) {
        return section(id, "LOCATION_MAP", Map.of("heading", heading, "note", note));
    }

    private static Map<String, Object> rsvp(String id, String heading) {
        return section(id, "RSVP_FORM", Map.of("heading", heading));
    }

    private static Map<String, Object> footer(String id, String note) {
        return section(id, "FOOTER", Map.of("note", note));
    }

    private static Map<String, Object> schedule(String id, String heading, List<Map<String, String>> items) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("heading", heading);
        properties.put("items", items);
        return section(id, "EVENT_SCHEDULE", properties);
    }

    private static Map<String, String> item(String time, String title, String detail) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("time", time);
        row.put("title", title);
        row.put("detail", detail);
        return row;
    }

    private static Map<String, Object> section(String id, String type, Map<String, ?> properties) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("id", id);
        section.put("type", type);
        section.put("properties", properties);
        return section;
    }
}
