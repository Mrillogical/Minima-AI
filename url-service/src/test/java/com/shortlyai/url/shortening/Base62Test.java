package com.minima-AI.url.shortening;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests â€” no Spring context, no mocks.
 * Base62 is a stateless utility: test the math, not the framework.
 */
class Base62Test {

    @Test
    void encode_idOfOne_returnsFirstAlphabetChar() {

        // ALPHABET[1] = '1'; 1 % 62 = 1 â†’ single char "1"
        assertThat(Base62.encode(1L)).isEqualTo("1");
    }

    @Test
    void encode_exactlyBase_returns10() {

        // 62 in base-62 is "10" - same logic as decimal
        assertThat(Base62.encode(62L)).isEqualTo("10");
    }

    @Test
    void encode_producesAlphanumericSlugOnly() {

        // Slug must be URL-safe - only [0-9a-zA-Z]
        assertThat(Base62.encode(99999L)).matches("[0-9a-zA-Z]+");
    }

    @Test
    void encode_zero_throwsIllegalArgumentException() {

        // DB IDs start at 1; 0 is invalid
        assertThatThrownBy(() -> Base62.encode(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void encode_negativeId_throwsIllegalArgumentException() {

        // Negative ID = programmer error
        assertThatThrownBy(() -> Base62.encode(-1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void roundtrip_smallId_decodesBackToOriginal() {

        long id = 12345L;

        assertThat(Base62.decode(Base62.encode(id))).isEqualTo(id);
    }

    @Test
    void roundtrip_largeId_decodesBackToOriginal() {

        // IDs can grow large in prod - 10B rows test upper bound
        long id = 10_000_000_000L;

        assertThat(Base62.decode(Base62.encode(id))).isEqualTo(id);
    }

    @Test
    void roundtrip_sequentialIds_produceDifferentSlugs() {

        // Consecutive IDs must never collide - Base62 is injective
        String s1 = Base62.encode(1L);

        String s2 = Base62.encode(2L);

        String s3 = Base62.encode(3L);

        assertThat(s1).isNotEqualTo(s2);

        assertThat(s2).isNotEqualTo(s3);
    }

    @Test
    void encode_result_isShortEnoughForSlugColumn() {

        // VARCHAR(20) constraint - even a huge ID must fit
        // Long.MAX_VALUE = 9223372036854775807 -> 11 chars in base-62
        String slug = Base62.encode(Long.MAX_VALUE);

        assertThat(slug.length()).isLessThanOrEqualTo(20);
    }

    @Test
    void generateRandomSlug_isSevenCharsAlphanumeric() {

        String slug = Base62.generateRandomSlug();

        assertThat(slug).hasSize(7);
        assertThat(slug).matches("[0-9a-zA-Z]+");
    }

    @Test
    void generateRandomSlug_repeatedCalls_areNotSequential() {

        // Regression guard for the enumeration bug: consecutive calls
        // must not look like Base62.encode(1), encode(2), encode(3)...
        String s1 = Base62.generateRandomSlug();
        String s2 = Base62.generateRandomSlug();
        String s3 = Base62.generateRandomSlug();

        assertThat(s1).isNotEqualTo(Base62.encode(1L));
        assertThat(s2).isNotEqualTo(Base62.encode(2L));
        assertThat(s3).isNotEqualTo(Base62.encode(3L));

        // Extremely unlikely all three collide with each other
        assertThat(java.util.Set.of(s1, s2, s3)).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void generateRandomSlug_manyCalls_haveNoCollisionsInSmallSample() {

        // Sanity check on entropy, not a proof - 62^7 space vs 10k samples
        java.util.Set<String> slugs = new java.util.HashSet<>();

        for (int i = 0; i < 10_000; i++) {
            slugs.add(Base62.generateRandomSlug());
        }

        assertThat(slugs).hasSize(10_000);
    }
}

