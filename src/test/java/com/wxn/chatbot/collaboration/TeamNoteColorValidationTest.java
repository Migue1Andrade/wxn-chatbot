package com.wxn.chatbot.collaboration;

import static org.assertj.core.api.Assertions.assertThat;

import com.wxn.chatbot.collaboration.dto.TeamDtos.Create;
import com.wxn.chatbot.collaboration.dto.TeamDtos.Edit;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TeamNoteColorValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsPaletteColorsAndLegacyMissingColorButRejectsArbitraryValues() {
        Create valid = new Create("Nota de teste", "operation-id-123456", "LAVENDER");
        Create legacy = new Create("Nota antiga", "operation-id-123456", null);
        Create invalid = new Create("Nota", "operation-id-123456", "#ffffff");
        Edit legacyEdit = new Edit("Texto editado", false, 0, null);

        assertThat(validator.validate(valid)).isEmpty();
        assertThat(validator.validate(legacy)).isEmpty();
        assertThat(validator.validate(legacyEdit)).isEmpty();
        assertThat(validator.validate(invalid)).isNotEmpty();
    }
}
