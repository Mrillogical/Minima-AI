package com.minima-AI.url.shortening;

import java.security.SecureRandom;

// Pure utility class â€” no Spring, no dependencies, fully testable
// Converts a long ID into a short Base62 string and back
public final class Base62 {

    // 62 characters â€” digits + lowercase + uppercase
    // Order matters â€” must never change once URLs are in production
    private static final String ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final int BASE = ALPHABET.length(); // 62

    // CSPRNG, not Random/ThreadLocalRandom â€” slugs must not be predictable
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // 7 chars of Base62 = 62^7 â‰ˆ 3.5 * 10^12 possibilities.
    // At millions of URLs, collision probability stays negligible.
    private static final int RANDOM_SLUG_LENGTH = 7;

    // Private constructor - no one should instantiate this class
    private Base62() {}

    // Generate a random, non-sequential Base62 slug.
    // DO NOT use encode(id) for auto-generated slugs â€” sequential IDs
    // encoded this way are trivially enumerable (id=1,2,3... -> slugs).
    // This is what shorten() should call instead.
    public static String generateRandomSlug() {

        StringBuilder result = new StringBuilder(RANDOM_SLUG_LENGTH);

        for (int i = 0; i < RANDOM_SLUG_LENGTH; i++) {
            result.append(ALPHABET.charAt(SECURE_RANDOM.nextInt(BASE)));
        }

        return result.toString();
    }

    // Encode: Long â†’ Base62 string
    // e.g. 12345 â†’ "dnh"
    public static String encode(long id) {

        if (id <= 0) throw new IllegalArgumentException("ID must be positive: " + id);

        StringBuilder result = new StringBuilder();

        // Repeatedly divide by 62, collect remainders as characters
        // Same logic as converting decimal to binary â€” just base 62
        while (id > 0) {

            result.append(ALPHABET.charAt((int) (id % BASE))); // remainder â†’ character

            id /= BASE; // shrink the number
        }

        // Remainders come out in reverse order â€” flip the string
        return result.reverse().toString();
    }

    // Decode: Base62 string â†’ Long
    // e.g. "dnh" â†’ 12345
    // Used for reversibility â€” slug back to DB id for fast lookup
    public static long decode(String slug) {

        if (slug == null || slug.isEmpty()) {
            throw new IllegalArgumentException("Slug cannot be null or empty");
        }

        long result = 0;

        for (char c : slug.toCharArray()) {

            int digit = ALPHABET.indexOf(c);

            if (digit == -1) {
                throw new IllegalArgumentException("Invalid Base62 character: '" + c + "' in slug: " + slug);
            }

            result = result * BASE + digit;
        }

        return result;
    }
}

